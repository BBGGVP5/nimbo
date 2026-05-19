use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::time::Duration;

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter};

const PRODUCT_NAME: &str = "Nimbo";
const PRODUCT_VERSION: &str = env!("CARGO_PKG_VERSION");
#[cfg(target_arch = "x86")]
const PRODUCT_ARCH: &str = "Windows x86";
#[cfg(target_arch = "x86_64")]
const PRODUCT_ARCH: &str = "Windows x64";
#[cfg(target_arch = "aarch64")]
const PRODUCT_ARCH: &str = "Windows ARM64";
#[cfg(not(any(target_arch = "x86", target_arch = "x86_64", target_arch = "aarch64")))]
const PRODUCT_ARCH: &str = "Windows";
const APP_ID: &str = "Nimbo";
const SERVICE_NAME: &str = "NimboHelper";
const APP_EXE: &str = "Nimbo.exe";
const HELPER_EXE: &str = "nimbo-svc.exe";
const UNINSTALL_EXE: &str = "Uninstall.exe";

const MAIN_APP_BYTES: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../../target/",
    env!("NIMBO_TARGET_TRIPLE"),
    "/release/nimbo-ui.exe"
));
const HELPER_BYTES: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../../target/",
    env!("NIMBO_TARGET_TRIPLE"),
    "/release/nimbo-svc.exe"
));
const TUN2SOCKS_BYTES: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../ui/src-tauri/resources/tun/tun2socks.exe"
));
const WINTUN_BYTES: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../ui/src-tauri/resources/tun/wintun.dll"
));
const ICON_BYTES: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../ui/src-tauri/icons/icon.ico"
));

#[derive(Debug, Clone, Serialize)]
pub struct InstallerProbe {
    default_install_dir: String,
    product_version: String,
    product_arch: String,
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
struct ProgressEvent {
    step: &'static str,
    state: &'static str,
    progress: u8,
    detail: String,
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
        helper_installed,
        helper_running,
    })
}

