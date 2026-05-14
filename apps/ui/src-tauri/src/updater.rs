use serde::{Deserialize, Serialize};
use tauri::AppHandle;

const DEFAULT_RELEASE_API_URL: &str = "https://api.github.com/repos/BBGGVP5/nimbo/releases/latest";

#[derive(Debug, Clone, Serialize)]
pub struct AppUpdateInfo {
    pub available: bool,
    pub current_version: String,
    pub latest_version: String,
    pub release_name: String,
    pub release_notes: Option<String>,
    pub release_url: String,
    pub published_at: Option<String>,
    pub target: String,
    pub asset: Option<AppUpdateAsset>,
    pub download_url: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct AppUpdateAsset {
    pub name: String,
    pub download_url: String,
    pub size: u64,
    pub content_type: Option<String>,
}

#[derive(Debug, Deserialize)]
struct GithubRelease {
    tag_name: String,
    name: Option<String>,
    body: Option<String>,
    html_url: String,
    published_at: Option<String>,
    assets: Vec<GithubAsset>,
}

#[derive(Debug, Deserialize)]
struct GithubAsset {
    name: String,
    browser_download_url: String,
    size: u64,
    content_type: Option<String>,
}

#[tauri::command]
pub async fn check_app_update() -> Result<AppUpdateInfo, String> {
    let current_version = env!("CARGO_PKG_VERSION").to_string();
    let release = fetch_latest_release().await?;
    let raw_latest = release
        .name
        .as_deref()
        .filter(|name| !name.trim().is_empty())
        .unwrap_or(&release.tag_name);
    let latest_version = normalize_version_label(raw_latest);
    let available = compare_versions(&latest_version, &current_version).is_gt();
    let asset = if available {
        select_asset(&release.assets).map(|asset| AppUpdateAsset {
            name: asset.name.clone(),
            download_url: asset.browser_download_url.clone(),
            size: asset.size,
            content_type: asset.content_type.clone(),
        })
    } else {
        None
    };
    let download_url = asset.as_ref().map(|asset| asset.download_url.clone());

    Ok(AppUpdateInfo {
        available,
        current_version,
        latest_version,
        release_name: raw_latest.trim().to_string(),
        release_notes: release.body,
        release_url: release.html_url,
        published_at: release.published_at,
        target: current_target_label(),
        asset,
        download_url,
    })
}

#[tauri::command]
pub fn open_update_download(_app: AppHandle, download_url: String) -> Result<(), String> {
    let url = download_url.trim();
    if !(url.starts_with("https://") || url.starts_with("http://")) {
        return Err("Некорректная ссылка обновления.".into());
    }
    open_url(url)
}

async fn fetch_latest_release() -> Result<GithubRelease, String> {
    let url = std::env::var("NIMBO_UPDATE_RELEASE_API")
        .ok()
        .filter(|value| !value.trim().is_empty())
        .unwrap_or_else(|| DEFAULT_RELEASE_API_URL.to_string());

    let response = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(18))
        .build()
        .map_err(|e| format!("Не удалось создать HTTP-клиент обновлений: {e}"))?
        .get(url)
        .header(reqwest::header::USER_AGENT, "Nimbo-Updater")
        .header(reqwest::header::ACCEPT, "application/vnd.github+json")
        .send()
        .await
        .map_err(|e| format!("Не удалось проверить обновления: {e}"))?
        .error_for_status()
        .map_err(|e| format!("GitHub не отдал релиз: {e}"))?;

    let text = response
        .text()
        .await
        .map_err(|e| format!("Не удалось прочитать ответ GitHub: {e}"))?;
    serde_json::from_str(&text).map_err(|e| format!("Не удалось разобрать релиз GitHub: {e}"))
}

fn select_asset(assets: &[GithubAsset]) -> Option<&GithubAsset> {
    assets
        .iter()
        .filter_map(|asset| asset_score(asset).map(|score| (score, asset)))
        .max_by_key(|(score, _)| *score)
        .map(|(_, asset)| asset)
}

fn asset_score(asset: &GithubAsset) -> Option<i32> {
    let name = asset.name.to_ascii_lowercase();
    let os_score = os_score(&name)?;
    let arch = arch_score(&name);
    if arch < 0 {
        return None;
    }
    let extension = extension_score(&name);
    let installer = installer_score(&name);
    Some(os_score + arch + extension + installer)
}

