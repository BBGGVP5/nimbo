//! Official TrustTunnel CLI client (AdGuard, Apache-2.0) as a loopback SOCKS
//! sidecar. Xray keeps routing/TUN and reaches the endpoint through it, the
//! same way NaiveProxy and AWG are wired. The credential-bearing TOML lives on
//! disk only until the client has read it.
use std::fs::OpenOptions;
use std::io::{Read, Seek, SeekFrom, Write};
use std::net::TcpStream;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::time::{Duration, Instant};

use nimbo_subscription::{AwgLocalSocks, Protocol, Server, TrustTunnelConfig, TrustTunnelUpstream};
use sha2::{Digest, Sha256};
use tauri::{AppHandle, Manager};

pub const VERSION: &str = "v1.1.11";
/// The client gives up on its own after ~25 s (10 connection attempts).
const START_TIMEOUT: Duration = Duration::from_secs(30);
const MAX_LOG_SCAN_BYTES: u64 = 256 * 1024;

pub struct TrustTunnelRuntime {
    child: Child,
}

impl TrustTunnelRuntime {
    pub fn has_exited(&mut self) -> bool {
        !matches!(self.child.try_wait(), Ok(None))
    }
}

impl Drop for TrustTunnelRuntime {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

/// SHA-256 of the executables extracted from the official v1.1.11 release
/// archives (see resources/trusttunnel/README.txt).
fn expected_sha256(platform: &str) -> Option<&'static str> {
    Some(match platform {
        "windows-x64" => "2572f6397ec4a39e018858da593ab278facf9b2e86c7e276753f4845ed78968d",
        "windows-arm64" => "39b8a66af90f8670c912377a46191d0026e7e140569e9075c2c04cee6907bf7a",
        "windows-x86" => "989713b8038e5ba08f312e5e77d788bbb2a602e84be7ef256751b7b04689d744",
        "linux-x64" => "e01411457f65c498063211fa670d55ea710d3a2d35534acd73438bd514df454a",
        "linux-arm64" => "8d1fddc866809d1c76f983578ae5cc68a62c7c63237520ef7cbf9294d2768b08",
        _ => return None,
    })
}

pub(crate) fn exe_name() -> &'static str {
    if cfg!(windows) {
        "trusttunnel_client.exe"
    } else {
        "trusttunnel_client"
    }
}

fn verify(path: &Path, expected: &str) -> Result<Vec<u8>, String> {
    let bytes = std::fs::read(path).map_err(|_| "Не удалось проверить клиент TrustTunnel")?;
    if format!("{:x}", Sha256::digest(&bytes)) != expected {
        return Err(
            "Контрольная сумма клиента TrustTunnel не совпала. Переустановите Nimbo".into(),
        );
    }
    Ok(bytes)
}

/// Verified official client. Installed copies live in `<data>/bin` (custom
/// installer); bundled resources are copied there once and re-verified.
pub(crate) fn binary(app: &AppHandle) -> Result<PathBuf, String> {
    if let Some(path) = std::env::var_os("NIMBO_TRUSTTUNNEL_PATH").map(PathBuf::from) {
        if path.is_file() {
            return Ok(path);
        }
    }
    let platform =
        crate::awg_runtime::platform().ok_or("TrustTunnel не поддерживается на этой платформе")?;
    let expected =
        expected_sha256(platform).ok_or("Клиент TrustTunnel не собран для этой платформы")?;
    let installed = crate::commands::nimbo_data_dir()?
        .join("bin")
        .join(exe_name());
    if installed.is_file() {
        if verify(&installed, expected).is_ok() {
            return Ok(installed);
        }
        let _ = std::fs::remove_file(&installed);
    }

    let relative = Path::new("trusttunnel").join(platform).join(exe_name());
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
    let source = paths
        .into_iter()
        .find(|p| p.is_file())
        .ok_or("Клиент TrustTunnel не включён в эту сборку Nimbo")?;
    let bytes = verify(&source, expected)?;
    let dir = installed
        .parent()
        .ok_or("Некорректный путь клиента TrustTunnel")?;
    std::fs::create_dir_all(dir).map_err(|_| "Не удалось установить клиент TrustTunnel")?;
    let temporary = dir.join(format!("{}.partial", uuid::Uuid::new_v4()));
    let install = (|| -> std::io::Result<()> {
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&temporary)?;
        file.write_all(&bytes)?;
        file.sync_all()?;
        drop(file);
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            std::fs::set_permissions(&temporary, std::fs::Permissions::from_mode(0o755))?;
        }
        std::fs::rename(&temporary, &installed)
    })();
    if install.is_err() {
        let _ = std::fs::remove_file(&temporary);
        return Err("Не удалось установить клиент TrustTunnel".into());
    }
    verify(&installed, expected)?;
    Ok(installed)
}

