use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::atomic::{AtomicU8, Ordering};
use std::time::Duration;

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter};

/// Mode flag set once during process bootstrap and read by the UI to decide
/// whether to render the install screen or the uninstall screen. We use a
/// plain atomic instead of Tauri `State<>` so a no-Tauri code path (e.g. the
/// legacy `--uninstall --silent` CLI) can still query it without standing up
/// the runtime.
static INSTALLER_MODE: AtomicU8 = AtomicU8::new(0);

// Mode value used when the atomic equals `MODE_UNINSTALL`. The default
// install mode is encoded as 0 (the atomic's initial value), so no separate
// constant is needed for it.
const MODE_UNINSTALL: u8 = 1;

pub fn set_uninstall_mode() {
    INSTALLER_MODE.store(MODE_UNINSTALL, Ordering::Relaxed);
}

pub fn is_uninstall_mode() -> bool {
    INSTALLER_MODE.load(Ordering::Relaxed) == MODE_UNINSTALL
}

#[tauri::command]
pub fn get_installer_mode() -> &'static str {
    if is_uninstall_mode() {
        "uninstall"
    } else {
        "install"
    }
}

const PRODUCT_NAME: &str = "Nimbo";
const PRODUCT_VERSION: &str = env!("CARGO_PKG_VERSION");

#[cfg(windows)]
const PRODUCT_PLATFORM: &str = "windows";
#[cfg(target_os = "linux")]
const PRODUCT_PLATFORM: &str = "linux";
#[cfg(all(not(windows), not(target_os = "linux")))]
const PRODUCT_PLATFORM: &str = "unknown";

#[cfg(all(windows, target_arch = "x86"))]
const PRODUCT_ARCH: &str = "Windows x86";
#[cfg(all(windows, target_arch = "x86_64"))]
const PRODUCT_ARCH: &str = "Windows x64";
#[cfg(all(windows, target_arch = "aarch64"))]
const PRODUCT_ARCH: &str = "Windows ARM64";
#[cfg(all(
    windows,
    not(any(target_arch = "x86", target_arch = "x86_64", target_arch = "aarch64"))
))]
const PRODUCT_ARCH: &str = "Windows";
#[cfg(all(target_os = "linux", target_arch = "x86_64"))]
const PRODUCT_ARCH: &str = "Linux x64";
#[cfg(all(target_os = "linux", target_arch = "aarch64"))]
const PRODUCT_ARCH: &str = "Linux ARM64";
#[cfg(all(
    target_os = "linux",
    not(any(target_arch = "x86_64", target_arch = "aarch64"))
))]
const PRODUCT_ARCH: &str = "Linux";
#[cfg(all(not(windows), not(target_os = "linux")))]
const PRODUCT_ARCH: &str = "Unsupported";
const APP_ID: &str = "Nimbo";
const SERVICE_NAME: &str = "NimboHelper";
#[cfg(windows)]
const APP_EXE: &str = "Nimbo.exe";
#[cfg(not(windows))]
const APP_EXE: &str = "nimbo";
#[cfg(windows)]
const HELPER_EXE: &str = "nimbo-svc.exe";
#[cfg(not(windows))]
const HELPER_EXE: &str = "nimbo-svc";
#[cfg(windows)]
const UNINSTALL_EXE: &str = "Uninstall.exe";
#[cfg(not(windows))]
const UNINSTALL_EXE: &str = "Uninstall";

#[cfg(windows)]
const MAIN_APP_BYTES: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../../target/",
    env!("NIMBO_TARGET_TRIPLE"),
    "/release/nimbo-ui.exe"
));
#[cfg(not(windows))]
const MAIN_APP_BYTES: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../../target/",
    env!("NIMBO_TARGET_TRIPLE"),
    "/release/nimbo-ui"
));
#[cfg(windows)]
const HELPER_BYTES: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../../target/",
    env!("NIMBO_TARGET_TRIPLE"),
    "/release/nimbo-svc.exe"
));
#[cfg(windows)]
const TUN2SOCKS_BYTES: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../ui/src-tauri/resources/tun/tun2socks.exe"
));
#[cfg(windows)]
const WINTUN_BYTES: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../ui/src-tauri/resources/tun/wintun.dll"
));
#[cfg(windows)]
const ICON_BYTES: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../ui/src-tauri/icons/icon.ico"
));
#[cfg(not(windows))]
const ICON_BYTES: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../ui/src-tauri/icons/icon.png"
));

#[derive(Debug, Clone, Serialize)]
pub struct InstallerProbe {
    default_install_dir: String,
    product_version: String,
    product_arch: String,
    platform: String,
    existing_install: bool,
    helper_installed: bool,
    helper_running: bool,
}

