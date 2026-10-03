use sha2::{Digest, Sha256};
use std::path::{Component, Path, PathBuf};
fn hash(path: &Path) -> String {
    println!("cargo:rerun-if-changed={}", path.display());
    format!(
        "{:x}",
        Sha256::digest(std::fs::read(path).expect("Mihomo staged file"))
    )
}
fn main() {
    let target = std::env::var("TARGET").unwrap();
    let platform = match target.as_str() {
        "x86_64-unknown-linux-gnu" => "linux-x64",
        "aarch64-unknown-linux-gnu" => "linux-arm64",
        _ => {
            println!("cargo:rustc-env=NIMBO_SERVICE_MIHOMO_SHA256=");
            return;
        }
    };
    let root = PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").unwrap())
        .join("../ui/src-tauri/resources/mihomo")
        .join(platform);
    let manifest = root.join("build-manifest.json");
    let binary = root.join("nimbo-mihomo");
    println!("cargo:rerun-if-changed={}", manifest.display());
    println!("cargo:rerun-if-changed={}", binary.display());
    if !manifest.is_file() || !binary.is_file() {
        // Existing Xray helper remains usable, but new root operations fail closed.
        println!("cargo:rustc-env=NIMBO_SERVICE_MIHOMO_SHA256=");
        return;
    }
    let m: serde_json::Value = serde_json::from_slice(&std::fs::read(manifest).unwrap()).unwrap();
    assert_eq!(m["apiVersion"], 1);
    assert_eq!(m["coreVersion"], "v1.19.31");
    assert_eq!(m["coreCommit"], "ab405bad5beeeac8b003bb01f60f134f6df54471");
    assert_eq!(
        m["target"],
        if platform == "linux-x64" {
            "linux/amd64"
        } else {
            "linux/arm64"
        }
    );
    assert!(m["toolchain"]
        .as_str()
        .is_some_and(|s| s.contains("go1.27.1")));
    let sources = m["sourceFiles"].as_array().expect("frozen source manifest");
    assert!(!sources.is_empty());
    for entry in sources {
        let path = Path::new(entry["path"].as_str().unwrap());
        assert!(path.components().all(|c| matches!(c, Component::Normal(_))));
        assert_eq!(
            entry["sha256"].as_str().unwrap(),
            hash(&root.join("adapter-source").join(path))
        );
    }
    assert_eq!(
        m["sourceLicenseManifestSHA256"].as_str().unwrap(),
        hash(&root.join("notices/source-license-manifest.json"))
    );
    let hash = hash(&binary);
    assert_eq!(m["sha256"].as_str(), Some(hash.as_str()));
    println!("cargo:rustc-env=NIMBO_SERVICE_MIHOMO_SHA256={hash}");
}
