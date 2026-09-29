#[path = "src/awg_payload.rs"]
mod awg_payload;

fn main() {
    println!("cargo:rerun-if-changed=windows-app-manifest.xml");
    println!("cargo:rerun-if-env-changed=TARGET");

    let target = std::env::var("TARGET").expect("TARGET env var is required");
    println!("cargo:rustc-env=NIMBO_TARGET_TRIPLE={target}");
    prepare_awg(&target);

    let windows = tauri_build::WindowsAttributes::new()
        .app_manifest(include_str!("windows-app-manifest.xml"));
    let attrs = tauri_build::Attributes::new().windows_attributes(windows);

    tauri_build::try_build(attrs).expect("failed to run Nimbo Setup build script");
}

fn prepare_awg(target: &str) {
    use std::path::PathBuf;
    let layout = awg_payload::target_layout(target).expect("unsupported AWG installer target");
    let root = PathBuf::from(std::env::var_os("CARGO_MANIFEST_DIR").unwrap())
        .join("../../ui/src-tauri/resources/awg")
        .join(layout.platform);
    let binary = root.join(layout.filename);
    let manifest = root.join(format!("{}.manifest.json", layout.filename));
    println!("cargo:rerun-if-changed=src/awg_payload.rs");
    for path in [&binary, &manifest] {
        println!("cargo:rerun-if-changed={}", path.display());
    }
    let bytes = std::fs::read(&binary).unwrap_or_else(|error| {
        panic!(
            "Missing AWG payload {}: {error}; build the matching Nimbo UI payload first",
            binary.display()
        )
    });
    let metadata = std::fs::read(&manifest).expect("AWG payload manifest is required");
    awg_payload::verify(&bytes, &metadata, target).expect("Invalid AWG custom installer payload");
    println!(
        "cargo:rustc-env=NIMBO_INSTALLER_AWG_BINARY={}",
        binary.display()
    );
    println!(
        "cargo:rustc-env=NIMBO_INSTALLER_AWG_MANIFEST={}",
        manifest.display()
    );
}
