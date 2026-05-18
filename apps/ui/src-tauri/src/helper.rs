// Client-side glue for the nimbo-svc helper service.
//
// Responsibilities:
//   * locate the nimbo-svc.exe binary (bundled next to nimbo.exe or in
//     the resource dir during dev),
//   * query SCM whether NimboHelper is installed and running,
//   * trigger one UAC prompt to install/uninstall the service,
//   * speak the named-pipe protocol to ask the service to kill PIDs.

#![cfg(windows)]

use std::ffi::OsStr;
use std::io::{self, Read, Write};
use std::os::windows::ffi::OsStrExt;
use std::path::{Path, PathBuf};
use std::ptr;
use std::time::{Duration, Instant};

use nimbo_ipc::{
    Command, KillReport, PIPE_NAME, Response, decode_response, encode_command, framing,
};
use tauri::{AppHandle, Manager};
use windows_sys::Win32::Foundation::{
    CloseHandle, ERROR_BROKEN_PIPE, ERROR_PIPE_BUSY, GENERIC_READ, GENERIC_WRITE, HANDLE,
    INVALID_HANDLE_VALUE,
};
use windows_sys::Win32::Storage::FileSystem::{
    CreateFileW, FILE_ATTRIBUTE_NORMAL, OPEN_EXISTING, ReadFile, WriteFile,
};

pub const HELPER_SERVICE_NAME: &str = "NimboHelper";
pub const HELPER_EXE_FILE_NAME: &str = "nimbo-svc.exe";

#[derive(Debug, Clone, serde::Serialize)]
pub struct HelperStatus {
    pub installed: bool,
    pub running: bool,
    pub version: Option<String>,
    pub exe_present: bool,
    pub exe_path: Option<String>,
}

pub fn status(app: &AppHandle) -> HelperStatus {
    let exe = locate_helper_exe(app);
    let exe_present = exe.is_some();
    let (installed, running) = service_state();
    let version = if running {
        ping_helper().ok().and_then(|resp| match resp {
            Response::Pong { service_version, .. } => Some(service_version),
            _ => None,
        })
    } else {
        None
    };
    HelperStatus {
        installed,
        running,
        version,
        exe_present,
        exe_path: exe.map(|p| p.to_string_lossy().to_string()),
    }
}

pub fn install(app: &AppHandle) -> Result<(), String> {
    let exe = locate_helper_exe(app).ok_or_else(|| {
        "nimbo-svc.exe не найден рядом с приложением. Переустановите Nimbo.".to_string()
    })?;
    run_elevated(&exe, "--install")?;
    wait_for_running(Duration::from_secs(10));
    Ok(())
}

pub fn uninstall(app: &AppHandle) -> Result<(), String> {
    let exe = locate_helper_exe(app).ok_or_else(|| {
        "nimbo-svc.exe не найден рядом с приложением.".to_string()
    })?;
    run_elevated(&exe, "--uninstall")?;
    Ok(())
}

pub fn kill_processes(pids: &[u32]) -> Result<KillReport, String> {
    if pids.is_empty() {
        return Ok(KillReport { killed: Vec::new(), failed: Vec::new() });
    }
    let response = send_command(Command::KillProcesses { pids: pids.to_vec() })?;
    match response {
        Response::KillReport(report) => Ok(report),
        Response::Error { message, .. } => Err(message),
        other => Err(format!("неожиданный ответ хелпера: {other:?}")),
    }
}

fn ping_helper() -> Result<Response, String> {
    send_command(Command::Ping)
}

fn send_command(command: Command) -> Result<Response, String> {
    let payload = encode_command(&command).map_err(|e| format!("encode: {e}"))?;
    let mut client = open_pipe()
        .map_err(|e| format!("connect to helper pipe: {e}"))?;
    framing::write_frame(&mut client, &payload)
        .map_err(|e| format!("write frame: {e}"))?;
    let bytes = framing::read_frame(&mut client)
        .map_err(|e| format!("read frame: {e}"))?;
    decode_response(&bytes).map_err(|e| format!("decode response: {e}"))
}

// ─── SCM query ──────────────────────────────────────────────────────────────

fn service_state() -> (bool, bool) {
    use windows_service::service::{ServiceAccess, ServiceState};
    use windows_service::service_manager::{ServiceManager, ServiceManagerAccess};

    let manager = match ServiceManager::local_computer(None::<&str>, ServiceManagerAccess::CONNECT)
    {
        Ok(m) => m,
        Err(_) => return (false, false),
    };
    let service = match manager.open_service(HELPER_SERVICE_NAME, ServiceAccess::QUERY_STATUS) {
        Ok(s) => s,
        Err(_) => return (false, false),
    };
    let running = matches!(
        service.query_status().map(|s| s.current_state),
        Ok(ServiceState::Running) | Ok(ServiceState::StartPending)
    );
    (true, running)
}

