pub fn prepare() {
    use sha2::{Digest, Sha256};
    use std::path::Path;
    println!("cargo:rerun-if-changed=helper_build.rs");
    let target = std::env::var("TARGET").unwrap();
    let machine = match target.as_str() {
        "x86_64-unknown-linux-gnu" => 62u16,
        "aarch64-unknown-linux-gnu" => 183u16,
        _ => return,
    };
    let binary = Path::new("resources/helper/linux/nimbo-svc");
    let manifest = binary.with_file_name("nimbo-svc.manifest.json");
    let archive = binary.with_file_name("nimbo-svc.zip");
    for path in [binary, &manifest, &archive] {
        println!("cargo:rerun-if-changed={}", path.display());
    }
    if !binary.is_file() || !manifest.is_file() {
        assert_ne!(std::env::var("PROFILE").as_deref(), Ok("release"), "Linux release helper missing; run scripts/build-linux.mjs --prepare-only --target {target}");
        println!("cargo:rustc-env=NIMBO_LINUX_HELPER_SHA256=");
        return;
    }
    let bytes = std::fs::read(binary).unwrap();
    assert!(
        archive.is_file(),
        "Linux helper archive missing; run build-linux.mjs --prepare-only"
    );
    assert!(bytes.len() >= 24 && &bytes[..4] == b"\x7fELF" && bytes[4] == 2 && bytes[5] == 1);
    assert_eq!(
        u16::from_le_bytes([bytes[18], bytes[19]]),
        machine,
        "wrong helper ELF target"
    );
    let metadata: serde_json::Value =
        serde_json::from_slice(&std::fs::read(manifest).unwrap()).unwrap();
    let digest = format!("{:x}", Sha256::digest(&bytes));
    assert_eq!(metadata["target"].as_str(), Some(target.as_str()));
    assert_eq!(
        metadata["version"].as_str(),
        Some(std::env::var("CARGO_PKG_VERSION").unwrap().as_str())
    );
    assert_eq!(
        metadata["sha256"].as_str(),
        Some(digest.as_str()),
        "Linux helper digest mismatch"
    );
    println!("cargo:rustc-env=NIMBO_LINUX_HELPER_SHA256={digest}");
}
