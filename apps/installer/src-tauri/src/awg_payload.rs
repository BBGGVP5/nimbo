//! Shared by build.rs and the installer: a custom package must contain the
//! exact pinned sidecar for its target, never a host-architecture fallback.
use sha2::{Digest, Sha256};

pub const VERSION: &str = "v3.1.20260828";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Target {
    pub platform: &'static str,
    pub filename: &'static str,
}

pub fn target_layout(triple: &str) -> Result<Target, String> {
    let (platform, filename) = match triple {
        "x86_64-pc-windows-msvc" => ("windows-x64", "nimbo-awg.exe"),
        "i686-pc-windows-msvc" => ("windows-x86", "nimbo-awg.exe"),
        "aarch64-pc-windows-msvc" => ("windows-arm64", "nimbo-awg.exe"),
        "x86_64-unknown-linux-gnu" => ("linux-x64", "nimbo-awg"),
        "aarch64-unknown-linux-gnu" => ("linux-arm64", "nimbo-awg"),
        _ => return Err(format!("Unsupported custom installer AWG target: {triple}")),
    };
    Ok(Target { platform, filename })
}

pub fn verify(binary: &[u8], manifest: &[u8], triple: &str) -> Result<(), String> {
    target_layout(triple)?;
    if binary.is_empty() {
        return Err("AWG payload is missing or empty".into());
    }
    let metadata: serde_json::Value =
        serde_json::from_slice(manifest).map_err(|_| "AWG manifest is invalid")?;
    if metadata["version"].as_str() != Some(VERSION) {
        return Err("AWG manifest version mismatch".into());
    }
    if metadata["target"].as_str() != Some(triple) {
        return Err("AWG manifest target mismatch".into());
    }
    let digest = format!("{:x}", Sha256::digest(binary));
    if metadata["sha256"].as_str() != Some(digest.as_str()) {
        return Err("AWG payload SHA-256 mismatch".into());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn manifest(binary: &[u8], target: &str) -> Vec<u8> {
        serde_json::to_vec(&serde_json::json!({
            "version": VERSION, "target": target,
            "sha256": format!("{:x}", Sha256::digest(binary)),
        }))
        .unwrap()
    }

    #[test]
    fn maps_all_custom_installer_targets_without_host_fallback() {
        for (triple, platform, filename) in [
            ("x86_64-pc-windows-msvc", "windows-x64", "nimbo-awg.exe"),
            ("i686-pc-windows-msvc", "windows-x86", "nimbo-awg.exe"),
            ("aarch64-pc-windows-msvc", "windows-arm64", "nimbo-awg.exe"),
            ("x86_64-unknown-linux-gnu", "linux-x64", "nimbo-awg"),
            ("aarch64-unknown-linux-gnu", "linux-arm64", "nimbo-awg"),
        ] {
            assert_eq!(
                target_layout(triple).unwrap(),
                Target { platform, filename }
            );
            verify(b"fixture", &manifest(b"fixture", triple), triple).unwrap();
        }
        assert!(target_layout("unknown-target").is_err());
    }

    #[test]
    fn rejects_corruption_wrong_target_version_and_missing_files() {
        let target = "x86_64-pc-windows-msvc";
        let good = manifest(b"fixture", target);
        assert!(verify(b"modified", &good, target).is_err());
        assert!(verify(b"", &good, target).is_err());
        assert!(verify(b"fixture", b"", target).is_err());
        assert!(verify(b"fixture", b"{}", target).is_err());
        assert!(verify(b"fixture", &good, "i686-pc-windows-msvc").is_err());
        let mut metadata: serde_json::Value = serde_json::from_slice(&good).unwrap();
        metadata["version"] = "old".into();
        assert!(verify(b"fixture", &serde_json::to_vec(&metadata).unwrap(), target).is_err());
    }
}