/// TOML basic string with every character TOML requires escaped.
fn toml_string(value: &str) -> String {
    let mut out = String::with_capacity(value.len() + 2);
    out.push('"');
    for ch in value.chars() {
        match ch {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 || c as u32 == 0x7f => {
                out.push_str(&format!("\\u{:04X}", c as u32));
            }
            c => out.push(c),
        }
    }
    out.push('"');
    out
}

fn toml_array(values: &[String]) -> String {
    let items: Vec<String> = values.iter().map(|v| toml_string(v)).collect();
    format!("[{}]", items.join(", "))
}

/// Client configuration with only a SOCKS listener: no TUN, routes, kill
/// switch or system DNS changes — Xray owns all of that.
/// `addresses` overrides the endpoint list (TUN mode pins resolved IPs so the
/// sidecar's own traffic matches the bypass routes).
pub(crate) fn render_config(
    config: &TrustTunnelConfig,
    socks: &AwgLocalSocks,
    addresses: Option<&[String]>,
) -> String {
    let addresses = addresses.unwrap_or(&config.addresses);
    // The CLI encodes a custom SNI as `sni|remote_id` in `hostname`.
    let hostname = match config.custom_sni.as_deref().map(str::trim) {
        Some(sni) if !sni.is_empty() && sni != config.hostname => {
            format!("{sni}|{}", config.hostname)
        }
        _ => config.hostname.clone(),
    };
    let upstream = match config.upstream {
        TrustTunnelUpstream::Http2 => "http2",
        TrustTunnelUpstream::Http3 => "http3",
    };
    let mut out = String::new();
    out.push_str("loglevel = \"info\"\n");
    out.push_str("vpn_mode = \"general\"\n");
    out.push_str("killswitch_enabled = false\n");
    out.push_str("exclusions = []\n\n");
    out.push_str("[endpoint]\n");
    out.push_str(&format!("hostname = {}\n", toml_string(&hostname)));
    out.push_str(&format!("addresses = {}\n", toml_array(addresses)));
    out.push_str(&format!("has_ipv6 = {}\n", config.has_ipv6));
    out.push_str(&format!("username = {}\n", toml_string(&config.username)));
    out.push_str(&format!("password = {}\n", toml_string(&config.password)));
    if let Some(prefix) = &config.client_random_prefix {
        out.push_str(&format!("client_random = {}\n", toml_string(prefix)));
    }
    if let Some(key) = &config.client_random_auth_key {
        out.push_str(&format!("client_random_auth_key = {}\n", toml_string(key)));
    }
    out.push_str(&format!(
        "skip_verification = {}\n",
        config.skip_verification
    ));
    if let Some(certificate) = &config.certificate {
        out.push_str(&format!("certificate = {}\n", toml_string(certificate)));
    }
    out.push_str(&format!("upstream_protocol = \"{upstream}\"\n"));
    out.push_str(&format!("anti_dpi = {}\n", config.anti_dpi));
    if !config.dns_upstreams.is_empty() {
        out.push_str(&format!(
            "dns_upstreams = {}\n",
            toml_array(&config.dns_upstreams)
        ));
    }
    out.push_str("\n[listener.socks]\n");
    out.push_str(&format!(
        "address = {}\n",
        toml_string(&format!("127.0.0.1:{}", socks.port))
    ));
    out.push_str(&format!("username = {}\n", toml_string(&socks.username)));
    out.push_str(&format!("password = {}\n", toml_string(&socks.password)));
    out
}

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum LinkState {
    Connecting,
    Connected,
    Failed(String),
}

/// Reads the client's own state transitions from its log output.
pub(crate) fn link_state(log: &str) -> LinkState {
    let mut state = LinkState::Connecting;
    let mut last_error: Option<String> = None;
    for line in log.lines() {
        if line.contains("VPN_SS_CONNECTED") {
            state = LinkState::Connected;
        } else if line.contains("VPN_SS_DISCONNECTED") && state != LinkState::Connected {
            state = LinkState::Failed(String::new());
        }
        if line.contains(" ERROR ") {
            let reason = line
                .split_once("Error: ")
                .map(|(_, reason)| reason)
                .or_else(|| line.rsplit_once(": ").map(|(_, reason)| reason))
                .unwrap_or_default();
            last_error = Some(reason.trim().chars().take(200).collect());
        }
    }
    match state {
        LinkState::Failed(_) => LinkState::Failed(last_error.unwrap_or_default()),
        other => other,
    }
}

