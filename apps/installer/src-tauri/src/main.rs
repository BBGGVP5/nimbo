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
        CloseHandle, GetLastError, ERROR_ACCESS_DENIED, ERROR_ALREADY_EXISTS,
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
    // We're in uninstall mode if either the binary is named like
    // `Uninstall.exe` (the copy we drop into the install dir) or the caller
    // passed `--uninstall` (used by the registry "UninstallString" entry from
    // the Windows "Программы и компоненты" panel). In both cases we want to
    // show a proper uninstaller UI rather than silently wiping things — old
    // behaviour was confusing: double-clicking Uninstall.exe just opened the
    // regular installer in "Обновление" mode.
    let args: Vec<String> = std::env::args().collect();
    let invoked_as_uninstaller = std::env::current_exe()
        .ok()
        .and_then(|path| {
            path.file_name()
                .map(|name| name.to_string_lossy().to_ascii_lowercase())
        })
        .is_some_and(|name| name.starts_with("uninstall"));
    let uninstall_arg = args.iter().any(|arg| arg == "--uninstall");
    // Headless escape hatch: `--uninstall --silent` keeps the old silent
    // behaviour for scripts that integrated against it.
    let silent_flag = args.iter().any(|arg| arg == "--silent");

    if (invoked_as_uninstaller || uninstall_arg) && silent_flag {
        let _ = payload::uninstall_from_cli();
        return;
    }

    let uninstall_mode = invoked_as_uninstaller || uninstall_arg;
    if uninstall_mode {
        payload::set_uninstall_mode();
    }

    let Some(single_instance_guard) = acquire_single_instance() else {
        return;
    };

    tauri::Builder::default()
        .manage(single_instance_guard)
        .invoke_handler(tauri::generate_handler![
            payload::probe_installation,
            payload::probe_uninstallation,
            payload::choose_install_dir,
            payload::install_nimbo,
            payload::uninstall_nimbo,
            payload::open_nimbo,
            payload::read_app_theme,
            payload::get_installer_mode,
        ])
        .run(tauri::generate_context!())
        .expect("failed to run Nimbo Setup");
}
