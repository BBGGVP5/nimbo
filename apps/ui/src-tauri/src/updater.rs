use crate::state::{AppState, Language, UpdateChannel};
use semver::Version;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::fs::{File, OpenOptions};
use std::io::{Read, Write};
use std::net::IpAddr;
use std::path::{Path, PathBuf};
use tauri::{AppHandle, Emitter, State};

const DEFAULT_RELEASE_API_URL: &str =
    "https://api.github.com/repos/BBGGVP5/nimbo/releases?per_page=20";
const LOCAL_HTTP_PROXY: &str = "http://127.0.0.1:10809";
const GITHUB_API_DOMAIN: &str = "api.github.com";
const GITHUB_RELEASE_DOMAIN: &str = "github.com";
const GITHUB_API_IPS: &[&str] = &[
    "140.82.112.6",
    "140.82.113.6",
    "140.82.114.6",
    "140.82.121.6",
];
const UPDATE_STORAGE_RESERVE_BYTES: u64 = 64 * 1024 * 1024;

#[derive(Debug, Clone, Serialize)]
pub struct AppUpdateInfo {
    pub available: bool,
    pub reason: Option<UpdateReason>,
    pub channel: UpdateChannel,
    pub current_version: String,
    pub latest_version: String,
    pub release_name: String,
    pub release_notes: Option<String>,
    pub release_url: String,
    pub published_at: Option<String>,
    pub target_commitish: String,
    pub target: String,
    pub asset: Option<AppUpdateAsset>,
    pub download_url: Option<String>,
}

