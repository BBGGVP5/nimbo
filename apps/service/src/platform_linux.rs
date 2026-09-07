#![cfg(target_os = "linux")]

//! Привилегированная часть Nimbo для Linux.
//!
//! Повторяет роль windows-сервиса: GUI работает под обычным пользователем и
//! не умеет создавать TUN-интерфейс, поэтому всё, что требует root, живёт
//! здесь. Хелпер слушает Unix-сокет, поднимает ядро с готовым конфигом,
//! следит за ним и возвращает маршруты на место, когда туннель гаснет —
//! в том числе если GUI упал и просто закрыл соединение.
//!
//! Протокол общий с Windows (`nimbo-ipc`), меняется только транспорт.

use std::fs;
use std::io::{BufReader, BufWriter};
use std::os::unix::fs::{MetadataExt, PermissionsExt};
use std::os::unix::net::{UnixListener, UnixStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use anyhow::{anyhow, Context, Result};
use nimbo_ipc::{
    decode_command, encode_response, framing, Command as IpcCommand, ErrorCode, Response, TunRequest,
    TunState, PROTOCOL_VERSION, UNIX_ALLOWED_UID_PATH, UNIX_SOCKET_PATH,
};
use tracing::{info, warn};

const VERSION: &str = env!("CARGO_PKG_VERSION");
const SERVICE_UNIT: &str = "nimbo-helper.service";
const UNIT_PATH: &str = "/etc/systemd/system/nimbo-helper.service";

/// Сколько ждём появления интерфейса после запуска ядра. Ядро поднимает TUN
/// не мгновенно, а без интерфейса маршруты ставить некуда.
const TUN_WAIT: Duration = Duration::from_secs(10);

/// Каталог хелпера: сюда кладётся ядро и сюда же пишется конфиг. Оба файла
/// должны быть недоступны на запись обычному пользователю — иначе он смог бы
/// подменить то, что хелпер запускает от root.
const CORE_DIR: &str = "/usr/local/lib/nimbo";
const CORE_NAME: &str = "xray";
const RUNTIME_CONFIG: &str = "/run/nimbo/core.json";

/// Имя таблицы nftables для kill switch. Отдельная таблица позволяет снять
/// правила целиком и не задеть чужие.
const NFT_TABLE: &str = "nimbo_killswitch";

pub fn run() -> Result<()> {
    let args: Vec<String> = std::env::args().collect();
    init_tracing();

    match args.get(1).map(String::as_str) {
        Some("--install-service") => install_service(args.get(2).map(String::as_str)),
        Some("--uninstall-service") => uninstall_service(),
        Some("--install-core") => match args.get(2) {
            Some(source) => install_core(Path::new(source)),
            None => Err(anyhow!("не указан путь к ядру")),
        },
        Some("--version") => {
            println!("{VERSION}");
            Ok(())
        }
        _ => serve(),
    }
}

fn init_tracing() {
    let _ = tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .with_target(false)
        .try_init();
}

// ─────────────────────────────────────────────────────────── установка

/// Ставит юнит systemd. Запускается установщиком под root: сам хелпер
/// повышать права не умеет и не должен.
fn install_service(allowed_uid: Option<&str>) -> Result<()> {
    require_root()?;
    let exe = std::env::current_exe().context("не удалось определить путь хелпера")?;

    if let Some(uid) = allowed_uid {
        let uid: u32 = uid.parse().context("uid владельца должен быть числом")?;
        write_allowed_uid(uid)?;
    }

    let unit = format!(
        "[Unit]\n\
         Description=Nimbo helper (TUN)\n\
         After=network.target\n\
         \n\
         [Service]\n\
         Type=simple\n\
         ExecStart={exe}\n\
         Restart=on-failure\n\
         RestartSec=2\n\
         RuntimeDirectory=nimbo\n\
         RuntimeDirectoryMode=0755\n\
         \n\
         [Install]\n\
         WantedBy=multi-user.target\n",
        exe = exe.display()
    );
    fs::write(UNIT_PATH, unit).context("не удалось записать юнит systemd")?;

    systemctl(&["daemon-reload"])?;
    systemctl(&["enable", "--now", SERVICE_UNIT])?;
    info!("nimbo helper installed");
    Ok(())
}

fn uninstall_service() -> Result<()> {
    require_root()?;
    // Ошибки на остановке не фатальны: юнита может уже не быть.
    let _ = systemctl(&["disable", "--now", SERVICE_UNIT]);
    let _ = fs::remove_file(UNIT_PATH);
    let _ = fs::remove_file(UNIX_ALLOWED_UID_PATH);
    let _ = systemctl(&["daemon-reload"]);
    info!("nimbo helper removed");
    Ok(())
}

fn systemctl(args: &[&str]) -> Result<()> {
    let status = Command::new("systemctl")
        .args(args)
        .status()
        .context("не удалось вызвать systemctl")?;
    if !status.success() {
        return Err(anyhow!("systemctl {:?} завершился с ошибкой", args));
    }
    Ok(())
}

fn require_root() -> Result<()> {
    if unsafe { libc::geteuid() } != 0 {
        return Err(anyhow!("операция требует прав root"));
    }
    Ok(())
}

fn write_allowed_uid(uid: u32) -> Result<()> {
    if let Some(parent) = Path::new(UNIX_ALLOWED_UID_PATH).parent() {
        fs::create_dir_all(parent).context("не удалось создать /etc/nimbo")?;
    }
    fs::write(UNIX_ALLOWED_UID_PATH, uid.to_string()).context("не удалось сохранить uid")?;
    Ok(())
}

/// Кому позволено командовать хелпером. Пустой файл означает «только root»:
/// лучше отказать, чем открыть туннель произвольному пользователю машины.
fn allowed_uid() -> Option<u32> {
    fs::read_to_string(UNIX_ALLOWED_UID_PATH)
        .ok()
        .and_then(|raw| raw.trim().parse().ok())
}


// ─────────────────────────────────────────────────────────── ядро

fn core_path() -> PathBuf {
    Path::new(CORE_DIR).join(CORE_NAME)
}

/// Ядро готово, только если файл принадлежит root и в него не может писать
/// никто другой. Иначе запуск от root — это чужой код с правами root.
fn core_is_secure(path: &Path) -> bool {
    let Ok(meta) = fs::metadata(path) else {
        return false;
    };
    if !meta.is_file() {
        return false;
    }
    let mode = meta.permissions().mode();
    meta.uid() == 0 && mode & 0o022 == 0
}

/// Копирует ядро в каталог хелпера. Вызывается под root через pkexec, то есть
/// пользователь подтвердил операцию явно.
fn install_core(source: &Path) -> Result<()> {
    require_root()?;
    if !source.is_file() {
        return Err(anyhow!("файл ядра не найден: {}", source.display()));
    }
    fs::create_dir_all(CORE_DIR).context("не удалось создать каталог ядра")?;
    fs::set_permissions(CORE_DIR, fs::Permissions::from_mode(0o755))
        .context("не удалось выставить права на каталог ядра")?;

    let target = core_path();
    // Пишем во временный файл рядом и переименовываем: подменить файл между
    // копированием и запуском так не получится.
    let staging = target.with_extension("new");
    fs::copy(source, &staging).context("не удалось скопировать ядро")?;
    fs::set_permissions(&staging, fs::Permissions::from_mode(0o755))
        .context("не удалось выставить права на ядро")?;
    fs::rename(&staging, &target).context("не удалось заменить ядро")?;
    info!(path = %target.display(), "core installed");
    Ok(())
}

// ─────────────────────────────────────────────────────────────── сервер

fn serve() -> Result<()> {
    require_root().context("хелпер должен работать от root")?;
    info!(version = VERSION, "nimbo helper starting");

    let socket = Path::new(UNIX_SOCKET_PATH);
    if let Some(parent) = socket.parent() {
        fs::create_dir_all(parent).context("не удалось создать каталог сокета")?;
    }
    // Сокет мог остаться от прошлого запуска: bind на существующий путь падает.
    let _ = fs::remove_file(socket);

    let listener = UnixListener::bind(socket).context("не удалось открыть сокет")?;
    // Доступ ограничивает не режим файла, а проверка uid клиента ниже.
    fs::set_permissions(socket, fs::Permissions::from_mode(0o666))
        .context("не удалось выставить права на сокет")?;

    let tunnel = Arc::new(Tunnel::default());
    let shutdown = Arc::new(AtomicBool::new(false));

    for stream in listener.incoming() {
        if shutdown.load(Ordering::SeqCst) {
            break;
        }
        match stream {
            Ok(stream) => {
                let tunnel = Arc::clone(&tunnel);
                let shutdown = Arc::clone(&shutdown);
                std::thread::spawn(move || {
                    if let Err(error) = handle_client(stream, &tunnel, &shutdown) {
                        warn!(%error, "client session failed");
                    }
                });
            }
            Err(error) => warn!(%error, "accept failed"),
        }
    }

    tunnel.down();
    let _ = fs::remove_file(socket);
    Ok(())
}

fn handle_client(stream: UnixStream, tunnel: &Tunnel, shutdown: &AtomicBool) -> Result<()> {
    let peer_uid = peer_uid(&stream)?;
    let expected = allowed_uid();
    if expected != Some(peer_uid) && peer_uid != 0 {
        warn!(peer_uid, "rejected: uid is not allowed");
        let mut writer = BufWriter::new(&stream);
        let payload = encode_response(&Response::Error {
            code: ErrorCode::PermissionDenied,
            message: "Этот пользователь не владеет установкой Nimbo.".into(),
        })?;
        framing::write_frame(&mut writer, &payload)?;
        return Ok(());
    }

    let mut reader = BufReader::new(stream.try_clone()?);
    let mut writer = BufWriter::new(stream);

    loop {
        let frame = match framing::read_frame(&mut reader) {
            Ok(frame) => frame,
            // Клиент отключился — гасим туннель, чтобы упавший GUI не оставил
            // систему с маршрутами в никуда.
            Err(_) => {
                if tunnel.is_up() {
                    info!("client disconnected, tearing tunnel down");
                    tunnel.down();
                }
                return Ok(());
            }
        };

        let response = match decode_command(&frame) {
            Ok(command) => dispatch(command, tunnel, shutdown),
            Err(error) => Response::Error {
                code: ErrorCode::InvalidPayload,
                message: error.to_string(),
            },
        };

        let payload = encode_response(&response)?;
        framing::write_frame(&mut writer, &payload)?;
    }
}

fn dispatch(command: IpcCommand, tunnel: &Tunnel, shutdown: &AtomicBool) -> Response {
    match command {
        IpcCommand::Ping => Response::Pong {
            service_version: VERSION.to_string(),
            protocol: PROTOCOL_VERSION,
        },
        IpcCommand::TunUp(request) => match tunnel.up(request) {
            Ok(state) => Response::TunState(state),
            Err(error) => Response::Error {
                code: ErrorCode::TunFailed,
                message: error.to_string(),
            },
        },
        IpcCommand::TunDown => {
            tunnel.down();
            Response::TunState(tunnel.state())
        }
        IpcCommand::GetStatus => Response::TunState(tunnel.state()),
        IpcCommand::InstallCore { .. } => Response::Error {
            code: ErrorCode::PermissionDenied,
            message: "Установка ядра выполняется отдельной командой под root.".into(),
        },
        IpcCommand::Shutdown => {
            shutdown.store(true, Ordering::SeqCst);
            tunnel.down();
            Response::Ok
        }
        _ => Response::Error {
            code: ErrorCode::UnknownCommand,
            message: "Команда не поддерживается linux-хелпером.".into(),
        },
    }
}

/// uid клиента через SO_PEERCRED. Стандартный `peer_cred` пока nightly,
/// поэтому спрашиваем ядро напрямую — данные заполняет оно само, подделать
/// их со стороны клиента нельзя.
fn peer_uid(stream: &UnixStream) -> Result<u32> {
    use std::os::unix::io::AsRawFd;

    let mut creds = libc::ucred {
        pid: 0,
        uid: 0,
        gid: 0,
    };
    let mut len = std::mem::size_of::<libc::ucred>() as libc::socklen_t;
    // SAFETY: fd принадлежит живому сокету, размер буфера совпадает с типом.
    let rc = unsafe {
        libc::getsockopt(
            stream.as_raw_fd(),
            libc::SOL_SOCKET,
            libc::SO_PEERCRED,
            &mut creds as *mut libc::ucred as *mut libc::c_void,
            &mut len,
        )
    };
    if rc != 0 {
        return Err(anyhow!(
            "не удалось прочитать учётные данные клиента: {}",
            std::io::Error::last_os_error()
        ));
    }
    Ok(creds.uid)
}

// ─────────────────────────────────────────────────────────────── туннель

#[derive(Default)]
struct Tunnel {
    inner: Mutex<Option<Running>>,
}

struct Running {
    child: Child,
    interface: String,
    routes: RouteBackup,
    dns: DnsBackup,
    kill_switch: bool,
}

impl Running {
    /// Снимает всё, что успели навесить. Порядок важен: сначала убираем
    /// блокировку, иначе пользователь останется без сети, если следующий шаг
    /// упадёт.
    fn teardown(&mut self) {
        if self.kill_switch {
            clear_kill_switch();
            self.kill_switch = false;
        }
        let _ = self.child.kill();
        let _ = self.child.wait();
        self.dns.restore();
        self.routes.restore();
        let _ = fs::remove_file(RUNTIME_CONFIG);
    }
}

impl Tunnel {
    fn is_up(&self) -> bool {
        self.inner.lock().map(|guard| guard.is_some()).unwrap_or(false)
    }

    fn state(&self) -> TunState {
        let guard = self.inner.lock().ok();
        let interface = guard
            .as_ref()
            .and_then(|inner| inner.as_ref().map(|running| running.interface.clone()));
        TunState {
            up: interface.is_some(),
            interface,
            last_error: None,
            core_ready: core_is_secure(&core_path()),
        }
    }

    fn up(&self, request: TunRequest) -> Result<TunState> {
        self.down();

        let core = core_path();
        if !core_is_secure(&core) {
            return Err(anyhow!(
                "ядро Nimbo не установлено в защищённый каталог. Переустановите Nimbo."
            ));
        }

        // Конфиг пишем сами в свой каталог: файл в домашней папке пользователь
        // может подменить в момент между проверкой и запуском.
        let config_path = Path::new(RUNTIME_CONFIG);
        if let Some(parent) = config_path.parent() {
            fs::create_dir_all(parent).context("не удалось создать каталог конфига")?;
        }
        fs::write(config_path, request.config.as_bytes())
            .context("не удалось записать конфиг ядра")?;
        fs::set_permissions(config_path, fs::Permissions::from_mode(0o600))
            .context("не удалось выставить права на конфиг")?;

        let routes = RouteBackup::capture(&request.bypass_ips)?;
        let mut dns = DnsBackup::capture();
        dns.interface = Some(request.interface.clone());

        let child = Command::new(&core)
            .arg("run")
            .arg("-c")
            .arg(config_path)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()
            .context("не удалось запустить ядро")?;

        let mut running = Running {
            child,
            interface: request.interface.clone(),
            routes,
            dns,
            kill_switch: false,
        };

        if let Err(error) = wait_for_interface(&request.interface) {
            running.teardown();
            return Err(error);
        }

        running.routes.apply_bypass();
        running.dns.apply(&request.dns);
        if request.kill_switch {
            running.kill_switch = apply_kill_switch(&request.interface, &request.bypass_ips);
            if !running.kill_switch {
                warn!("kill switch не включён: nftables недоступен");
            }
        }

        let interface = running.interface.clone();
        if let Ok(mut guard) = self.inner.lock() {
            *guard = Some(running);
        }
        info!(%interface, "tunnel is up");
        Ok(TunState {
            up: true,
            interface: Some(interface),
            last_error: None,
            core_ready: true,
        })
    }

    fn down(&self) {
        let Ok(mut guard) = self.inner.lock() else {
            return;
        };
        if let Some(mut running) = guard.take() {
            let interface = running.interface.clone();
            running.teardown();
            info!(%interface, "tunnel is down");
        }
    }
}

fn wait_for_interface(name: &str) -> Result<()> {
    let deadline = Instant::now() + TUN_WAIT;
    let path = PathBuf::from("/sys/class/net").join(name);
    while Instant::now() < deadline {
        if path.exists() {
            return Ok(());
        }
        std::thread::sleep(Duration::from_millis(150));
    }
    Err(anyhow!(
        "интерфейс {name} не появился за {} с",
        TUN_WAIT.as_secs()
    ))
}

/// Маршруты в обход туннеля: трафик до самого VPN-сервера обязан идти через
/// исходный шлюз, иначе получается петля. Храним, что добавили, и снимаем
/// ровно это — чужие маршруты не трогаем.
struct RouteBackup {
    gateway: Option<String>,
    device: Option<String>,
    bypass_ips: Vec<String>,
    applied: Vec<String>,
}

impl RouteBackup {
    fn capture(bypass_ips: &[String]) -> Result<Self> {
        let (gateway, device) = default_route();
        Ok(Self {
            gateway,
            device,
            bypass_ips: bypass_ips.to_vec(),
            applied: Vec::new(),
        })
    }

    fn apply_bypass(&mut self) {
        let (Some(gateway), Some(device)) = (self.gateway.clone(), self.device.clone()) else {
            warn!("маршрут по умолчанию не найден, обход сервера не настроен");
            return;
        };
        for ip in self.bypass_ips.clone() {
            let ok = Command::new("ip")
                .args(["route", "add", &ip, "via", &gateway, "dev", &device])
                .status()
                .map(|status| status.success())
                .unwrap_or(false);
            if ok {
                self.applied.push(ip);
            }
        }
    }

    fn restore(&mut self) {
        for ip in self.applied.drain(..) {
            let _ = Command::new("ip").args(["route", "del", &ip]).status();
        }
    }
}

fn default_route() -> (Option<String>, Option<String>) {
    let output = Command::new("ip")
        .args(["route", "show", "default"])
        .output()
        .ok();
    let Some(output) = output else {
        return (None, None);
    };
    let text = String::from_utf8_lossy(&output.stdout);
    let line = text.lines().next().unwrap_or_default();
    let mut gateway = None;
    let mut device = None;
    let mut parts = line.split_whitespace();
    while let Some(token) = parts.next() {
        match token {
            "via" => gateway = parts.next().map(str::to_string),
            "dev" => device = parts.next().map(str::to_string),
            _ => {}
        }
    }
    (gateway, device)
}

// ─────────────────────────────────────────────────────────────────── DNS

/// Подмена DNS на время туннеля. Без неё запросы уходят мимо VPN: маршруты
/// завёрнуты, а резолвер продолжает спрашивать провайдерский сервер.
///
/// Сначала пробуем systemd-resolved — он есть в большинстве современных
/// дистрибутивов и умеет откатываться сам. Если его нет, правим resolv.conf,
/// сохранив прежний файл.
struct DnsBackup {
    interface: Option<String>,
    resolv_backup: Option<Vec<u8>>,
    used_resolvectl: bool,
}

impl DnsBackup {
    fn capture() -> Self {
        Self {
            interface: None,
            resolv_backup: None,
            used_resolvectl: false,
        }
    }

    fn apply(&mut self, servers: &[String]) {
        if servers.is_empty() {
            return;
        }
        if self.apply_resolvectl(servers) {
            self.used_resolvectl = true;
            return;
        }
        self.apply_resolv_conf(servers);
    }

    fn apply_resolvectl(&mut self, servers: &[String]) -> bool {
        let Some(interface) = self.interface.clone() else {
            return false;
        };
        let mut command = Command::new("resolvectl");
        command.arg("dns").arg(&interface);
        for server in servers {
            command.arg(server);
        }
        let ok = command
            .status()
            .map(|status| status.success())
            .unwrap_or(false);
        if ok {
            // Домен по умолчанию отправляет все запросы в туннель.
            let _ = Command::new("resolvectl")
                .args(["domain", &interface, "~."])
                .status();
        }
        ok
    }

    fn apply_resolv_conf(&mut self, servers: &[String]) {
        let path = Path::new("/etc/resolv.conf");
        if let Ok(current) = fs::read(path) {
            self.resolv_backup = Some(current);
        }
        let body = servers
            .iter()
            .map(|server| format!("nameserver {server}
"))
            .collect::<String>();
        if fs::write(path, body).is_err() {
            warn!("не удалось подменить resolv.conf, DNS остаётся системным");
            self.resolv_backup = None;
        }
    }

    fn restore(&mut self) {
        if self.used_resolvectl {
            if let Some(interface) = self.interface.clone() {
                let _ = Command::new("resolvectl").args(["revert", &interface]).status();
            }
            self.used_resolvectl = false;
        }
        if let Some(backup) = self.resolv_backup.take() {
            let _ = fs::write("/etc/resolv.conf", backup);
        }
    }
}

// ──────────────────────────────────────────────────────────── kill switch

/// Блокировка трафика мимо туннеля через nftables. Разрешено только то, без
/// чего связь не восстановится: сам туннель, адреса VPN-сервера, локальная
/// петля, DHCP и локальные сети.
fn apply_kill_switch(interface: &str, bypass_ips: &[String]) -> bool {
    clear_kill_switch();

    let mut rules = String::new();
    rules.push_str(&format!("table inet {NFT_TABLE} {{
"));
    rules.push_str("  chain output {
");
    rules.push_str("    type filter hook output priority 0; policy drop;
");
    rules.push_str("    ct state established,related accept
");
    rules.push_str("    oif lo accept
");
    rules.push_str(&format!("    oifname \"{interface}\" accept
"));
    rules.push_str("    ip daddr { 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16, 224.0.0.0/4 } accept
");
    rules.push_str("    udp dport { 67, 68 } accept
");
    for ip in bypass_ips {
        rules.push_str(&format!("    ip daddr {ip} accept
"));
    }
    rules.push_str("  }
}
");

    run_nft(&rules)
}

fn clear_kill_switch() {
    let _ = run_nft(&format!("table inet {NFT_TABLE} {{}}
delete table inet {NFT_TABLE}
"));
}

fn run_nft(rules: &str) -> bool {
    use std::io::Write;

    let mut child = match Command::new("nft")
        .arg("-f")
        .arg("-")
        .stdin(Stdio::piped())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
    {
        Ok(child) => child,
        Err(_) => return false,
    };
    if let Some(stdin) = child.stdin.as_mut() {
        if stdin.write_all(rules.as_bytes()).is_err() {
            return false;
        }
    }
    child
        .wait()
        .map(|status| status.success())
        .unwrap_or(false)
}
