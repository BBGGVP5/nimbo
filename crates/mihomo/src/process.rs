use crate::{
    controller::Controller,
    wire::{self, Envelope, RuntimeInfo, VerifiedBinary},
    FullProfile, Inspection, ProfileKind,
};
use std::{
    io::{BufRead, BufReader, Read, Write},
    path::Path,
    process::{Child, Command, Stdio},
    time::Duration,
};

/// Child lifetime is owned by one Rust object. Killing this owner never changes
/// routes/DNS/firewall: native networkOwner is desktop-proxy only.
struct OwnedChild {
    child: Child,
    #[cfg(windows)]
    _job: Job,
}
impl OwnedChild {
    fn spawn(binary: &VerifiedBinary, operation: &str) -> Result<Self, String> {
        binary.reverify()?;
        let mut command = Command::new(binary.path());
        command
            .arg(operation)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null());
        // Native diagnostics can include config/provider material. Never inherit
        // app logs or a terminal; protocol stdout is separately bounded.
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            command.creation_flags(0x08000000);
        }
        #[cfg(target_os = "linux")]
        unsafe {
            use std::os::unix::process::CommandExt;
            let parent = libc::getpid();
            command.pre_exec(move || {
                if libc::prctl(libc::PR_SET_PDEATHSIG, libc::SIGKILL) != 0 {
                    return Err(std::io::Error::last_os_error());
                }
                if libc::getppid() != parent {
                    return Err(std::io::Error::other("parent exited"));
                }
                Ok(())
            });
        }
        let child = command.spawn().map_err(|_| "CORE_SPAWN_FAILED")?;
        #[cfg(windows)]
        let job = match Job::attach(&child) {
            Ok(job) => job,
            Err(error) => {
                let mut child = child;
                let _ = child.kill();
                let _ = child.wait();
                return Err(error);
            }
        };
        Ok(Self {
            child,
            #[cfg(windows)]
            _job: job,
        })
    }
    async fn exchange(&mut self, input: Vec<u8>, deadline: Duration) -> Result<Vec<u8>, String> {
        let mut stdin = self.child.stdin.take().ok_or("NATIVE_PIPE_FAILED")?;
        let stdout = self.child.stdout.take().ok_or("NATIVE_PIPE_FAILED")?;
        let (sender, receiver) = tokio::sync::oneshot::channel();
        std::thread::spawn(move || {
            let result = (|| {
                stdin.write_all(&input).map_err(|_| "NATIVE_PIPE_FAILED")?;
                drop(stdin);
                let mut bytes = Vec::new();
                let mut reader = BufReader::new(stdout).take((wire::MAX_RESPONSE + 1) as u64);
                reader
                    .read_until(b'\n', &mut bytes)
                    .map_err(|_| "NATIVE_PIPE_FAILED")?;
                if bytes.is_empty() || bytes.len() > wire::MAX_RESPONSE {
                    return Err("INVALID_NATIVE_RESPONSE");
                }
                Ok(bytes)
            })()
            .map_err(String::from);
            let _ = sender.send(result);
        });
        tokio::time::timeout(deadline, receiver)
            .await
            .map_err(|_| "CORE_START_TIMEOUT")?
            .map_err(|_| "NATIVE_PIPE_FAILED")?
    }
    fn running(&mut self) -> bool {
        matches!(self.child.try_wait(), Ok(None))
    }
    fn terminate(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}
impl Drop for OwnedChild {
    fn drop(&mut self) {
        self.terminate();
    }
}

#[cfg(windows)]
struct Job(isize);
#[cfg(windows)]
impl Job {
    fn attach(child: &Child) -> Result<Self, String> {
        use std::os::windows::io::AsRawHandle;
        use windows_sys::Win32::{Foundation::CloseHandle, System::JobObjects::*};
        unsafe {
            let handle = CreateJobObjectW(std::ptr::null(), std::ptr::null());
            if handle.is_null() {
                return Err("CHILD_OWNERSHIP_FAILED".into());
            }
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
                CloseHandle(handle);
                return Err("CHILD_OWNERSHIP_FAILED".into());
            }
            Ok(Self(handle as isize))
        }
    }
}
#[cfg(windows)]
impl Drop for Job {
    fn drop(&mut self) {
        unsafe {
            windows_sys::Win32::Foundation::CloseHandle(self.0 as _);
        }
    }
}

pub async fn inspect(binary: &VerifiedBinary, profile: &FullProfile) -> Result<Inspection, String> {
    profile.verify().map_err(String::from)?;
    let mut child = OwnedChild::spawn(binary, "inspect")?;
    let bytes = child
        .exchange(
            profile.original_text.as_bytes().to_vec(),
            Duration::from_secs(30),
        )
        .await?;
    wire::decode_inspection(Envelope::decode(&bytes, None)?, profile)
}