fn failure_message(reason: &str) -> String {
    if reason.is_empty() {
        "TrustTunnel не подключился к серверу".into()
    } else {
        format!("TrustTunnel не подключился к серверу: {reason}")
    }
}

fn read_log_from(path: &Path, offset: u64) -> String {
    let Ok(mut file) = std::fs::File::open(path) else {
        return String::new();
    };
    if file.seek(SeekFrom::Start(offset)).is_err() {
        return String::new();
    }
    let mut bytes = Vec::new();
    let _ = file.take(MAX_LOG_SCAN_BYTES).read_to_end(&mut bytes);
    String::from_utf8_lossy(&bytes).into_owned()
}

pub(crate) fn new_local_socks() -> Result<AwgLocalSocks, String> {
    let listener = std::net::TcpListener::bind(("127.0.0.1", 0))
        .map_err(|_| "Не удалось выбрать локальный порт TrustTunnel")?;
    let port = listener
        .local_addr()
        .map_err(|_| "Не удалось определить локальный порт TrustTunnel")?
        .port();
    Ok(AwgLocalSocks {
        port,
        username: uuid::Uuid::new_v4().simple().to_string(),
        password: uuid::Uuid::new_v4().simple().to_string(),
    })
}

pub(crate) fn write_private(path: &Path, text: &str) -> Result<(), String> {
    let _ = std::fs::remove_file(path);
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let result = options
        .open(path)
        .and_then(|mut file| file.write_all(text.as_bytes()));
    if result.is_err() {
        let _ = std::fs::remove_file(path);
        return Err("Не удалось записать конфиг TrustTunnel".into());
    }
    Ok(())
}

pub(crate) fn command(binary: &Path, config_path: &Path) -> Command {
    let mut command = Command::new(binary);
    command
        .arg("--config")
        .arg(config_path)
        .current_dir(config_path.parent().unwrap_or_else(|| Path::new(".")))
        .stdin(Stdio::null());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    command
}

fn launch(
    binary: &Path,
    config: &TrustTunnelConfig,
    addresses: Option<&[String]>,
) -> Result<(TrustTunnelRuntime, AwgLocalSocks), String> {
    launch_in(
        binary,
        config,
        addresses,
        &crate::commands::nimbo_data_dir()?.join("runtime"),
    )
}

fn launch_in(
    binary: &Path,
    config: &TrustTunnelConfig,
    addresses: Option<&[String]>,
    runtime_dir: &Path,
) -> Result<(TrustTunnelRuntime, AwgLocalSocks), String> {
    let socks = new_local_socks()?;
    std::fs::create_dir_all(runtime_dir).map_err(|_| "Не удалось создать папку TrustTunnel")?;
    let log_path = runtime_dir.join("trusttunnel.log");
    crate::commands::rotate_runtime_log(&log_path)?;
    let log = OpenOptions::new()
        .create(true)
        .append(true)
        .open(&log_path)
        .map_err(|_| "Не удалось открыть лог TrustTunnel")?;
    let offset = log.metadata().map(|m| m.len()).unwrap_or(0);
    let stderr = log
        .try_clone()
        .map_err(|_| "Не удалось открыть лог TrustTunnel")?;
    let config_path = runtime_dir.join("trusttunnel.toml");
    write_private(&config_path, &render_config(config, &socks, addresses))?;

    tracing::info!(version = VERSION, "starting TrustTunnel sidecar");
    let mut command = command(binary, &config_path);
    command.stdout(Stdio::from(log)).stderr(Stdio::from(stderr));
    let child = match command.spawn() {
        Ok(child) => child,
        Err(_) => {
            let _ = std::fs::remove_file(&config_path);
            return Err("Не удалось запустить клиент TrustTunnel".into());
        }
    };
    let mut runtime = TrustTunnelRuntime { child };
    let deadline = Instant::now() + START_TIMEOUT;
    let result = loop {
        let state = link_state(&read_log_from(&log_path, offset));
        match state {
            LinkState::Connected => break Ok(()),
            LinkState::Failed(reason) => break Err(failure_message(&reason)),
            LinkState::Connecting => {}
        }
        if runtime.has_exited() {
            let reason = match link_state(&read_log_from(&log_path, offset)) {
                LinkState::Failed(reason) => reason,
                _ => String::new(),
            };
            break Err(failure_message(&reason));
        }
        if Instant::now() >= deadline {
            break Err("TrustTunnel не подключился к серверу за отведённое время".into());
        }
        std::thread::sleep(Duration::from_millis(100));
    };
    // The client has read its credentials by now (or failed to start).
    let _ = std::fs::remove_file(&config_path);
    result?;
    if TcpStream::connect_timeout(&([127, 0, 0, 1], socks.port).into(), Duration::from_secs(2))
        .is_err()
    {
        return Err("Локальный SOCKS TrustTunnel недоступен".into());
    }
    Ok((runtime, socks))
}

