#![cfg(windows)]

use std::io;

use tracing::debug;
use windows_sys::Win32::Foundation::CloseHandle;
use windows_sys::Win32::System::Threading::{OpenProcess, TerminateProcess, PROCESS_TERMINATE};

pub fn kill_pids(pids: &[u32]) -> (Vec<u32>, Vec<(u32, String)>) {
    let mut killed = Vec::new();
    let mut failed = Vec::new();
    for &pid in pids {
        match terminate_one(pid) {
            Ok(()) => {
                debug!(pid, "terminated");
                killed.push(pid);
            }
            Err(err) => {
                debug!(pid, error = %err, "terminate failed");
                failed.push((pid, err.to_string()));
            }
        }
    }
    (killed, failed)
}

fn terminate_one(pid: u32) -> Result<(), io::Error> {
    if pid == 0 {
        return Err(io::Error::new(io::ErrorKind::InvalidInput, "pid 0 rejected"));
    }
    let handle = unsafe { OpenProcess(PROCESS_TERMINATE, 0, pid) };
    if handle.is_null() {
        return Err(io::Error::last_os_error());
    }
    let result = unsafe { TerminateProcess(handle, 1) };
    unsafe {
        CloseHandle(handle);
    }
    if result == 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(())
}
