//! Root-only Linux Mihomo broker. No client paths/executables/environment.
use nimbo_ipc::{MihomoReady, MihomoTunRequest};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    fs,
    io::{BufRead, BufReader, Read, Write},
    os::unix::fs::{MetadataExt, OpenOptionsExt, PermissionsExt},
    path::{Path, PathBuf},
    process::{Child, Command, Stdio},
    sync::{mpsc, Mutex},
    time::{Duration, Instant},
};
const CORE: &str = "/usr/local/lib/nimbo/nimbo-mihomo";
const JOURNAL: &str = "/run/nimbo-mihomo-tun-rules.json";
const HOME: &str = "/run/nimbo/mihomo";
const MAX_REPLY: u64 = 12 * 1024 * 1024;
pub fn expected_hash() -> &'static str {
    option_env!("NIMBO_SERVICE_MIHOMO_SHA256").unwrap_or("")
}
fn validate_request(r: &MihomoTunRequest, expected: &str) -> Result<(), String> {
    if expected.len() != 64 || r.binary_sha256 != expected {
        return Err("CORE_HASH_MISMATCH".into());
    }
    if r.yaml.is_empty() || r.yaml.len() > 4 * 1024 * 1024 {
        return Err("SOURCE_TOO_LARGE".into());
    }
    if format!("{:x}", Sha256::digest(r.yaml.as_bytes())) != r.source_sha256 {
        return Err("SOURCE_DIGEST_MISMATCH".into());
    }
    Ok(())
}
fn journal_reserved(path: &Path) -> bool {
    !matches!(fs::symlink_metadata(path), Err(e) if e.kind() == std::io::ErrorKind::NotFound)
}
fn protected(path: &Path, directory: bool) -> bool {
    fs::symlink_metadata(path).is_ok_and(|m| {
        m.uid() == 0
            && m.permissions().mode() & 0o022 == 0
            && if directory { m.is_dir() } else { m.is_file() }
    })
}
fn protected_chain(path: &Path) -> bool {
    path.ancestors().all(|p| protected(p, true))
}
fn core_ready() -> bool {
    !expected_hash().is_empty()
        && protected_chain(Path::new("/usr/local/lib/nimbo"))
        && protected(Path::new(CORE), false)
        && hash_file(Path::new(CORE)).is_ok_and(|hash| hash == expected_hash())
}
fn hash_file(path: &Path) -> Result<String, std::io::Error> {
    let mut file = fs::File::open(path)?;
    let mut hash = Sha256::new();
    let mut buffer = [0u8; 64 * 1024];
    loop {
        let n = file.read(&mut buffer)?;
        if n == 0 {
            break;
        }
        hash.update(&buffer[..n]);
    }
    Ok(format!("{:x}", hash.finalize()))
}
fn safe_code(reply: &Value) -> String {
    reply["error"]["code"]
        .as_str()
        .filter(|s| s.len() < 80 && s.bytes().all(|b| b.is_ascii_uppercase() || b == b'_'))
        .unwrap_or("NATIVE_FAILED")
        .to_owned()
}
fn spawn(mode: &str) -> Result<Child, String> {
    if !core_ready() {
        return Err("CORE_UNAVAILABLE".into());
    }
    Command::new(CORE)
        .arg(mode)
        .env_clear()
        .env("PATH", "/usr/sbin:/usr/bin:/sbin:/bin")
        .env("GOMEMLIMIT", "128MiB")
        .env("GOGC", "75")
        .env("GOMAXPROCS", "2")
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|_| "CORE_SPAWN_FAILED".into())
}
fn read_reply(child: &mut Child) -> Result<mpsc::Receiver<Result<Value, String>>, String> {
    let stdout = child.stdout.take().ok_or("NATIVE_PIPE_FAILED")?;
    let (tx, rx) = mpsc::sync_channel(1);
    std::thread::spawn(move || {
        let mut bytes = Vec::new();
        let result = BufReader::new(stdout)
            .take(MAX_REPLY + 1)
            .read_until(b'\n', &mut bytes)
            .map_err(|_| "NATIVE_PIPE_FAILED".to_owned())
            .and_then(|_| {
                if bytes.is_empty() || bytes.len() as u64 > MAX_REPLY {
                    return Err("INVALID_NATIVE_RESPONSE".into());
                }
                serde_json::from_slice(&bytes).map_err(|_| "INVALID_NATIVE_RESPONSE".into())
            });
        let _ = tx.send(result);
    });
    Ok(rx)
}
fn wait_reply(
    child: &mut Child,
    rx: mpsc::Receiver<Result<Value, String>>,
    alive: &dyn Fn() -> bool,
) -> Result<Value, String> {
    let deadline = Instant::now() + Duration::from_secs(32);
    loop {
        match rx.recv_timeout(Duration::from_millis(25)) {
            Ok(r) => return r,
            Err(mpsc::RecvTimeoutError::Disconnected) => return Err("NATIVE_PIPE_FAILED".into()),
            Err(mpsc::RecvTimeoutError::Timeout) => {}
        }
        if !alive() {
            drop(child.stdin.take());
            return Err("CONNECTION_CANCELLED".into());
        }
        if Instant::now() >= deadline {
            return Err("CORE_START_TIMEOUT".into());
        }
    }
}
fn recover_rules() -> Result<(), String> {
    // Fixed root entry in the same rehashed protected binary; no IPC arguments.
    let mut child = spawn("recover-tun")?;
    drop(child.stdin.take());
    let rx = match read_reply(&mut child) {
        Ok(rx) => rx,
        Err(error) => {
            let _ = child.kill();
            let _ = child.wait();
            return Err(error);
        }
    };
    let deadline = Instant::now() + Duration::from_secs(5);
    while Instant::now() < deadline {
        match child.try_wait() {
            Ok(Some(status)) => {
                let reply = rx
                    .recv_timeout(Duration::from_millis(250))
                    .ok()
                    .and_then(Result::ok);
                return if status.success()
                    && reply.is_some_and(|r| r["apiVersion"] == 1 && r["success"] == true)
                {
                    Ok(())
                } else {
                    Err("TUN_CLEANUP_FAILED".into())
                };
            }
            Ok(None) => std::thread::sleep(Duration::from_millis(10)),
            Err(_) => break,
        }
    }
    let _ = child.kill();
    let _ = child.wait();
    Err("TUN_CLEANUP_FAILED".into())
}
fn join(child: &mut Child) -> Result<(), String> {
    drop(child.stdin.take());
    let deadline = Instant::now() + Duration::from_secs(30);
    while Instant::now() < deadline {
        match child.try_wait() {
            Ok(Some(s)) => return if s.success() { Ok(()) } else { recover_rules() },
            Ok(None) => std::thread::sleep(Duration::from_millis(10)),
            Err(_) => return Err("CORE_WAIT_FAILED".into()),
        }
    }
    // A forced kill is not cleanup. Only verified journal recovery may succeed.
    let _ = child.kill();
    let _ = child.wait();
    recover_rules()
}
pub fn install(source: &Path) -> Result<(), String> {
    if unsafe { libc::geteuid() } != 0 {
        return Err("PRIVILEGE_REQUIRED".into());
    }
    let bytes = fs::read(source).map_err(|_| "CORE_UNAVAILABLE")?;
    if expected_hash().is_empty() || format!("{:x}", Sha256::digest(&bytes)) != expected_hash() {
        return Err("CORE_HASH_MISMATCH".into());
    }
    let parent = Path::new(CORE).parent().unwrap();
    if !protected_chain(parent.parent().unwrap()) {
        return Err("UNSAFE_SERVICE_DIRECTORY".into());
    }
    fs::create_dir_all(parent).map_err(|_| "INSTALL_FAILED")?;
    if !protected(parent, true) {
        return Err("UNSAFE_SERVICE_DIRECTORY".into());
    }
    let temporary = parent.join(format!(".mihomo-{}.new", uuid::Uuid::new_v4()));
    let result = (|| -> Result<(), String> {
        let mut out = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o700)
            .custom_flags(libc::O_NOFOLLOW)
            .open(&temporary)
            .map_err(|_| "INSTALL_FAILED")?;
        out.write_all(&bytes).map_err(|_| "INSTALL_FAILED")?;
        out.sync_all().map_err(|_| "INSTALL_FAILED")?;
        fs::rename(&temporary, CORE).map_err(|_| "INSTALL_FAILED")?;
        Ok(())
    })();
    if result.is_err() {
        let _ = fs::remove_file(temporary);
    }
    result
}
#[derive(Clone, Copy, PartialEq, Eq)]
struct FileIdentity {
    device: u64,
    inode: u64,
    size: u64,
    modified: (i64, i64),
    changed: (i64, i64),
}
#[derive(Default)]
pub struct MihomoOwner {
    inner: Mutex<Option<Running>>,
    verified: Mutex<Option<FileIdentity>>,
}
struct Running {
    client: u64,
    child: Child,
    home: PathBuf,
    joined: bool,
}
impl Running {
    fn stop(&mut self) -> Result<(), String> {
        if self.joined {
            return Ok(());
        }
        let result = join(&mut self.child);
        self.joined = result.is_ok();
        result
    }
}
impl Drop for Running {
    fn drop(&mut self) {
        let _ = self.stop();
        let _ = fs::remove_dir_all(&self.home);
    }
}
impl MihomoOwner {
    pub fn available(&self) -> bool {
        // Avoid hashing the entire core on every tray/status tick. Start always
        // rehashes; protected root inode identity/time changes invalidate cache.
        if expected_hash().is_empty()
            || !protected_chain(Path::new("/usr/local/lib/nimbo"))
            || !protected(Path::new(CORE), false)
        {
            return false;
        }
        let Ok(m) = fs::symlink_metadata(CORE) else {
            return false;
        };
        let key = FileIdentity {
            device: m.dev(),
            inode: m.ino(),
            size: m.len(),
            modified: (m.mtime(), m.mtime_nsec()),
            changed: (m.ctime(), m.ctime_nsec()),
        };
        let Ok(mut verified) = self.verified.lock() else {
            return false;
        };
        if verified.as_ref() == Some(&key) {
            return true;
        }
        if !core_ready() {
            *verified = None;
            return false;
        }
        *verified = Some(key);
        true
    }
    pub fn has_lease(&self) -> bool {
        // A helper restart loses its in-memory lease, not native WAL ownership.
        // Do not let legacy Xray/AWG take over retained or unsafe native state.
        self.inner.lock().map_or(true, |g| g.is_some()) || journal_reserved(Path::new(JOURNAL))
    }
    pub fn running(&self) -> bool {
        self.inner.lock().is_ok_and(|mut g| {
            let Some(running) = g.as_mut() else {
                return false;
            };
            if matches!(running.child.try_wait(), Ok(None)) {
                return true;
            }
            // A dead child is never connected. Retain the lease on recovery
            // failure so another engine cannot take over unknown network state.
            if running.stop().is_ok() {
                drop(g.take());
            }
            false
        })
    }
    pub fn preflight(&self, r: &MihomoTunRequest, alive: &dyn Fn() -> bool) -> Result<(), String> {
        validate_request(r, expected_hash())?;
        let mut child = spawn("validate-tun")?;
        let result = (|| {
            let rx = read_reply(&mut child)?;
            child
                .stdin
                .as_mut()
                .ok_or("NATIVE_PIPE_FAILED")?
                .write_all(r.yaml.as_bytes())
                .map_err(|_| "NATIVE_PIPE_FAILED")?;
            drop(child.stdin.take());
            let reply = wait_reply(&mut child, rx, alive)?;
            if reply["apiVersion"] != 1 || reply["success"] != true {
                return Err(safe_code(&reply));
            }
            if reply["data"]["sourceSHA256"] != r.source_sha256 {
                return Err("SOURCE_DIGEST_MISMATCH".into());
            }
            Ok(())
        })();
        if result.is_err() {
            let _ = child.kill();
        }
        let _ = child.wait();
        result
    }
    pub fn up(
        &self,
        client: u64,
        r: &MihomoTunRequest,
        alive: &dyn Fn() -> bool,
    ) -> Result<MihomoReady, String> {
        self.preflight(r, alive)?;
        let mut guard = self.inner.lock().map_err(|_| "BUSY")?;
        if guard.is_some() {
            return Err("TUN_IN_USE".into());
        }
        let root = Path::new(HOME);
        if !protected_chain(Path::new("/run")) {
            return Err("UNSAFE_SERVICE_DIRECTORY".into());
        }
        for parent in [Path::new("/run/nimbo"), root] {
            if parent.exists() && !protected_chain(parent) {
                return Err("UNSAFE_SERVICE_DIRECTORY".into());
            }
        }
        fs::create_dir_all(root).map_err(|_| "INVALID_DATA_DIRECTORY")?;
        for parent in [Path::new("/run/nimbo"), root] {
            if !protected(parent, true) {
                return Err("UNSAFE_SERVICE_DIRECTORY".into());
            }
        }
        let home = root.join(uuid::Uuid::new_v4().to_string());
        fs::create_dir(&home).map_err(|_| "INVALID_DATA_DIRECTORY")?;
        fs::set_permissions(&home, fs::Permissions::from_mode(0o700))
            .map_err(|_| "INVALID_DATA_DIRECTORY")?;
        let child = match spawn("serve-tun") {
            Ok(c) => c,
            Err(e) => {
                let _ = fs::remove_dir(&home);
                return Err(e);
            }
        };
        let mut running = Running {
            client,
            child,
            home,
            joined: false,
        };
        let secret = format!(
            "{}{}",
            uuid::Uuid::new_v4().simple(),
            uuid::Uuid::new_v4().simple()
        );
        let id = uuid::Uuid::new_v4().to_string();
        let mut options = json!({"dataDir":running.home,"networkOwner":"desktop-tun","desktopIPv6":true,"controllerAddress":"127.0.0.1:0","secret":secret});
        if r.mixed {
            options["mixedAddress"] = json!("127.0.0.1:0");
        }
        let payload=serde_json::to_vec(&json!({"apiVersion":1,"requestId":id,"operation":"start","yaml":r.yaml,"options":options})).map_err(|_|"INVALID_REQUEST")?;
        if payload.len() > 8 * 1024 * 1024 {
            return Err("SOURCE_TOO_LARGE".into());
        }
        let rx = read_reply(&mut running.child)?;
        let stdin = running.child.stdin.as_mut().ok_or("NATIVE_PIPE_FAILED")?;
        stdin
            .write_all(&(payload.len() as u32).to_be_bytes())
            .and_then(|_| stdin.write_all(&payload))
            .and_then(|_| stdin.flush())
            .map_err(|_| "NATIVE_PIPE_FAILED")?;
        let reply = wait_reply(&mut running.child, rx, alive)?;
        if reply["apiVersion"] != 1 || reply["requestId"] != id || reply["success"] != true {
            return Err(safe_code(&reply));
        }
        let info = &reply["data"];
        if info["state"] != "running"
            || info["networkOwner"] != "desktop-tun"
            || info["tunReady"] != true
            || info["sourceSHA256"] != r.source_sha256
        {
            return Err("INVALID_NATIVE_READINESS".into());
        }
        let generation = reply["generation"]
            .as_u64()
            .filter(|g| *g > 0)
            .ok_or("INVALID_NATIVE_READINESS")?;
        let ready = MihomoReady {
            info: info.clone(),
            generation,
            secret,
        };
        *guard = Some(running);
        Ok(ready)
    }
    pub fn down(&self, client: u64) -> Result<(), String> {
        let mut guard = self.inner.lock().map_err(|_| "BUSY")?;
        if let Some(running) = guard.as_mut().filter(|r| r.client == client) {
            let result = running.stop();
            if result.is_ok() {
                drop(guard.take());
            }
            return result;
        }
        Ok(())
    }
    pub fn down_all(&self) {
        if let Ok(mut guard) = self.inner.lock() {
            drop(guard.take());
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn source_and_binary_identity_are_before_spawn() {
        let mut r = MihomoTunRequest {
            yaml: "# exact\r\n".into(),
            source_sha256: format!("{:x}", Sha256::digest(b"# exact\r\n")),
            binary_sha256: "a".repeat(64),
            mixed: false,
        };
        assert!(validate_request(&r, &"a".repeat(64)).is_ok());
        r.binary_sha256 = "b".repeat(64);
        assert_eq!(
            validate_request(&r, &"a".repeat(64)).unwrap_err(),
            "CORE_HASH_MISMATCH"
        );
        r.binary_sha256 = "a".repeat(64);
        r.yaml.push('x');
        assert_eq!(
            validate_request(&r, &"a".repeat(64)).unwrap_err(),
            "SOURCE_DIGEST_MISMATCH"
        );
    }
    #[test]
    fn any_retained_journal_reserves_network_ownership() {
        let directory =
            std::env::temp_dir().join(format!("nimbo-journal-{}", uuid::Uuid::new_v4()));
        fs::create_dir(&directory).unwrap();
        let file = directory.join("journal");
        assert!(!journal_reserved(&file));
        fs::write(&file, b"invalid json").unwrap();
        assert!(journal_reserved(&file));
        fs::remove_file(&file).unwrap();
        std::os::unix::fs::symlink(directory.join("missing"), &file).unwrap();
        assert!(journal_reserved(&file));
        fs::remove_file(file).unwrap();
        fs::remove_dir(directory).unwrap();
    }
    #[test]
    fn native_details_are_never_exposed() {
        assert_eq!(
            safe_code(
                &json!({"error":{"code":"BAD https://secret","message":"private","path":"private"}})
            ),
            "NATIVE_FAILED"
        );
        assert_eq!(
            safe_code(&json!({"error":{"code":"TUN_IN_USE","message":"private"}})),
            "TUN_IN_USE"
        );
    }
}
