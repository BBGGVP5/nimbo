//! Reviewed release pins; floating GitHub latest is intentionally never used.
use serde::Deserialize;
use sha2::{Digest, Sha256};
use std::{collections::HashMap, path::{Path, PathBuf}, sync::OnceLock};

#[derive(Deserialize)]
pub(crate) struct Release {
    pub version: String,
    pub tag: String,
    pub assets: Vec<Asset>,
}

#[derive(Deserialize)]
pub(crate) struct Asset {
    pub os: String,
    pub arch: String,
    pub archive: String,
    pub sha256: String,
    pub files: HashMap<String, String>,
}

pub(crate) fn release() -> &'static Release {
    static RELEASE: OnceLock<Release> = OnceLock::new();
    RELEASE.get_or_init(|| serde_json::from_str(include_str!("../xray-release.json")).expect("reviewed Xray pins"))
}

pub(crate) fn asset(os: &str, arch: &str) -> Option<&'static Asset> {
    release().assets.iter().find(|asset| asset.os == os && asset.arch == arch)
}

pub(crate) fn current() -> Result<&'static Asset, String> {
    asset(std::env::consts::OS, std::env::consts::ARCH).ok_or_else(|| "Unsupported Xray platform".into())
}

impl Asset {
    pub fn select_runtime(&self, custom: Option<&Path>, candidates: &[PathBuf]) -> Result<Option<PathBuf>, String> {
        if let Some(path) = custom {
            self.verify_runtime(path).map_err(|error| format!("NIMBO_XRAY_PATH: {error}; the custom path was not modified. Supply the pinned runtime or remove the override to use automatic download."))?;
            return Ok(Some(path.to_path_buf()));
        }
        Ok(candidates.iter().find(|path| self.verify_runtime(path).is_ok()).cloned())
    }
    pub fn url(&self) -> String {
        format!("https://github.com/XTLS/Xray-core/releases/download/{}/{}", release().tag, self.archive)
    }
    pub fn verify_archive(&self, bytes: &[u8]) -> Result<(), String> {
        verify(bytes, &self.sha256)
    }
    pub fn verify_file(&self, name: &str, bytes: &[u8]) -> Result<(), String> {
        verify(bytes, self.files.get(name).ok_or("Unpinned Xray file")?)
    }
    pub fn verify_runtime(&self, path: &Path) -> Result<(), String> {
        let name = if self.os == "windows" { "xray.exe" } else { "xray" };
        let bytes = std::fs::read(path).map_err(|_| "Xray runtime is missing")?;
        self.verify_file(name, &bytes)?;
        // Match accompanying data to the same reviewed archive as well.
        for name in ["geoip.dat", "geosite.dat"] {
            let bytes = std::fs::read(path.with_file_name(name)).map_err(|_| "Xray data is missing")?;
            self.verify_file(name, &bytes)?;
        }
        Ok(())
    }
}

fn verify(bytes: &[u8], expected: &str) -> Result<(), String> {
    if format!("{:x}", Sha256::digest(bytes)) == expected { Ok(()) }
    else { Err(format!("Xray {} SHA-256 mismatch", release().version)) }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn xray_pins_cover_all_supported_architectures_without_latest() {
        assert_eq!(release().version, "26.9.9");
        for os in ["windows", "linux"] {
            for arch in ["x86", "x86_64", "aarch64"] {
                let asset = asset(os, arch).unwrap();
                assert!(asset.url().contains("/v26.9.9/"));
                assert!(!asset.url().contains("latest"));
                assert_eq!(asset.sha256.len(), 64);
                assert_eq!(asset.files.len(), 3);
            }
        }
        assert!(asset("windows", "arm").is_none());
        assert!(asset("macos", "x86_64").is_none());
    }

    #[test]
    fn xray_rejects_old_or_tampered_archives_and_binaries() {
        for asset in &release().assets {
            assert!(asset.verify_archive(b"old stable 26.3.27").is_err());
            for name in asset.files.keys() { assert!(asset.verify_file(name, b"tampered").is_err()); }
            assert!(asset.verify_file("unlisted.exe", b"anything").is_err());
        }
    }

    #[test]
    fn xray_existing_default_cache_refreshes_but_custom_override_is_never_replaced() {
        let root = std::env::temp_dir().join(format!("nimbo-core-policy-{}", uuid::Uuid::new_v4()));
        let old = root.join("old");
        let new = root.join("new");
        std::fs::create_dir_all(&old).unwrap();
        std::fs::create_dir_all(&new).unwrap();
        let bytes = b"reviewed test binary";
        let hash = format!("{:x}", Sha256::digest(bytes));
        let asset = Asset { os: "windows".into(), arch: "x86_64".into(), archive: "test.zip".into(), sha256: hash.clone(),
            files: ["xray.exe", "geoip.dat", "geosite.dat"].into_iter().map(|n| (n.into(), hash.clone())).collect() };
        for name in asset.files.keys() {
            std::fs::write(old.join(name), b"old 26.3.27").unwrap();
            std::fs::write(new.join(name), bytes).unwrap();
        }
        let old_exe = old.join("xray.exe");
        let new_exe = new.join("xray.exe");
        assert_eq!(asset.select_runtime(None, std::slice::from_ref(&old_exe)).unwrap(), None); // caller must download
        assert_eq!(asset.select_runtime(None, &[old_exe.clone(), new_exe.clone()]).unwrap(), Some(new_exe.clone())); // fresh bundle accepted
        assert!(asset.select_runtime(Some(&old_exe), &[new_exe.clone()]).is_err());
        assert_eq!(std::fs::read(&old_exe).unwrap(), b"old 26.3.27");
        assert_eq!(asset.select_runtime(Some(&new_exe), &[]).unwrap(), Some(new_exe));
        std::fs::remove_dir_all(root).unwrap();
    }
}