enum SessionOwner {
    Proxy(OwnedChild),
    #[cfg(target_os = "linux")]
    Tun(crate::helper::Lease),
}
impl SessionOwner {
    fn running(&mut self) -> bool {
        match self {
            Self::Proxy(c) => c.running(),
            #[cfg(target_os = "linux")]
            Self::Tun(c) => c.running(),
        }
    }
    fn terminate(&mut self) -> Result<(), String> {
        match self {
            Self::Proxy(c) => {
                c.terminate();
                Ok(())
            }
            #[cfg(target_os = "linux")]
            Self::Tun(c) => c.stop(),
        }
    }
}
pub struct Session {
    child: SessionOwner,
    controller: Controller,
    pub info: RuntimeInfo,
    pub profile_id: String,
    pub source_digest: String,
    /// Native generation restarts for each helper process; this UUID does not.
    /// Frontend operations MUST supply it, not only native generation 1.
    pub session_id: String,
}
impl Session {
    #[cfg(target_os = "linux")]
    pub async fn start_tun(
        binary: &VerifiedBinary,
        profile: &FullProfile,
        mixed: bool,
    ) -> Result<Self, String> {
        profile.verify().map_err(String::from)?;
        binary.reverify()?;
        let (lease, ready) =
            crate::helper::Lease::start(tun_request(binary, profile, mixed)?).await?;
        let info: RuntimeInfo =
            serde_json::from_value(ready.info).map_err(|_| "INVALID_NATIVE_READINESS")?;
        info.validate_tun(&profile.source_digest, mixed)?;
        let controller = Controller::new(&info.controller_address, ready.secret, ready.generation)?;
        let session = Self {
            child: SessionOwner::Tun(lease),
            controller,
            info,
            profile_id: profile.id.clone(),
            source_digest: profile.source_digest.clone(),
            session_id: uuid::Uuid::new_v4().to_string(),
        };
        let actual: RuntimeInfo = serde_json::from_value(session.controller.status().await?)
            .map_err(|_| "INVALID_NATIVE_READINESS")?;
        actual.validate_tun(&profile.source_digest, mixed)?;
        if actual.controller_address != session.info.controller_address
            || actual.mixed_address != session.info.mixed_address
        {
            return Err("INVALID_NATIVE_READINESS".into());
        }
        for (group, name) in &profile.selections {
            session.controller.select(group, name).await?;
        }
        Ok(session)
    }

    pub async fn start(
        binary: &VerifiedBinary,
        profile: &FullProfile,
        data_dir: &Path,
    ) -> Result<Self, String> {
        if profile.kind != ProfileKind::MihomoYaml {
            return Err("UNSUPPORTED_CORE".into());
        }
        #[cfg(not(any(windows, target_os = "linux")))]
        return Err("PLATFORM_UNAVAILABLE".into());
        profile.verify().map_err(String::from)?;
        let inspection = inspect(binary, profile).await?;
        if !inspection.issues.is_empty() {
            return Err("UNSUPPORTED_FIELD".into());
        }
        if !data_dir.is_absolute() {
            return Err("INVALID_DATA_DIRECTORY".into());
        }
        std::fs::create_dir_all(data_dir).map_err(|_| "INVALID_DATA_DIRECTORY")?;
        let secret = wire::fresh_secret();
        let request = wire::start_request(profile, data_dir, &secret)?;
        let id = request["requestId"].as_str().ok_or("INVALID_REQUEST")?;
        let mut child = OwnedChild::spawn(binary, "serve")?;
        let bytes = child
            .exchange(
                serde_json::to_vec(&request).map_err(|_| "INVALID_REQUEST")?,
                Duration::from_secs(35),
            )
            .await?;
        let envelope = Envelope::decode(&bytes, Some(id))?;
        let info: RuntimeInfo =
            serde_json::from_value(envelope.data).map_err(|_| "INVALID_NATIVE_READINESS")?;
        info.validate(&profile.source_digest)?;
        if !child.running() {
            return Err("CORE_EXITED".into());
        }
        let controller = Controller::new(&info.controller_address, secret, envelope.generation)?;
        let session = Self {
            child: SessionOwner::Proxy(child),
            controller,
            info,
            profile_id: profile.id.clone(),
            source_digest: profile.source_digest.clone(),
            session_id: uuid::Uuid::new_v4().to_string(),
        };
        // Authenticated status identity check is required; an open socket is not readiness.
        let status: RuntimeInfo = serde_json::from_value(session.controller.status().await?)
            .map_err(|_| "INVALID_NATIVE_READINESS")?;
        status.validate(&profile.source_digest)?;
        if status.controller_address != session.info.controller_address
            || status.mixed_address != session.info.mixed_address
        {
            return Err("INVALID_NATIVE_READINESS".into());
        }
        for (group, name) in &profile.selections {
            // A stale provider member must fail startup explicitly, not silently
            // fall back to a different proxy while presenting the saved choice.
            session.controller.select(group, name).await?;
        }
        Ok(session)
    }
    pub fn controller(&self) -> Controller {
        self.controller.clone()
    }
    pub fn is_running(&mut self) -> bool {
        self.child.running()
    }
    pub fn owns(&self, profile_id: &str, session_id: &str) -> bool {
        self.profile_id == profile_id && self.session_id == session_id
    }
    pub async fn stop(&mut self) -> Result<(), String> {
        let result = tokio::time::timeout(Duration::from_secs(3), self.controller.stop()).await;
        let closed = self.child.terminate();
        closed?;
        match result {
            Ok(Ok(())) => Ok(()),
            _ => Err("CORE_STOP_FORCED".into()),
        }
    }
    /// For synchronous app exit or generic legacy teardown. Only this owned
    /// process is terminated; parent manages its separate system-proxy snapshot.
    pub fn stop_now(&mut self) -> Result<(), String> {
        self.child.terminate()
    }
}

#[cfg(target_os = "linux")]
pub fn tun_request(
    binary: &VerifiedBinary,
    profile: &FullProfile,
    mixed: bool,
) -> Result<nimbo_ipc::MihomoTunRequest, String> {
    profile.verify().map_err(String::from)?;
    if profile.kind != ProfileKind::MihomoYaml {
        return Err("UNSUPPORTED_CORE".into());
    }
    let hash = binary.identity()["sha256"]
        .as_str()
        .ok_or("CORE_UNAVAILABLE")?
        .to_owned();
    Ok(nimbo_ipc::MihomoTunRequest {
        yaml: profile.original_text.clone(),
        source_sha256: profile.source_digest.clone(),
        binary_sha256: hash,
        mixed,
    })
}
