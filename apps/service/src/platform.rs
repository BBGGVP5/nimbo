#![cfg(windows)]

use std::ffi::OsString;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use anyhow::{Context, Result, anyhow};
use tracing::{error, info, warn};

mod elevation;
mod kill;
mod pipe;

pub const SERVICE_NAME: &str = "NimboHelper";
pub const SERVICE_DISPLAY_NAME: &str = "Nimbo Helper Service";
pub const SERVICE_DESCRIPTION: &str =
    "Helper service for Nimbo. Terminates conflicting VPN processes on request from Nimbo UI.";
const VERSION: &str = env!("CARGO_PKG_VERSION");

pub fn run() -> Result<()> {
    let args: Vec<String> = std::env::args().collect();
    init_tracing(&args);
    info!(version = VERSION, "nimbo-svc starting");

    let mode = parse_mode(&args);
    match mode {
        Mode::Install => maybe_elevate_then("--install", install_service),
        Mode::Uninstall => maybe_elevate_then("--uninstall", uninstall_service),
        Mode::PreInstall => maybe_elevate_then("--pre-install", pre_install_stop),
        Mode::RunForeground => run_pipe_loop(Arc::new(AtomicBool::new(false))),
        Mode::Service => run_as_service(),
    }
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

#[derive(Debug)]
enum Mode {
    Install,
    Uninstall,
    PreInstall,
    RunForeground,
    Service,
}

fn parse_mode(args: &[String]) -> Mode {
    for arg in args.iter().skip(1) {
        match arg.as_str() {
            "--install" | "install" => return Mode::Install,
            "--uninstall" | "uninstall" => return Mode::Uninstall,
            "--pre-install" | "pre-install" => return Mode::PreInstall,
            "--run-foreground" | "run-foreground" => return Mode::RunForeground,
            _ => {}
        }
    }
    Mode::Service
}

fn init_tracing(args: &[String]) {
    let foreground = args.iter().any(|a| a == "--run-foreground" || a == "run-foreground");
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
        if let Ok(file) = std::fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(&log_path)
        {
            let _ = builder.with_writer(std::sync::Mutex::new(file)).try_init();
            return;
        }
    }
    let _ = builder.try_init();
}

fn attach_parent_console() {
    // Best-effort: attaches stdio to the parent terminal if there is one.
    // Silently no-ops when launched from a GUI parent (NSIS, double-click).
    use windows_sys::Win32::System::Console::{ATTACH_PARENT_PROCESS, AttachConsole};
    unsafe {
        AttachConsole(ATTACH_PARENT_PROCESS);
    }
}

fn log_file_path() -> Option<PathBuf> {
    let base = std::env::var_os("ProgramData")
        .map(PathBuf::from)
        .or_else(|| dirs::data_local_dir())?;
    Some(base.join("Nimbo").join("helper.log"))
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
    pipe::serve(shutdown)
}

// ─── Service install / uninstall ────────────────────────────────────────────

fn install_service() -> Result<()> {
    use windows_service::service::{
        ServiceAccess, ServiceErrorControl, ServiceInfo, ServiceStartType, ServiceType,
    };
    use windows_service::service_manager::{ServiceManager, ServiceManagerAccess};

    let manager = ServiceManager::local_computer(
        None::<&str>,
        ServiceManagerAccess::CONNECT | ServiceManagerAccess::CREATE_SERVICE,
    )
    .context("open SCM (CONNECT|CREATE_SERVICE) — нужны права администратора")?;

    let exe = std::env::current_exe().context("locate nimbo-svc executable path")?;

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
                    if status.current_state
                        != windows_service::service::ServiceState::Stopped
                    {
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

    service.delete().context("delete service")?;
    println!("nimbo-svc uninstalled");
    Ok(())
}

fn wait_until_stopped(service: &windows_service::service::Service) {
    use windows_service::service::ServiceState;
    for _ in 0..50 {
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