#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum UpdateReason {
    NewVersion,
    Reissued,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppUpdateAsset {
    pub id: u64,
    pub name: String,
    pub download_url: String,
    pub size: u64,
    pub content_type: Option<String>,
    pub digest: Option<String>,
    pub created_at: Option<String>,
    pub updated_at: Option<String>,
    pub fingerprint: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct AppUpdateInstallResult {
    pub verified: bool,
    pub digest: String,
    pub local_path: String,
    pub rollback_supported: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct AppUpdateProgress {
    pub downloaded_bytes: u64,
    pub total_bytes: u64,
    pub percent: u8,
    pub stage: &'static str,
}

#[derive(Debug, Clone, Serialize)]
pub struct AppPostUpdateInfo {
    pub version: String,
    pub release_notes: Option<String>,
    pub release_url: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
struct GithubRelease {
    tag_name: String,
    name: Option<String>,
    body: Option<String>,
    html_url: String,
    published_at: Option<String>,
    #[serde(default)]
    target_commitish: String,
    #[serde(default)]
    draft: bool,
    #[serde(default)]
    prerelease: bool,
    #[serde(default)]
    assets: Vec<GithubAsset>,
}

#[derive(Debug, Clone, Deserialize)]
struct GithubAsset {
    #[serde(default)]
    id: u64,
    name: String,
    browser_download_url: String,
    size: u64,
    content_type: Option<String>,
    digest: Option<String>,
    created_at: Option<String>,
    updated_at: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct UpdateReceipt {
    version: String,
    channel: UpdateChannel,
    fingerprint: String,
    digest: String,
    asset_updated_at: Option<String>,
    executable_path: String,
    #[serde(default)]
    release_notes: Option<String>,
    #[serde(default)]
    release_url: Option<String>,
    #[serde(default)]
    show_changelog: bool,
}

#[tauri::command]
pub async fn check_app_update(
    app: AppHandle,
    state: State<'_, AppState>,
    channel: UpdateChannel,
) -> Result<AppUpdateInfo, String> {
    let current_version = env!("CARGO_PKG_VERSION").to_string();
    let releases = fetch_releases().await?;
    let release = select_release(&releases, channel).ok_or_else(|| match channel {
        UpdateChannel::Stable => "В GitHub нет опубликованного стабильного релиза.".to_string(),
        UpdateChannel::Beta => "В GitHub нет опубликованного релиза для бета-канала.".to_string(),
    })?;
    let latest_version = normalize_version_label(&release.tag_name);
    let selected_asset = select_asset(&release.assets).map(app_asset_from_github);
    let installed = read_installed_receipt(&app).ok().flatten();
    let installed_fingerprint = installed
        .as_ref()
        .filter(|receipt| receipt.version == current_version)
        .map(|receipt| receipt.fingerprint.as_str());
    let reason = selected_asset.as_ref().and_then(|asset| {
        update_reason(
            &latest_version,
            &current_version,
            installed_fingerprint,
            &asset.fingerprint,
        )
    });
    let available = reason.is_some();
    if should_record_initial_fingerprint(
        &latest_version,
        &current_version,
        installed_fingerprint,
        reason,
    ) {
        if let Some(asset) = selected_asset.as_ref() {
            match receipt_for_asset(&latest_version, channel, asset, None, None, false)
                .and_then(|receipt| write_receipt(&app, "installed.json", &receipt))
            {
                Ok(()) => tracing::info!(
                    version = %latest_version,
                    fingerprint = %asset.fingerprint,
                    "recorded initial update asset fingerprint"
                ),
                Err(error) => tracing::warn!(
                    %error,
                    "failed to record initial update asset fingerprint"
                ),
            }
        }
    }
    let download_url = if available {
        selected_asset
            .as_ref()
            .map(|asset| asset.download_url.clone())
    } else {
        None
    };
    let release_name = release
        .name
        .as_deref()
        .filter(|name| !name.trim().is_empty())
        .unwrap_or(&release.tag_name)
        .trim()
        .to_string();
    let release_notes = release
        .body
        .as_deref()
        .and_then(release_notes_for_desktop)
        .or_else(|| {
            bundled_release_notes(
                &latest_version,
                state.snapshot().preferences.language.resolved(),
            )
        });

    Ok(AppUpdateInfo {
        available,
        reason,
        channel,
        current_version,
        latest_version,
        release_name,
        release_notes,
        release_url: release.html_url.clone(),
        published_at: release.published_at.clone(),
        target_commitish: release.target_commitish.clone(),
        target: current_target_label(),
        asset: selected_asset,
        download_url,
    })
}

#[tauri::command]
pub async fn install_app_update(
    app: AppHandle,
    state: State<'_, AppState>,
    fingerprint: String,
    latest_version: String,
    channel: UpdateChannel,
) -> Result<AppUpdateInstallResult, String> {
    // Re-read trusted GitHub metadata at install time. The frontend only sends
    // the fingerprint it displayed, so it cannot substitute another URL or
    // digest through the IPC command.
    let releases = fetch_releases().await?;
    let release = select_release(&releases, channel)
        .ok_or_else(|| "Релиз больше недоступен. Проверьте обновления ещё раз.".to_string())?;
    if normalize_version_label(&release.tag_name) != normalize_version_label(&latest_version) {
        return Err("Релиз изменился после проверки. Проверьте обновления ещё раз.".into());
    }
    let asset = select_asset(&release.assets)
        .map(app_asset_from_github)
        .ok_or_else(|| "Файл для этой системы больше недоступен.".to_string())?;
    if asset.fingerprint != fingerprint {
        return Err("Файл релиза был заменён после проверки. Проверьте обновления ещё раз.".into());
    }
    validate_update_download_url(&asset.download_url)?;
    let expected_digest = normalize_sha256_digest(asset.digest.as_deref().ok_or_else(|| {
        "GitHub не предоставил SHA-256 для этого файла. Установка отменена.".to_string()
    })?)?;

    let downloads_dir = updates_dir(&app)?.join("downloads");
    std::fs::create_dir_all(&downloads_dir)
        .map_err(|e| format!("Не удалось создать папку обновлений: {e}"))?;
    let file_name = Path::new(&asset.name)
        .file_name()
        .and_then(|name| name.to_str())
        .filter(|name| !name.is_empty())
        .ok_or_else(|| "Некорректное имя файла обновления.".to_string())?;
    let fingerprint_prefix = sha256_hex(asset.fingerprint.as_bytes());
    let target = downloads_dir.join(format!("{}-{}", &fingerprint_prefix[..12], file_name));
    let partial = target.with_file_name(format!(
        "{}.part",
        target
            .file_name()
            .and_then(|name| name.to_str())
            .ok_or_else(|| "Некорректное имя временного файла обновления.".to_string())?
    ));
    let wifi_only = state.snapshot().preferences.update_wifi_only;
    let wifi_address = if wifi_only {
        Some(active_wifi_address()?.ok_or_else(|| {
            "Загрузка разрешена только по Wi‑Fi, но активный Wi‑Fi-интерфейс не найден.".to_string()
        })?)
    } else {
        None
    };

    let cached_is_valid = target.is_file()
        && (asset.size == 0 || target.metadata().map(|meta| meta.len()).unwrap_or(0) == asset.size)
        && verify_sha256_file(&target, &expected_digest).is_ok();
    if cached_is_valid {
        emit_update_progress(
            &app,
            target
                .metadata()
                .map(|meta| meta.len())
                .unwrap_or(asset.size),
            asset.size,
            "verifying",
        );
    }
    if !cached_is_valid {
        if target.exists() {
            std::fs::remove_file(&target)
                .map_err(|e| format!("Не удалось удалить повреждённый файл обновления: {e}"))?;
        }
        normalize_partial_file(&partial, asset.size)?;
        download_asset_to_file(
            &app,
            &asset.download_url,
            &partial,
            asset.size,
            wifi_address,
        )
        .await?;
        emit_update_progress(
            &app,
            partial.metadata().map(|meta| meta.len()).unwrap_or(0),
            asset.size,
            "verifying",
        );
        verify_downloaded_file(&partial, asset.size, &expected_digest)?;
        if target.exists() {
            std::fs::remove_file(&target)
                .map_err(|e| format!("Не удалось заменить файл обновления: {e}"))?;
        }
        std::fs::rename(&partial, &target)
            .map_err(|e| format!("Не удалось завершить загрузку обновления: {e}"))?;
    }

    let release_notes = release
        .body
        .as_deref()
        .and_then(release_notes_for_desktop)
        .or_else(|| {
            bundled_release_notes(
                &latest_version,
                state.snapshot().preferences.language.resolved(),
            )
        });
    let receipt = receipt_for_asset(
        &latest_version,
        channel,
        &asset,
        release_notes,
        Some(release.html_url.clone()),
        true,
    )?;
    write_receipt(&app, "pending.json", &receipt)?;
    emit_update_progress(&app, asset.size, asset.size, "ready");
    open_verified_package(&target)?;

    Ok(AppUpdateInstallResult {
        verified: true,
        digest: expected_digest,
        local_path: target.display().to_string(),
        rollback_supported: cfg!(windows),
    })
}

#[tauri::command]
pub fn open_update_download(_app: AppHandle, download_url: String) -> Result<(), String> {
    let url = download_url.trim();
    if !url.starts_with("https://") {
        return Err("Некорректная ссылка обновления.".into());
    }
    open_url(url)
}

pub fn run_update_health_check() -> Result<(), String> {
    let executable = std::env::current_exe()
        .map_err(|e| format!("Не удалось определить файл приложения: {e}"))?;
    if !executable.is_file() {
        return Err("Файл приложения отсутствует после обновления.".into());
    }
    crate::state::AppState::load()
        .map_err(|e| format!("Не удалось загрузить состояние приложения: {e}"))?;
    promote_pending_receipt()?;
    Ok(())
}

#[tauri::command]
pub fn get_post_update_info(app: AppHandle) -> Result<Option<AppPostUpdateInfo>, String> {
    let Some(receipt) = read_installed_receipt(&app)? else {
        return Ok(None);
    };
    if !receipt.show_changelog
        || compare_versions(&receipt.version, env!("CARGO_PKG_VERSION")).is_ne()
    {
        return Ok(None);
    }
    Ok(Some(AppPostUpdateInfo {
        version: receipt.version,
        release_notes: receipt.release_notes,
        release_url: receipt.release_url,
    }))
}

#[tauri::command]
pub fn dismiss_post_update_info(app: AppHandle) -> Result<(), String> {
    let Some(mut receipt) = read_installed_receipt(&app)? else {
        return Ok(());
    };
    if !receipt.show_changelog {
        return Ok(());
    }
    receipt.show_changelog = false;
    write_receipt(&app, "installed.json", &receipt)
}

async fn fetch_releases() -> Result<Vec<GithubRelease>, String> {
    let url = std::env::var("NIMBO_UPDATE_RELEASE_API")
        .ok()
        .filter(|value| !value.trim().is_empty())
        .unwrap_or_else(|| DEFAULT_RELEASE_API_URL.to_string());

    let direct_result = fetch_release_text(&url, FetchMode::Direct).await;
    let text = match direct_result {
        Ok(text) => text,
        Err(direct_error) => match fetch_release_text(&url, FetchMode::PinnedGithubDns).await {
            Ok(text) => text,
            Err(pinned_error) => fetch_release_text(&url, FetchMode::LocalProxy)
                .await
                .map_err(|proxy_error| {
                    format!(
                        "Не удалось проверить обновления напрямую ({direct_error}), через GitHub DNS fallback ({pinned_error}) и через локальный proxy ({proxy_error})"
                    )
                })?,
        },
    };

    parse_releases(&text)
}

fn parse_releases(text: &str) -> Result<Vec<GithubRelease>, String> {
    if let Ok(releases) = serde_json::from_str::<Vec<GithubRelease>>(text) {
        return Ok(releases);
    }
    serde_json::from_str::<GithubRelease>(text)
        .map(|release| vec![release])
        .map_err(|e| format!("Не удалось разобрать релизы GitHub: {e}"))
}

fn select_release(releases: &[GithubRelease], channel: UpdateChannel) -> Option<&GithubRelease> {
    select_release_for_target(
        releases,
        channel,
        std::env::consts::OS,
        std::env::consts::ARCH,
    )
}

fn select_release_for_target<'a>(
    releases: &'a [GithubRelease],
    channel: UpdateChannel,
    os: &str,
    arch: &str,
) -> Option<&'a GithubRelease> {
    releases
        .iter()
        .filter(|release| release_matches_channel(release, channel))
        .filter(|release| select_asset_for_target(&release.assets, os, arch).is_some())
        .max_by(|left, right| compare_versions(&left.tag_name, &right.tag_name))
}

fn release_matches_channel(release: &GithubRelease, channel: UpdateChannel) -> bool {
    !release.draft
        && match channel {
            UpdateChannel::Stable => !release.prerelease,
            UpdateChannel::Beta => true,
        }
}

fn release_notes_for_desktop(body: &str) -> Option<String> {
    let tagged = extract_platform_section(body, "desktop");
    let source = tagged.unwrap_or(body);
    let legacy = tagged.is_none();
    let mut lines = Vec::new();

    for raw_line in source.lines() {
        let trimmed = raw_line.trim();
        let lower = trimmed.to_ascii_lowercase();
        if trimmed.starts_with("<!--")
            || trimmed.starts_with("<")
            || trimmed.starts_with("![")
            || trimmed.starts_with('|')
            || lower.starts_with("> [!")
        {
            continue;
        }
        if legacy
            && lower.contains("android")
            && !lower.contains("windows")
            && !lower.contains("linux")
        {
            continue;
        }
        if is_release_asset_line(&lower) {
            continue;
        }
        lines.push(trimmed.trim_start_matches('>').trim_start().to_string());
    }

    let mut compact = Vec::new();
    for line in lines {
        if line.is_empty() && compact.last().is_some_and(|last: &String| last.is_empty()) {
            continue;
        }
        compact.push(line);
    }
    let result = compact.join("\n").trim().to_string();
    (!result.is_empty()).then_some(result)
}

fn extract_platform_section<'a>(body: &'a str, platform: &str) -> Option<&'a str> {
    let start = format!("<!-- nimbo:{platform}:start -->");
    let end = format!("<!-- nimbo:{platform}:end -->");
    let start_index = body.find(&start)? + start.len();
    let end_index = body[start_index..].find(&end)? + start_index;
    Some(&body[start_index..end_index])
}

