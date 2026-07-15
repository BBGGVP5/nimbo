#![cfg(windows)]

use std::ffi::{c_void, OsStr};
use std::io::{self, Read, Write};
use std::os::windows::ffi::OsStrExt;
use std::ptr;
use std::sync::atomic::{AtomicBool, AtomicPtr, Ordering};
use std::sync::Arc;

use anyhow::{anyhow, Result};
use nimbo_ipc::{
    decode_command, encode_response, framing, Command, ErrorCode, KillFailure, KillReport,
    Response, PIPE_NAME, PROTOCOL_VERSION,
};
use tracing::{debug, error, info, warn};
use windows_sys::Win32::Foundation::{
    CloseHandle, ERROR_BROKEN_PIPE, ERROR_PIPE_CONNECTED, FALSE, GENERIC_READ, GENERIC_WRITE,
    HANDLE, INVALID_HANDLE_VALUE, TRUE,
};
use windows_sys::Win32::Security::{
    InitializeSecurityDescriptor, SetSecurityDescriptorDacl, ACL, PSECURITY_DESCRIPTOR,
    SECURITY_ATTRIBUTES, SECURITY_DESCRIPTOR,
};
use windows_sys::Win32::Storage::FileSystem::{
    CreateFileW, ReadFile, WriteFile, FILE_ATTRIBUTE_NORMAL, FILE_SHARE_READ, FILE_SHARE_WRITE,
    OPEN_EXISTING, PIPE_ACCESS_DUPLEX,
};
use windows_sys::Win32::System::Pipes::{
    ConnectNamedPipe, CreateNamedPipeW, DisconnectNamedPipe, PIPE_READMODE_MESSAGE,
    PIPE_REJECT_REMOTE_CLIENTS, PIPE_TYPE_MESSAGE, PIPE_UNLIMITED_INSTANCES, PIPE_WAIT,
};

use super::kill;

const SECURITY_DESCRIPTOR_REVISION: u32 = 1;
const BUFFER_BYTES: u32 = 64 * 1024;

const SERVICE_VERSION: &str = env!("CARGO_PKG_VERSION");

/// Handle of the pipe instance currently blocked in `ConnectNamedPipe`. We
/// keep it in a global atomic so the service stop event can close it and
/// break us out of the blocking accept.
static ACCEPTING_HANDLE: AtomicPtr<c_void> = AtomicPtr::new(ptr::null_mut());

pub fn wake_pending_accept() {
    let raw = ACCEPTING_HANDLE.load(Ordering::SeqCst);
    if raw.is_null() || raw == INVALID_HANDLE_VALUE {
        return;
    }

    // Do not close the server handle from the service-control callback:
    // synchronous ConnectNamedPipe may keep ControlService blocked until the
    // SCM timeout. A short client-side connect completes the pending accept
    // instead, letting the main service loop observe `shutdown` and exit.
    let client = connect_wake_client();
    if client != INVALID_HANDLE_VALUE {
        unsafe {
            CloseHandle(client);
        }
    }
}

pub fn occupy_accept_briefly_for_stop() {
    let client = connect_wake_client();
    if client == INVALID_HANDLE_VALUE {
        return;
    }

    let raw = client as isize;
    let _ = std::thread::spawn(move || {
        // Keep the old service out of ConnectNamedPipe while SCM delivers
        // STOP. This avoids the legacy handler path that closed the server
        // handle and could block ControlService until its timeout.
        std::thread::sleep(std::time::Duration::from_secs(1));
        unsafe {
            CloseHandle(raw as HANDLE);
        }
    });
}

fn connect_wake_client() -> HANDLE {
    let wide = pipe_name_wide();
    unsafe {
        CreateFileW(
            wide.as_ptr(),
            GENERIC_READ | GENERIC_WRITE,
            FILE_SHARE_READ | FILE_SHARE_WRITE,
            ptr::null_mut(),
            OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL,
            ptr::null_mut(),
        )
    }
}

pub fn serve(shutdown: Arc<AtomicBool>) -> Result<()> {
    info!(pipe = PIPE_NAME, "pipe server starting");

    while !shutdown.load(Ordering::SeqCst) {
        let pipe = match create_pipe_instance() {
            Ok(handle) => handle,
            Err(err) => {
                error!(error = %err, "create_pipe_instance failed; retrying in 1s");
                std::thread::sleep(std::time::Duration::from_secs(1));
                continue;
            }
        };

        ACCEPTING_HANDLE.store(pipe.0, Ordering::SeqCst);
        let connect_result = unsafe { ConnectNamedPipe(pipe.0, ptr::null_mut()) };
        ACCEPTING_HANDLE.store(ptr::null_mut(), Ordering::SeqCst);

        if shutdown.load(Ordering::SeqCst) {
            break;
        }

        if connect_result == 0 {
            let err = io::Error::last_os_error();
            if err.raw_os_error() != Some(ERROR_PIPE_CONNECTED as i32) {
                warn!(error = %err, "ConnectNamedPipe failed");
                drop(pipe);
                continue;
            }
        }

        if let Err(err) = handle_client(&pipe) {
            warn!(error = %err, "client session failed");
        }
        unsafe {
            let _ = DisconnectNamedPipe(pipe.0);
        }
        drop(pipe);
    }

    info!("pipe server stopping");
    Ok(())
}

