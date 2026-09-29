use sha2::{Digest, Sha256};
use std::path::Path;
pub fn prepare() {
    println!("cargo:rerun-if-changed=mihomo_build.rs");
    let target = std::env::var("TARGET").unwrap();
    // Other architectures remain unavailable until the shared source helper has
    // built and recorded their own artifact; never reuse Windows x64 elsewhere.
    let platform = match target.as_str() {
        "x86_64-pc-windows-msvc" => "windows-x64",
        _ => {
            println!("cargo:rustc-env=NIMBO_MIHOMO_SHA256=");
            println!("cargo:rustc-env=NIMBO_MIHOMO_PLATFORM=");
            return;
        }
    };
    println!("cargo:rustc-env=NIMBO_MIHOMO_PLATFORM={platform}");
    let dir = Path::new("resources/mihomo").join(platform);
    let binary = dir.join("nimbo-mihomo.exe");
    let manifest = dir.join("build-manifest.json");
    for p in [&binary, &manifest] {
        println!("cargo:rerun-if-changed={}", p.display());
    }
    if !binary.is_file() || !manifest.is_file() {
        if std::env::var("PROFILE").as_deref() == Ok("release") {
            panic!("Mihomo source-built helper missing; run native/stage-mihomo.ps1 against the verified shared module before final release build");
        }
        println!("cargo:warning=Mihomo unavailable: no staged source-built helper");
        println!("cargo:rustc-env=NIMBO_MIHOMO_SHA256=");
        return;
    }
    let manifest_bytes = std::fs::read(&manifest).expect("read Mihomo manifest");
    let bytes = manifest_bytes
        .strip_prefix(&[0xef, 0xbb, 0xbf])
        .unwrap_or(&manifest_bytes);
    let metadata: serde_json::Value =
        serde_json::from_slice(bytes).expect("invalid Mihomo manifest");
    if std::env::var("PROFILE").as_deref() == Ok("release") {
        assert!(
            metadata["sourceFiles"]
                .as_array()
                .is_some_and(|v| !v.is_empty()),
            "Mihomo release requires build-time adapter-source manifest"
        );
        for entry in metadata["sourceFiles"].as_array().unwrap() {
            let relative = Path::new(entry["path"].as_str().expect("missing source path"));
            assert!(
                relative
                    .components()
                    .all(|c| matches!(c, std::path::Component::Normal(_))),
                "unsafe Mihomo source manifest path"
            );
            let path = Path::new("resources/mihomo/adapter-source").join(relative);
            println!("cargo:rerun-if-changed={}", path.display());
            let hash = format!(
                "{:x}",
                Sha256::digest(std::fs::read(&path).expect("frozen Mihomo source missing"))
            );
            assert_eq!(
                entry["sha256"].as_str(),
                Some(hash.as_str()),
                "frozen Mihomo source differs from built revision"
            );
        }
        let licenses = std::fs::read("resources/mihomo/notices/source-license-manifest.json")
            .expect("Mihomo transitive notices missing");
        let hash = format!("{:x}", Sha256::digest(licenses));
        assert_eq!(
            metadata["sourceLicenseManifestSHA256"].as_str(),
            Some(hash.as_str()),
            "Mihomo license inventory mismatch"
        );
    }
    let binary_bytes = std::fs::read(binary).expect("read Mihomo helper");
    let actual = format!("{:x}", Sha256::digest(binary_bytes));
    assert_eq!(
        metadata["apiVersion"].as_u64(),
        Some(1),
        "Mihomo wire mismatch"
    );
    assert_eq!(
        metadata["coreVersion"].as_str(),
        Some("v1.19.31"),
        "Mihomo pin mismatch"
    );
    assert_eq!(
        metadata["coreCommit"].as_str(),
        Some("ab405bad5beeeac8b003bb01f60f134f6df54471"),
        "Mihomo commit mismatch"
    );
    assert_eq!(
        metadata["target"].as_str(),
        Some("windows/amd64"),
        "Mihomo target mismatch"
    );
    assert!(
        metadata["toolchain"]
            .as_str()
            .is_some_and(|s| s.contains("go1.27.1")),
        "Mihomo toolchain mismatch"
    );
    assert_eq!(
        metadata["sha256"].as_str(),
        Some(actual.as_str()),
        "Mihomo binary hash mismatch"
    );
    println!("cargo:rustc-env=NIMBO_MIHOMO_SHA256={actual}");
}
