use std::path::PathBuf;
use std::sync::OnceLock;

use serde::{Deserialize, Serialize};

mod platform;

const HWID_FILE: &str = "hwid.txt";
const APP_NAME: &str = "Nimbo";
const APP_VERSION: &str = env!("CARGO_PKG_VERSION");

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceInfo {
    pub hwid: String,
    pub os: String,
    pub os_version: String,
    pub hostname: String,
    pub user_agent: String,
}

pub fn device_info() -> DeviceInfo {
    static CACHE: OnceLock<DeviceInfo> = OnceLock::new();
    CACHE.get_or_init(build_device_info).clone()
}

pub fn hwid() -> String {
    device_info().hwid
}

fn build_device_info() -> DeviceInfo {
    let hwid = resolve_hwid();
    let os = platform::os_name();
    let os_version = platform::os_version();
    let hostname = platform::hostname();
    let user_agent = build_user_agent(APP_NAME, APP_VERSION, os);

    DeviceInfo {
        hwid,
        os: os.into(),
        os_version,
        hostname,
        user_agent,
    }
}

fn build_user_agent(app_name: &str, app_version: &str, os: &str) -> String {
    format!("{app_name}/{app_version}/{os}")
}

fn resolve_hwid() -> String {
    if let Some(cached) = read_cache() {
        if is_valid(&cached) {
            return cached;
        }
    }

    let id = platform::machine_guid()
        .map(|g| normalize(&g))
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());

    if let Err(e) = write_cache(&id) {
        tracing::warn!(?e, "failed to cache hwid");
    }

    id
}

fn cache_path() -> Option<PathBuf> {
    dirs::data_dir().map(|d| d.join(APP_NAME).join(HWID_FILE))
}

fn read_cache() -> Option<String> {
    let path = cache_path()?;
    let raw = std::fs::read_to_string(&path).ok()?;
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        None
    } else {
        Some(trimmed.to_string())
    }
}

fn write_cache(value: &str) -> std::io::Result<()> {
    let Some(path) = cache_path() else {
        return Ok(());
    };
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(&path, value)
}

fn normalize(raw: &str) -> String {
    raw.trim().trim_matches('{').trim_matches('}').to_lowercase()
}

fn is_valid(s: &str) -> bool {
    !s.is_empty() && s.len() <= 128 && s.chars().all(|c| c.is_ascii_graphic())
}

pub fn reset_cache() -> std::io::Result<()> {
    if let Some(path) = cache_path() {
        if path.exists() {
            std::fs::remove_file(path)?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalize_strips_braces_and_lowercases() {
        assert_eq!(
            normalize("{1AB2-CDEF}"),
            "1ab2-cdef"
        );
    }

    #[test]
    fn validity_rejects_garbage() {
        assert!(!is_valid(""));
        assert!(!is_valid(&"x".repeat(200)));
        assert!(!is_valid("with spaces"));
        assert!(is_valid("01234567-89ab-cdef-0123-456789abcdef"));
    }

    #[test]
    fn device_info_returns_consistent_values() {
        let a = device_info();
        let b = device_info();
        assert_eq!(a.hwid, b.hwid);
        assert!(!a.hwid.is_empty());
        assert!(!a.user_agent.is_empty());
        assert!(a.user_agent.starts_with("Nimbo/"));
    }

    #[test]
    fn user_agent_uses_name_version_platform_format() {
        assert_eq!(build_user_agent("Nimbo", "0.1.0", "Windows"), "Nimbo/0.1.0/Windows");
    }
}
