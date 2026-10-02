#![cfg(target_os = "linux")]

//! Клиент привилегированного хелпера для Linux.
//!
//! GUI работает под обычным пользователем и не может создать TUN-интерфейс,
//! поэтому запуск ядра в режиме туннеля делегируется хелперу (`nimbo-svc`,
//! systemd-юнит от root). Здесь только транспорт: соединение, отправка
//! команды и разбор ответа.
//!
//! Соединение держится открытым на всё время туннеля намеренно: если Nimbo
//! упадёт, хелпер увидит разрыв и сам погасит туннель, вернув маршруты.

use sha2::{Digest, Sha256};
use std::io::{BufReader, BufWriter};
use std::os::unix::net::UnixStream;
use std::path::{Path, PathBuf};
use std::time::Duration;
use tauri::{AppHandle, Manager};

use nimbo_ipc::{
    decode_response, encode_command, framing, Command, Response, TunRequest, TunState,
    UNIX_SOCKET_PATH,
};

/// Ответ хелпера ждём недолго: поднятие туннеля упирается в появление
/// интерфейса, у которого свой таймаут на той стороне.
const IO_TIMEOUT: Duration = Duration::from_secs(30);
static SERVICE_SETUP: std::sync::Mutex<()> = std::sync::Mutex::new(());

/// Живое соединение с хелпером. Пока структура жива — туннель поднят.
pub struct TunSession {
    stream: UnixStream,
}

impl TunSession {
    /// Поднимает туннель. Ошибку возвращаем текстом: она уходит прямо в
    /// интерфейс, поэтому важнее понятность, чем типизация.
    pub fn up(request: TunRequest) -> Result<Self, String> {
        let stream = connect()?;
        let mut session = Self { stream };
        match session.call(&Command::TunUp(request))? {
            Response::TunState(state) if state.up => Ok(session),
            Response::TunState(state) => Err(state
                .last_error
                .unwrap_or_else(|| "Хелпер не смог поднять туннель.".into())),
            Response::Error { message, .. } => Err(message),
            _ => Err("Хелпер вернул неожиданный ответ на запуск туннеля.".into()),
        }
    }

    /// Гасит туннель явно. Даже если вызов не прошёл, закрытие соединения
    /// само приведёт к тому же результату на стороне хелпера.
    pub fn down(&mut self) {
        let _ = self.call(&Command::TunDown);
    }

    fn call(&mut self, command: &Command) -> Result<Response, String> {
        call(&self.stream, command)
    }
}

fn call(stream: &UnixStream, command: &Command) -> Result<Response, String> {
    let payload =
        encode_command(command).map_err(|e| format!("Не удалось собрать команду: {e}"))?;
    framing::write_frame(&mut BufWriter::new(stream), &payload)
        .map_err(|e| format!("Хелпер недоступен: {e}"))?;
    let frame = framing::read_frame(&mut BufReader::new(stream))
        .map_err(|e| format!("Хелпер не ответил: {e}"))?;
    decode_response(&frame).map_err(|e| format!("Некорректный ответ хелпера: {e}"))
}

impl Drop for TunSession {
    fn drop(&mut self) {
        self.down();
    }
}

/// Установлен ли хелпер. Проверяем по сокету: юнит мог быть выключен, тогда
/// сокета нет и подключаться некуда.
pub fn is_available() -> bool {
    Path::new(UNIX_SOCKET_PATH).exists()
}

fn connect() -> Result<UnixStream, String> {
    if !is_available() {
        return Err(
            "Служба Nimbo для TUN не запущена. Переустановите Nimbo или запустите её командой \
             `sudo systemctl enable --now nimbo-helper`."
                .into(),
        );
    }
    let stream = UnixStream::connect(UNIX_SOCKET_PATH)
        .map_err(|e| format!("Не удалось соединиться со службой Nimbo: {e}"))?;
    stream
        .set_read_timeout(Some(IO_TIMEOUT))
        .and_then(|_| stream.set_write_timeout(Some(IO_TIMEOUT)))
        .map_err(|e| format!("Не удалось настроить соединение со службой: {e}"))?;
    Ok(stream)
}

/// Состояние службы: готово ли ядро в защищённом каталоге. По нему интерфейс
/// понимает, нужно ли один раз попросить права на установку ядра.
pub fn status() -> Result<TunState, String> {
    let stream = connect()?;
    match call(&stream, &Command::GetStatus)? {
        Response::TunState(state) => Ok(state),
        Response::Error { message, .. } => Err(message),
        _ => Err("Служба вернула неожиданный ответ.".into()),
    }
}

/// Кладёт ядро в каталог службы. Требует прав root, поэтому идём через
/// pkexec: пользователь подтверждает операцию системным окном.
pub fn install_core(app: &AppHandle, source: &std::path::Path) -> Result<(), String> {
    let helper = helper_binary(app)?;
    let status = std::process::Command::new("pkexec")
        .arg(&helper)
        .arg("--install-core")
        .arg(source)
        .status()
        .map_err(|e| format!("Не удалось запустить pkexec: {e}"))?;
    if !status.success() {
        return Err("Установка ядра отменена или не удалась.".into());
    }
    Ok(())
}

const INSTALLED_HELPER: &str = "/usr/local/lib/nimbo/nimbo-svc";
const SERVICE_UNIT: &str = "/etc/systemd/system/nimbo-helper.service";