fn is_release_asset_line(lower: &str) -> bool {
    let has_package = [
        ".apk",
        ".exe",
        ".msi",
        ".dmg",
        ".appimage",
        ".deb",
        ".rpm",
        ".sha256",
    ]
    .iter()
    .any(|extension| lower.contains(extension));
    has_package
        && (lower.contains("http://")
            || lower.contains("https://")
            || lower
                .trim_start_matches(['-', '*', ' ', '|'])
                .starts_with("nimbo"))
}

#[derive(Debug, Clone, Copy)]
enum FetchMode {
    Direct,
    PinnedGithubDns,
    LocalProxy,
}

async fn fetch_release_text(url: &str, mode: FetchMode) -> Result<String, String> {
    if matches!(mode, FetchMode::PinnedGithubDns) {
        let mut last_error = None;
        for ip in GITHUB_API_IPS {
            match fetch_release_text_with_pinned_ip(url, ip).await {
                Ok(text) => return Ok(text),
                Err(error) => last_error = Some(error),
            }
        }
        return Err(last_error.unwrap_or_else(|| "нет доступных GitHub IP fallback".into()));
    }

    let mut builder = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(18))
        .no_proxy();

    if matches!(mode, FetchMode::LocalProxy) {
        let proxy = reqwest::Proxy::all(LOCAL_HTTP_PROXY)
            .map_err(|e| format!("Не удалось настроить локальный proxy обновлений: {e}"))?;
        builder = builder.proxy(proxy);
    }

    fetch_release_text_with_client(builder, url).await
}

async fn fetch_release_text_with_pinned_ip(url: &str, ip: &str) -> Result<String, String> {
    let socket_addr = format!("{ip}:443")
        .parse()
        .map_err(|e| format!("Некорректный GitHub IP fallback {ip}: {e}"))?;
    let builder = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(18))
        .no_proxy()
        .resolve(GITHUB_API_DOMAIN, socket_addr);

    fetch_release_text_with_client(builder, url).await
}

async fn fetch_release_text_with_client(
    builder: reqwest::ClientBuilder,
    url: &str,
) -> Result<String, String> {
    let response = builder
        .build()
        .map_err(|e| format!("Не удалось создать HTTP-клиент обновлений: {e}"))?
        .get(url)
        .header(reqwest::header::USER_AGENT, "Nimbo-Updater")
        .header(reqwest::header::ACCEPT, "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2026-03-10")
        .send()
        .await
        .map_err(|e| format!("Не удалось проверить обновления: {e}"))?
        .error_for_status()
        .map_err(|e| {
            if e.status() == Some(reqwest::StatusCode::NOT_FOUND) {
                "GitHub не нашёл опубликованный release. Проверьте, что это именно опубликованный релиз, а не draft или только tag.".to_string()
            } else {
                format!("GitHub не отдал релизы: {e}")
            }
        })?;

    response
        .text()
        .await
        .map_err(|e| format!("Не удалось прочитать ответ GitHub: {e}"))
}

async fn download_asset_to_file(
    app: &AppHandle,
    url: &str,
    target: &Path,
    expected_bytes: u64,
    wifi_address: Option<IpAddr>,
) -> Result<(), String> {
    let on_progress =
        |downloaded, total| emit_update_progress(app, downloaded, total, "downloading");
    let mut direct_builder = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(180))
        .no_proxy();
    if let Some(address) = wifi_address {
        direct_builder = direct_builder.local_address(address);
    }
    let direct = download_asset_to_file_with_builder(
        direct_builder,
        url,
        target,
        expected_bytes,
        &on_progress,
    )
    .await;
    if direct.is_ok() {
        return Ok(());
    }
    if wifi_address.is_some() {
        return direct.map_err(|error| format!("Не удалось скачать обновление по Wi‑Fi: {error}"));
    }

    let proxy = reqwest::Proxy::all(LOCAL_HTTP_PROXY)
        .map_err(|e| format!("Не удалось настроить proxy загрузки: {e}"))?;
    download_asset_to_file_with_builder(
        reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(180))
            .proxy(proxy),
        url,
        target,
        expected_bytes,
        &on_progress,
    )
    .await
    .map_err(|proxy_error| {
        format!(
            "Не удалось скачать обновление напрямую ({}) и через локальный proxy ({proxy_error})",
            direct.unwrap_err()
        )
    })
}

async fn download_asset_to_file_with_builder(
    builder: reqwest::ClientBuilder,
    url: &str,
    target: &Path,
    expected_bytes: u64,
    on_progress: &(dyn Fn(u64, u64) + Send + Sync),
) -> Result<(), String> {
    let existing_bytes = target.metadata().map(|meta| meta.len()).unwrap_or(0);
    on_progress(existing_bytes, expected_bytes);
    if expected_bytes > 0 && existing_bytes == expected_bytes {
        return Ok(());
    }
    let parent = target
        .parent()
        .ok_or_else(|| "Не удалось определить папку загрузки обновления.".to_string())?;
    ensure_download_space(parent, expected_bytes, existing_bytes, 0)?;

    let client = builder
        .build()
        .map_err(|e| format!("Не удалось создать загрузчик обновления: {e}"))?;
    let requested_start = range_start(existing_bytes, expected_bytes);
    let mut request = client
        .get(url)
        .header(reqwest::header::USER_AGENT, "Nimbo-Updater");
    if let Some(start) = requested_start {
        request = request.header(reqwest::header::RANGE, format!("bytes={start}-"));
    }
    let mut response = request
        .send()
        .await
        .map_err(|e| format!("Не удалось скачать обновление: {e}"))?;
    if response.status() == reqwest::StatusCode::RANGE_NOT_SATISFIABLE
        && expected_bytes > 0
        && existing_bytes == expected_bytes
    {
        return Ok(());
    }
    if !response.status().is_success() {
        return Err(format!(
            "Сервер не отдал файл обновления: HTTP {}",
            response.status()
        ));
    }

    let append =
        requested_start.is_some() && response.status() == reqwest::StatusCode::PARTIAL_CONTENT;
    if append {
        let content_range = response
            .headers()
            .get(reqwest::header::CONTENT_RANGE)
            .and_then(|value| value.to_str().ok())
            .unwrap_or_default();
        if !content_range_matches(content_range, requested_start.unwrap_or_default()) {
            return Err("Сервер обновлений вернул неверный диапазон файла.".into());
        }
    } else if requested_start.is_some() {
        ensure_download_space(parent, expected_bytes, 0, existing_bytes)?;
    }

    let mut output = OpenOptions::new()
        .create(true)
        .write(true)
        .append(append)
        .truncate(!append)
        .open(target)
        .map_err(|e| format!("Не удалось открыть временный файл обновления: {e}"))?;
    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(|e| format!("Загрузка обновления была прервана: {e}"))?
    {
        output
            .write_all(&chunk)
            .map_err(|e| format!("Не удалось записать обновление на диск: {e}"))?;
        let downloaded = output.metadata().map(|meta| meta.len()).unwrap_or(0);
        on_progress(downloaded, expected_bytes);
    }
    output
        .sync_all()
        .map_err(|e| format!("Не удалось сохранить загруженное обновление: {e}"))?;

    let final_bytes = output
        .metadata()
        .map_err(|e| format!("Не удалось проверить размер обновления: {e}"))?
        .len();
    if expected_bytes > 0 && final_bytes != expected_bytes {
        return Err(format!(
            "Загрузка прервана: сохранено {final_bytes} из {expected_bytes} байт. Повторная попытка продолжит загрузку."
        ));
    }
    Ok(())
}

