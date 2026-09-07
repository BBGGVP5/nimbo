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

use std::io::{BufReader, BufWriter};
use std::os::unix::net::UnixStream;
use std::path::Path;
use std::time::Duration;

use nimbo_ipc::{
    decode_response, encode_command, framing, Command, Response, TunRequest, TunState,
    UNIX_SOCKET_PATH,
};

/// Ответ хелпера ждём недолго: поднятие туннеля упирается в появление
/// интерфейса, у которого свой таймаут на той стороне.
const IO_TIMEOUT: Duration = Duration::from_secs(30);

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
        let payload =
            encode_command(command).map_err(|e| format!("Не удалось собрать команду: {e}"))?;
        {
            let mut writer = BufWriter::new(&self.stream);
            framing::write_frame(&mut writer, &payload)
                .map_err(|e| format!("Хелпер недоступен: {e}"))?;
        }
        let mut reader = BufReader::new(&self.stream);
        let frame =
            framing::read_frame(&mut reader).map_err(|e| format!("Хелпер не ответил: {e}"))?;
        decode_response(&frame).map_err(|e| format!("Некорректный ответ хелпера: {e}"))
    }
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
    let mut session = TunSession { stream };
    match session.call(&Command::GetStatus)? {
        Response::TunState(state) => Ok(state),
        Response::Error { message, .. } => Err(message),
        _ => Err("Служба вернула неожиданный ответ.".into()),
    }
}

/// Кладёт ядро в каталог службы. Требует прав root, поэтому идём через
/// pkexec: пользователь подтверждает операцию системным окном.
pub fn install_core(source: &std::path::Path) -> Result<(), String> {
    let helper = helper_binary()?;
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

/// Хелпер лежит рядом с приложением — установщик кладёт их вместе.
fn helper_binary() -> Result<std::path::PathBuf, String> {
    let exe = std::env::current_exe()
        .map_err(|e| format!("Не удалось определить путь Nimbo: {e}"))?;
    let dir = exe
        .parent()
        .ok_or_else(|| "Не удалось определить папку Nimbo.".to_string())?;
    let helper = dir.join("nimbo-svc");
    if helper.is_file() {
        Ok(helper)
    } else {
        Err("Служба Nimbo не найдена рядом с приложением. Переустановите Nimbo.".into())
    }
}
