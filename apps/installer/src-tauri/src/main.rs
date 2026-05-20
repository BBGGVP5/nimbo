#![cfg_attr(windows, windows_subsystem = "windows")]

mod payload;

#[cfg(windows)]
struct SingleInstanceGuard(windows_sys::Win32::Foundation::HANDLE);

#[cfg(windows)]
unsafe impl Send for SingleInstanceGuard {}
#[cfg(windows)]
unsafe impl Sync for SingleInstanceGuard {}

#[cfg(windows)]
impl Drop for SingleInstanceGuard {
    fn drop(&mut self) {
        unsafe {
            let _ = windows_sys::Win32::Foundation::CloseHandle(self.0);
        }
    }
}

#[cfg(not(windows))]
struct SingleInstanceGuard;

#[cfg(windows)]
fn acquire_single_instance() -> Option<SingleInstanceGuard> {
    use std::ffi::OsStr;
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::Foundation::{
        CloseHandle, ERROR_ACCESS_DENIED, ERROR_ALREADY_EXISTS, GetLastError,
    };
    use windows_sys::Win32::System::Threading::CreateMutexW;

    let name: Vec<u16> = OsStr::new("Local\\Nimbo.Installer.Singleton")
        .encode_wide()
        .chain(std::iter::once(0))
        .collect();
    let handle = unsafe { CreateMutexW(std::ptr::null(), 1, name.as_ptr()) };
    if handle.is_null() {
        if unsafe { GetLastError() } == ERROR_ACCESS_DENIED {
            eprintln!("Nimbo Setup is already running; exiting duplicate instance");
            return None;
        }
        eprintln!(
            "failed to create Nimbo Setup single-instance mutex: {}",
            std::io::Error::last_os_error()
        );
        return None;
    }
    if unsafe { GetLastError() } == ERROR_ALREADY_EXISTS {
        unsafe {
            let _ = CloseHandle(handle);
        }
        eprintln!("Nimbo Setup is already running; exiting duplicate instance");
        return None;
    }
    Some(SingleInstanceGuard(handle))
}

#[cfg(not(windows))]
fn acquire_single_instance() -> Option<SingleInstanceGuard> {
    Some(SingleInstanceGuard)
}

fn main() {
    if std::env::args().any(|arg| arg == "--uninstall") {
        let _ = payload::uninstall_from_cli();
        return;
    }

    let Some(single_instance_guard) = acquire_single_instance() else {
        return;
    };

    tauri::Builder::default()
        .manage(single_instance_guard)
        .invoke_handler(tauri::generate_handler![
            payload::probe_installation,
            payload::choose_install_dir,
            payload::install_nimbo,
            payload::open_nimbo,
        ])
        .run(tauri::generate_context!())
        .expect("failed to run Nimbo Setup");
}
