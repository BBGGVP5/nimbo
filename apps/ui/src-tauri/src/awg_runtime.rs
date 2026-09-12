//! Secrets travel only through stdin. Dropping this owner closes stdin and reaps the process.
use std::io::{BufRead, BufReader, Read, Write};
use std::net::{IpAddr, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::time::{Duration, Instant};

use nimbo_subscription::{AwgLocalSocks, Protocol, Server};
use serde::Deserialize;
use sha2::{Digest, Sha256};
use tauri::{AppHandle, Manager};

pub const VERSION: &str = "v3.1.20260828";
const START_TIMEOUT: Duration = Duration::from_secs(15);

pub struct AwgRuntime {
    child: Child,
}

impl AwgRuntime {
    pub fn has_exited(&mut self) -> bool {
        !matches!(self.child.try_wait(), Ok(None))
    }
}

impl Drop for AwgRuntime {
    fn drop(&mut self) {
        // Give the CLI a short opportunity to close its UDP sockets on owner EOF.
        drop(self.child.stdin.take());
        let deadline = Instant::now() + Duration::from_millis(500);
        while Instant::now() < deadline {
            if matches!(self.child.try_wait(), Ok(Some(_))) {
                return;
            }
            std::thread::sleep(Duration::from_millis(20));
        }
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Ready {
    port: u16,
    version: String,
}

fn parse_ready(bytes: &[u8]) -> Result<u16, String> {
    if bytes.len() > 1024 || !bytes.ends_with(b"\n") {
        return Err("Некорректный ответ AWG runtime".into());
    }
    let ready: Ready =
        serde_json::from_slice(bytes).map_err(|_| "Некорректный ответ AWG runtime")?;
    if ready.port == 0 || ready.version != VERSION {
        return Err("Несовместимая версия AWG runtime".into());
    }
    Ok(ready.port)
}

fn authenticate(socks: &AwgLocalSocks) -> Result<(), String> {
    let mut stream =
        TcpStream::connect_timeout(&([127, 0, 0, 1], socks.port).into(), Duration::from_secs(2))
            .map_err(|_| "Локальный SOCKS AWG недоступен")?;
    stream
        .set_read_timeout(Some(Duration::from_secs(2)))
        .map_err(|_| "Не удалось проверить SOCKS AWG")?;
    stream
        .set_write_timeout(Some(Duration::from_secs(2)))
        .map_err(|_| "Не удалось проверить SOCKS AWG")?;
    let check = || -> std::io::Result<()> {
        stream.write_all(&[5, 1, 2])?;
        let mut reply = [0; 2];
        stream.read_exact(&mut reply)?;
        if reply != [5, 2] {
            return Err(std::io::ErrorKind::PermissionDenied.into());
        }
        let mut auth = vec![1, socks.username.len() as u8];
        auth.extend_from_slice(socks.username.as_bytes());
        auth.push(socks.password.len() as u8);
        auth.extend_from_slice(socks.password.as_bytes());
        stream.write_all(&auth)?;
        stream.read_exact(&mut reply)?;
        if reply != [1, 0] {
            return Err(std::io::ErrorKind::PermissionDenied.into());
        }
        Ok(())
    };
    let mut check = check;
    check().map_err(|_| "AWG SOCKS не подтвердил авторизацию".into())
}

fn launch(
    binary: &Path,
    config: &str,
    timeout: Duration,
) -> Result<(AwgRuntime, AwgLocalSocks), String> {
    launch_command(Command::new(binary), config, timeout)
}

fn launch_command(
    mut command: Command,
    config: &str,
    timeout: Duration,
) -> Result<(AwgRuntime, AwgLocalSocks), String> {
    command
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::null());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    let child = command
        .spawn()
        .map_err(|_| "Не удалось запустить AWG runtime")?;
    let mut runtime = AwgRuntime { child };
    let mut stdin = runtime
        .child
        .stdin
        .take()
        .ok_or("Не удалось открыть stdin AWG")?;
    let stdout = runtime
        .child
        .stdout
        .take()
        .ok_or("Не удалось открыть stdout AWG")?;
    let mut socks = AwgLocalSocks {
        port: 0,
        username: uuid::Uuid::new_v4().simple().to_string(),
        password: uuid::Uuid::new_v4().simple().to_string(),
    };
    let request = serde_json::to_vec(&serde_json::json!({
        "config": config, "listen": "127.0.0.1:0", "username": socks.username, "password": socks.password,
    })).map_err(|_| "Не удалось подготовить AWG runtime")?;
    let (sender, receiver) = std::sync::mpsc::sync_channel(1);
    // Both writing and reading are bounded by the owner timeout, including a hung child.
    let worker = std::thread::spawn(move || {
        let result = (|| -> std::io::Result<_> {
            stdin.write_all(&request)?;
            stdin.write_all(b"\n")?;
            stdin.flush()?;
            let mut reader = BufReader::new(stdout).take(1025);
            let mut line = Vec::new();
            reader.read_until(b'\n', &mut line)?;
            Ok((stdin, line, reader.into_inner()))
        })();
        let _ = sender.send(result);
    });
    let startup = receiver.recv_timeout(timeout);
    match startup {
        Ok(Ok((stdin, line, mut stdout))) => {
            runtime.child.stdin = Some(stdin);
            socks.port = parse_ready(&line)?;
            // Never log child output, and never let a full pipe hang teardown.
            std::thread::spawn(move || {
                let _ = std::io::copy(&mut stdout, &mut std::io::sink());
            });
            let _ = worker.join();
            authenticate(&socks)?;
            if runtime.has_exited() {
                return Err("AWG runtime завершился при запуске".into());
            }
            Ok((runtime, socks))
        }
        _ => {
            let _ = runtime.child.kill();
            let _ = runtime.child.wait();
            let _ = worker.join();
            Err("AWG runtime не подтвердил запуск за отведённое время".into())
        }
    }
}

pub fn platform() -> Option<&'static str> {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("windows", "x86_64") => Some("windows-x64"),
        ("windows", "x86") => Some("windows-x86"),
        ("windows", "aarch64") => Some("windows-arm64"),
        ("linux", "x86_64") => Some("linux-x64"),
        ("linux", "aarch64") => Some("linux-arm64"),
        _ => None,
    }
}

