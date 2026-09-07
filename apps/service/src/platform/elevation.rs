#![cfg(windows)]

use std::ffi::OsStr;
use std::io;
use std::os::windows::ffi::OsStrExt;
use std::ptr;

use anyhow::{anyhow, Result};
use windows_sys::Win32::Foundation::{CloseHandle, HANDLE};
use windows_sys::Win32::Security::{
    GetTokenInformation, TokenElevation, TOKEN_ELEVATION, TOKEN_QUERY,
};
use windows_sys::Win32::System::Com::{
    CoInitializeEx, CoUninitialize, COINIT_APARTMENTTHREADED, COINIT_DISABLE_OLE1DDE,
};
use windows_sys::Win32::System::Threading::{
    GetCurrentProcess, GetExitCodeProcess, OpenProcessToken, WaitForSingleObject, INFINITE,
};
use windows_sys::Win32::UI::Shell::{ShellExecuteExW, SEE_MASK_NOCLOSEPROCESS, SHELLEXECUTEINFOW};
use windows_sys::Win32::UI::WindowsAndMessaging::SW_NORMAL;

pub fn is_elevated() -> bool {
    unsafe {
        let mut token: HANDLE = ptr::null_mut();
        if OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &mut token) == 0 {
            return false;
        }
        let mut elevation: TOKEN_ELEVATION = std::mem::zeroed();
        let mut size: u32 = 0;
        let ok = GetTokenInformation(
            token,
            TokenElevation,
            &mut elevation as *mut _ as *mut _,
            std::mem::size_of::<TOKEN_ELEVATION>() as u32,
            &mut size,
        );
        CloseHandle(token);
        ok != 0 && elevation.TokenIsElevated != 0
    }
}

pub fn relaunch_elevated(action_arg: &str) -> Result<i32> {
    // MSDN explicitly requires COM to be initialized before ShellExecuteEx
    // — otherwise the "runas" verb (which delegates to consent.exe via
    // COM) can hang or fail in unintuitive ways, especially when the
    // caller is a windows-subsystem child of another windows-subsystem
    // process (NSIS installer → nimbo-svc.exe). STA is what UAC expects.
    let com_initialized = unsafe {
        let hr = CoInitializeEx(
            std::ptr::null(),
            (COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE) as u32,
        );
        // S_OK (0) or S_FALSE (1, already initialized) — both are fine.
        hr == 0 || hr == 1
    };

    let result = (|| -> Result<i32> {
        let exe = std::env::current_exe().map_err(|e| anyhow!("locate current executable: {e}"))?;
        let exe_wide = wide(exe.as_os_str());
        let verb_wide = wide(OsStr::new("runas"));
        let args_wide = wide(OsStr::new(action_arg));

        let mut info: SHELLEXECUTEINFOW = unsafe { std::mem::zeroed() };
        info.cbSize = std::mem::size_of::<SHELLEXECUTEINFOW>() as u32;
        info.fMask = SEE_MASK_NOCLOSEPROCESS;
        info.lpVerb = verb_wide.as_ptr();
        info.lpFile = exe_wide.as_ptr();
        info.lpParameters = args_wide.as_ptr();
        // SW_NORMAL — the child has no window of its own (windows-subsystem,
        // no window class registered) so this only matters in that it does
        // not interfere with UAC focus the way SW_HIDE occasionally does.
        info.nShow = SW_NORMAL;

        let ok = unsafe { ShellExecuteExW(&mut info) };
        if ok == 0 {
            let err = io::Error::last_os_error();
            // ERROR_CANCELLED (1223) — user clicked No on UAC.
            if err.raw_os_error() == Some(1223) {
                return Ok(1223);
            }
            return Err(anyhow!("ShellExecuteExW(runas) failed: {err}"));
        }

        let process = info.hProcess;
        if process.is_null() {
            return Err(anyhow!(
                "ShellExecuteExW(runas) did not return a process handle"
            ));
        }

        unsafe { WaitForSingleObject(process, INFINITE) };
        let mut exit_code: u32 = 0;
        unsafe { GetExitCodeProcess(process, &mut exit_code) };
        unsafe { CloseHandle(process) };
        Ok(exit_code as i32)
    })();

    if com_initialized {
        unsafe { CoUninitialize() };
    }
    result
}

fn wide(input: &OsStr) -> Vec<u16> {
    input.encode_wide().chain(std::iter::once(0)).collect()
}
