#![cfg(windows)]

use std::ffi::{OsStr, OsString};
use std::os::windows::ffi::OsStrExt;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use anyhow::{anyhow, Context, Result};
use tracing::{error, info, warn};

mod elevation;
mod kill;
mod mihomo_pipe;
mod pipe;

pub const SERVICE_NAME: &str = "NimboHelper";
pub const SERVICE_DISPLAY_NAME: &str = "Nimbo Helper Service";
pub const SERVICE_DESCRIPTION: &str =
    "Helper service for Nimbo. Terminates conflicting VPN processes on request from Nimbo UI.";
const VERSION: &str = env!("CARGO_PKG_VERSION");
const MAX_LOG_BYTES: u64 = 5 * 1024 * 1024;

pub fn run() -> Result<()> {
    let args: Vec<String> = std::env::args().collect();
    let mode = parse_mode(&args);
    if matches!(mode, Mode::CheckInstallDirectory) {
        // Read-only means no ProgramData log creation/rotation either.
        return check_install_directory();
    }
    if matches!(mode, Mode::RepairInstallPermissions) {
        // Explicit action only; no logging, service stop/start or runtime guard.
        let user = nimbo_ipc::windows::permissions::current_user().map_err(|e| anyhow!(e))?;
        let original = args.iter().find_map(|a| a.strip_prefix("--repair-user="));
        if original.is_some_and(|sid| sid != user) {
            return Err(anyhow!("PERMISSIONS_ACCOUNT_CHANGED"));
        }
        if !elevation::is_elevated() {
            let code = elevation::relaunch_elevated(&format!(
                "--repair-install-permissions --repair-user={user}"
            ))?;
            return if code == 0 {
                Ok(())
            } else {
                Err(anyhow!(
                    "elevated permission repair exited with code {code}"
                ))
            };
        }
        if crate::mihomo_windows::expected_hash().is_empty() {
            return Err(anyhow!("UNSAFE_SERVICE_DIRECTORY"));
        }
        nimbo_ipc::windows::permissions::repair(&user).map_err(|e| anyhow!(e))?;
        return check_install_directory();
    }
    init_tracing(&args);
    info!(version = VERSION, "nimbo-svc starting");

    let _runtime_guard = match mode {
        Mode::RunForeground | Mode::Service => match acquire_runtime_guard()? {
            Some(guard) => Some(guard),
            None => {
                warn!("another nimbo-svc runtime is already active; exiting duplicate");
                return Ok(());
            }
        },
        Mode::Install
        | Mode::Uninstall
        | Mode::PreInstall
        | Mode::CheckInstallDirectory
        | Mode::RepairInstallPermissions => None,
    };

    match mode {
        Mode::CheckInstallDirectory => check_install_directory(),
        Mode::RepairInstallPermissions => unreachable!("handled before logging"),
        Mode::Install => maybe_elevate_then("--install", install_service),
        Mode::Uninstall => maybe_elevate_then("--uninstall", uninstall_service),
        Mode::PreInstall => maybe_elevate_then("--pre-install", pre_install_stop),
        Mode::RunForeground => run_pipe_loop(Arc::new(AtomicBool::new(false))),
        Mode::Service => run_as_service(),
    }
}

fn check_install_directory() -> Result<()> {
    crate::mihomo_windows::InstallPlan::check_directory()
        .map(|_| ())
        .map_err(|error| {
            if error == "UNSAFE_SERVICE_DIRECTORY"
                && !crate::mihomo_windows::expected_hash().is_empty()
                && nimbo_ipc::windows::permissions::repair_available()
            {
                anyhow!("PERMISSIONS_REPAIR_AVAILABLE")
            } else {
                anyhow!(error)
            }
        })
}

fn maybe_elevate_then(action_arg: &str, run: fn() -> Result<()>) -> Result<()> {
    if elevation::is_elevated() {
        return run();
    }
    let code = elevation::relaunch_elevated(action_arg)?;
    if code == 0 {
        Ok(())
    } else if code == 1223 {
        Err(anyhow!("UAC отменён пользователем"))
    } else {
        Err(anyhow!("elevated {action_arg} exited with code {code}"))
    }
}

#[derive(Debug, PartialEq, Eq)]
enum Mode {
    Install,
    Uninstall,
    PreInstall,
    CheckInstallDirectory,
    RepairInstallPermissions,
    RunForeground,
    Service,
}