fn verified(path: &Path, expected: &str) -> bool {
    if expected.is_empty() {
        return false;
    }
    let Ok(metadata) = std::fs::metadata(path) else {
        return false;
    };
    use std::os::unix::fs::PermissionsExt;
    if !metadata.is_file()
        || metadata.len() > 64 * 1024 * 1024
        || metadata.permissions().mode() & 0o111 == 0
    {
        return false;
    }
    std::fs::read(path)
        .map(|bytes| format!("{:x}", Sha256::digest(bytes)) == expected)
        .unwrap_or(false)
}

fn expected_digest() -> &'static str {
    option_env!("NIMBO_LINUX_HELPER_SHA256").unwrap_or("")
}

fn helper_binary(app: &AppHandle) -> Result<PathBuf, String> {
    let mut candidates = Vec::new();
    if let Ok(exe) = std::env::current_exe() {
        if let Some(directory) = exe.parent() {
            candidates.push(directory.join("nimbo-svc"));
        }
    }
    if let Ok(resources) = app.path().resource_dir() {
        candidates.push(resources.join("resources/helper/linux/nimbo-svc"));
        candidates.push(resources.join("nimbo-svc"));
    }
    candidates.push(PathBuf::from(INSTALLED_HELPER));
    #[cfg(debug_assertions)]
    candidates.push(Path::new(env!("CARGO_MANIFEST_DIR")).join("resources/helper/linux/nimbo-svc"));
    candidates
        .into_iter()
        .find(|path| verified(path, expected_digest()))
        .ok_or_else(|| "Проверенный компонент TUN не найден. Переустановите пакет Nimbo.".into())
}

// Reading status never elevates privileges or sends TunDown.
pub fn helper_status(app: &AppHandle) -> serde_json::Value {
    let binary = helper_binary(app).ok();
    let current = verified(Path::new(INSTALLED_HELPER), expected_digest());
    serde_json::json!({
        "installed": Path::new(SERVICE_UNIT).is_file() && current,
        "running": is_available(),
        "version": if current { Some(env!("CARGO_PKG_VERSION")) } else { None },
        "exe_present": binary.is_some(),
        "exe_path": binary.map(|path| path.to_string_lossy().into_owned()),
    })
}

fn ping() -> Result<(), String> {
    let stream = connect()?;
    let _ = stream.set_read_timeout(Some(Duration::from_secs(1)));
    let _ = stream.set_write_timeout(Some(Duration::from_secs(1)));
    match call(&stream, &Command::Ping)? {
        Response::Pong { protocol, .. } if protocol == nimbo_ipc::PROTOCOL_VERSION => Ok(()),
        Response::Error { message, .. } => Err(message),
        _ => Err("Служба TUN использует несовместимый протокол.".into()),
    }
}

pub fn ensure_service(app: &AppHandle) -> Result<(), String> {
    let _guard = SERVICE_SETUP
        .lock()
        .map_err(|_| "Не удалось подготовить службу TUN".to_string())?;
    if verified(Path::new(INSTALLED_HELPER), expected_digest())
        && Path::new(SERVICE_UNIT).is_file()
        && ping().is_ok()
    {
        return Ok(());
    }
    let helper = helper_binary(app)?;
    let uid = unsafe { libc::getuid() }.to_string();
    let result = std::process::Command::new("pkexec")
        .arg(&helper)
        .arg("--install-service")
        .arg(uid)
        .status()
        .map_err(|e| format!("Не удалось открыть системное подтверждение: {e}"))?;
    if !result.success() {
        return Err("Установка службы TUN отменена или не удалась.".into());
    }
    if !verified(Path::new(INSTALLED_HELPER), expected_digest()) {
        return Err("Установленный компонент TUN не совпадает с пакетом.".into());
    }
    let deadline = std::time::Instant::now() + Duration::from_secs(5);
    loop {
        if ping().is_ok() {
            return Ok(());
        }
        if std::time::Instant::now() >= deadline {
            return Err("Служба TUN установлена, но не отвечает. Проверьте systemd.".into());
        }
        std::thread::sleep(Duration::from_millis(100));
    }
}

pub fn uninstall_service(app: &AppHandle) -> Result<(), String> {
    let _guard = SERVICE_SETUP
        .lock()
        .map_err(|_| "Не удалось удалить службу TUN".to_string())?;
    if is_available() && status()?.up {
        return Err("Отключите VPN перед удалением службы TUN.".into());
    }
    let result = std::process::Command::new("pkexec")
        .arg(helper_binary(app)?)
        .arg("--uninstall-service")
        .status()
        .map_err(|e| format!("Не удалось открыть системное подтверждение: {e}"))?;
    if result.success() {
        Ok(())
    } else {
        Err("Удаление службы TUN отменено или не удалось.".into())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn status_query_never_sends_tunnel_down() {
        let (client, server) = UnixStream::pair().unwrap();
        let worker = std::thread::spawn(move || {
            let frame = framing::read_frame(&mut BufReader::new(&server)).unwrap();
            assert!(matches!(
                nimbo_ipc::decode_command(&frame).unwrap(),
                Command::GetStatus
            ));
            let reply = Response::TunState(TunState {
                up: true,
                interface: Some("test-tun".into()),
                last_error: None,
                core_ready: true,
            });
            framing::write_frame(
                &mut BufWriter::new(&server),
                &nimbo_ipc::encode_response(&reply).unwrap(),
            )
            .unwrap();
            assert!(framing::read_frame(&mut BufReader::new(&server)).is_err());
        });
        assert!(matches!(
            call(&client, &Command::GetStatus).unwrap(),
            Response::TunState(TunState { up: true, .. })
        ));
        drop(client);
        worker.join().unwrap();
    }
}
