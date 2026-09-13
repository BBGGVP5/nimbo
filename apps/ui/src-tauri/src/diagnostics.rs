//! Per-server diagnostic processes. No active runtime, routing profile, TUN or
//! system-proxy state is used or changed here.
use std::{future::Future, path::{Path, PathBuf}, process::Stdio, time::Duration};
use nimbo_subscription::{Protocol, Server};
use serde_json::{json, Value};
use tokio::{io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader}, process::{Child, Command}, sync::{OwnedSemaphorePermit, Semaphore}};
use crate::latency::{PingRoute, measure_http};

const MAX_PROBES: usize = 3;
static SLOTS: std::sync::OnceLock<std::sync::Arc<Semaphore>> = std::sync::OnceLock::new();
fn slots() -> std::sync::Arc<Semaphore> {
    SLOTS.get_or_init(|| std::sync::Arc::new(Semaphore::new(MAX_PROBES))).clone()
}

pub(crate) struct Binaries {
    pub xray: PathBuf,
    pub awg: Option<PathBuf>,
    pub naive: Option<PathBuf>,
}

/// Holds the permit until children have been killed AND reaped, including when
/// the caller drops its future. Cleanup is event-driven, never a scheduler.
#[derive(Default)]
struct Resources {
    children: Vec<Child>,
    directory: Option<PathBuf>,
    permit: Option<OwnedSemaphorePermit>,
}