fn handle_client(pipe: &PipeHandle) -> Result<()> {
    let mut io = PipeIo { handle: pipe.0 };
    let payload = framing::read_frame(&mut io).map_err(|e| anyhow!("read frame: {e}"))?;
    let command = decode_command(&payload).map_err(|e| anyhow!("decode command: {e}"))?;
    let response = process_command(command);
    let bytes = encode_response(&response).map_err(|e| anyhow!("encode response: {e}"))?;
    framing::write_frame(&mut io, &bytes).map_err(|e| anyhow!("write frame: {e}"))?;
    Ok(())
}

fn process_command(command: Command) -> Response {
    match command {
        Command::Ping => Response::Pong {
            service_version: SERVICE_VERSION.to_string(),
            protocol: PROTOCOL_VERSION,
        },
        Command::KillProcesses { pids } => {
            debug!(?pids, "kill request");
            let (killed, failed) = kill::kill_pids(&pids);
            Response::KillReport(KillReport {
                killed,
                failed: failed
                    .into_iter()
                    .map(|(pid, message)| KillFailure { pid, message })
                    .collect(),
            })
        }
        Command::Shutdown => Response::Error {
            code: ErrorCode::PermissionDenied,
            message: "shutdown via pipe not supported".into(),
        },
        other => {
            warn!(?other, "unsupported command");
            Response::Error {
                code: ErrorCode::UnknownCommand,
                message: "command not implemented in helper service".into(),
            }
        }
    }
}

struct PipeHandle(HANDLE);

impl Drop for PipeHandle {
    fn drop(&mut self) {
        if !self.0.is_null() && self.0 != INVALID_HANDLE_VALUE {
            unsafe {
                CloseHandle(self.0);
            }
        }
    }
}

struct PipeIo {
    handle: HANDLE,
}

impl Read for PipeIo {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        let mut read: u32 = 0;
        let ok = unsafe {
            ReadFile(
                self.handle,
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

impl Write for PipeIo {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        let mut written: u32 = 0;
        let ok = unsafe {
            WriteFile(
                self.handle,
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

fn create_pipe_instance() -> Result<PipeHandle> {
    let wide = pipe_name_wide();
    let mut descriptor = SecurityDescriptor::allow_authenticated_writers()?;
    let attrs = SECURITY_ATTRIBUTES {
        nLength: std::mem::size_of::<SECURITY_ATTRIBUTES>() as u32,
        lpSecurityDescriptor: descriptor.as_ptr(),
        bInheritHandle: FALSE,
    };

    let handle = unsafe {
        CreateNamedPipeW(
            wide.as_ptr(),
            PIPE_ACCESS_DUPLEX,
            PIPE_TYPE_MESSAGE | PIPE_READMODE_MESSAGE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS,
            PIPE_UNLIMITED_INSTANCES,
            BUFFER_BYTES,
            BUFFER_BYTES,
            0,
            &attrs,
        )
    };

    if handle == INVALID_HANDLE_VALUE {
        return Err(anyhow!(
            "CreateNamedPipeW failed: {}",
            io::Error::last_os_error()
        ));
    }
    Ok(PipeHandle(handle))
}

fn pipe_name_wide() -> Vec<u16> {
    OsStr::new(PIPE_NAME)
        .encode_wide()
        .chain(std::iter::once(0))
        .collect()
}

/// Owns a NULL-DACL SECURITY_DESCRIPTOR so any authenticated process on this
/// machine can write to the pipe. The pipe handle holds the only reference, so
/// the descriptor must outlive every accept. We embed it on the PipeHandle's
/// stack frame via the SECURITY_ATTRIBUTES.lpSecurityDescriptor pointer that
/// CreateNamedPipeW captures.
struct SecurityDescriptor {
    sd: Box<SECURITY_DESCRIPTOR>,
}

impl SecurityDescriptor {
    fn allow_authenticated_writers() -> Result<Self> {
        let mut sd: Box<SECURITY_DESCRIPTOR> = Box::new(unsafe { std::mem::zeroed() });
        let raw: *mut SECURITY_DESCRIPTOR = &mut *sd;
        let ok = unsafe {
            InitializeSecurityDescriptor(raw as PSECURITY_DESCRIPTOR, SECURITY_DESCRIPTOR_REVISION)
        };
        if ok == 0 {
            return Err(anyhow!(
                "InitializeSecurityDescriptor: {}",
                io::Error::last_os_error()
            ));
        }
        // NULL DACL = allow all. For v1 this matches user expectations: any
        // local process can ask the helper to kill PIDs. The helper still
        // enforces that PIDs match the conflicting-process allowlist (the
        // current command set is limited to KillProcesses).
        let null_dacl: *mut ACL = ptr::null_mut();
        let ok = unsafe {
            SetSecurityDescriptorDacl(raw as PSECURITY_DESCRIPTOR, TRUE, null_dacl, FALSE)
        };
        if ok == 0 {
            return Err(anyhow!(
                "SetSecurityDescriptorDacl: {}",
                io::Error::last_os_error()
            ));
        }
        Ok(Self { sd })
    }

    fn as_ptr(&mut self) -> PSECURITY_DESCRIPTOR {
        (&mut *self.sd) as *mut SECURITY_DESCRIPTOR as PSECURITY_DESCRIPTOR
    }
}