fn wait_for_running(deadline: Duration) {
    let start = Instant::now();
    while start.elapsed() < deadline {
        let (_, running) = service_state();
        if running {
            return;
        }
        std::thread::sleep(Duration::from_millis(250));
    }
}

// ─── UAC install/uninstall via Start-Process -Verb RunAs ────────────────────

fn run_elevated(exe: &Path, action_arg: &str) -> Result<(), String> {
    use std::process::Command as ProcessCommand;
    use std::process::Stdio;

    let escaped_exe = exe.to_string_lossy().replace('\'', "''");
    let outer = format!(
        "try {{ $p = Start-Process -FilePath '{escaped_exe}' -ArgumentList '{action_arg}' -Verb RunAs -Wait -PassThru -ErrorAction Stop; exit $p.ExitCode }} catch {{ exit 1223 }}"
    );

    let mut command = ProcessCommand::new("powershell");
    command
        .arg("-NoProfile")
        .arg("-ExecutionPolicy")
        .arg("Bypass")
        .arg("-Command")
        .arg(&outer)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }

    let status = command
        .status()
        .map_err(|e| format!("Не удалось запустить запрос UAC: {e}"))?;

    if status.success() {
        return Ok(());
    }
    if status.code() == Some(1223) {
        return Err("UAC-запрос отменён.".into());
    }
    Err(format!("nimbo-svc {} завершился с кодом {:?}", action_arg, status.code()))
}

// ─── Locate the nimbo-svc.exe binary ────────────────────────────────────────

pub fn locate_helper_exe(app: &AppHandle) -> Option<PathBuf> {
    let mut candidates: Vec<PathBuf> = Vec::new();

    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            candidates.push(dir.join(HELPER_EXE_FILE_NAME));
            candidates.push(dir.join("resources").join(HELPER_EXE_FILE_NAME));
            candidates.push(dir.join("binaries").join(HELPER_EXE_FILE_NAME));
        }
    }

    if let Ok(resource_dir) = app.path().resource_dir() {
        candidates.push(resource_dir.join(HELPER_EXE_FILE_NAME));
        candidates.push(resource_dir.join("resources").join(HELPER_EXE_FILE_NAME));
        candidates.push(resource_dir.join("binaries").join(HELPER_EXE_FILE_NAME));
    }

    if let Ok(cwd) = std::env::current_dir() {
        candidates.push(cwd.join("target").join("release").join(HELPER_EXE_FILE_NAME));
        candidates.push(cwd.join("target").join("debug").join(HELPER_EXE_FILE_NAME));
    }

    candidates.into_iter().find(|p| p.exists())
}

// ─── Named pipe client ──────────────────────────────────────────────────────

struct PipeClient(HANDLE);

impl Drop for PipeClient {
    fn drop(&mut self) {
        unsafe {
            CloseHandle(self.0);
        }
    }
}

impl Read for PipeClient {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        let mut read: u32 = 0;
        let ok = unsafe {
            ReadFile(
                self.0,
                buf.as_mut_ptr() as *mut _,
                buf.len() as u32,
                &mut read,
                ptr::null_mut(),
            )
        };
        if ok == 0 {
            let err = io::Error::last_os_error();
            if err.raw_os_error() == Some(ERROR_BROKEN_PIPE as i32) {
                return Ok(0);
            }
            return Err(err);
        }
        Ok(read as usize)
    }
}

impl Write for PipeClient {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        let mut written: u32 = 0;
        let ok = unsafe {
            WriteFile(
                self.0,
                buf.as_ptr() as *const _,
                buf.len() as u32,
                &mut written,
                ptr::null_mut(),
            )
        };
        if ok == 0 {
            return Err(io::Error::last_os_error());
        }
        Ok(written as usize)
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

fn open_pipe() -> io::Result<PipeClient> {
    let wide: Vec<u16> = OsStr::new(PIPE_NAME)
        .encode_wide()
        .chain(std::iter::once(0))
        .collect();
    let deadline = Instant::now() + Duration::from_secs(2);
    loop {
        let handle = unsafe {
            CreateFileW(
                wide.as_ptr(),
                GENERIC_READ | GENERIC_WRITE,
                0,
                ptr::null(),
                OPEN_EXISTING,
                FILE_ATTRIBUTE_NORMAL,
                ptr::null_mut(),
            )
        };
        if handle != INVALID_HANDLE_VALUE {
            return Ok(PipeClient(handle));
        }
        let err = io::Error::last_os_error();
        if err.raw_os_error() == Some(ERROR_PIPE_BUSY as i32) && Instant::now() < deadline {
            std::thread::sleep(Duration::from_millis(50));
            continue;
        }
        return Err(err);
    }
}