/// Starts the sidecar for TrustTunnel servers and points the server at it.
pub fn prepare(
    app: &AppHandle,
    mut server: Server,
    pinned_addresses: Option<&[String]>,
) -> Result<(Server, Option<TrustTunnelRuntime>), String> {
    let Protocol::TrustTunnel(config) = &mut server.protocol else {
        return Ok((server, None));
    };
    let (runtime, socks) = launch(&binary(app)?, config, pinned_addresses)?;
    config.local_socks = Some(socks);
    Ok((server, Some(runtime)))
}

/// Every endpoint `host:port` the sidecar may dial.
pub(crate) fn dial_endpoints(config: &TrustTunnelConfig) -> Vec<(String, u16)> {
    let mut out = Vec::new();
    for address in &config.addresses {
        if let Ok(endpoint) =
            nimbo_subscription::parser::trusttunnel::split_host_port(address, config.port)
        {
            if !out.contains(&endpoint) {
                out.push(endpoint);
            }
        }
    }
    if out.is_empty() {
        out.push((config.address.clone(), config.port));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> TrustTunnelConfig {
        let server = nimbo_subscription::parser::trusttunnel::parse(
            "tt://?AQZ0dC50bGQCCnR0LnRsZDo0NDMFAXUGAXA",
        )
        .unwrap();
        match server.protocol {
            Protocol::TrustTunnel(config) => config,
            _ => unreachable!(),
        }
    }

    fn socks() -> AwgLocalSocks {
        AwgLocalSocks {
            port: 41000,
            username: "lu".into(),
            password: "lp".into(),
        }
    }

    #[test]
    fn renders_socks_only_config_with_escaped_values() {
        let mut config = sample();
        config.password = "p\"a\\ss\nword".into();
        config.custom_sni = Some("cdn.example.org".into());
        config.upstream = TrustTunnelUpstream::Http3;
        config.certificate =
            Some("-----BEGIN CERTIFICATE-----\nAAA\n-----END CERTIFICATE-----\n".into());
        let text = render_config(&config, &socks(), None);
        assert!(text.contains("hostname = \"cdn.example.org|tt.tld\"\n"));
        assert!(text.contains("addresses = [\"tt.tld:443\"]\n"));
        assert!(text.contains("password = \"p\\\"a\\\\ss\\nword\"\n"));
        assert!(text.contains("upstream_protocol = \"http3\"\n"));
        assert!(text.contains("killswitch_enabled = false\n"));
        assert!(text.contains("[listener.socks]\naddress = \"127.0.0.1:41000\"\n"));
        assert!(!text.contains("[listener.tun]"));
        assert!(text.contains("certificate = \"-----BEGIN CERTIFICATE-----\\nAAA"));
    }

    #[test]
    fn pinned_addresses_replace_endpoint_list() {
        let pinned = vec!["203.0.113.7:443".to_string()];
        let text = render_config(&sample(), &socks(), Some(&pinned));
        assert!(text.contains("addresses = [\"203.0.113.7:443\"]\n"));
        assert!(text.contains("hostname = \"tt.tld\"\n"));
    }

    #[test]
    fn reads_client_state_from_log() {
        assert_eq!(
            link_state("INFO raise_state: [0] VPN_SS_CONNECTING\n"),
            LinkState::Connecting
        );
        assert_eq!(
            link_state("raise_state: [0] VPN_SS_CONNECTING\nraise_state: [0] VPN_SS_CONNECTED\n"),
            LinkState::Connected
        );
        // Recorded from trusttunnel_client v1.1.11 with an unreachable endpoint.
        let failed = "18.10.2026 20:21:55.221216 INFO  [37852] VPNCORE raise_state: [0] VPN_SS_DISCONNECTED\n\
            18.10.2026 20:21:55.221306 ERROR [37852] TRUSTTUNNEL_CLIENT_APP operator (): Error: 10 Number of connection attempts exceeded\n";
        assert_eq!(
            link_state(failed),
            LinkState::Failed("10 Number of connection attempts exceeded".into())
        );
    }

    #[test]
    fn every_supported_desktop_platform_has_a_pin() {
        for platform in [
            "windows-x64",
            "windows-arm64",
            "windows-x86",
            "linux-x64",
            "linux-arm64",
        ] {
            assert_eq!(expected_sha256(platform).map(str::len), Some(64));
        }
    }

    #[test]
    fn bundled_binary_for_this_host_matches_its_pin() {
        let Some(platform) = crate::awg_runtime::platform() else {
            return;
        };
        let path = Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("resources/trusttunnel")
            .join(platform)
            .join(exe_name());
        verify(&path, expected_sha256(platform).unwrap()).unwrap();
    }

    #[test]
    fn readme_documents_the_pinned_version_and_hashes() {
        let readme = std::fs::read_to_string(
            Path::new(env!("CARGO_MANIFEST_DIR")).join("resources/trusttunnel/README.txt"),
        )
        .unwrap();
        assert!(readme.contains(&format!("Version: {VERSION}")));
        for platform in [
            "windows-x64",
            "windows-arm64",
            "windows-x86",
            "linux-x64",
            "linux-arm64",
        ] {
            assert!(readme.contains(expected_sha256(platform).unwrap()));
        }
    }

    /// Live check against a real endpoint: the official client must reach
    /// `VPN_SS_CONNECTED` and carry an HTTP request through its SOCKS5.
    /// NIMBO_TT_TEST_LINK=tt://?... cargo test -p nimbo-ui live_endpoint -- --ignored
    #[test]
    #[ignore = "requires NIMBO_TT_TEST_LINK pointing to a reachable TrustTunnel endpoint"]
    fn live_endpoint_carries_http_through_socks() {
        let link = std::env::var("NIMBO_TT_TEST_LINK").expect("NIMBO_TT_TEST_LINK");
        let server = nimbo_subscription::parser::trusttunnel::parse(&link).unwrap();
        let Protocol::TrustTunnel(config) = server.protocol else {
            unreachable!()
        };
        let platform = crate::awg_runtime::platform().unwrap();
        let binary = Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("resources/trusttunnel")
            .join(platform)
            .join(exe_name());
        let dir = std::env::temp_dir().join(format!("nimbo-tt-live-{}", uuid::Uuid::new_v4()));
        let (runtime, socks) = launch_in(&binary, &config, None, &dir).expect("sidecar connects");
        assert!(
            !dir.join("trusttunnel.toml").exists(),
            "credentials left on disk"
        );

        let mut stream = TcpStream::connect(("127.0.0.1", socks.port)).unwrap();
        stream
            .set_read_timeout(Some(Duration::from_secs(15)))
            .unwrap();
        stream.write_all(&[5, 1, 2]).unwrap();
        let mut reply = [0u8; 2];
        stream.read_exact(&mut reply).unwrap();
        assert_eq!(reply, [5, 2]);
        let mut auth = vec![1, socks.username.len() as u8];
        auth.extend_from_slice(socks.username.as_bytes());
        auth.push(socks.password.len() as u8);
        auth.extend_from_slice(socks.password.as_bytes());
        stream.write_all(&auth).unwrap();
        stream.read_exact(&mut reply).unwrap();
        assert_eq!(reply, [1, 0]);
        // A literal IP keeps the check independent of the endpoint's resolver.
        let target = std::env::var("NIMBO_TT_TEST_HOST").unwrap_or_else(|_| "example.com".into());
        let host = target.as_bytes();
        let mut connect = vec![5, 1, 0, 3, host.len() as u8];
        connect.extend_from_slice(host);
        connect.extend_from_slice(&80u16.to_be_bytes());
        stream.write_all(&connect).unwrap();
        let mut head = [0u8; 4];
        stream.read_exact(&mut head).unwrap();
        assert_eq!(head[1], 0, "SOCKS CONNECT through TrustTunnel failed");
        let skip = match head[3] {
            1 => 6,
            4 => 18,
            _ => {
                let mut len = [0u8; 1];
                stream.read_exact(&mut len).unwrap();
                len[0] as usize + 2
            }
        };
        let mut rest = vec![0u8; skip];
        stream.read_exact(&mut rest).unwrap();
        stream
            .write_all(
                format!("GET / HTTP/1.1\r\nHost: {target}\r\nConnection: close\r\n\r\n").as_bytes(),
            )
            .unwrap();
        let mut response = Vec::new();
        let _ = stream.read_to_end(&mut response);
        let text = String::from_utf8_lossy(&response);
        assert!(
            text.starts_with("HTTP/1.1 "),
            "unexpected response: {text:.80}"
        );
        drop(runtime);
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn lists_every_dial_endpoint() {
        let mut config = sample();
        config.addresses = vec!["a.example:443".into(), "[2001:db8::1]:8443".into()];
        assert_eq!(
            dial_endpoints(&config),
            vec![
                ("a.example".to_string(), 443),
                ("2001:db8::1".to_string(), 8443)
            ]
        );
    }
}