fn parse_mode(args: &[String]) -> Mode {
    for arg in args.iter().skip(1) {
        match arg.as_str() {
            "--check-install-directory" => return Mode::CheckInstallDirectory,
            "--repair-install-permissions" => return Mode::RepairInstallPermissions,
            "--install" | "install" => return Mode::Install,
            "--uninstall" | "uninstall" => return Mode::Uninstall,
            "--pre-install" | "pre-install" => return Mode::PreInstall,
            "--run-foreground" | "run-foreground" => return Mode::RunForeground,
            _ => {}
        }
    }
    Mode::Service
}

struct RuntimeGuard(windows_sys::Win32::Foundation::HANDLE);

impl Drop for RuntimeGuard {
    fn drop(&mut self) {
        unsafe {
            let _ = windows_sys::Win32::Foundation::CloseHandle(self.0);
        }
    }
}

fn acquire_runtime_guard() -> Result<Option<RuntimeGuard>> {
    use windows_sys::Win32::Foundation::{
        CloseHandle, GetLastError, ERROR_ACCESS_DENIED, ERROR_ALREADY_EXISTS,
    };
    use windows_sys::Win32::System::Threading::CreateMutexW;

    let name: Vec<u16> = OsStr::new("Global\\Nimbo.Helper.Runtime")
        .encode_wide()
        .chain(std::iter::once(0))
        .collect();
    let handle = unsafe { CreateMutexW(std::ptr::null(), 1, name.as_ptr()) };
    if handle.is_null() {
        if unsafe { GetLastError() } == ERROR_ACCESS_DENIED {
            return Ok(None);
        }
        return Err(anyhow!(
            "create helper runtime mutex: {}",
            std::io::Error::last_os_error()
        ));
    }
    if unsafe { GetLastError() } == ERROR_ALREADY_EXISTS {
        unsafe {
            let _ = CloseHandle(handle);
        }
        return Ok(None);
    }
    Ok(Some(RuntimeGuard(handle)))
}

fn init_tracing(args: &[String]) {
    let foreground = args
        .iter()
        .any(|a| a == "--run-foreground" || a == "run-foreground");
    let env = tracing_subscriber::EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("info"));
    let builder = tracing_subscriber::fmt().with_env_filter(env);

    if foreground {
        attach_parent_console();
        let _ = builder.try_init();
        return;
    }

    if let Some(log_path) = log_file_path() {
        if let Some(parent) = log_path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        let _ = rotate_log_if_needed(&log_path);
        if let Ok(file) = std::fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(&log_path)
        {
            let _ = builder
                .with_ansi(false)
                .with_writer(std::sync::Mutex::new(file))
                .try_init();
            return;
        }
    }
    let _ = builder.try_init();
}

fn attach_parent_console() {
    // Best-effort: attaches stdio to the parent terminal if there is one.
    // Silently no-ops when launched from a GUI parent (NSIS, double-click).
    use windows_sys::Win32::System::Console::{AttachConsole, ATTACH_PARENT_PROCESS};
    unsafe {
        AttachConsole(ATTACH_PARENT_PROCESS);
    }
}

fn log_file_path() -> Option<PathBuf> {
    let base = std::env::var_os("ProgramData")
        .map(PathBuf::from)
        .or_else(dirs::data_local_dir)?;
    Some(base.join("Nimbo").join("helper.log"))
}

fn rotate_log_if_needed(path: &std::path::Path) -> std::io::Result<()> {
    let Ok(metadata) = std::fs::metadata(path) else {
        return Ok(());
    };
    if metadata.len() < MAX_LOG_BYTES {
        return Ok(());
    }

    let rotated = path.with_extension("log.1");
    if rotated.exists() {
        std::fs::remove_file(&rotated)?;
    }
    std::fs::rename(path, rotated)
}

// ─── Service runtime ────────────────────────────────────────────────────────

windows_service::define_windows_service!(ffi_service_main, service_main);

fn run_as_service() -> Result<()> {
    use windows_service::service_dispatcher;
    service_dispatcher::start(SERVICE_NAME, ffi_service_main)
        .map_err(|e| anyhow!("service dispatcher start: {e}"))?;
    Ok(())
}

fn service_main(_args: Vec<OsString>) {
    if let Err(err) = service_entrypoint() {
        error!(error = %err, "service entrypoint failed");
    }
}