fn emit_update_progress(
    app: &AppHandle,
    downloaded_bytes: u64,
    total_bytes: u64,
    stage: &'static str,
) {
    let percent = downloaded_bytes
        .saturating_mul(100)
        .checked_div(total_bytes)
        .unwrap_or(0)
        .min(100) as u8;
    let _ = app.emit(
        "nimbo:update-progress",
        AppUpdateProgress {
            downloaded_bytes,
            total_bytes,
            percent,
            stage,
        },
    );
}

fn normalize_partial_file(path: &Path, expected_bytes: u64) -> Result<(), String> {
    let length = path.metadata().map(|meta| meta.len()).unwrap_or(0);
    if expected_bytes > 0 && length > expected_bytes {
        std::fs::remove_file(path)
            .map_err(|e| format!("Не удалось удалить некорректный временный файл: {e}"))?;
    }
    Ok(())
}

fn ensure_download_space(
    directory: &Path,
    expected_bytes: u64,
    partial_bytes: u64,
    reclaimable_bytes: u64,
) -> Result<(), String> {
    let available = fs2::available_space(directory)
        .map_err(|e| format!("Не удалось проверить свободное место: {e}"))?
        .saturating_add(reclaimable_bytes);
    let required = required_free_bytes(expected_bytes, partial_bytes);
    if available < required {
        let required_mb = required.saturating_add(1024 * 1024 - 1) / (1024 * 1024);
        return Err(format!(
            "Недостаточно свободного места для обновления. Освободите не менее {required_mb} МБ."
        ));
    }
    Ok(())
}

fn verify_downloaded_file(path: &Path, expected_bytes: u64, digest: &str) -> Result<(), String> {
    let length = path.metadata().map(|meta| meta.len()).unwrap_or(0);
    if length == 0 || (expected_bytes > 0 && length != expected_bytes) {
        let _ = std::fs::remove_file(path);
        return Err("Размер загруженного файла не совпадает с данными GitHub.".into());
    }
    if let Err(error) = verify_sha256_file(path, digest) {
        let _ = std::fs::remove_file(path);
        return Err(error);
    }
    Ok(())
}

fn verify_sha256_file(path: &Path, expected: &str) -> Result<(), String> {
    let expected = normalize_sha256_digest(expected)?;
    let mut file =
        File::open(path).map_err(|e| format!("Не удалось открыть обновление для проверки: {e}"))?;
    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 64 * 1024];
    loop {
        let count = file
            .read(&mut buffer)
            .map_err(|e| format!("Не удалось прочитать обновление для проверки: {e}"))?;
        if count == 0 {
            break;
        }
        hasher.update(&buffer[..count]);
    }
    let actual = format!(
        "sha256:{}",
        hasher
            .finalize()
            .iter()
            .map(|byte| format!("{byte:02x}"))
            .collect::<String>()
    );
    if actual != expected {
        return Err(format!(
            "SHA-256 файла обновления не совпал. Ожидался {expected}, получен {actual}. Файл не будет запущен."
        ));
    }
    Ok(())
}

#[cfg(windows)]
fn active_wifi_address() -> Result<Option<IpAddr>, String> {
    use std::net::{Ipv4Addr, Ipv6Addr};
    use windows_sys::Win32::Foundation::{ERROR_BUFFER_OVERFLOW, ERROR_SUCCESS};
    use windows_sys::Win32::NetworkManagement::IpHelper::{
        GetAdaptersAddresses, GAA_FLAG_SKIP_ANYCAST, GAA_FLAG_SKIP_DNS_SERVER,
        GAA_FLAG_SKIP_MULTICAST, IF_TYPE_IEEE80211, IP_ADAPTER_ADDRESSES_LH,
    };
    use windows_sys::Win32::NetworkManagement::Ndis::IfOperStatusUp;
    use windows_sys::Win32::Networking::WinSock::{AF_INET, AF_INET6, SOCKADDR_IN, SOCKADDR_IN6};

    let flags = GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_DNS_SERVER;
    let mut size = 0u32;
    let initial = unsafe {
        GetAdaptersAddresses(0, flags, std::ptr::null(), std::ptr::null_mut(), &mut size)
    };
    if initial != ERROR_BUFFER_OVERFLOW || size == 0 {
        return Err(format!(
            "Windows не смог определить сетевые адаптеры: код {initial}."
        ));
    }

    let mut storage = vec![0u64; (size as usize).div_ceil(std::mem::size_of::<u64>())];
    let adapters = storage.as_mut_ptr().cast::<IP_ADAPTER_ADDRESSES_LH>();
    let result = unsafe { GetAdaptersAddresses(0, flags, std::ptr::null(), adapters, &mut size) };
    if result != ERROR_SUCCESS {
        return Err(format!(
            "Windows не смог прочитать сетевые адаптеры: код {result}."
        ));
    }

    let mut adapter = adapters;
    let mut ipv6_fallback = None;
    while !adapter.is_null() {
        let current = unsafe { &*adapter };
        if current.IfType == IF_TYPE_IEEE80211 && current.OperStatus == IfOperStatusUp {
            let mut unicast = current.FirstUnicastAddress;
            while !unicast.is_null() {
                let socket = unsafe { &(*unicast).Address };
                if !socket.lpSockaddr.is_null() {
                    let family = unsafe { (*socket.lpSockaddr).sa_family };
                    if family == AF_INET {
                        let address = unsafe { &*(socket.lpSockaddr.cast::<SOCKADDR_IN>()) };
                        let ip =
                            Ipv4Addr::from(u32::from_be(unsafe { address.sin_addr.S_un.S_addr }));
                        if !ip.is_loopback() && !ip.is_unspecified() {
                            return Ok(Some(IpAddr::V4(ip)));
                        }
                    } else if family == AF_INET6 {
                        let address = unsafe { &*(socket.lpSockaddr.cast::<SOCKADDR_IN6>()) };
                        let ip = Ipv6Addr::from(unsafe { address.sin6_addr.u.Byte });
                        let first = ip.segments()[0];
                        let is_unicast_link_local = (first & 0xffc0) == 0xfe80;
                        if !ip.is_loopback() && !ip.is_unspecified() && !is_unicast_link_local {
                            ipv6_fallback = Some(IpAddr::V6(ip));
                        }
                    }
                }
                unicast = unsafe { (*unicast).Next };
            }
        }
        adapter = current.Next;
    }
    Ok(ipv6_fallback)
}