impl Resources {
    fn directory(&mut self, parent: &Path) -> Result<&Path, String> {
        let path = parent.join(format!("probe-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(parent).map_err(|_| "Cannot create diagnostic directory")?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::DirBuilderExt;
            std::fs::DirBuilder::new().mode(0o700).create(&path).map_err(|_| "Cannot create private diagnostic directory")?;
        }
        #[cfg(not(unix))]
        std::fs::create_dir(&path).map_err(|_| "Cannot create diagnostic directory")?;
        self.directory = Some(path);
        Ok(self.directory.as_deref().unwrap())
    }

    fn write_config(&self, name: &str, value: &Value) -> Result<PathBuf, String> {
        use std::io::Write;
        let path = self.directory.as_ref().ok_or("Missing diagnostic directory")?.join(name);
        let mut options = std::fs::OpenOptions::new();
        options.write(true).create_new(true);
        #[cfg(unix)]
        { use std::os::unix::fs::OpenOptionsExt; options.mode(0o600); }
        let mut file = options.open(&path).map_err(|_| "Cannot write private diagnostic config")?;
        file.write_all(&serde_json::to_vec(value).map_err(|_| "Invalid diagnostic config")?)
            .map_err(|_| "Cannot write diagnostic config")?;
        Ok(path)
    }

    fn spawn(&mut self, command: &mut Command) -> Result<usize, String> {
        command.kill_on_drop(true).stderr(Stdio::null());
        #[cfg(windows)]
        command.creation_flags(0x08000000);
        let child = command.spawn().map_err(|_| "Cannot start diagnostic runtime")?;
        self.children.push(child);
        Ok(self.children.len() - 1)
    }

    fn alive(&mut self) -> bool {
        self.children.iter_mut().all(|child| matches!(child.try_wait(), Ok(None)))
    }

    async fn cleanup(&mut self) {
        for child in &mut self.children {
            drop(child.stdin.take());
            let _ = child.start_kill();
        }
        for child in &mut self.children { let _ = child.wait().await; }
        self.children.clear();
        if let Some(path) = self.directory.take() {
            // Only these files are created by this owner. No recursive deletion
            // and no active runtime paths, shared logs, keys or route snapshots.
            for name in ["xray.json", "naive.json"] { let _ = std::fs::remove_file(path.join(name)); }
            let _ = std::fs::remove_dir(path);
        }
        self.permit.take();
    }
}

// Separate owner with NON-recursive Drop: Tokio may drop a newly spawned
// cleanup future during runtime shutdown. Never spawn again from that Drop.
struct Cleanup { children: Vec<Child>, directory: Option<PathBuf>, _permit: Option<OwnedSemaphorePermit> }
impl Drop for Cleanup {
    fn drop(&mut self) {
        for child in &mut self.children { let _ = child.start_kill(); }
        if let Some(path) = self.directory.take() {
            for name in ["xray.json", "naive.json"] { let _ = std::fs::remove_file(path.join(name)); }
            let _ = std::fs::remove_dir(path);
        }
    }
}
impl Drop for Resources {
    fn drop(&mut self) {
        if self.children.is_empty() && self.directory.is_none() { return; }
        let mut pending = Cleanup {
            children: std::mem::take(&mut self.children),
            directory: self.directory.take(), _permit: self.permit.take(),
        };
        for child in &mut pending.children { let _ = child.start_kill(); }
        if let Ok(handle) = tokio::runtime::Handle::try_current() {
            handle.spawn(async move {
                for child in &mut pending.children { let _ = child.wait().await; }
                pending.children.clear();
                // Drop removes private files and releases the permit after reaping.
            });
        }
    }
}

fn isolated_config(server: &Server, template: Option<&Value>) -> Result<(PingRoute, Value), String> {
    let mut config = json!({
        "log": {"loglevel":"none"}, "inbounds": [],
        "outbounds": [{"tag":"unmatched-block", "protocol":"blackhole"}],
        "routing": {"domainStrategy":"AsIs", "rules":[]}
    });
    let route = PingRoute::prepare(server, &mut config)?;
    if let Some(template) = template {
        if template.get("outbounds").is_some() {
            config = crate::diagnostic_template::derive(server, template, &config)?;
        } else {
            for key in ["dns", "policy", "transport"] {
                if let Some(value) = template.get(key) { config[key] = value.clone(); }
            }
        }
    }
    Ok((route, config))
}

async fn ready(resources: &mut Resources, port: u16) -> Result<(), String> {
    loop {
        if !resources.alive() { return Err("Diagnostic runtime exited before readiness".into()); }
        if tokio::net::TcpStream::connect(("127.0.0.1", port)).await.is_ok() { return Ok(()); }
        tokio::time::sleep(Duration::from_millis(10)).await;
    }
}

async fn prepare_sidecar(resources: &mut Resources, server: &mut Server, binaries: &Binaries) -> Result<(), String> {
    match &mut server.protocol {
        Protocol::Awg(config) => {
            *config = nimbo_subscription::parser::awg::parse_ini(&config.config).map_err(|_| "Invalid AWG config")?;
            let mut command = Command::new(binaries.awg.as_ref().ok_or("Verified AWG runtime unavailable")?);
            command.stdin(Stdio::piped()).stdout(Stdio::piped());
            let index = resources.spawn(&mut command)?;
            let username = uuid::Uuid::new_v4().simple().to_string();
            let password = uuid::Uuid::new_v4().simple().to_string();
            let request = json!({"config":config.config, "listen":"127.0.0.1:0", "username":username, "password":password});
            let child = &mut resources.children[index];
            let stdin = child.stdin.as_mut().ok_or("Missing AWG input")?;
            stdin.write_all(&serde_json::to_vec(&request).map_err(|_| "Invalid AWG request")?).await.map_err(|_| "AWG input failed")?;
            stdin.write_all(b"\n").await.map_err(|_| "AWG input failed")?;
            stdin.flush().await.map_err(|_| "AWG input failed")?;
            let stdout = child.stdout.as_mut().ok_or("Missing AWG readiness")?;
            let mut line = Vec::new();
            BufReader::new(stdout.take(1025)).read_until(b'\n', &mut line).await.map_err(|_| "AWG readiness failed")?;
            let port = crate::awg_runtime::parse_ready(&line)?;
            // Authenticate this probe's SOCKS instance before using its route.
            let mut socket = tokio::net::TcpStream::connect(("127.0.0.1", port)).await.map_err(|_| "AWG SOCKS unavailable")?;
            socket.write_all(&[5,1,2]).await.map_err(|_| "AWG authentication failed")?;
            let mut reply = [0;2];
            socket.read_exact(&mut reply).await.map_err(|_| "AWG authentication failed")?;
            if reply != [5,2] { return Err("AWG authentication required".into()); }
            let mut auth = vec![1,username.len() as u8]; auth.extend(username.as_bytes());
            auth.push(password.len() as u8); auth.extend(password.as_bytes());
            socket.write_all(&auth).await.map_err(|_| "AWG authentication failed")?;
            socket.read_exact(&mut reply).await.map_err(|_| "AWG authentication failed")?;
            if reply != [1,0] { return Err("AWG authentication rejected".into()); }
            config.local_socks = Some(nimbo_subscription::AwgLocalSocks {port,username,password});
        }
        Protocol::Naive(config) => {
            let listener = std::net::TcpListener::bind(("127.0.0.1",0)).map_err(|_| "Cannot allocate Naive diagnostic port")?;
            let port = listener.local_addr().map_err(|_| "Cannot read Naive diagnostic port")?.port();
            let scheme = match config.transport { nimbo_subscription::NaiveTransport::Https => "https", nimbo_subscription::NaiveTransport::Quic => "quic" };
            let host = if config.address.contains(':') { format!("[{}]", config.address) } else { config.address.clone() };
            let mut proxy = url::Url::parse(&format!("{scheme}://{host}:{}", config.port)).map_err(|_| "Invalid Naive endpoint")?;
            proxy.set_username(&config.username).map_err(|_| "Invalid Naive user")?;
            proxy.set_password(Some(&config.password)).map_err(|_| "Invalid Naive password")?;
            let path = resources.write_config("naive.json", &json!({"listen":format!("socks://127.0.0.1:{port}"), "proxy":proxy.as_str()}))?;
            let mut command = Command::new(binaries.naive.as_ref().ok_or("Verified Naive runtime unavailable")?);
            command.arg(path).stdin(Stdio::null()).stdout(Stdio::null());
            drop(listener);
            resources.spawn(&mut command)?;
            ready(resources, port).await?;
            config.local_port = Some(port);
        }
        _ => {}
    }
    Ok(())
}

async fn run(resources: &mut Resources, mut server: Server, binaries: Binaries, parent: &Path, url: &str, timeout_ms: u32, template: Option<Value>) -> Result<u64, String> {
    resources.directory(parent)?;
    prepare_sidecar(resources, &mut server, &binaries).await?;
    let (route, config) = isolated_config(&server, template.as_ref())?;
    #[cfg(test)]
    let config = { let mut value=config; value["log"]["loglevel"]="debug".into(); value };
    let path = resources.write_config("xray.json", &config)?;
    let mut command = Command::new(&binaries.xray);
    command.args(["run", "-c"]).arg(path).stdin(Stdio::null()).stdout(Stdio::null());
    #[cfg(test)] command.stdout(Stdio::inherit());
    resources.spawn(&mut command)?;
    ready(resources, route.port).await?;
    let result = measure_http(&route, "nimbo", url, timeout_ms).await;
    if !resources.alive() { return Err("Diagnostic runtime exited during measurement".into()); }
    result
}

pub(crate) async fn measure(
    server: Server, resolve: impl Future<Output=Result<Binaries,String>>, parent: &Path,
    url: &str, timeout_ms: u32, valid: impl Fn() -> bool, template: Option<Value>,
) -> Result<u64, String> {
    let mut resources = Resources::default();
    let result = {
        let operation = async {
            resources.permit = Some(slots().acquire_owned().await.map_err(|_| "Diagnostic queue closed")?);
            let binaries = resolve.await?;
            tokio::task::yield_now().await; // Recheck deadline after synchronous file verification.
            run(&mut resources, server, binaries, parent, url, timeout_ms, template).await
        };
        tokio::pin!(operation);
        let deadline = tokio::time::sleep(Duration::from_millis(u64::from(timeout_ms)));
        tokio::pin!(deadline);
        loop {
            if !valid() { break Err("Ping cancelled or settings/server changed".into()); }
            tokio::select! {
                biased;
                _ = &mut deadline => break Err("timeout (queue, startup and request)".into()),
                result = &mut operation => break if valid() { result } else { Err("Ping cancelled or settings/server changed".into()) },
                _ = tokio::time::sleep(Duration::from_millis(20)) => {},
            }
        }
    };
    resources.cleanup().await;
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    fn server(id: &str, port: u16) -> Server {
        Server { id:id.into(), name:id.into(), server_description:None, host_uuid:None, xray_json_template_uuid:None,
            protocol:Protocol::Vless(nimbo_subscription::VlessConfig { address:"127.0.0.1".into(),port,
                uuid:"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee".into(), flow:None,encryption:"none".into(),stream:Default::default() }) }
    }

    #[test]
    fn isolated_configs_have_distinct_routes_and_no_direct_or_active_state() {
        let (a,ca) = isolated_config(&server("a",23451), None).unwrap();
        let (b,cb) = isolated_config(&server("b",23452), None).unwrap();
        assert!(a != b);
        for (config,port) in [(ca,23451),(cb,23452)] {
            assert_eq!(config["inbounds"].as_array().unwrap().len(),1);
            let outs = config["outbounds"].as_array().unwrap();
            assert_eq!(outs.len(),2);
            assert_eq!(outs[0]["protocol"],"blackhole");
            assert_eq!(outs[1]["protocol"],"vless");
            assert_eq!(outs[1]["settings"]["vnext"][0]["port"],port);
            assert_eq!(config["routing"]["rules"][0]["outboundTag"],outs[1]["tag"]);
            assert!(!config.to_string().contains("freedom"));
        }
    }

    #[test]
    fn isolated_config_preserves_tls_verification_and_server_identity() {
        let mut srv = server("tls",443);
        if let Protocol::Vless(cfg) = &mut srv.protocol {
            cfg.stream.security = nimbo_subscription::Security::Tls;
            cfg.stream.sni = Some("vpn.example".into());
        }
        let (_,config) = isolated_config(&srv, None).unwrap();
        let stream = &config["outbounds"][1]["streamSettings"];
        assert_eq!(stream["security"],"tls");
        assert_eq!(stream["tlsSettings"]["serverName"],"vpn.example");
        assert!(stream["tlsSettings"].get("allowInsecure").is_none());
    }

    #[tokio::test]
    async fn cancelled_before_start_does_not_resolve_or_create_resources() {
        let parent = std::env::temp_dir().join(format!("nimbo-diagnostic-test-{}",uuid::Uuid::new_v4()));
        let resolve = async { panic!("must not resolve binaries"); #[allow(unreachable_code)] Ok(Binaries{xray:PathBuf::new(),awg:None,naive:None}) };
        assert!(measure(server("a",1),resolve,&parent,"http://test.invalid",100,||false,None).await.is_err());
        assert!(!parent.exists());
    }

    #[tokio::test]
    async fn total_deadline_includes_resolution_without_spawning() {
        let parent = std::env::temp_dir().join(format!("nimbo-diagnostic-test-{}",uuid::Uuid::new_v4()));
        let result = measure(server("a",1),std::future::pending(),&parent,"http://test.invalid",30,||true,None).await;
        assert!(result.unwrap_err().contains("timeout"));
        assert!(!parent.exists());
    }
}

#[cfg(test)]
#[path = "diagnostics_tests.rs"]
mod integration;
