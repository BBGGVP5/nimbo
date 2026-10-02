//! Only manifest-listed source/notices are embedded, never caches or stage receipts.
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeMap,
    path::{Component, Path, PathBuf},
};

fn checked_path(root: &Path, relative: &str) -> PathBuf {
    assert!(
        !relative.is_empty() && !relative.contains('\\') && !relative.contains(':'),
        "unsafe resource path"
    );
    let path = Path::new(relative);
    assert!(
        path.components()
            .all(|part| matches!(part, Component::Normal(_))),
        "unsafe resource path"
    );
    let path = root
        .join(path)
        .canonicalize()
        .expect("missing Mihomo resource");
    assert!(
        path.starts_with(root.canonicalize().unwrap()),
        "resource escapes Mihomo staging root"
    );
    path
}

fn json(root: &Path, relative: &str) -> Value {
    let data = std::fs::read(checked_path(root, relative)).unwrap();
    serde_json::from_slice(data.strip_prefix(&[0xef, 0xbb, 0xbf]).unwrap_or(&data)).unwrap()
}

pub fn prepare(target: &str) {
    println!("cargo:rerun-if-changed=mihomo_bundle.rs");
    let mut generated = String::from("const MIHOMO_FILES: &[(&str, &[u8], &str)] = &[\n");
    if target == "x86_64-pc-windows-msvc" {
        let root =
            Path::new(env!("CARGO_MANIFEST_DIR")).join("../../ui/src-tauri/resources/mihomo");
        let manifest = json(&root, "windows-x64/build-manifest.json");
        assert_eq!(manifest["apiVersion"], 1);
        assert_eq!(manifest["coreVersion"], "v1.19.31");
        assert_eq!(
            manifest["coreCommit"],
            "ab405bad5beeeac8b003bb01f60f134f6df54471"
        );
        assert_eq!(manifest["target"], "windows/amd64");
        let mut files = BTreeMap::<String, Option<String>>::new();
        files.insert("windows-x64/build-manifest.json".into(), None);
        for (name, field) in [
            ("nimbo-mihomo.exe", "sha256"),
            ("go.mod", "goModSHA256"),
            ("go.sum", "goSumSHA256"),
            ("pins.json", "pinsSHA256"),
        ] {
            files.insert(
                format!("windows-x64/{name}"),
                Some(manifest[field].as_str().unwrap().into()),
            );
        }
        let sources = manifest["sourceFiles"]
            .as_array()
            .expect("missing frozen sources");
        assert!(!sources.is_empty());
        for source in sources {
            files.insert(
                format!("adapter-source/{}", source["path"].as_str().unwrap()),
                Some(source["sha256"].as_str().unwrap().into()),
            );
        }
        files.insert(
            "notices/source-license-manifest.json".into(),
            Some(
                manifest["sourceLicenseManifestSHA256"]
                    .as_str()
                    .unwrap()
                    .into(),
            ),
        );
        let inventory = json(&root, "notices/source-license-manifest.json");
        for field in ["goModSHA256", "goSumSHA256", "pinsSHA256"] {
            assert_eq!(
                inventory[field], manifest[field],
                "license lock differs from helper"
            );
        }
        for module in inventory["modules"].as_array().unwrap() {
            for notice in module["notices"].as_array().unwrap() {
                let path = notice["path"]
                    .as_str()
                    .unwrap()
                    .strip_prefix("licenses/")
                    .expect("unexpected notice root");
                files.insert(
                    format!("notices/{path}"),
                    Some(notice["sha256"].as_str().unwrap().into()),
                );
            }
        }
        for name in [
            "Mihomo-LICENSE",
            "Mihomo-README.md",
            "Protobuf-LICENSE",
            "Protobuf-PATENTS",
            "protobuf-directive.patch",
        ] {
            files.insert(format!("notices/{name}"), None);
        }
        for (relative, expected) in files {
            let path = checked_path(&root, &relative);
            println!("cargo:rerun-if-changed={}", path.display());
            let digest = format!("{:x}", Sha256::digest(std::fs::read(&path).unwrap()));
            if let Some(expected) = expected {
                assert_eq!(
                    digest, expected,
                    "Mihomo resource hash mismatch: {relative}"
                );
            }
            generated.push_str(&format!(
                "({relative:?}, include_bytes!({:?}), {digest:?}),\n",
                path.to_str().unwrap()
            ));
        }
    }
    generated.push_str("];\n");
    let output = PathBuf::from(std::env::var_os("OUT_DIR").unwrap()).join("mihomo_files.rs");
    std::fs::write(output, generated).unwrap();
}