fn service_entrypoint() -> Result<()> {
    use windows_service::service::{
        ServiceControl, ServiceControlAccept, ServiceExitCode, ServiceState, ServiceStatus,
        ServiceType,
    };
    use windows_service::service_control_handler::{self, ServiceControlHandlerResult};

    let shutdown = Arc::new(AtomicBool::new(false));
    let shutdown_for_handler = Arc::clone(&shutdown);

    let event_handler = move |control_event| -> ServiceControlHandlerResult {
        match control_event {
            ServiceControl::Stop | ServiceControl::Shutdown => {
                shutdown_for_handler.store(true, Ordering::SeqCst);
                pipe::wake_pending_accept();
                ServiceControlHandlerResult::NoError
            }
            ServiceControl::Interrogate => ServiceControlHandlerResult::NoError,
            _ => ServiceControlHandlerResult::NotImplemented,
        }
    };

    let status_handle = service_control_handler::register(SERVICE_NAME, event_handler)
        .map_err(|e| anyhow!("register service control handler: {e}"))?;

    status_handle
        .set_service_status(ServiceStatus {
            service_type: ServiceType::OWN_PROCESS,
            current_state: ServiceState::Running,
            controls_accepted: ServiceControlAccept::STOP | ServiceControlAccept::SHUTDOWN,
            exit_code: ServiceExitCode::Win32(0),
            checkpoint: 0,
            wait_hint: Duration::from_secs(5),
            process_id: None,
        })
        .map_err(|e| anyhow!("set running status: {e}"))?;

    info!("nimbo-svc running");
    let run_result = run_pipe_loop(Arc::clone(&shutdown));
    if let Err(ref err) = run_result {
        error!(error = %err, "pipe loop terminated with error");
    }

    let _ = status_handle.set_service_status(ServiceStatus {
        service_type: ServiceType::OWN_PROCESS,
        current_state: ServiceState::Stopped,
        controls_accepted: ServiceControlAccept::empty(),
        exit_code: ServiceExitCode::Win32(0),
        checkpoint: 0,
        wait_hint: Duration::ZERO,
        process_id: None,
    });
    run_result
}

fn run_pipe_loop(shutdown: Arc<AtomicBool>) -> Result<()> {
    let tun_shutdown = shutdown.clone();
    let tun = std::thread::spawn(move || {
        if let Err(error) = mihomo_pipe::serve(tun_shutdown) {
            warn!(%error,"native TUN broker unavailable");
        }
    });
    let result = pipe::serve(shutdown.clone());
    shutdown.store(true, Ordering::SeqCst);
    let _ = tun.join();
    result
}

// ─── Service install / uninstall ────────────────────────────────────────────

fn install_service() -> Result<()> {
    let install = crate::mihomo_windows::InstallPlan::prepare().map_err(|e| anyhow!(e))?;
    use windows_service::service::{
        ServiceAccess, ServiceErrorControl, ServiceInfo, ServiceStartType, ServiceType,
    };
    use windows_service::service_manager::{ServiceManager, ServiceManagerAccess};

    let manager = ServiceManager::local_computer(
        None::<&str>,
        ServiceManagerAccess::CONNECT | ServiceManagerAccess::CREATE_SERVICE,
    )
    .context("open SCM (CONNECT|CREATE_SERVICE) — нужны права администратора")?;

    if let Ok(existing) = manager.open_service(
        SERVICE_NAME,
        ServiceAccess::STOP | ServiceAccess::QUERY_STATUS,
    ) {
        if existing
            .query_status()
            .is_ok_and(|s| s.current_state != windows_service::service::ServiceState::Stopped)
        {
            pipe::occupy_accept_briefly_for_stop();
            existing
                .stop()
                .context("stop helper before protected upgrade")?;
            wait_until_stopped(&existing);
            if !existing
                .query_status()
                .is_ok_and(|s| s.current_state == windows_service::service::ServiceState::Stopped)
            {
                return Err(anyhow!("helper did not stop"));
            }
        }
    }
    let exe = install.apply().map_err(|e| anyhow!(e))?;

    let info = ServiceInfo {
        name: OsString::from(SERVICE_NAME),
        display_name: OsString::from(SERVICE_DISPLAY_NAME),
        service_type: ServiceType::OWN_PROCESS,
        start_type: ServiceStartType::AutoStart,
        error_control: ServiceErrorControl::Normal,
        executable_path: exe,
        launch_arguments: vec![],
        dependencies: vec![],
        account_name: None, // LocalSystem
        account_password: None,
    };

    let access = ServiceAccess::CHANGE_CONFIG
        | ServiceAccess::START
        | ServiceAccess::STOP
        | ServiceAccess::QUERY_STATUS;

    let (service, was_upgrade) = match manager.create_service(&info, access) {
        Ok(svc) => (svc, false),
        Err(err) => {
            if is_already_exists(&err) {
                info!("service already installed; updating");
                let existing = manager.open_service(SERVICE_NAME, access)?;
                // Stop so the SCM lets go of the (now-renamed) old binary
                // image. We can then update the config to point at the new
                // file path and start the service back up.
                if let Ok(status) = existing.query_status() {
                    if status.current_state != windows_service::service::ServiceState::Stopped {
                        pipe::occupy_accept_briefly_for_stop();
                        if let Err(stop_err) = existing.stop() {
                            warn!(error = %stop_err, "stop existing service");
                        } else {
                            wait_until_stopped(&existing);
                        }
                    }
                }
                existing
                    .change_config(&info)
                    .context("update service config")?;
                (existing, true)
            } else {
                return Err(anyhow!("create service: {err}"));
            }
        }
    };

    service
        .set_description(SERVICE_DESCRIPTION)
        .context("set service description")?;

    match service.query_status() {
        Ok(status)
            if matches!(
                status.current_state,
                windows_service::service::ServiceState::Running
                    | windows_service::service::ServiceState::StartPending
            ) =>
        {
            info!("service already running");
        }
        _ => {
            service
                .start::<&str>(&[])
                .context("start service after install")?;
            info!("service started");
        }
    }

    if was_upgrade {
        cleanup_old_binaries();
    }
    Ok(())
}