fn os_score(name: &str) -> Option<i32> {
    let foreign_os: &[&str] = match std::env::consts::OS {
        "windows" => &["linux", "mac", "darwin", "apple"],
        "macos" => &["windows", "win32", "linux"],
        "linux" => &["windows", "win32", "mac", "darwin", "apple"],
        _ => &["windows", "linux", "mac", "darwin"],
    };
    if foreign_os.iter().any(|token| name.contains(token)) {
        return None;
    }

    match std::env::consts::OS {
        "windows" => {
            if name.ends_with(".exe") || name.ends_with(".msi") || name.contains("windows") || name.contains("win") {
                Some(35)
            } else {
                None
            }
        }
        "macos" => {
            if name.ends_with(".dmg") || name.ends_with(".app.tar.gz") || name.contains("mac") || name.contains("darwin") {
                Some(35)
            } else {
                None
            }
        }
        "linux" => {
            if name.ends_with(".appimage") || name.ends_with(".deb") || name.ends_with(".rpm") || name.contains("linux") {
                Some(35)
            } else {
                None
            }
        }
        _ => Some(5),
    }
}

fn arch_score(name: &str) -> i32 {
    match std::env::consts::ARCH {
        "x86_64" => {
            if contains_any(name, &["x64", "x86_64", "amd64", "64-bit", "64bit"]) {
                45
            } else if contains_any(name, &["x86", "ia32", "i686", "win32", "32-bit", "32bit"]) {
                -100
            } else {
                8
            }
        }
        "x86" => {
            if contains_any(name, &["x64", "x86_64", "amd64", "64-bit", "64bit", "arm64", "aarch64"]) {
                -100
            } else if contains_any(name, &["x86", "ia32", "i686", "win32", "32-bit", "32bit"]) {
                45
            } else {
                8
            }
        }
        "aarch64" => {
            if contains_any(name, &["arm64", "aarch64"]) {
                45
            } else if contains_any(name, &["x64", "x86_64", "amd64", "x86", "ia32", "i686"]) {
                -100
            } else {
                8
            }
        }
        _ => 8,
    }
}

fn extension_score(name: &str) -> i32 {
    if name.ends_with(".exe") || name.ends_with(".msi") || name.ends_with(".dmg") || name.ends_with(".appimage") {
        20
    } else if name.ends_with(".zip") || name.ends_with(".tar.gz") || name.ends_with(".deb") || name.ends_with(".rpm") {
        10
    } else {
        0
    }
}

fn installer_score(name: &str) -> i32 {
    if contains_any(name, &["setup", "installer", "install"]) {
        8
    } else {
        0
    }
}

fn contains_any(value: &str, needles: &[&str]) -> bool {
    needles.iter().any(|needle| value.contains(needle))
}

fn current_target_label() -> String {
    let os = match std::env::consts::OS {
        "windows" => "Windows",
        "macos" => "macOS",
        "linux" => "Linux",
        other => other,
    };
    let arch = match std::env::consts::ARCH {
        "x86_64" => "x64",
        "x86" => "x86",
        "aarch64" => "arm64",
        other => other,
    };
    format!("{os} {arch}")
}

fn normalize_version_label(value: &str) -> String {
    value
        .trim()
        .trim_start_matches(|ch| ch == 'v' || ch == 'V')
        .trim()
        .to_string()
}

fn compare_versions(a: &str, b: &str) -> std::cmp::Ordering {
    let left = version_parts(a);
    let right = version_parts(b);
    left.cmp(&right)
}

fn version_parts(value: &str) -> Vec<u64> {
    let mut parts = normalize_version_label(value)
        .split(|ch: char| !(ch.is_ascii_digit()))
        .filter(|part| !part.is_empty())
        .take(4)
        .map(|part| part.parse::<u64>().unwrap_or(0))
        .collect::<Vec<_>>();
    while parts.len() < 4 {
        parts.push(0);
    }
    parts
}

#[cfg(windows)]
fn open_url(url: &str) -> Result<(), String> {
    std::process::Command::new("rundll32")
        .arg("url.dll,FileProtocolHandler")
        .arg(url)
        .spawn()
        .map_err(|e| format!("Не удалось открыть ссылку обновления: {e}"))?;
    Ok(())
}

#[cfg(target_os = "macos")]
fn open_url(url: &str) -> Result<(), String> {
    std::process::Command::new("open")
        .arg(url)
        .spawn()
        .map_err(|e| format!("Не удалось открыть ссылку обновления: {e}"))?;
    Ok(())
}

#[cfg(all(unix, not(target_os = "macos")))]
fn open_url(url: &str) -> Result<(), String> {
    std::process::Command::new("xdg-open")
        .arg(url)
        .spawn()
        .map_err(|e| format!("Не удалось открыть ссылку обновления: {e}"))?;
    Ok(())
}

#[cfg(not(any(windows, unix)))]
fn open_url(_url: &str) -> Result<(), String> {
    Err("Открытие ссылки не поддерживается на этой системе.".into())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn compares_plain_and_v_prefixed_versions() {
        assert!(compare_versions("v1.2.0", "1.1.9").is_gt());
        assert!(compare_versions("1.0.0", "1.0.0").is_eq());
        assert!(compare_versions("0.9.9", "1.0.0").is_lt());
    }
}