#[derive(Debug, Clone, Deserialize)]
pub struct InstallOptions {
    install_dir: String,
    start_menu_shortcut: bool,
    desktop_shortcut: bool,
    launch_after_install: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct InstallResult {
    install_dir: String,
    app_exe: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct UninstallerProbe {
    install_dir: String,
    product_version: String,
    product_arch: String,
    platform: String,
    helper_installed: bool,
    helper_running: bool,
    user_data_dir: String,
    user_data_present: bool,
}

#[derive(Debug, Clone, Deserialize)]
pub struct UninstallOptions {
    /// If true, also wipe `%APPDATA%\Nimbo` (subscriptions, runtime, hwid).
    /// `bin/` (tun2socks/wintun) is always removed regardless.
    remove_user_data: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct UninstallResult {
    install_dir: String,
    removed_user_data: bool,
}

#[derive(Debug, Clone, Serialize)]
struct ProgressEvent {
    step: &'static str,
    state: &'static str,
    progress: u8,
    detail: String,
}

#[derive(Debug, Clone, Serialize, Default)]
pub struct AppTheme {
    ui_style: Option<String>,
    theme_mode: Option<String>,
    accent_mode: Option<String>,
    accent_color: Option<String>,
}

#[tauri::command]
pub fn read_app_theme() -> AppTheme {
    let Some(base) = dirs::data_dir() else {
        return AppTheme::default();
    };
    let path = base.join("Nimbo").join("subscriptions.json");
    let Ok(bytes) = fs::read(&path) else {
        return AppTheme::default();
    };
    let Ok(value) = serde_json::from_slice::<serde_json::Value>(&bytes) else {
        return AppTheme::default();
    };
    let prefs = value.get("preferences");
    let pick = |key: &str| -> Option<String> {
        prefs
            .and_then(|p| p.get(key))
            .and_then(|v| v.as_str())
            .map(|s| s.to_string())
    };
    AppTheme {
        ui_style: pick("ui_style"),
        theme_mode: pick("theme_mode"),
        accent_mode: pick("accent_mode"),
        accent_color: pick("accent_color"),
    }
}

#[tauri::command]
pub fn probe_installation() -> Result<InstallerProbe, String> {
    let install_dir = default_install_dir()?;
    let (helper_installed, helper_running) = helper_state();
    Ok(InstallerProbe {
        existing_install: install_dir.join(APP_EXE).exists(),
        default_install_dir: install_dir.to_string_lossy().to_string(),
        product_version: PRODUCT_VERSION.to_string(),
        product_arch: PRODUCT_ARCH.to_string(),
        platform: PRODUCT_PLATFORM.to_string(),
        helper_installed,
        helper_running,
    })
}

#[tauri::command]
pub fn choose_install_dir(current_dir: String) -> Result<Option<String>, String> {
    let start_dir = dialog_start_dir(&current_dir)?;

    #[cfg(target_os = "linux")]
    {
        return choose_install_dir_linux(&start_dir);
    }

    #[cfg(not(target_os = "linux"))]
    {
        let picked = rfd::FileDialog::new()
            .set_title("Выберите папку установки Nimbo")
            .set_directory(start_dir)
            .pick_folder();

        Ok(picked.map(|path| path.to_string_lossy().to_string()))
    }
}

#[tauri::command]
pub async fn install_nimbo(
    app: AppHandle,
    options: InstallOptions,
) -> Result<InstallResult, String> {
    tauri::async_runtime::spawn_blocking(move || install_blocking(app, options))
        .await
        .map_err(|e| format!("installer task failed: {e}"))?
}

#[tauri::command]
pub fn open_nimbo(install_dir: String) -> Result<(), String> {
    let exe = PathBuf::from(install_dir).join(APP_EXE);
    Command::new(exe)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .map(|_| ())
        .map_err(|e| format!("Не удалось открыть Nimbo: {e}"))
}

fn install_blocking(app: AppHandle, options: InstallOptions) -> Result<InstallResult, String> {
    #[cfg(windows)]
    {
        install_blocking_windows(app, options)
    }

    #[cfg(target_os = "linux")]
    {
        install_blocking_linux(app, options)
    }

    #[cfg(all(not(windows), not(target_os = "linux")))]
    {
        let _ = (app, options);
        Err("Кастомный установщик Nimbo сейчас поддерживает только Windows и Linux.".into())
    }
}

#[cfg(windows)]
fn install_blocking_windows(
    app: AppHandle,
    options: InstallOptions,
) -> Result<InstallResult, String> {
    let install_dir = PathBuf::from(options.install_dir.trim());
    if install_dir.as_os_str().is_empty() {
        return Err("Папка установки не выбрана.".into());
    }

    emit(
        &app,
        "prepare",
        "running",
        6,
        "Отключаем Nimbo и останавливаем хелпер",
    );
    prepare_windows_upgrade(&install_dir)?;
    fs::create_dir_all(&install_dir)
        .map_err(|e| format!("Не удалось создать папку установки: {e}"))?;
    emit(&app, "prepare", "done", 12, "Окружение готово");

    emit(&app, "files", "running", 18, "Обновляем исполняемые файлы");
    replace_payload(&install_dir.join(APP_EXE), MAIN_APP_BYTES)?;
    replace_payload(&install_dir.join(HELPER_EXE), HELPER_BYTES)?;
    write_payload(&install_dir.join("icon.ico"), ICON_BYTES)?;
    copy_self_uninstaller(&install_dir)?;
    emit(&app, "files", "done", 36, "Файлы Nimbo установлены");

    emit(&app, "tun", "running", 44, "Копируем TUN-компоненты");
    let tun_dir = roaming_nimbo_bin_dir()?;
    fs::create_dir_all(&tun_dir).map_err(|e| format!("Не удалось создать папку TUN: {e}"))?;
    replace_payload(&tun_dir.join("tun2socks.exe"), TUN2SOCKS_BYTES)?;
    replace_payload(&tun_dir.join("wintun.dll"), WINTUN_BYTES)?;
    run_status(&install_dir.join(APP_EXE), &["--install-tun"])
        .map_err(|e| format!("TUN-компоненты не установились: {e}"))?;
    cleanup_old_tun_binaries(&tun_dir);
    emit(&app, "tun", "done", 56, "TUN готов");

    emit(&app, "service", "running", 64, "Регистрируем helper-сервис");
    run_status(&install_dir.join(HELPER_EXE), &["--install"])
        .map_err(|e| format!("Хелпер не установился: {e}"))?;
    cleanup_old_binaries(&install_dir);
    emit(&app, "service", "done", 74, "Хелпер готов");

    emit(&app, "shortcuts", "running", 80, "Создаем ярлыки");
    if options.start_menu_shortcut {
        create_start_menu_shortcut(&install_dir)?;
    }
    if options.desktop_shortcut {
        create_desktop_shortcut(&install_dir)?;
    }
    emit(&app, "shortcuts", "done", 88, "Ярлыки настроены");

    emit(
        &app,
        "registry",
        "running",
        93,
        "Записываем системные записи",
    );
    write_registry(&install_dir)?;
    emit(&app, "registry", "done", 100, "Nimbo установлен");

    if options.launch_after_install {
        let _ = Command::new(install_dir.join(APP_EXE))
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn();
    }

    Ok(InstallResult {
        app_exe: install_dir.join(APP_EXE).to_string_lossy().to_string(),
        install_dir: install_dir.to_string_lossy().to_string(),
    })
}

#[cfg(target_os = "linux")]
fn install_blocking_linux(
    app: AppHandle,
    options: InstallOptions,
) -> Result<InstallResult, String> {
    let install_dir = PathBuf::from(options.install_dir.trim());
    if install_dir.as_os_str().is_empty() {
        return Err("Папка установки не выбрана.".into());
    }

    emit(&app, "prepare", "running", 8, "Готовим папку установки");
    fs::create_dir_all(&install_dir)
        .map_err(|e| format!("Не удалось создать папку установки: {e}"))?;
    emit(&app, "prepare", "done", 16, "Окружение готово");

    emit(&app, "files", "running", 28, "Обновляем исполняемый файл");
    let app_path = install_dir.join(APP_EXE);
    replace_payload(&app_path, MAIN_APP_BYTES)?;
    make_executable(&app_path)?;
    write_payload(&install_dir.join("icon.png"), ICON_BYTES)?;
    copy_self_uninstaller(&install_dir)?;
    make_executable(&install_dir.join(UNINSTALL_EXE))?;
    emit(&app, "files", "done", 48, "Файлы Nimbo установлены");

    emit(
        &app,
        "integrate",
        "running",
        62,
        "Настраиваем desktop entry",
    );
    if options.start_menu_shortcut {
        create_start_menu_shortcut(&install_dir)?;
    }
    write_registry(&install_dir)?;
    emit(&app, "integrate", "done", 74, "Интеграция готова");

    emit(&app, "shortcuts", "running", 84, "Настраиваем ярлыки");
    if options.desktop_shortcut {
        create_desktop_shortcut(&install_dir)?;
    }
    emit(&app, "shortcuts", "done", 92, "Ярлыки настроены");

    emit(&app, "registry", "running", 97, "Проверяем установку");
    emit(&app, "registry", "done", 100, "Nimbo установлен");

    if options.launch_after_install {
        let _ = Command::new(&app_path)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn();
    }

    Ok(InstallResult {
        app_exe: app_path.to_string_lossy().to_string(),
        install_dir: install_dir.to_string_lossy().to_string(),
    })
}

pub fn uninstall_from_cli() -> Result<(), String> {
    // CLI / silent flow used by the registry "UninstallString" entry and by
    // older scripts. We never remove user data here — that's an explicit
    // opt-in available from the UI flow.
    perform_uninstall(
        None,
        UninstallOptions {
            remove_user_data: false,
        },
    )
    .map(|_| ())
}

#[tauri::command]
pub fn probe_uninstallation() -> Result<UninstallerProbe, String> {
    let install_dir = current_install_dir()?;
    let user_data_dir = user_data_root()?;
    let (helper_installed, helper_running) = helper_state();
    Ok(UninstallerProbe {
        install_dir: install_dir.to_string_lossy().to_string(),
        product_version: PRODUCT_VERSION.to_string(),
        product_arch: PRODUCT_ARCH.to_string(),
        platform: PRODUCT_PLATFORM.to_string(),
        helper_installed,
        helper_running,
        user_data_present: user_data_present(&user_data_dir),
        user_data_dir: user_data_dir.to_string_lossy().to_string(),
    })
}

#[tauri::command]
pub async fn uninstall_nimbo(
    app: AppHandle,
    options: UninstallOptions,
) -> Result<UninstallResult, String> {
    tauri::async_runtime::spawn_blocking(move || perform_uninstall(Some(app), options))
        .await
        .map_err(|e| format!("uninstaller task failed: {e}"))?
}

fn perform_uninstall(
    app: Option<AppHandle>,
    options: UninstallOptions,
) -> Result<UninstallResult, String> {
    let install_dir = current_install_dir()?;
    let user_data_dir = user_data_root()?;

    uemit(app.as_ref(), "prepare", "running", 8, "Останавливаем Nimbo");
    #[cfg(windows)]
    stop_nimbo_runtime_processes();
    #[cfg(not(windows))]
    taskkill_image(APP_EXE);
    let helper = install_dir.join(HELPER_EXE);
    if helper.exists() {
        let _ = run_status(&helper, &["--uninstall"]);
    }
    std::thread::sleep(Duration::from_millis(600));
    uemit(app.as_ref(), "prepare", "done", 20, "Процессы остановлены");

    uemit(app.as_ref(), "shortcuts", "running", 32, "Убираем ярлыки");
    delete_shortcuts();
    uemit(app.as_ref(), "shortcuts", "done", 44, "Ярлыки удалены");

    uemit(
        app.as_ref(),
        "registry",
        "running",
        54,
        "Чистим системные записи",
    );
    delete_registry();
    uemit(
        app.as_ref(),
        "registry",
        "done",
        64,
        "Системные записи удалены",
    );

    uemit(app.as_ref(), "files", "running", 72, "Удаляем файлы Nimbo");
    let tun_dir = roaming_nimbo_bin_dir()?;
    let _ = fs::remove_file(tun_dir.join("tun2socks.exe"));
    let _ = fs::remove_file(tun_dir.join("wintun.dll"));
    let _ = fs::remove_dir(&tun_dir);

    #[cfg(windows)]
    for name in [
        APP_EXE,
        HELPER_EXE,
        "icon.ico",
        "nimbo-svc.exe.old",
        "Nimbo.exe.old",
    ] {
        let _ = fs::remove_file(install_dir.join(name));
    }
    #[cfg(not(windows))]
    for name in [APP_EXE, UNINSTALL_EXE, "icon.png", "nimbo.exe.old"] {
        let _ = fs::remove_file(install_dir.join(name));
    }
    uemit(app.as_ref(), "files", "done", 84, "Файлы удалены");

    let removed_user_data = if options.remove_user_data {
        uemit(
            app.as_ref(),
            "user_data",
            "running",
            92,
            "Удаляем подписки и настройки",
        );
        wipe_user_data(&user_data_dir);
        uemit(
            app.as_ref(),
            "user_data",
            "done",
            96,
            "Пользовательские данные удалены",
        );
        true
    } else {
        uemit(
            app.as_ref(),
            "user_data",
            "done",
            96,
            "Пользовательские данные сохранены",
        );
        false
    };

    schedule_self_cleanup(&install_dir);
    uemit(app.as_ref(), "finish", "done", 100, "Nimbo удалён");

    Ok(UninstallResult {
        install_dir: install_dir.to_string_lossy().to_string(),
        removed_user_data,
    })
}

fn current_install_dir() -> Result<PathBuf, String> {
    if let Ok(exe) = std::env::current_exe() {
        if let Some(parent) = exe.parent() {
            return Ok(parent.to_path_buf());
        }
    }
    default_install_dir()
}

fn user_data_root() -> Result<PathBuf, String> {
    dirs::data_dir()
        .map(|base| base.join(PRODUCT_NAME))
        .ok_or_else(|| "Не удалось определить AppData.".to_string())
}

fn user_data_present(root: &Path) -> bool {
    // Treat "user data" as anything beyond the `bin/` folder (which holds
    // TUN binaries we always remove). If `subscriptions.json`, `runtime/`,
    // or `hwid.txt` exist, there's something to wipe.
    if !root.exists() {
        return false;
    }
    for name in ["subscriptions.json", "runtime", "hwid.txt"] {
        if root.join(name).exists() {
            return true;
        }
    }
    false
}

fn wipe_user_data(root: &Path) {
    if !root.exists() {
        return;
    }
    let _ = fs::remove_file(root.join("subscriptions.json"));
    let _ = fs::remove_file(root.join("hwid.txt"));
    let _ = fs::remove_dir_all(root.join("runtime"));
    // `bin/` is removed by the file-step (TUN binaries). If empty now, drop
    // the whole Nimbo data folder too.
    let _ = fs::remove_dir(root.join("bin"));
    let _ = fs::remove_dir(root);
}

fn uemit(
    app: Option<&AppHandle>,
    step: &'static str,
    state: &'static str,
    progress: u8,
    detail: &str,
) {
    if let Some(app) = app {
        let _ = app.emit(
            "uninstaller_progress",
            ProgressEvent {
                step,
                state,
                progress,
                detail: detail.to_string(),
            },
        );
    }
}

fn dialog_start_dir(current_dir: &str) -> Result<PathBuf, String> {
    let trimmed = current_dir.trim();
    if trimmed.is_empty() {
        return default_install_dir().map(existing_or_parent).or_else(|_| {
            dirs::home_dir().ok_or_else(|| "Не удалось определить домашнюю папку.".into())
        });
    }

    Ok(existing_or_parent(PathBuf::from(trimmed)))
}

fn existing_or_parent(path: PathBuf) -> PathBuf {
    if path.is_dir() {
        return path;
    }

    path.parent()
        .filter(|parent| parent.is_dir())
        .map(Path::to_path_buf)
        .or_else(dirs::home_dir)
        .unwrap_or_else(default_root_dir)
}

#[cfg(windows)]
fn default_root_dir() -> PathBuf {
    PathBuf::from(r"C:\")
}

#[cfg(not(windows))]
fn default_root_dir() -> PathBuf {
    PathBuf::from("/")
}

#[cfg(target_os = "linux")]
fn choose_install_dir_linux(start_dir: &Path) -> Result<Option<String>, String> {
    for program in ["zenity", "kdialog"] {
        let mut command = hidden_command(program);
        if program == "zenity" {
            command
                .arg("--file-selection")
                .arg("--directory")
                .arg("--title=Выберите папку установки Nimbo")
                .arg(format!("--filename={}/", start_dir.display()));
        } else {
            command.arg("--getexistingdirectory").arg(start_dir);
        }

        let output = match command.output() {
            Ok(output) => output,
            Err(_) => continue,
        };
        if !output.status.success() {
            return Ok(None);
        }
        let picked = String::from_utf8_lossy(&output.stdout).trim().to_string();
        return Ok((!picked.is_empty()).then_some(picked));
    }

    Ok(None)
}

fn emit(app: &AppHandle, step: &'static str, state: &'static str, progress: u8, detail: &str) {
    let _ = app.emit(
        "installer_progress",
        ProgressEvent {
            step,
            state,
            progress,
            detail: detail.to_string(),
        },
    );
}

#[cfg(windows)]
fn default_install_dir() -> Result<PathBuf, String> {
    dirs::data_local_dir()
        .map(|base| base.join("Programs").join(PRODUCT_NAME))
        .ok_or_else(|| "Не удалось определить LocalAppData.".to_string())
}

#[cfg(target_os = "linux")]
fn default_install_dir() -> Result<PathBuf, String> {
    dirs::data_local_dir()
        .or_else(dirs::home_dir)
        .map(|base| base.join(PRODUCT_NAME))
        .ok_or_else(|| "Не удалось определить домашнюю папку.".to_string())
}

#[cfg(all(not(windows), not(target_os = "linux")))]
fn default_install_dir() -> Result<PathBuf, String> {
    dirs::data_local_dir()
        .map(|base| base.join(PRODUCT_NAME))
        .ok_or_else(|| "Не удалось определить папку данных.".to_string())
}

fn roaming_nimbo_bin_dir() -> Result<PathBuf, String> {
    dirs::data_dir()
        .map(|base| base.join(PRODUCT_NAME).join("bin"))
        .ok_or_else(|| "Не удалось определить AppData.".to_string())
}

fn replace_payload(path: &Path, bytes: &[u8]) -> Result<(), String> {
    if path.exists() {
        let old = old_payload_path(path);
        let _ = remove_file_with_retries(&old);
        rename_with_retries(path, &old)
            .map_err(|e| format!("Не удалось заменить {}: {e}", path.display()))?;
    }
    write_payload(path, bytes)
}

fn write_payload(path: &Path, bytes: &[u8]) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .map_err(|e| format!("Не удалось создать {}: {e}", parent.display()))?;
    }
    retry_io(|| fs::write(path, bytes))
        .map_err(|e| format!("Не удалось записать {}: {e}", path.display()))
}

fn old_payload_path(path: &Path) -> PathBuf {
    let Some(file_name) = path.file_name().and_then(|name| name.to_str()) else {
        return path.with_extension("old");
    };
    path.with_file_name(format!("{file_name}.old"))
}

fn rename_with_retries(from: &Path, to: &Path) -> std::io::Result<()> {
    retry_io(|| fs::rename(from, to))
}

fn remove_file_with_retries(path: &Path) -> std::io::Result<()> {
    retry_io(|| match fs::remove_file(path) {
        Ok(()) => Ok(()),
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(err) => Err(err),
    })
}

fn retry_io<F>(mut action: F) -> std::io::Result<()>
where
    F: FnMut() -> std::io::Result<()>,
{
    let mut delay = Duration::from_millis(180);
    let mut last_error = None;
    for _ in 0..8 {
        match action() {
            Ok(()) => return Ok(()),
            Err(err) => {
                last_error = Some(err);
                std::thread::sleep(delay);
                delay = (delay + Duration::from_millis(180)).min(Duration::from_millis(900));
            }
        }
    }
    Err(last_error.unwrap_or_else(std::io::Error::last_os_error))
}

fn copy_self_uninstaller(install_dir: &Path) -> Result<(), String> {
    let current = std::env::current_exe()
        .map_err(|e| format!("Не удалось определить путь установщика: {e}"))?;
    let target = install_dir.join(UNINSTALL_EXE);
    fs::copy(current, &target).map_err(|e| format!("Не удалось создать деинсталлятор: {e}"))?;
    make_executable(&target)
}

#[cfg(unix)]
fn make_executable(path: &Path) -> Result<(), String> {
    use std::os::unix::fs::PermissionsExt;

    let mut permissions = fs::metadata(path)
        .map_err(|e| format!("Не удалось прочитать права {}: {e}", path.display()))?
        .permissions();
    permissions.set_mode(0o755);
    fs::set_permissions(path, permissions)
        .map_err(|e| format!("Не удалось сделать {} исполняемым: {e}", path.display()))
}

#[cfg(not(unix))]
fn make_executable(_path: &Path) -> Result<(), String> {
    Ok(())
}

fn run_status(exe: &Path, args: &[&str]) -> Result<(), String> {
    let status = hidden_command(exe)
        .args(args)
        .status()
        .map_err(|e| format!("{}: {e}", exe.display()))?;
    if status.success() {
        Ok(())
    } else {
        Err(format!(
            "{} завершился с кодом {:?}",
            exe.display(),
            status.code()
        ))
    }
}

fn hidden_command<P: AsRef<Path>>(program: P) -> Command {
    let mut command = Command::new(program.as_ref());
    command
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    command
}

#[cfg(windows)]
fn prepare_windows_upgrade(install_dir: &Path) -> Result<(), String> {
    stop_nimbo_runtime_processes();
    stop_helper_for_upgrade(install_dir)?;
    std::thread::sleep(Duration::from_millis(700));
    Ok(())
}

#[cfg(windows)]
fn stop_nimbo_runtime_processes() {
    for image in [APP_EXE, "nimbo-ui.exe", "tun2socks.exe", "xray.exe"] {
        taskkill_image(image);
    }
}

#[cfg(windows)]
fn stop_helper_for_upgrade(install_dir: &Path) -> Result<(), String> {
    let (installed, running) = helper_state();
    if !installed {
        return Ok(());
    }

    if running {
        let helper = install_dir.join(HELPER_EXE);
        let stopped_by_helper = helper.exists() && run_status(&helper, &["--pre-install"]).is_ok();
        if !stopped_by_helper {
            stop_service_with_sc();
        }
    }

    wait_for_helper_stopped(Duration::from_secs(7));
    let (_, still_running) = helper_state();
    if still_running {
        return Err(
            "Не удалось остановить helper-сервис Nimbo. Закройте подключение и подтвердите запуск установщика от имени администратора."
                .into(),
        );
    }

    Ok(())
}

#[cfg(windows)]
fn stop_service_with_sc() {
    let _ = hidden_command("sc.exe")
        .args(["stop", SERVICE_NAME])
        .status();
}

#[cfg(windows)]
fn wait_for_helper_stopped(timeout: Duration) {
    let start = std::time::Instant::now();
    while start.elapsed() < timeout {
        let (_, running) = helper_state();
        if !running {
            return;
        }
        std::thread::sleep(Duration::from_millis(250));
    }
}

#[cfg(windows)]
fn taskkill_image(image: &str) {
    let _ = hidden_command("taskkill.exe")
        .args(["/F", "/IM", image, "/T"])
        .status();
}

#[cfg(not(windows))]
fn taskkill_image(_image: &str) {}

#[cfg(windows)]
fn create_start_menu_shortcut(install_dir: &Path) -> Result<(), String> {
    let start_menu = dirs::data_dir()
        .ok_or_else(|| "Не удалось определить AppData.".to_string())?
        .join("Microsoft")
        .join("Windows")
        .join("Start Menu")
        .join("Programs")
        .join(PRODUCT_NAME);
    fs::create_dir_all(&start_menu)
        .map_err(|e| format!("Не удалось создать папку меню Пуск: {e}"))?;
    create_shortcut(
        &start_menu.join("Nimbo.lnk"),
        &install_dir.join(APP_EXE),
        install_dir,
    )
}

#[cfg(target_os = "linux")]
fn create_start_menu_shortcut(install_dir: &Path) -> Result<(), String> {
    let applications = linux_applications_dir()?;
    fs::create_dir_all(&applications)
        .map_err(|e| format!("Не удалось создать папку приложений Linux: {e}"))?;
    fs::create_dir_all(linux_icon_dir()?)
        .map_err(|e| format!("Не удалось создать папку иконок Linux: {e}"))?;
    write_payload(&linux_icon_path()?, ICON_BYTES)?;
    write_linux_desktop_entry(&applications.join("nimbo.desktop"), install_dir, "nimbo")?;
    Ok(())
}

#[cfg(all(not(windows), not(target_os = "linux")))]
fn create_start_menu_shortcut(_install_dir: &Path) -> Result<(), String> {
    Ok(())
}

#[cfg(windows)]
fn create_desktop_shortcut(install_dir: &Path) -> Result<(), String> {
    let desktop =
        dirs::desktop_dir().ok_or_else(|| "Не удалось определить рабочий стол.".to_string())?;
    create_shortcut(
        &desktop.join("Nimbo.lnk"),
        &install_dir.join(APP_EXE),
        install_dir,
    )
}

#[cfg(target_os = "linux")]
fn create_desktop_shortcut(install_dir: &Path) -> Result<(), String> {
    let desktop = dirs::desktop_dir()
        .or_else(|| dirs::home_dir().map(|home| home.join("Desktop")))
        .ok_or_else(|| "Не удалось определить рабочий стол.".to_string())?;
    fs::create_dir_all(&desktop)
        .map_err(|e| format!("Не удалось создать папку рабочего стола: {e}"))?;
    let desktop_file = desktop.join("Nimbo.desktop");
    write_linux_desktop_entry(
        &desktop_file,
        install_dir,
        &install_dir.join("icon.png").to_string_lossy(),
    )?;
    make_executable(&desktop_file)
}

#[cfg(all(not(windows), not(target_os = "linux")))]
fn create_desktop_shortcut(_install_dir: &Path) -> Result<(), String> {
    Ok(())
}

#[cfg(windows)]
fn create_shortcut(link: &Path, target: &Path, working_dir: &Path) -> Result<(), String> {
    let script = format!(
        "$w=New-Object -ComObject WScript.Shell;$s=$w.CreateShortcut('{}');$s.TargetPath='{}';$s.WorkingDirectory='{}';$s.IconLocation='{},0';$s.Save()",
        ps_escape(link),
        ps_escape(target),
        ps_escape(working_dir),
        ps_escape(target),
    );
    let status = hidden_command("powershell.exe")
        .args([
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            &script,
        ])
        .status()
        .map_err(|e| format!("Не удалось создать ярлык: {e}"))?;
    if status.success() {
        Ok(())
    } else {
        Err("Не удалось создать ярлык.".into())
    }
}

fn ps_escape(path: &Path) -> String {
    path.to_string_lossy().replace('\'', "''")
}

#[cfg(target_os = "linux")]
fn linux_applications_dir() -> Result<PathBuf, String> {
    dirs::data_dir()
        .map(|base| base.join("applications"))
        .ok_or_else(|| "Не удалось определить XDG data dir.".to_string())
}

#[cfg(target_os = "linux")]
fn linux_icon_dir() -> Result<PathBuf, String> {
    dirs::data_dir()
        .map(|base| {
            base.join("icons")
                .join("hicolor")
                .join("256x256")
                .join("apps")
        })
        .ok_or_else(|| "Не удалось определить папку иконок Linux.".to_string())
}

#[cfg(target_os = "linux")]
fn linux_icon_path() -> Result<PathBuf, String> {
    Ok(linux_icon_dir()?.join("nimbo.png"))
}

#[cfg(target_os = "linux")]
fn write_linux_desktop_entry(path: &Path, install_dir: &Path, icon: &str) -> Result<(), String> {
    let app_path = install_dir.join(APP_EXE);
    let content = format!(
        "[Desktop Entry]\nType=Application\nName=Nimbo\nComment=Nimbo VPN client\nExec={} %u\nIcon={}\nTerminal=false\nCategories=Network;\nMimeType=x-scheme-handler/nimbo;\nStartupNotify=true\n",
        desktop_quote(&app_path),
        icon,
    );
    write_payload(path, content.as_bytes())?;
    make_executable(path)
}

#[cfg(target_os = "linux")]
fn desktop_quote(path: &Path) -> String {
    let escaped = path
        .to_string_lossy()
        .replace('\\', "\\\\")
        .replace('"', "\\\"");
    format!("\"{escaped}\"")
}

#[cfg(windows)]
fn write_registry(install_dir: &Path) -> Result<(), String> {
    use winreg::enums::HKEY_CURRENT_USER;
    use winreg::RegKey;

    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let (app_key, _) = hkcu
        .create_subkey(format!("Software\\{APP_ID}"))
        .map_err(|e| format!("Не удалось записать ключ приложения: {e}"))?;
    app_key
        .set_value("InstallDir", &install_dir.to_string_lossy().to_string())
        .map_err(|e| format!("Не удалось записать путь установки: {e}"))?;

    let uninstall_path =
        format!("Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{APP_ID}");
    let (uninstall, _) = hkcu
        .create_subkey(uninstall_path)
        .map_err(|e| format!("Не удалось записать uninstall-запись: {e}"))?;
    uninstall
        .set_value("DisplayName", &PRODUCT_NAME)
        .map_err(reg_err)?;
    uninstall
        .set_value("DisplayVersion", &PRODUCT_VERSION)
        .map_err(reg_err)?;
    uninstall
        .set_value("Publisher", &"BBGGVP5")
        .map_err(reg_err)?;
    uninstall
        .set_value(
            "DisplayIcon",
            &install_dir.join(APP_EXE).to_string_lossy().to_string(),
        )
        .map_err(reg_err)?;
    uninstall
        .set_value(
            "InstallLocation",
            &install_dir.to_string_lossy().to_string(),
        )
        .map_err(reg_err)?;
    uninstall
        .set_value(
            "UninstallString",
            &format!(
                "\"{}\" --uninstall",
                install_dir.join(UNINSTALL_EXE).display()
            ),
        )
        .map_err(reg_err)?;
    uninstall.set_value("NoModify", &1u32).map_err(reg_err)?;
    uninstall.set_value("NoRepair", &1u32).map_err(reg_err)?;

    let (protocol, _) = hkcu
        .create_subkey("Software\\Classes\\nimbo")
        .map_err(|e| format!("Не удалось зарегистрировать nimbo://: {e}"))?;
    protocol
        .set_value("", &"URL:Nimbo Protocol")
        .map_err(reg_err)?;
    protocol.set_value("URL Protocol", &"").map_err(reg_err)?;
    let (icon, _) = hkcu
        .create_subkey("Software\\Classes\\nimbo\\DefaultIcon")
        .map_err(reg_err)?;
    icon.set_value("", &format!("{},0", install_dir.join(APP_EXE).display()))
        .map_err(reg_err)?;
    let (open, _) = hkcu
        .create_subkey("Software\\Classes\\nimbo\\shell\\open\\command")
        .map_err(reg_err)?;
    open.set_value(
        "",
        &format!("\"{}\" \"%1\"", install_dir.join(APP_EXE).display()),
    )
    .map_err(reg_err)?;
    Ok(())
}

#[cfg(target_os = "linux")]
fn write_registry(_install_dir: &Path) -> Result<(), String> {
    if linux_applications_dir()?.join("nimbo.desktop").exists() {
        let _ = hidden_command("xdg-mime")
            .args(["default", "nimbo.desktop", "x-scheme-handler/nimbo"])
            .status();
        if let Ok(applications) = linux_applications_dir() {
            let _ = hidden_command("update-desktop-database")
                .arg(applications)
                .status();
        }
    }
    Ok(())
}

#[cfg(all(not(windows), not(target_os = "linux")))]
fn write_registry(_install_dir: &Path) -> Result<(), String> {
    Ok(())
}

fn reg_err(error: std::io::Error) -> String {
    format!("Ошибка реестра: {error}")
}

#[cfg(windows)]
fn helper_state() -> (bool, bool) {
    let output = hidden_command("sc.exe")
        .args(["query", SERVICE_NAME])
        .output();
    let Ok(output) = output else {
        return (false, false);
    };
    let text = String::from_utf8_lossy(&output.stdout).to_ascii_uppercase();
    (output.status.success(), text.contains("RUNNING"))
}

#[cfg(not(windows))]
fn helper_state() -> (bool, bool) {
    (false, false)
}

fn cleanup_old_binaries(install_dir: &Path) {
    for name in ["nimbo-svc.exe.old", "Nimbo.exe.old"] {
        let _ = fs::remove_file(install_dir.join(name));
    }
}

fn cleanup_old_tun_binaries(tun_dir: &Path) {
    for name in ["tun2socks.exe.old", "wintun.dll.old", "wintun.exe.old"] {
        let _ = fs::remove_file(tun_dir.join(name));
    }
}

#[cfg(windows)]
fn delete_shortcuts() {
    if let Some(desktop) = dirs::desktop_dir() {
        let _ = fs::remove_file(desktop.join("Nimbo.lnk"));
    }
    if let Some(data) = dirs::data_dir() {
        let dir = data
            .join("Microsoft")
            .join("Windows")
            .join("Start Menu")
            .join("Programs")
            .join(PRODUCT_NAME);
        let _ = fs::remove_file(dir.join("Nimbo.lnk"));
        let _ = fs::remove_dir(dir);
    }
}

#[cfg(target_os = "linux")]
fn delete_shortcuts() {
    if let Some(desktop) =
        dirs::desktop_dir().or_else(|| dirs::home_dir().map(|home| home.join("Desktop")))
    {
        let _ = fs::remove_file(desktop.join("Nimbo.desktop"));
    }
    if let Ok(applications) = linux_applications_dir() {
        let _ = fs::remove_file(applications.join("nimbo.desktop"));
    }
    if let Ok(icon) = linux_icon_path() {
        let _ = fs::remove_file(icon);
    }
}

#[cfg(all(not(windows), not(target_os = "linux")))]
fn delete_shortcuts() {}

#[cfg(windows)]
fn delete_registry() {
    use winreg::enums::HKEY_CURRENT_USER;
    use winreg::RegKey;
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let _ = hkcu.delete_subkey_all("Software\\Classes\\nimbo");
    let _ = hkcu.delete_subkey_all(format!(
        "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{APP_ID}"
    ));
    let _ = hkcu.delete_subkey_all(format!("Software\\{APP_ID}"));
}

#[cfg(not(windows))]
fn delete_registry() {}

#[cfg(windows)]
fn schedule_self_cleanup(install_dir: &Path) {
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::Storage::FileSystem::{MoveFileExW, MOVEFILE_DELAY_UNTIL_REBOOT};

    let _ = fs::remove_file(install_dir.join(UNINSTALL_EXE));
    let _ = fs::remove_dir(install_dir);
    if install_dir.exists() {
        let wide: Vec<u16> = install_dir
            .as_os_str()
            .encode_wide()
            .chain(std::iter::once(0))
            .collect();
        unsafe {
            MoveFileExW(wide.as_ptr(), std::ptr::null(), MOVEFILE_DELAY_UNTIL_REBOOT);
        }
    }
}

#[cfg(target_os = "linux")]
fn schedule_self_cleanup(install_dir: &Path) {
    let _ = fs::remove_dir_all(install_dir);
}

#[cfg(all(not(windows), not(target_os = "linux")))]
fn schedule_self_cleanup(_install_dir: &Path) {}