// Deletes the *.old files left next to nimbo-svc.exe by the installer's
// rename-before-overwrite step. We call this after the freshly installed
// service has started, by which point Windows has dropped its handle on
// the previous image.
fn cleanup_old_binaries() {
    let Ok(exe) = std::env::current_exe() else {
        return;
    };
    let Some(dir) = exe.parent() else {
        return;
    };
    for name in ["nimbo-svc.exe.old", "Nimbo.exe.old"] {
        let path = dir.join(name);
        if path.exists() {
            if let Err(err) = std::fs::remove_file(&path) {
                warn!(?path, error = %err, "remove stale .old binary");
            }
        }
    }
}

fn uninstall_service() -> Result<()> {
    use windows_service::service::{ServiceAccess, ServiceState};
    use windows_service::service_manager::{ServiceManager, ServiceManagerAccess};

    let manager = ServiceManager::local_computer(None::<&str>, ServiceManagerAccess::CONNECT)
        .context("open SCM (CONNECT)")?;

    let service = match manager.open_service(
        SERVICE_NAME,
        ServiceAccess::STOP | ServiceAccess::DELETE | ServiceAccess::QUERY_STATUS,
    ) {
        Ok(svc) => svc,
        Err(err) if is_not_found(&err) => {
            crate::mihomo_firewall::release_for_uninstall().map_err(|e| anyhow!(e))?;
            println!("nimbo-svc was not installed");
            return Ok(());
        }
        Err(err) => return Err(anyhow!("open service: {err}")),
    };

    if let Ok(status) = service.query_status() {
        if status.current_state != ServiceState::Stopped {
            pipe::occupy_accept_briefly_for_stop();
            if let Err(err) = service.stop() {
                warn!(error = %err, "stop service before delete");
            } else {
                wait_until_stopped(&service);
            }
        }
    }

    if !service
        .query_status()
        .is_ok_and(|s| s.current_state == ServiceState::Stopped)
    {
        return Err(anyhow!("helper did not stop; owned protection retained"));
    }
    crate::mihomo_firewall::release_for_uninstall().map_err(|e| anyhow!(e))?;
    service.delete().context("delete service")?;
    println!("nimbo-svc uninstalled");
    Ok(())
}

fn wait_until_stopped(service: &windows_service::service::Service) {
    use windows_service::service::ServiceState;
    // Native TUN teardown can take up to 30 seconds; do not race it during upgrades.
    for _ in 0..400 {
        match service.query_status() {
            Ok(status) if status.current_state == ServiceState::Stopped => return,
            Ok(_) => std::thread::sleep(Duration::from_millis(100)),
            Err(_) => return,
        }
    }
}