#[cfg(target_os = "linux")]
fn active_wifi_address() -> Result<Option<IpAddr>, String> {
    let interfaces = std::fs::read_dir("/sys/class/net")
        .map_err(|e| format!("Не удалось прочитать сетевые интерфейсы: {e}"))?;
    for entry in interfaces.filter_map(Result::ok) {
        let path = entry.path();
        if !path.join("wireless").is_dir() {
            continue;
        }
        let state = std::fs::read_to_string(path.join("operstate")).unwrap_or_default();
        if state.trim() != "up" {
            continue;
        }
        let name = entry.file_name();
        let output = std::process::Command::new("ip")
            .args(["-j", "-4", "address", "show", "dev"])
            .arg(&name)
            .output()
            .map_err(|e| format!("Не удалось запросить адрес Wi‑Fi: {e}"))?;
        if !output.status.success() {
            continue;
        }
        let value: serde_json::Value = serde_json::from_slice(&output.stdout)
            .map_err(|e| format!("Не удалось разобрать адрес Wi‑Fi: {e}"))?;
        let address = value
            .as_array()
            .and_then(|items| items.first())
            .and_then(|item| item.get("addr_info"))
            .and_then(serde_json::Value::as_array)
            .and_then(|items| {
                items.iter().find_map(|item| {
                    (item.get("family")?.as_str()? == "inet")
                        .then(|| item.get("local")?.as_str()?.parse::<IpAddr>().ok())
                        .flatten()
                })
            });
        if address.is_some() {
            return Ok(address);
        }
    }
    Ok(None)
}

#[cfg(not(any(windows, target_os = "linux")))]
fn active_wifi_address() -> Result<Option<IpAddr>, String> {
    Ok(None)
}

fn select_asset(assets: &[GithubAsset]) -> Option<&GithubAsset> {
    select_asset_for_target(assets, std::env::consts::OS, std::env::consts::ARCH)
}

fn select_asset_for_target<'a>(
    assets: &'a [GithubAsset],
    os: &str,
    arch: &str,
) -> Option<&'a GithubAsset> {
    assets
        .iter()
        .filter_map(|asset| asset_score_for(asset, os, arch).map(|score| (score, asset)))
        .max_by_key(|(score, _)| *score)
        .map(|(_, asset)| asset)
}

fn app_asset_from_github(asset: &GithubAsset) -> AppUpdateAsset {
    let fingerprint = format!(
        "{}:{}:{}:{}",
        asset.id,
        asset.updated_at.as_deref().unwrap_or_default(),
        asset.size,
        asset.digest.as_deref().unwrap_or_default()
    );
    AppUpdateAsset {
        id: asset.id,
        name: asset.name.clone(),
        download_url: asset.browser_download_url.clone(),
        size: asset.size,
        content_type: asset.content_type.clone(),
        digest: asset.digest.clone(),
        created_at: asset.created_at.clone(),
        updated_at: asset.updated_at.clone(),
        fingerprint,
    }
}

fn update_reason(
    latest_version: &str,
    current_version: &str,
    installed_fingerprint: Option<&str>,
    remote_fingerprint: &str,
) -> Option<UpdateReason> {
    match compare_versions(latest_version, current_version) {
        std::cmp::Ordering::Greater => Some(UpdateReason::NewVersion),
        std::cmp::Ordering::Equal
            if installed_fingerprint.is_some_and(|installed| installed != remote_fingerprint) =>
        {
            Some(UpdateReason::Reissued)
        }
        _ => None,
    }
}

fn should_record_initial_fingerprint(
    latest_version: &str,
    current_version: &str,
    installed_fingerprint: Option<&str>,
    reason: Option<UpdateReason>,
) -> bool {
    reason.is_none()
        && installed_fingerprint.is_none()
        && compare_versions(latest_version, current_version).is_eq()
}

fn asset_score_for(asset: &GithubAsset, os: &str, arch: &str) -> Option<i32> {
    let name = asset.name.to_ascii_lowercase();
    if name.ends_with(".sig") || name.ends_with(".sha256") || name.ends_with(".sha256sum") {
        return None;
    }
    let os_score = os_score_for(os, &name)?;
    let arch_score = arch_score_for(arch, &name);
    if arch_score < 0 {
        return None;
    }
    let extension = extension_score(&name);
    let installer = installer_score(&name);
    Some(os_score + arch_score + extension + installer)
}

fn os_score_for(os: &str, name: &str) -> Option<i32> {
    let foreign_os: &[&str] = match os {
        "windows" => &[
            "linux", "mac", "darwin", "apple", "android", ".apk", "ios", "iphone", "ipad",
        ],
        "macos" => &[
            "windows", "win32", "linux", "android", ".apk", "ios", "iphone", "ipad",
        ],
        "linux" => &[
            "windows", "win32", "mac", "darwin", "apple", "android", ".apk", "ios", "iphone",
            "ipad",
        ],
        _ => &[
            "windows", "linux", "mac", "darwin", "android", ".apk", "ios",
        ],
    };
    if foreign_os.iter().any(|token| name.contains(token)) {
        return None;
    }

    match os {
        "windows" => {
            if name.ends_with(".exe")
                || name.ends_with(".msi")
                || name.contains("windows")
                || name.contains("win")
            {
                Some(35)
            } else {
                None
            }
        }
        "macos" => {
            if name.ends_with(".dmg")
                || name.ends_with(".app.tar.gz")
                || name.contains("mac")
                || name.contains("darwin")
            {
                Some(35)
            } else {
                None
            }
        }
        "linux" => {
            if name.ends_with(".appimage")
                || name.ends_with(".deb")
                || name.ends_with(".rpm")
                || name.contains("linux")
            {
                Some(35)
            } else {
                None
            }
        }
        _ => Some(5),
    }
}