#[tauri::command]
pub fn choose_install_dir(current_dir: String) -> Result<Option<String>, String> {
    let start_dir = dialog_start_dir(&current_dir)?;
    let picked = rfd::FileDialog::new()
        .set_title("Выберите папку установки Nimbo")
        .set_directory(start_dir)
        .pick_folder();

    Ok(picked.map(|path| path.to_string_lossy().to_string()))
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
    let install_dir = PathBuf::from(options.install_dir.trim());
    if install_dir.as_os_str().is_empty() {
        return Err("Папка установки не выбрана.".into());
    }

    emit(&app, "prepare", "running", 6, "Закрываем запущенный Nimbo");
    taskkill_image(APP_EXE);
    taskkill_image("nimbo-ui.exe");
    fs::create_dir_all(&install_dir).map_err(|e| format!("Не удалось создать папку установки: {e}"))?;
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
    write_payload(&tun_dir.join("tun2socks.exe"), TUN2SOCKS_BYTES)?;
    write_payload(&tun_dir.join("wintun.dll"), WINTUN_BYTES)?;
    run_status(&install_dir.join(APP_EXE), &["--install-tun"])
        .map_err(|e| format!("TUN-компоненты не установились: {e}"))?;
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

    emit(&app, "registry", "running", 93, "Записываем системные записи");
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

pub fn uninstall_from_cli() -> Result<(), String> {
    let install_dir = std::env::current_exe()
        .ok()
        .and_then(|path| path.parent().map(Path::to_path_buf))
        .unwrap_or(default_install_dir()?);

    taskkill_image(APP_EXE);
    let helper = install_dir.join(HELPER_EXE);
    if helper.exists() {
        let _ = run_status(&helper, &["--uninstall"]);
    }
    std::thread::sleep(Duration::from_millis(600));

    delete_shortcuts();
    delete_registry();
    let tun_dir = roaming_nimbo_bin_dir()?;
    let _ = fs::remove_file(tun_dir.join("tun2socks.exe"));
    let _ = fs::remove_file(tun_dir.join("wintun.dll"));
    let _ = fs::remove_dir(tun_dir);

    for name in [APP_EXE, HELPER_EXE, "icon.ico", "nimbo-svc.exe.old", "Nimbo.exe.old"] {
        let _ = fs::remove_file(install_dir.join(name));
    }
    schedule_self_cleanup(&install_dir);
    Ok(())
}

fn dialog_start_dir(current_dir: &str) -> Result<PathBuf, String> {
    let trimmed = current_dir.trim();
    if trimmed.is_empty() {
        return default_install_dir()
            .map(existing_or_parent)
            .or_else(|_| dirs::home_dir().ok_or_else(|| "Не удалось определить домашнюю папку.".into()));
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
        .unwrap_or_else(|| PathBuf::from(r"C:\"))
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

fn default_install_dir() -> Result<PathBuf, String> {
    dirs::data_local_dir()
        .map(|base| base.join("Programs").join(PRODUCT_NAME))
        .ok_or_else(|| "Не удалось определить LocalAppData.".to_string())
}

fn roaming_nimbo_bin_dir() -> Result<PathBuf, String> {
    dirs::data_dir()
        .map(|base| base.join(PRODUCT_NAME).join("bin"))
        .ok_or_else(|| "Не удалось определить AppData.".to_string())
}

fn replace_payload(path: &Path, bytes: &[u8]) -> Result<(), String> {
    if path.exists() {
        let old = path.with_extension("exe.old");
        let _ = fs::remove_file(&old);
        if fs::rename(path, &old).is_err() {
            std::thread::sleep(Duration::from_millis(500));
            let _ = fs::remove_file(&old);
            fs::rename(path, &old)
                .map_err(|e| format!("Не удалось заменить {}: {e}", path.display()))?;
        }
    }
    write_payload(path, bytes)
}

fn write_payload(path: &Path, bytes: &[u8]) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .map_err(|e| format!("Не удалось создать {}: {e}", parent.display()))?;
    }
    fs::write(path, bytes).map_err(|e| format!("Не удалось записать {}: {e}", path.display()))
}

fn copy_self_uninstaller(install_dir: &Path) -> Result<(), String> {
    let current = std::env::current_exe()
        .map_err(|e| format!("Не удалось определить путь установщика: {e}"))?;
    fs::copy(current, install_dir.join(UNINSTALL_EXE))
        .map(|_| ())
        .map_err(|e| format!("Не удалось создать деинсталлятор: {e}"))
}

fn run_status(exe: &Path, args: &[&str]) -> Result<(), String> {
    let status = hidden_command(exe)
        .args(args)
        .status()
        .map_err(|e| format!("{}: {e}", exe.display()))?;
    if status.success() {
        Ok(())
    } else {
        Err(format!("{} завершился с кодом {:?}", exe.display(), status.code()))
    }
}

fn hidden_command<P: AsRef<Path>>(program: P) -> Command {
    let mut command = Command::new(program.as_ref());
    command.stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    command
}

fn taskkill_image(image: &str) {
    let _ = hidden_command("taskkill.exe")
        .args(["/F", "/IM", image, "/T"])
        .status();
}

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
    create_shortcut(&start_menu.join("Nimbo.lnk"), &install_dir.join(APP_EXE), install_dir)
}

fn create_desktop_shortcut(install_dir: &Path) -> Result<(), String> {
    let desktop = dirs::desktop_dir()
        .ok_or_else(|| "Не удалось определить рабочий стол.".to_string())?;
    create_shortcut(&desktop.join("Nimbo.lnk"), &install_dir.join(APP_EXE), install_dir)
}

fn create_shortcut(link: &Path, target: &Path, working_dir: &Path) -> Result<(), String> {
    let script = format!(
        "$w=New-Object -ComObject WScript.Shell;$s=$w.CreateShortcut('{}');$s.TargetPath='{}';$s.WorkingDirectory='{}';$s.IconLocation='{},0';$s.Save()",
        ps_escape(link),
        ps_escape(target),
        ps_escape(working_dir),
        ps_escape(target),
    );
    let status = hidden_command("powershell.exe")
        .args(["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", &script])
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

    let uninstall_path = format!("Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{APP_ID}");
    let (uninstall, _) = hkcu
        .create_subkey(uninstall_path)
        .map_err(|e| format!("Не удалось записать uninstall-запись: {e}"))?;
    uninstall.set_value("DisplayName", &PRODUCT_NAME).map_err(reg_err)?;
    uninstall.set_value("DisplayVersion", &PRODUCT_VERSION).map_err(reg_err)?;
    uninstall.set_value("Publisher", &"Danila").map_err(reg_err)?;
    uninstall
        .set_value("DisplayIcon", &install_dir.join(APP_EXE).to_string_lossy().to_string())
        .map_err(reg_err)?;
    uninstall
        .set_value("InstallLocation", &install_dir.to_string_lossy().to_string())
        .map_err(reg_err)?;
    uninstall
        .set_value(
            "UninstallString",
            &format!("\"{}\" --uninstall", install_dir.join(UNINSTALL_EXE).display()),
        )
        .map_err(reg_err)?;
    uninstall.set_value("NoModify", &1u32).map_err(reg_err)?;
    uninstall.set_value("NoRepair", &1u32).map_err(reg_err)?;

    let (protocol, _) = hkcu
        .create_subkey("Software\\Classes\\nimbo")
        .map_err(|e| format!("Не удалось зарегистрировать nimbo://: {e}"))?;
    protocol.set_value("", &"URL:Nimbo Protocol").map_err(reg_err)?;
    protocol.set_value("URL Protocol", &"").map_err(reg_err)?;
    let (icon, _) = hkcu.create_subkey("Software\\Classes\\nimbo\\DefaultIcon").map_err(reg_err)?;
    icon.set_value("", &format!("{},0", install_dir.join(APP_EXE).display())).map_err(reg_err)?;
    let (open, _) = hkcu
        .create_subkey("Software\\Classes\\nimbo\\shell\\open\\command")
        .map_err(reg_err)?;
    open.set_value("", &format!("\"{}\" \"%1\"", install_dir.join(APP_EXE).display()))
        .map_err(reg_err)?;
    Ok(())
}

#[cfg(not(windows))]
fn write_registry(_install_dir: &Path) -> Result<(), String> {
    Ok(())
}

fn reg_err(error: std::io::Error) -> String {
    format!("Ошибка реестра: {error}")
}

fn helper_state() -> (bool, bool) {
    let output = hidden_command("sc.exe").args(["query", SERVICE_NAME]).output();
    let Ok(output) = output else {
        return (false, false);
    };
    let text = String::from_utf8_lossy(&output.stdout).to_ascii_uppercase();
    (output.status.success(), text.contains("RUNNING"))
}

fn cleanup_old_binaries(install_dir: &Path) {
    for name in ["nimbo-svc.exe.old", "Nimbo.exe.old"] {
        let _ = fs::remove_file(install_dir.join(name));
    }
}

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

#[cfg(windows)]
fn delete_registry() {
    use winreg::enums::HKEY_CURRENT_USER;
    use winreg::RegKey;
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let _ = hkcu.delete_subkey_all("Software\\Classes\\nimbo");
    let _ = hkcu.delete_subkey_all(format!("Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{APP_ID}"));
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

#[cfg(not(windows))]
fn schedule_self_cleanup(_install_dir: &Path) {}