fn binary(app: &AppHandle) -> Result<PathBuf, String> {
    let platform = platform().ok_or("AWG runtime не поддерживает эту платформу")?;
    let name = if cfg!(windows) {
        "nimbo-awg.exe"
    } else {
        "nimbo-awg"
    };
    let relative = Path::new("awg").join(platform).join(name);
    let mut paths = Vec::new();
    if let Ok(root) = app.path().resource_dir() {
        paths.push(root.join("resources").join(&relative));
        paths.push(root.join(&relative));
    }
    if let Ok(exe) = std::env::current_exe() {
        if let Some(root) = exe.parent() {
            paths.push(root.join("resources").join(&relative));
        }
    }
    #[cfg(debug_assertions)]
    paths.push(
        Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("resources")
            .join(&relative),
    );
    let path = paths
        .into_iter()
        .find(|p| p.is_file())
        .ok_or("Бинарник AWG не включён в эту сборку Nimbo")?;
    let expected = env!("NIMBO_AWG_SHA256");
    let bytes = std::fs::read(&path).map_err(|_| "Не удалось проверить AWG runtime")?;
    if expected.is_empty() || format!("{:x}", Sha256::digest(&bytes)) != expected {
        return Err(
            "Контрольная сумма AWG runtime не совпала. Пересоберите или переустановите Nimbo"
                .into(),
        );
    }
    #[cfg(target_os = "linux")]
    {
        // Some Linux packagers strip executable bits from ordinary resources.
        // Install verified bytes as an unprivileged, content-addressed executable;
        // never ask the root TUN helper to execute a user-writable AWG path.
        let root = app
            .path()
            .app_local_data_dir()
            .map_err(|_| "Нет каталога AWG runtime")?;
        let dir = root.join("bin").join("awg").join(expected);
        std::fs::create_dir_all(&dir).map_err(|_| "Не удалось установить AWG runtime")?;
        use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
        std::fs::set_permissions(&dir, std::fs::Permissions::from_mode(0o700))
            .map_err(|_| "Не удалось защитить каталог AWG runtime")?;
        let cached = dir.join("nimbo-awg");
        let valid = std::fs::symlink_metadata(&cached).is_ok_and(|m| m.is_file())
            && std::fs::read(&cached).is_ok_and(|b| format!("{:x}", Sha256::digest(b)) == expected);
        if !valid {
            let temporary = dir.join(format!("{}.partial", uuid::Uuid::new_v4()));
            let install = (|| -> std::io::Result<()> {
                let mut file = std::fs::OpenOptions::new()
                    .write(true)
                    .create_new(true)
                    .mode(0o700)
                    .open(&temporary)?;
                file.write_all(&bytes)?;
                file.sync_all()?;
                std::fs::rename(&temporary, &cached)
            })();
            if install.is_err() {
                let _ = std::fs::remove_file(&temporary);
                return Err("Не удалось установить AWG runtime".into());
            }
        }
        std::fs::set_permissions(&cached, std::fs::Permissions::from_mode(0o700))
            .map_err(|_| "Не удалось разрешить запуск AWG runtime")?;
        Ok(cached)
    }
    #[cfg(not(target_os = "linux"))]
    Ok(path)
}