fn arch_score_for(architecture: &str, name: &str) -> i32 {
    match architecture {
        "x86_64" => {
            if contains_any(name, &["x64", "x86_64", "amd64", "64-bit", "64bit"]) {
                45
            } else if contains_any(
                name,
                &[
                    "x86", "ia32", "i686", "win32", "32-bit", "32bit", "arm64", "aarch64",
                ],
            ) {
                -100
            } else {
                8
            }
        }
        "x86" => {
            if contains_any(
                name,
                &[
                    "x64", "x86_64", "amd64", "64-bit", "64bit", "arm64", "aarch64",
                ],
            ) {
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
    if name.ends_with(".exe")
        || name.ends_with(".msi")
        || name.ends_with(".dmg")
        || name.ends_with(".appimage")
    {
        20
    } else if name.ends_with(".zip")
        || name.ends_with(".tar.gz")
        || name.ends_with(".deb")
        || name.ends_with(".rpm")
    {
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
        .trim_start_matches(['v', 'V'])
        .trim()
        .to_string()
}

fn bundled_release_notes(version: &str, language: Language) -> Option<String> {
    if normalize_version_label(version) != "1.0.2" {
        return None;
    }
    let notes = match language.resolved() {
        Language::Ru => {
            r#"## Безопасные обновления
- Добавлены каналы «Стабильный» и «Бета».
- Установщик автоматически выбирается под архитектуру компьютера.
- Перед запуском проверяются SHA-256, размер файла и данные релиза GitHub.
- Прерванная загрузка продолжается с сохранённого места.
- Nimbo проверяет свободное место до начала загрузки.
- Появилась настройка загрузки обновлений только по Wi-Fi.
- Если файл релиза заменён без смены версии, Nimbo предложит исправленное обновление повторно.

## Установка и восстановление
- После установки доступна кнопка «Что изменилось».
- Для Windows добавлена проверка запуска новой версии и автоматический откат при проблеме.
- Проверенный установщик сохраняется локально и не скачивается повторно без необходимости.
- В окне обновления показываются целевая система, имя файла и время его загрузки в релиз."#
        }
        Language::En | Language::System => {
            r#"## Safer updates
- Added Stable and Beta update channels.
- The installer is selected automatically for the computer architecture.
- SHA-256, file size, and GitHub release metadata are checked before launch.
- Interrupted downloads resume from the saved position.
- Nimbo checks free disk space before downloading.
- Added a download updates over Wi-Fi only option.
- If a release file is replaced without changing its version, Nimbo offers the corrected update again.

## Installation and recovery
- A What changed button is available after installation.
- Windows now verifies the new version launch and rolls back automatically if it fails.
- A verified installer is cached locally and is not downloaded again unnecessarily.
- The update screen shows the target system, file name, and release upload time."#
        }
    };
    Some(notes.to_string())
}

fn compare_versions(a: &str, b: &str) -> std::cmp::Ordering {
    let left = Version::parse(&normalize_version_label(a));
    let right = Version::parse(&normalize_version_label(b));
    match (left, right) {
        (Ok(left), Ok(right)) => left.cmp(&right),
        _ => version_parts(a).cmp(&version_parts(b)),
    }
}

fn range_start(partial_bytes: u64, expected_bytes: u64) -> Option<u64> {
    (partial_bytes > 0 && (expected_bytes == 0 || partial_bytes < expected_bytes))
        .then_some(partial_bytes)
}

fn content_range_matches(value: &str, requested_start: u64) -> bool {
    let normalized = value.trim().to_ascii_lowercase();
    let Some(range) = normalized.strip_prefix("bytes ") else {
        return false;
    };
    range
        .split_once('-')
        .and_then(|(start, _)| start.parse::<u64>().ok())
        == Some(requested_start)
}

fn required_free_bytes(expected_bytes: u64, partial_bytes: u64) -> u64 {
    expected_bytes
        .saturating_sub(partial_bytes)
        .saturating_add(UPDATE_STORAGE_RESERVE_BYTES)
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

fn validate_update_download_url(value: &str) -> Result<(), String> {
    let url =
        url::Url::parse(value).map_err(|e| format!("Некорректная ссылка файла обновления: {e}"))?;
    let valid_path = url.path().starts_with("/BBGGVP5/nimbo/releases/download/");
    if url.scheme() != "https" || url.host_str() != Some(GITHUB_RELEASE_DOMAIN) || !valid_path {
        return Err(
            "Файл обновления должен быть опубликован в GitHub Releases проекта Nimbo.".into(),
        );
    }
    Ok(())
}

fn normalize_sha256_digest(value: &str) -> Result<String, String> {
    let (algorithm, digest) = value
        .trim()
        .split_once(':')
        .ok_or_else(|| "GitHub вернул некорректный digest обновления.".to_string())?;
    if !algorithm.eq_ignore_ascii_case("sha256")
        || digest.len() != 64
        || !digest.chars().all(|ch| ch.is_ascii_hexdigit())
    {
        return Err("Обновление не имеет корректной проверки SHA-256.".into());
    }
    Ok(format!("sha256:{}", digest.to_ascii_lowercase()))
}

#[cfg(test)]
fn verify_sha256(bytes: &[u8], expected: &str) -> Result<(), String> {
    let expected = normalize_sha256_digest(expected)?;
    let actual = format!("sha256:{}", sha256_hex(bytes));
    if actual != expected {
        return Err(format!(
            "SHA-256 файла обновления не совпал. Ожидался {expected}, получен {actual}. Файл не будет запущен."
        ));
    }
    Ok(())
}

fn sha256_hex(bytes: &[u8]) -> String {
    let digest = Sha256::digest(bytes);
    digest.iter().map(|byte| format!("{byte:02x}")).collect()
}

fn updates_dir(_app: &AppHandle) -> Result<PathBuf, String> {
    dirs::data_dir()
        .map(|base| base.join("Nimbo").join("updates"))
        .ok_or_else(|| "Не удалось определить папку данных Nimbo.".to_string())
}

fn receipt_path(file_name: &str) -> Result<PathBuf, String> {
    dirs::data_dir()
        .map(|base| base.join("Nimbo").join("updates").join(file_name))
        .ok_or_else(|| "Не удалось определить папку данных Nimbo.".to_string())
}

fn read_installed_receipt(_app: &AppHandle) -> Result<Option<UpdateReceipt>, String> {
    let path = receipt_path("installed.json")?;
    if !path.exists() {
        return Ok(None);
    }
    let bytes = std::fs::read(&path)
        .map_err(|e| format!("Не удалось прочитать историю обновлений: {e}"))?;
    serde_json::from_slice(&bytes)
        .map(Some)
        .map_err(|e| format!("Не удалось разобрать историю обновлений: {e}"))
}

fn write_receipt(app: &AppHandle, file_name: &str, receipt: &UpdateReceipt) -> Result<(), String> {
    let dir = updates_dir(app)?;
    std::fs::create_dir_all(&dir)
        .map_err(|e| format!("Не удалось создать папку состояния обновления: {e}"))?;
    let bytes = serde_json::to_vec_pretty(receipt)
        .map_err(|e| format!("Не удалось сохранить состояние обновления: {e}"))?;
    write_atomic(&dir.join(file_name), &bytes)
}

fn receipt_for_asset(
    version: &str,
    channel: UpdateChannel,
    asset: &AppUpdateAsset,
    release_notes: Option<String>,
    release_url: Option<String>,
    show_changelog: bool,
) -> Result<UpdateReceipt, String> {
    let executable_path = std::env::current_exe()
        .map_err(|e| format!("Не удалось определить текущий файл Nimbo: {e}"))?;
    Ok(UpdateReceipt {
        version: normalize_version_label(version),
        channel,
        fingerprint: asset.fingerprint.clone(),
        digest: asset.digest.clone().unwrap_or_default(),
        asset_updated_at: asset.updated_at.clone(),
        executable_path: executable_path.display().to_string(),
        release_notes,
        release_url,
        show_changelog,
    })
}

fn promote_pending_receipt() -> Result<(), String> {
    let pending = receipt_path("pending.json")?;
    if !pending.exists() {
        return Ok(());
    }
    let bytes = std::fs::read(&pending)
        .map_err(|e| format!("Не удалось прочитать ожидающее обновление: {e}"))?;
    let receipt: UpdateReceipt = serde_json::from_slice(&bytes)
        .map_err(|e| format!("Не удалось разобрать ожидающее обновление: {e}"))?;
    match compare_versions(&receipt.version, env!("CARGO_PKG_VERSION")) {
        // The download this receipt belongs to never made it onto disk.
        std::cmp::Ordering::Greater => {
            return Err(format!(
                "Проверяется версия {}, но установлен файл версии {}.",
                receipt.version,
                env!("CARGO_PKG_VERSION")
            ));
        }
        // Left over from an update that was abandoned or already superseded —
        // failing the health check over it would roll back a good install.
        std::cmp::Ordering::Less => {
            let _ = std::fs::remove_file(&pending);
            return Ok(());
        }
        std::cmp::Ordering::Equal => {}
    }
    let installed = receipt_path("installed.json")?;
    write_atomic(&installed, &bytes)?;
    std::fs::remove_file(&pending)
        .map_err(|e| format!("Не удалось завершить запись обновления: {e}"))?;
    Ok(())
}

fn write_atomic(target: &Path, bytes: &[u8]) -> Result<(), String> {
    if let Some(parent) = target.parent() {
        std::fs::create_dir_all(parent)
            .map_err(|e| format!("Не удалось создать папку {}: {e}", parent.display()))?;
    }
    let temp = target.with_extension("tmp");
    std::fs::write(&temp, bytes)
        .map_err(|e| format!("Не удалось записать {}: {e}", temp.display()))?;
    if target.exists() {
        std::fs::remove_file(target)
            .map_err(|e| format!("Не удалось заменить {}: {e}", target.display()))?;
    }
    std::fs::rename(&temp, target)
        .map_err(|e| format!("Не удалось завершить запись {}: {e}", target.display()))
}

/// Launched through a short-lived `cmd /C start`, not as our own child: the
/// installer terminates Nimbo before replacing its files, and as a direct child
/// it went down together with us whenever that kill walked the process tree.
#[cfg(windows)]
fn open_verified_package(path: &Path) -> Result<(), String> {
    use std::os::windows::process::CommandExt;

    const CREATE_NO_WINDOW: u32 = 0x0800_0000;

    let detached = std::process::Command::new("cmd.exe")
        .args(["/C", "start", "", "/B"])
        .arg(path)
        .creation_flags(CREATE_NO_WINDOW)
        .spawn();
    if detached.is_ok() {
        return Ok(());
    }

    std::process::Command::new(path)
        .spawn()
        .map_err(|e| format!("Не удалось запустить проверенный установщик: {e}"))?;
    Ok(())
}

#[cfg(target_os = "macos")]
fn open_verified_package(path: &Path) -> Result<(), String> {
    std::process::Command::new("open")
        .arg(path)
        .spawn()
        .map_err(|e| format!("Не удалось открыть проверенный пакет: {e}"))?;
    Ok(())
}

#[cfg(all(unix, not(target_os = "macos")))]
fn open_verified_package(path: &Path) -> Result<(), String> {
    std::process::Command::new("xdg-open")
        .arg(path)
        .spawn()
        .map_err(|e| format!("Не удалось открыть проверенный пакет: {e}"))?;
    Ok(())
}

#[cfg(not(any(windows, unix)))]
fn open_verified_package(_path: &Path) -> Result<(), String> {
    Err("Установка обновлений не поддерживается на этой системе.".into())
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
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

    fn release(tag: &str, prerelease: bool) -> GithubRelease {
        GithubRelease {
            tag_name: tag.into(),
            name: None,
            body: None,
            html_url: "https://github.com/BBGGVP5/nimbo/releases".into(),
            published_at: Some("2026-07-19T10:00:00Z".into()),
            target_commitish: "main".into(),
            draft: false,
            prerelease,
            assets: vec![],
        }
    }

    fn release_with_assets(tag: &str, prerelease: bool, asset_names: &[&str]) -> GithubRelease {
        let mut result = release(tag, prerelease);
        result.assets = asset_names
            .iter()
            .enumerate()
            .map(|(index, name)| GithubAsset {
                id: index as u64 + 1,
                name: (*name).into(),
                browser_download_url: format!(
                    "https://github.com/BBGGVP5/nimbo/releases/download/{tag}/{name}"
                ),
                size: 1024,
                content_type: Some("application/octet-stream".into()),
                digest: Some(format!("sha256:{}", "11".repeat(32))),
                created_at: Some("2026-07-30T10:00:00Z".into()),
                updated_at: Some("2026-07-30T10:00:00Z".into()),
            })
            .collect();
        result
    }

    #[test]
    fn compares_semver_including_prereleases() {
        assert!(compare_versions("v1.2.0", "1.1.9").is_gt());
        assert!(compare_versions("1.2.0-beta.1", "1.2.0").is_lt());
        assert!(compare_versions("1.2.0", "1.2.0-beta.1").is_gt());
        assert!(compare_versions("1.0.0", "1.0.0").is_eq());
    }

    #[test]
    fn default_release_channel_is_bbgvp5_nimbo() {
        assert_eq!(
            DEFAULT_RELEASE_API_URL,
            "https://api.github.com/repos/BBGGVP5/nimbo/releases?per_page=20"
        );
    }

    #[test]
    fn selects_stable_and_beta_channels() {
        let releases = vec![
            release_with_assets("v1.3.0-beta.1", true, &["NimboSetup_1.3.0-beta.1_x64.exe"]),
            release_with_assets("v1.2.0", false, &["NimboSetup_1.2.0_x64.exe"]),
        ];
        assert_eq!(
            select_release_for_target(&releases, UpdateChannel::Stable, "windows", "x86_64")
                .unwrap()
                .tag_name,
            "v1.2.0"
        );
        assert_eq!(
            select_release_for_target(&releases, UpdateChannel::Beta, "windows", "x86_64")
                .unwrap()
                .tag_name,
            "v1.3.0-beta.1"
        );
    }

    #[test]
    fn desktop_release_notes_use_only_tagged_desktop_section() {
        let body = r#"
<!-- nimbo:android:start -->
## Android
- Новый экран APK.
<!-- nimbo:android:end -->
<!-- nimbo:desktop:start -->
## Windows и Linux
- Загрузка показывает процент и размер.
<!-- nimbo:desktop:end -->
"#;

        let notes = release_notes_for_desktop(body).unwrap();
        assert!(notes.contains("Windows и Linux"));
        assert!(notes.contains("процент и размер"));
        assert!(!notes.contains("Android"));
        assert!(!notes.contains("APK"));
    }

    #[test]
    fn desktop_release_notes_hide_legacy_html_tables_and_android_assets() {
        let body = r#"
<!-- versionCode: 5 -->
<div align="center"><img src="logo.png"></div>
| Платформа | Скачать |
|:--|:--|
| Android | [APK](https://example.test/Nimbo.apk) |
> [!IMPORTANT]
## Улучшения
- Исправлено фоновое обновление.
"#;

        assert_eq!(
            release_notes_for_desktop(body).as_deref(),
            Some("## Улучшения\n- Исправлено фоновое обновление.")
        );
    }

    #[test]
    fn skips_newer_releases_without_a_compatible_asset() {
        let releases = vec![
            release_with_assets(
                "v1.0.2",
                false,
                &[
                    "Nimbo_v1.0.2_arm64_v8a_release.apk",
                    "Nimbo_v1.0.2_universal_release.apk",
                ],
            ),
            release_with_assets("v1.0.1", false, &["NimboSetup_1.0.1_x64.exe"]),
        ];

        assert_eq!(
            select_release_for_target(&releases, UpdateChannel::Stable, "windows", "x86_64")
                .unwrap()
                .tag_name,
            "v1.0.1"
        );
    }

    #[test]
    fn android_assets_are_never_desktop_installers() {
        let release = release_with_assets(
            "v1.0.2",
            false,
            &[
                "Nimbo_v1.0.2_arm64_v8a_release.apk",
                "Nimbo_v1.0.2_armeabi_v7a_release.apk",
                "Nimbo_v1.0.2_universal_release.apk",
            ],
        );

        assert!(select_asset_for_target(&release.assets, "windows", "x86_64").is_none());
        assert!(select_asset_for_target(&release.assets, "macos", "aarch64").is_none());
        assert!(select_asset_for_target(&release.assets, "linux", "x86_64").is_none());
    }

    #[test]
    fn detects_same_version_reissue_only_with_a_prior_receipt() {
        assert_eq!(
            update_reason("1.2.0", "1.2.0", Some("old"), "new"),
            Some(UpdateReason::Reissued)
        );
        assert_eq!(update_reason("1.2.0", "1.2.0", Some("same"), "same"), None);
        assert_eq!(update_reason("1.2.0", "1.2.0", None, "new"), None);
    }

    #[test]
    fn records_a_baseline_for_a_fresh_or_manually_updated_install() {
        assert!(should_record_initial_fingerprint(
            "1.2.0", "1.2.0", None, None
        ));
        assert!(!should_record_initial_fingerprint(
            "1.2.0",
            "1.2.0",
            Some("known"),
            None
        ));
        assert!(!should_record_initial_fingerprint(
            "1.3.0",
            "1.2.0",
            None,
            Some(UpdateReason::NewVersion)
        ));
    }

    #[test]
    fn asset_upload_time_changes_the_fingerprint() {
        let make_asset = |updated_at: &str| GithubAsset {
            id: 42,
            name: "Nimbo_1.2.0_x64-setup.exe".into(),
            browser_download_url:
                "https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/Nimbo.exe".into(),
            size: 1024,
            content_type: Some("application/octet-stream".into()),
            digest: Some(format!("sha256:{}", "11".repeat(32))),
            created_at: Some("2026-07-19T10:00:00Z".into()),
            updated_at: Some(updated_at.into()),
        };
        let first = app_asset_from_github(&make_asset("2026-07-19T10:00:00Z"));
        let reuploaded = app_asset_from_github(&make_asset("2026-07-19T11:00:00Z"));
        assert_ne!(first.fingerprint, reuploaded.fingerprint);
    }

    #[test]
    fn verifies_sha256_digest() {
        let expected = format!("sha256:{}", sha256_hex(b"nimbo"));
        assert!(verify_sha256(b"nimbo", &expected).is_ok());
        assert!(verify_sha256(b"nimbo", "md5:bad").is_err());
        assert!(verify_sha256(b"nimbo", &format!("sha256:{}", "00".repeat(32))).is_err());
    }

    #[test]
    fn update_download_url_is_locked_to_project_releases() {
        assert!(validate_update_download_url(
            "https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/Nimbo.exe"
        )
        .is_ok());
        assert!(validate_update_download_url("https://example.com/Nimbo.exe").is_err());
        assert!(validate_update_download_url(
            "https://github.com/other/project/releases/download/v1/Nimbo.exe"
        )
        .is_err());
    }

    #[test]
    fn architecture_policy_rejects_incompatible_installers() {
        assert!(arch_score_for("x86_64", "nimbo_1.0.2_x64-setup.exe") > 0);
        assert!(arch_score_for("x86_64", "nimbo_1.0.2_x86-setup.exe") < 0);
        assert!(arch_score_for("x86_64", "nimbo_1.0.2_arm64-setup.exe") < 0);
        assert!(arch_score_for("aarch64", "nimbo_1.0.2_arm64-setup.exe") > 0);
        assert!(arch_score_for("aarch64", "nimbo_1.0.2_x64-setup.exe") < 0);
    }

    #[test]
    fn resumable_download_policy_validates_ranges_and_space() {
        assert_eq!(range_start(0, 1_000), None);
        assert_eq!(range_start(400, 1_000), Some(400));
        assert_eq!(range_start(1_000, 1_000), None);
        assert!(content_range_matches("bytes 400-999/1000", 400));
        assert!(!content_range_matches("bytes 0-999/1000", 400));
        assert_eq!(
            required_free_bytes(1_000, 400),
            600 + UPDATE_STORAGE_RESERVE_BYTES
        );
    }

    #[tokio::test]
    async fn resumes_partial_download_with_an_http_range_request() {
        let listener = tokio::net::TcpListener::bind(("127.0.0.1", 0))
            .await
            .unwrap();
        let address = listener.local_addr().unwrap();
        let server = tokio::spawn(async move {
            let (mut stream, _) = listener.accept().await.unwrap();
            let mut request = Vec::new();
            let mut buffer = [0u8; 1024];
            while !request.windows(4).any(|window| window == b"\r\n\r\n") {
                let count = stream.read(&mut buffer).await.unwrap();
                if count == 0 {
                    break;
                }
                request.extend_from_slice(&buffer[..count]);
            }
            let request = String::from_utf8_lossy(&request);
            assert!(
                request
                    .lines()
                    .any(|line| line.eq_ignore_ascii_case("range: bytes=4-")),
                "request did not contain the expected Range header: {request}"
            );
            stream
                .write_all(
                    b"HTTP/1.1 206 Partial Content\r\n\
                      Content-Length: 6\r\n\
                      Content-Range: bytes 4-9/10\r\n\
                      Connection: close\r\n\r\n\
                      efghij",
                )
                .await
                .unwrap();
        });

        let unique = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let target = std::env::temp_dir().join(format!(
            "nimbo-update-resume-{}-{unique}.part",
            std::process::id()
        ));
        std::fs::write(&target, b"abcd").unwrap();

        let result = download_asset_to_file_with_builder(
            reqwest::Client::builder().timeout(std::time::Duration::from_secs(5)),
            &format!("http://{address}/Nimbo.exe"),
            &target,
            10,
            &|_, _| {},
        )
        .await;
        server.await.unwrap();

        assert!(result.is_ok(), "{result:?}");
        assert_eq!(std::fs::read(&target).unwrap(), b"abcdefghij");
        std::fs::remove_file(target).unwrap();
    }

    #[test]
    fn update_receipt_changelog_fields_are_backward_compatible() {
        let old: UpdateReceipt = serde_json::from_value(serde_json::json!({
            "version": "1.0.1",
            "channel": "stable",
            "fingerprint": "asset",
            "digest": format!("sha256:{}", "11".repeat(32)),
            "asset_updated_at": "2026-07-24T10:00:00Z",
            "executable_path": "Nimbo.exe"
        }))
        .unwrap();
        assert!(!old.show_changelog);
        assert!(old.release_notes.is_none());

        let pending = UpdateReceipt {
            release_notes: Some("- Fixed reconnect".into()),
            release_url: Some("https://github.com/BBGGVP5/nimbo/releases/tag/v1.0.2".into()),
            show_changelog: true,
            ..old
        };
        let restored: UpdateReceipt =
            serde_json::from_value(serde_json::to_value(pending).unwrap()).unwrap();
        assert_eq!(restored.release_notes.as_deref(), Some("- Fixed reconnect"));
        assert!(restored.show_changelog);
    }

    #[test]
    fn bundled_changelog_is_versioned_and_localized() {
        let russian = bundled_release_notes("v1.0.2", crate::state::Language::Ru).unwrap();
        let english = bundled_release_notes("1.0.2", crate::state::Language::En).unwrap();

        assert!(russian.contains("Безопасные обновления"));
        assert!(russian.contains("SHA-256"));
        assert!(english.contains("Safer updates"));
        assert!(english.contains("SHA-256"));
        assert!(bundled_release_notes("9.9.9", crate::state::Language::Ru).is_none());
    }
}
