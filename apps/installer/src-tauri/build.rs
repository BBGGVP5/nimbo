fn main() {
    println!("cargo:rerun-if-changed=windows-app-manifest.xml");
    println!("cargo:rerun-if-env-changed=TARGET");

    let target = std::env::var("TARGET").expect("TARGET env var is required");
    println!("cargo:rustc-env=NIMBO_TARGET_TRIPLE={target}");

    let windows = tauri_build::WindowsAttributes::new()
        .app_manifest(include_str!("windows-app-manifest.xml"));
    let attrs = tauri_build::Attributes::new().windows_attributes(windows);

    tauri_build::try_build(attrs).expect("failed to run Nimbo Setup build script");
}
