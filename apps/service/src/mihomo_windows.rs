//! Protected Windows native broker. No client paths/executables/environment.
use nimbo_ipc::windows::{private_directory, protected, protected_chain, root};
use nimbo_ipc::{MihomoReady, MihomoTunRequest};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    fs,
    io::{BufRead, BufReader, Read, Write},
    path::{Path, PathBuf},
    process::{Child, Command, Stdio},
    sync::{mpsc, Mutex},
    time::{Duration, Instant},
};
const MAX_REPLY: u64 = 12 * 1024 * 1024;
fn core() -> Result<PathBuf, String> {
    Ok(root()?.join("nimbo-mihomo.exe"))
}
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
fn core_ready() -> bool {
    let Ok(core) = core() else {
        return false;
    };
    !expected_hash().is_empty()
        && protected_chain(core.parent().unwrap())
        && protected(&core)
        && hash_file(&core).is_ok_and(|hash| hash == expected_hash())
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
struct NativeChild {
    child: Child,
    _job: Job,
}
impl std::ops::Deref for NativeChild {
    type Target = Child;
    fn deref(&self) -> &Child {
        &self.child
    }
}
impl std::ops::DerefMut for NativeChild {
    fn deref_mut(&mut self) -> &mut Child {
        &mut self.child
    }
}
struct Job {
    _handle: nimbo_ipc::windows::Handle,
}
impl Job {
    fn attach(child: &Child) -> Result<Self, String> {
        use std::os::windows::io::AsRawHandle;
        use windows_sys::Win32::System::JobObjects::*;
        unsafe {
            let handle = CreateJobObjectW(std::ptr::null(), std::ptr::null());
            if handle.is_null() {
                return Err("CHILD_OWNERSHIP_FAILED".into());
            }
            let job = Self {
                _handle: nimbo_ipc::windows::Handle(handle as isize),
            };
            let mut info: JOBOBJECT_EXTENDED_LIMIT_INFORMATION = std::mem::zeroed();
            info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
            if SetInformationJobObject(
                handle,
                JobObjectExtendedLimitInformation,
                (&info as *const JOBOBJECT_EXTENDED_LIMIT_INFORMATION).cast(),
                std::mem::size_of_val(&info) as u32,
            ) == 0
                || AssignProcessToJobObject(handle, child.as_raw_handle() as _) == 0
            {
                return Err("CHILD_OWNERSHIP_FAILED".into());
            }
            Ok(job)
        }
    }
}
fn spawn(mode: &str) -> Result<NativeChild, String> {
    use std::os::windows::process::CommandExt;
    if !core_ready() {
        return Err("CORE_UNAVAILABLE".into());
    }
    let root = root()?;
    let mut system = vec![0u16; 32768];
    let n = unsafe {
        windows_sys::Win32::System::SystemInformation::GetWindowsDirectoryW(
            system.as_mut_ptr(),
            system.len() as u32,
        )
    };
    if n == 0 || n as usize >= system.len() {
        return Err("CORE_SPAWN_FAILED".into());
    }
    let windows = PathBuf::from(String::from_utf16_lossy(&system[..n as usize]));
    let mut child = Command::new(core()?)
        .arg(mode)
        .current_dir(root)
        .env_clear()
        .env("SystemRoot", &windows)
        .env("PATH", windows.join("System32"))
        .env("GOMEMLIMIT", "128MiB")
        .env("GOGC", "75")
        .env("GOMAXPROCS", "2")
        .creation_flags(0x08000000)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|_| "CORE_SPAWN_FAILED")?;
    // The pinned CLI waits for stdin before any TUN mutation. Attach the Job
    // before sending even one byte; helper death then kills the whole native tree.
    let job = match Job::attach(&child) {
        Ok(j) => j,
        Err(e) => {
            let _ = child.kill();
            let _ = child.wait();
            return Err(e);
        }
    };
    Ok(NativeChild { child, _job: job })
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
fn join(child: &mut Child) -> Result<(), String> {
    drop(child.stdin.take());
    let deadline = Instant::now() + Duration::from_secs(30);
    while Instant::now() < deadline {
        match child.try_wait() {
            Ok(Some(s)) => {
                return if s.success() {
                    Ok(())
                } else {
                    Err("TUN_CLEANUP_FAILED".into())
                }
            }
            Ok(None) => std::thread::sleep(Duration::from_millis(10)),
            Err(_) => return Err("CORE_WAIT_FAILED".into()),
        }
    }
    // A forced kill is not verified cleanup; Windows owner stays failed/retained.
    let _ = child.kill();
    let _ = child.wait();
    Err("TUN_CLEANUP_FAILED".into())
}
/// Prepare before stopping the existing service. No service or filesystem mutation.
pub struct InstallPlan {
    source: PathBuf,
    destination: Option<PathBuf>,
    native: Option<Vec<u8>>,
    image: Vec<u8>,
}
impl InstallPlan {
    pub fn prepare() -> Result<Self, String> {
        let source = std::env::current_exe().map_err(|_| "INSTALL_FAILED")?;
        // Keep legacy-only architectures on their existing installation path.
        // They never expose native TUN operations or a verified native anchor.
        if expected_hash().is_empty() {
            return Ok(Self {
                source,
                destination: None,
                native: None,
                image: Vec::new(),
            });
        }
        let destination = root()?;
        if !protected_chain(destination.parent().ok_or("UNSAFE_SERVICE_DIRECTORY")?)
            || (fs::symlink_metadata(&destination).is_ok()
                && (!destination.is_dir() || !protected(&destination)))
        {
            return Err("UNSAFE_SERVICE_DIRECTORY".into());
        }
        for name in ["nimbo-svc.exe", "nimbo-mihomo.exe"] {
            let path = destination.join(name);
            if fs::symlink_metadata(&path).is_ok() && (!path.is_file() || !protected(&path)) {
                return Err("UNSAFE_SERVICE_DIRECTORY".into());
            }
        }
        let base = source.parent().ok_or("INSTALL_FAILED")?;
        let platform = option_env!("NIMBO_SERVICE_MIHOMO_PLATFORM").unwrap_or("");
        let candidates = [
            base.join("resources/mihomo")
                .join(platform)
                .join("nimbo-mihomo.exe"),
            base.join("resources/resources/mihomo")
                .join(platform)
                .join("nimbo-mihomo.exe"),
            base.join("mihomo").join(platform).join("nimbo-mihomo.exe"),
        ];
        let native = candidates
            .iter()
            .find_map(|p| {
                fs::read(p)
                    .ok()
                    .filter(|b| format!("{:x}", Sha256::digest(b)) == expected_hash())
            })
            .ok_or("CORE_UNAVAILABLE")?;
        let image = fs::read(&source).map_err(|_| "INSTALL_FAILED")?;
        Ok(Self {
            source,
            destination: Some(destination),
            native: Some(native),
            image,
        })
    }
    /// Called only by explicit elevated installation, never pipe commands.
    pub fn apply(self) -> Result<PathBuf, String> {
        let Some(root) = self.destination else {
            return Ok(self.source);
        };
        nimbo_ipc::windows::installation_directory(&root)?;
        replace_private(
            &root.join("nimbo-mihomo.exe"),
            self.native.as_deref().ok_or("CORE_UNAVAILABLE")?,
        )?;
        let image = root.join("nimbo-svc.exe");
        if fs::canonicalize(&self.source).ok() != fs::canonicalize(&image).ok() {
            replace_private(&image, &self.image)?;
        }
        Ok(image)
    }
}

fn replace_private(target: &Path, bytes: &[u8]) -> Result<(), String> {
    if fs::symlink_metadata(target).is_ok() && !protected(target) {
        return Err("UNSAFE_SERVICE_DIRECTORY".into());
    }
    let temporary = target
        .parent()
        .unwrap()
        .join(format!("{}.new", uuid::Uuid::new_v4()));
    let result = (|| {
        let mut file = fs::OpenOptions::new()
            .create_new(true)
            .write(true)
            .open(&temporary)
            .map_err(|_| "INSTALL_FAILED")?;
        file.write_all(bytes)
            .and_then(|_| file.sync_all())
            .map_err(|_| "INSTALL_FAILED")?;
        if !protected(&temporary) {
            return Err("UNSAFE_SERVICE_DIRECTORY".into());
        }
        use std::os::windows::ffi::OsStrExt;
        let wide = |p: &Path| {
            p.as_os_str()
                .encode_wide()
                .chain(Some(0))
                .collect::<Vec<_>>()
        };
        if unsafe {
            windows_sys::Win32::Storage::FileSystem::MoveFileExW(
                wide(&temporary).as_ptr(),
                wide(target).as_ptr(),
                windows_sys::Win32::Storage::FileSystem::MOVEFILE_REPLACE_EXISTING
                    | windows_sys::Win32::Storage::FileSystem::MOVEFILE_WRITE_THROUGH,
            )
        } == 0
        {
            return Err("INSTALL_FAILED".into());
        }
        Ok(())
    })();
    if result.is_err() {
        let _ = fs::remove_file(temporary);
    }
    result
}
#[derive(Default)]
pub struct MihomoOwner {
    inner: Mutex<Option<Running>>,
    verified: Mutex<Option<(u64, std::time::SystemTime)>>,
    validation: Mutex<()>,
}
struct Running {
    client: u64,
    child: NativeChild,
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
        let Ok(core) = core() else {
            return false;
        };
        if !protected_chain(core.parent().unwrap()) || !protected(&core) {
            return false;
        }
        let Ok(meta) = fs::metadata(&core) else {
            return false;
        };
        let Ok(modified) = meta.modified() else {
            return false;
        };
        let key = (meta.len(), modified);
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
        let _validation = self.validation.try_lock().map_err(|_| "BUSY")?;
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
        let root = root()?.join("sessions");
        private_directory(&root)?;
        let home = root.join(uuid::Uuid::new_v4().to_string());
        private_directory(&home)?;
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
        let result = (|| -> Result<MihomoReady, String> {
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
            Ok(ready)
        })();
        match result {
            Ok(ready) => {
                *guard = Some(running);
                Ok(ready)
            }
            Err(error) => {
                // An uncertain partial start must reserve the owner as well.
                if running.stop().is_err() {
                    *guard = Some(running);
                }
                Err(error)
            }
        }
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
        if guard.is_some() {
            Err("LEASE_NOT_OWNED".into())
        } else {
            Ok(())
        }
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
    fn native_job_closes_only_its_owned_child() {
        use std::os::windows::process::CommandExt;
        let mut child = Command::new("powershell.exe")
            .args([
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "[Console]::In.ReadToEnd() | Out-Null; exit 0",
            ])
            .creation_flags(0x08000000)
            .stdin(Stdio::piped())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()
            .unwrap();
        let job = Job::attach(&child).unwrap();
        assert!(child.try_wait().unwrap().is_none());
        drop(job);
        let deadline = Instant::now() + Duration::from_secs(3);
        while child.try_wait().unwrap().is_none() && Instant::now() < deadline {
            std::thread::sleep(Duration::from_millis(10));
        }
        if child.try_wait().unwrap().is_none() {
            let _ = child.kill();
            let _ = child.wait();
            panic!("native job retained child");
        }
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
