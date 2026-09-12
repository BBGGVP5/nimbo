fn main() {
    println!("cargo:rerun-if-changed=../dist");
    println!("cargo:rerun-if-changed=../dist/index.html");
    println!("cargo:rerun-if-changed=tauri.conf.json");
    println!("cargo:rerun-if-changed=tauri.windows.conf.json");
    println!("cargo:rerun-if-changed=tauri.linux.conf.json");
    prepare_awg();
    tauri_build::build()
}

fn prepare_awg() {
    use sha2::{Digest, Sha256};
    use std::path::Path;
    println!("cargo:rerun-if-env-changed=NIMBO_AWG_CORE_DIR");
    println!("cargo:rerun-if-changed=../scripts/build-awg.mjs");
    let repository_source = Path::new(&std::env::var_os("CARGO_MANIFEST_DIR").unwrap())
        .join("../../../tools/native/awg-core");
    // Observe creation of the local module as well as edits to the selected one.
    println!("cargo:rerun-if-changed={}", repository_source.display());
    let source = std::env::var_os("NIMBO_AWG_CORE_DIR")
        .filter(|value| !value.is_empty())
        .map(std::path::PathBuf::from)
        .or_else(|| repository_source.join("go.mod").is_file().then_some(repository_source))
        .or_else(|| {
            std::env::var_os(if cfg!(windows) { "USERPROFILE" } else { "HOME" }).map(|home| {
                std::path::PathBuf::from(home)
                    .join("AndroidStudioProjects/Nimbo/tools/native/awg-core")
            })
        });
    if let Some(source) = &source {
        println!("cargo:rerun-if-changed={}", source.display());
    }
    let target = std::env::var("TARGET").unwrap();
    let platform = match target.as_str() {
        "x86_64-pc-windows-msvc" => "windows-x64",
        "i686-pc-windows-msvc" => "windows-x86",
        "aarch64-pc-windows-msvc" => "windows-arm64",
        "x86_64-unknown-linux-gnu" => "linux-x64",
        "aarch64-unknown-linux-gnu" => "linux-arm64",
        _ => {
            println!("cargo:rustc-env=NIMBO_AWG_SHA256=");
            return;
        }
    };
    if std::env::var("PROFILE").as_deref() == Ok("release") {
        let status = std::process::Command::new("node")
            .args(["../scripts/build-awg.mjs", &target])
            .status()
            .expect("Node is required to build the AWG sidecar");
        assert!(
            status.success(),
            "AWG sidecar build failed; set NIMBO_AWG_CORE_DIR to the shared module"
        );
    }
    let name = if target.contains("windows") {
        "nimbo-awg.exe"
    } else {
        "nimbo-awg"
    };
    let binary = Path::new("resources/awg").join(platform).join(name);
    println!("cargo:rerun-if-changed={}", binary.display());
    let manifest = binary.with_file_name(format!("{name}.manifest.json"));
    println!("cargo:rerun-if-changed={}", manifest.display());
    if !binary.exists() {
        println!(
            "cargo:warning=AWG binary missing; run npm run build:awg before connecting to AWG"
        );
        println!("cargo:rustc-env=NIMBO_AWG_SHA256=");
        return;
    }
    let bytes = std::fs::read(&binary).expect("read AWG binary");
    let actual = format!("{:x}", Sha256::digest(bytes));
    let metadata: serde_json::Value =
        serde_json::from_slice(&std::fs::read(manifest).expect("AWG manifest missing"))
            .expect("invalid AWG manifest");
    assert_eq!(
        metadata["sha256"].as_str(),
        Some(actual.as_str()),
        "AWG manifest digest mismatch"
    );
    assert_eq!(
        metadata["version"].as_str(),
        Some("v3.1.20260828"),
        "AWG version mismatch"
    );
    assert_eq!(
        metadata["target"].as_str(),
        Some(target.as_str()),
        "AWG target mismatch"
    );
    println!("cargo:rustc-env=NIMBO_AWG_SHA256={actual}");
}