pub fn prepare(
    app: &AppHandle,
    mut server: Server,
) -> Result<(Server, Option<AwgRuntime>), String> {
    let Protocol::Awg(config) = &mut server.protocol else {
        return Ok((server, None));
    };
    *config = nimbo_subscription::parser::awg::parse_ini(&config.config)
        .map_err(|_| "Некорректная конфигурация AWG")?;
    let (runtime, socks) = launch(&binary(app)?, &config.config, START_TIMEOUT)?;
    config.local_socks = Some(socks);
    Ok((server, Some(runtime)))
}

pub fn pin(server: &mut Server, ip: IpAddr) -> Result<(), String> {
    if let Protocol::Awg(config) = &mut server.protocol {
        *config = nimbo_subscription::parser::awg::pin_endpoint(&config.config, ip)
            .map_err(|_| "Некорректная конфигурация AWG")?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn bounded_versioned_readiness_never_echoes_secrets() {
        assert_eq!(
            parse_ready(format!("{{\"port\":43210,\"version\":\"{VERSION}\"}}\n").as_bytes())
                .unwrap(),
            43210
        );
        for bad in [
            "{\"port\":0,\"version\":\"v3.1.20260828\"}\n",
            "{\"port\":70000}\n",
            "SECRET config",
            "{\"port\":1,\"version\":\"old\"}\n",
        ] {
            assert!(!parse_ready(bad.as_bytes()).unwrap_err().contains("SECRET"));
        }
        assert!(parse_ready(&vec![b'x'; 1025]).is_err());
    }
    #[cfg(windows)]
    #[test]
    fn startup_timeout_kills_child_and_invalid_readiness_is_redacted() {
        for script in [
            "Start-Sleep -Seconds 60",
            "Write-Output 'SECRET_CONFIG'; Start-Sleep -Seconds 60",
        ] {
            let mut command = Command::new("powershell.exe");
            command.args(["-NoProfile", "-NonInteractive", "-Command", script]);
            let started = Instant::now();
            let result = launch_command(command, "test-config", Duration::from_millis(800));
            let error = match result {
                Err(error) => error,
                Ok(_) => panic!("unready runtime accepted"),
            };
            assert!(!error.contains("SECRET_CONFIG"));
            assert!(started.elapsed() < Duration::from_secs(4));
        }
    }

    #[cfg(unix)]
    #[test]
    fn startup_timeout_kills_child() {
        let mut command = Command::new("sleep");
        command.arg("60");
        let started = Instant::now();
        assert!(launch_command(command, "test-config", Duration::from_millis(200)).is_err());
        assert!(started.elapsed() < Duration::from_secs(3));
    }

    #[test]
    #[ignore = "requires NIMBO_AWG_TEST_BINARY pointing to the staged host CLI"]
    fn bundled_cli_starts_authenticates_and_exits_on_owner_eof() {
        let path = std::env::var_os("NIMBO_AWG_TEST_BINARY").expect("staged runtime path required");
        let key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";
        let config = format!("[Interface]\nPrivateKey={key}\nAddress=10.0.0.2/32\n[Peer]\nPublicKey={key}\nEndpoint=127.0.0.1:9\nAllowedIPs=0.0.0.0/0\n");
        let (runtime, socks) = launch(Path::new(&path), &config, START_TIMEOUT).unwrap();
        assert_ne!(socks.port, 0);
        assert!(socks.password.len() >= 16);
        let port = socks.port;
        drop(runtime);
        assert!(TcpStream::connect(("127.0.0.1", port)).is_err());
    }
}