// Stops the helper service without removing it. Used by the installer right
// before file copy so the running service does not hold an open handle on
// nimbo-svc.exe (which would otherwise make the upgrade fail with "cannot
// open for writing"). Followed by a short sleep to let Windows release the
// file handle.
fn pre_install_stop() -> Result<()> {
    use windows_service::service::{ServiceAccess, ServiceState};
    use windows_service::service_manager::{ServiceManager, ServiceManagerAccess};

    let manager = match ServiceManager::local_computer(None::<&str>, ServiceManagerAccess::CONNECT)
    {
        Ok(m) => m,
        Err(_) => return Ok(()),
    };

    let service = match manager.open_service(
        SERVICE_NAME,
        ServiceAccess::STOP | ServiceAccess::QUERY_STATUS,
    ) {
        Ok(svc) => svc,
        Err(err) if is_not_found(&err) => return Ok(()),
        Err(err) => return Err(anyhow!("open service for stop: {err}")),
    };

    if let Ok(status) = service.query_status() {
        if status.current_state != ServiceState::Stopped {
            pipe::occupy_accept_briefly_for_stop();
            if let Err(err) = service.stop() {
                warn!(error = %err, "stop service in pre-install");
            } else {
                wait_until_stopped(&service);
            }
        }
    }

    // Even after the SCM reports Stopped, Windows can hold the executable
    // image for a short moment. Sleep so the subsequent File-copy step in
    // the installer succeeds.
    std::thread::sleep(Duration::from_millis(800));
    Ok(())
}

fn is_already_exists(err: &windows_service::Error) -> bool {
    if let windows_service::Error::Winapi(io_err) = err {
        return io_err.raw_os_error() == Some(1073);
    }
    false
}

fn is_not_found(err: &windows_service::Error) -> bool {
    if let windows_service::Error::Winapi(io_err) = err {
        return io_err.raw_os_error() == Some(1060);
    }
    false
}

/// Preserve specific causes through the ShellExecute/UAC boundary; the installer
/// can explain static codes without reading unrelated logs or suppressing errors.
pub fn failure_exit_code(error: &anyhow::Error) -> i32 {
    let message = format!("{error:#}");
    for (reason, code) in [
        ("UNSAFE_SERVICE_DIRECTORY", 21),
        ("CORE_UNAVAILABLE", 22),
        ("CORE_HASH_MISMATCH", 23),
        ("PERMISSIONS_REPAIR_AVAILABLE", 24),
        ("PERMISSIONS_REPAIR_FAILED", 25),
        ("PERMISSIONS_BACKUP_FAILED", 26),
        ("PERMISSIONS_ACCOUNT_CHANGED", 27),
        ("PERMISSIONS_ROLLBACK_FAILED", 28),
        ("PERMISSIONS_ELEVATION_REQUIRED", 29),
    ] {
        if message.contains(reason) || message.contains(&format!("exited with code {code}")) {
            return code;
        }
    }
    if message.contains("UAC отменён") || message.contains("exited with code 1223") {
        1223
    } else {
        1
    }
}
#[cfg(test)]
mod install_error_tests {
    #[test]
    fn install_causes_survive_elevation() {
        for (cause, code) in [
            ("UNSAFE_SERVICE_DIRECTORY", 21),
            ("CORE_UNAVAILABLE", 22),
            ("CORE_HASH_MISMATCH", 23),
            ("PERMISSIONS_REPAIR_AVAILABLE", 24),
            ("PERMISSIONS_REPAIR_FAILED", 25),
            ("PERMISSIONS_BACKUP_FAILED", 26),
            ("PERMISSIONS_ACCOUNT_CHANGED", 27),
            ("PERMISSIONS_ROLLBACK_FAILED", 28),
            ("PERMISSIONS_ELEVATION_REQUIRED", 29),
        ] {
            assert_eq!(super::failure_exit_code(&anyhow::anyhow!(cause)), code);
            assert_eq!(
                super::failure_exit_code(&anyhow::anyhow!(
                    "elevated --install exited with code {code}"
                )),
                code
            );
        }
        assert_eq!(
            super::failure_exit_code(&anyhow::anyhow!("SCM unavailable")),
            1
        );
        assert_eq!(
            super::failure_exit_code(&anyhow::anyhow!(
                "elevated permission repair exited with code 1223"
            )),
            1223
        );
    }
    #[test]
    fn permission_recovery_requires_its_own_explicit_mode() {
        use super::{parse_mode, Mode};
        assert_eq!(
            parse_mode(&["svc".into(), "--check-install-directory".into()]),
            Mode::CheckInstallDirectory
        );
        assert_eq!(
            parse_mode(&["svc".into(), "--install".into()]),
            Mode::Install
        );
        assert_eq!(
            parse_mode(&["svc".into(), "--repair-install-permissions".into()]),
            Mode::RepairInstallPermissions
        );
        assert_eq!(
            parse_mode(&["svc".into(), "--repair-user=S-1-5-21-123".into()]),
            Mode::Service
        );
    }
}
