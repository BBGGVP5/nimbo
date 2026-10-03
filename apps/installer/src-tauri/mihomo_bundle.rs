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
    let platform = match target {
        "x86_64-pc-windows-msvc" => Some("windows-x64"),
        "x86_64-unknown-linux-gnu" => Some("linux-x64"),
        "aarch64-unknown-linux-gnu" => Some("linux-arm64"),
        _ => None,
    };
    if let Some(platform) = platform {
        let root =
            Path::new(env!("CARGO_MANIFEST_DIR")).join("../../ui/src-tauri/resources/mihomo");
        let manifest = json(&root, &format!("{platform}/build-manifest.json"));
        assert_eq!(manifest["apiVersion"], 1);
        assert_eq!(manifest["coreVersion"], "v1.19.31");
        assert_eq!(
            manifest["coreCommit"],
            "ab405bad5beeeac8b003bb01f60f134f6df54471"
        );
        assert_eq!(
            manifest["target"],
            match platform {
                "linux-x64" => "linux/amd64",
                "linux-arm64" => "linux/arm64",
                _ => "windows/amd64",
            }
        );
        let prefix = if platform == "windows-x64" {
            String::new()
        } else {
            format!("{platform}/")
        };
        let mut files = BTreeMap::<String, Option<String>>::new();
        files.insert(format!("{platform}/build-manifest.json"), None);
        for (name, field) in [
            (
                if platform == "windows-x64" {
                    "nimbo-mihomo.exe"
                } else {
                    "nimbo-mihomo.zip"
                },
                if platform == "windows-x64" {
                    "sha256"
                } else {
                    "archiveSHA256"
                },
            ),
            ("go.mod", "goModSHA256"),
            ("go.sum", "goSumSHA256"),
            ("pins.json", "pinsSHA256"),
        ] {
            files.insert(
                format!("{platform}/{name}"),
                Some(manifest[field].as_str().unwrap().into()),
            );
        }
        let sources = manifest["sourceFiles"]
            .as_array()
            .expect("missing frozen sources");
        assert!(!sources.is_empty());
        for source in sources {
            files.insert(
                format!(
                    "{prefix}adapter-source/{}",
                    source["path"].as_str().unwrap()
                ),
                Some(source["sha256"].as_str().unwrap().into()),
            );
        }
        files.insert(
            format!("{prefix}notices/source-license-manifest.json"),
            Some(
                manifest["sourceLicenseManifestSHA256"]
                    .as_str()
                    .unwrap()
                    .into(),
            ),
        );
        let inventory = json(
            &root,
            &format!("{prefix}notices/source-license-manifest.json"),
        );
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
                    .or_else(|| notice["path"].as_str().unwrap().strip_prefix("notices/"))
                    .expect("unexpected notice root");
                files.insert(
                    format!("{prefix}notices/{path}"),
                    Some(notice["sha256"].as_str().unwrap().into()),
                );
            }
        }
        if platform == "windows-x64" {
            for name in [
                "Mihomo-LICENSE",
                "Mihomo-README.md",
                "Protobuf-LICENSE",
                "Protobuf-PATENTS",
                "protobuf-directive.patch",
            ] {
                files.insert(format!("notices/{name}"), None);
            }
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
