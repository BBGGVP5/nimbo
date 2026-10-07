use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;

pub const MAX_SOURCE_BYTES: usize = 4 * 1024 * 1024;
pub const WIRE_API: u32 = 1;
pub const CORE_VERSION: &str = "v1.19.32";
pub const CORE_COMMIT: &str = "88dcbf7f1614a67c3b36b848ee3592dfa92ada36";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum CoreKind {
    Xray,
    Awg,
    Mihomo,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ProfileKind {
    XrayJson,
    AwgIni,
    MihomoYaml,
}
impl ProfileKind {
    pub fn required_core(self) -> CoreKind {
        match self {
            Self::XrayJson => CoreKind::Xray,
            Self::AwgIni => CoreKind::Awg,
            Self::MihomoYaml => CoreKind::Mihomo,
        }
    }
}

/// Contains secrets by design: deliberately no Debug and never returned by list/status.
/// Exact source bytes are exported only by an explicit export command.
#[derive(Clone, Serialize, Deserialize)]
pub struct FullProfile {
    pub id: String,
    pub name: String,
    pub kind: ProfileKind,
    pub original_text: String,
    pub source_digest: String,
    pub revision: u64,
    #[serde(default)]
    pub selections: BTreeMap<String, String>,
    /// Native projection is non-authoritative and bound to source_digest.
    #[serde(default)]
    pub inspection: Option<Inspection>,
}

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Inspection {
    pub api: u32,
    pub source_digest: String,
    #[serde(default)]
    pub graph: serde_json::Value,
    #[serde(default)]
    pub issues: Vec<FieldIssue>,
    #[serde(default)]
    pub native_validated: bool,
}

impl std::fmt::Debug for Inspection {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Inspection")
            .field("api", &self.api)
            .field("issue_count", &self.issues.len())
            .field("native_validated", &self.native_validated)
            .finish_non_exhaustive()
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FieldIssue {
    pub code: String,
    pub path: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProfileSummary {
    pub id: String,
    pub name: String,
    pub kind: ProfileKind,
    pub source_digest: String,
    pub revision: u64,
    pub selections: BTreeMap<String, String>,
    pub inspection: Option<Inspection>,
}

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct CoreProfiles {
    /// None preserves legacy dispatch. An explicit preference never coerces formats.
    #[serde(with = "crate::selection::persisted")]
    pub preferred_core: Option<CoreKind>,
    pub profiles: Vec<FullProfile>,
    pub active_profile_id: Option<String>,
}

impl std::fmt::Debug for CoreProfiles {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("CoreProfiles")
            .field("preferred_core", &self.preferred_core)
            .field("profile_count", &self.profiles.len())
            .finish_non_exhaustive()
    }
}

pub fn source_digest(source: &str) -> String {
    format!("{:x}", Sha256::digest(source.as_bytes()))
}

impl FullProfile {
    pub fn new(name: String, kind: ProfileKind, source: String) -> Result<Self, &'static str> {
        check_source(&source)?;
        let name = name.trim().to_owned();
        if name.is_empty() || name.len() > 512 {
            return Err("INVALID_PROFILE_NAME");
        }
        Ok(Self {
            id: uuid::Uuid::new_v4().to_string(),
            name,
            kind,
            source_digest: source_digest(&source),
            original_text: source,
            revision: 1,
            selections: BTreeMap::new(),
            inspection: None,
        })
    }
    pub fn verify(&self) -> Result<(), &'static str> {
        if uuid::Uuid::parse_str(&self.id).is_err() || self.revision == 0 {
            return Err("INVALID_PROFILE_ID");
        }
        check_source(&self.original_text)?;
        if self.source_digest != source_digest(&self.original_text) {
            return Err("SOURCE_DIGEST_MISMATCH");
        }
        if let Some(i) = &self.inspection {
            if i.api != WIRE_API || i.source_digest != self.source_digest {
                return Err("STALE_INSPECTION");
            }
        }
        Ok(())
    }
    pub fn attach_inspection(&mut self, inspection: Inspection) -> Result<(), &'static str> {
        if inspection.api != WIRE_API {
            return Err("UNSUPPORTED_WIRE_VERSION");
        }
        if inspection.source_digest != self.source_digest {
            return Err("STALE_INSPECTION");
        }
        self.inspection = Some(inspection);
        Ok(())
    }
    pub fn replace_source(&mut self, revision: u64, source: String) -> Result<(), &'static str> {
        if revision != self.revision {
            return Err("STALE_REVISION");
        }
        check_source(&source)?;
        let next = self.revision.checked_add(1).ok_or("REVISION_OVERFLOW")?;
        self.source_digest = source_digest(&source);
        self.original_text = source;
        self.revision = next;
        self.inspection = None;
        // Names may have changed. Never restore a stale choice into a new document.
        self.selections.clear();
        Ok(())
    }
    pub fn summary(&self) -> ProfileSummary {
        let mut inspection = self.inspection.clone();
        if let Some(i) = &mut inspection {
            i.graph = display_graph(&i.graph);
        }
        ProfileSummary {
            id: self.id.clone(),
            name: self.name.clone(),
            kind: self.kind,
            source_digest: self.source_digest.clone(),
            revision: self.revision,
            selections: self.selections.clone(),
            inspection,
        }
    }
}

/// Native declared mappings contain credentials. Category metadata is a separate,
/// allowlisted projection; full native mappings remain stored, never logged/listed.
fn display_graph(graph: &serde_json::Value) -> serde_json::Value {
    fn entry(v: &serde_json::Value) -> serde_json::Value {
        let mut out = serde_json::Map::new();
        for key in [
            "name",
            "type",
            "proxies",
            "use",
            "hidden",
            "include-all",
            "include-all-proxies",
            "include-all-providers",
            "filter",
            "exclude-filter",
            "exclude-type",
        ] {
            if let Some(value) = v.get(key) {
                out.insert(key.into(), value.clone());
            }
        }
        serde_json::Value::Object(out)
    }
    let array = |key: &str| {
        graph
            .get(key)
            .and_then(serde_json::Value::as_array)
            .map(|a| a.iter().map(entry).collect::<Vec<_>>())
            .unwrap_or_default()
    };
    let providers = graph
        .get("providers")
        .and_then(serde_json::Value::as_object)
        .map(|m| {
            m.iter()
                .map(|(k, v)| (k.clone(), entry(v)))
                .collect::<serde_json::Map<_, _>>()
        })
        .unwrap_or_default();
    serde_json::json!({"proxies":array("proxies"),"groups":array("groups"),"providers":providers})
}
pub fn check_source(source: &str) -> Result<(), &'static str> {
    if source.len() > MAX_SOURCE_BYTES {
        return Err("PROFILE_TOO_LARGE");
    }
    if source.trim_start_matches('\u{feff}').trim().is_empty() {
        return Err("EMPTY_PROFILE");
    }
    if source.contains('\0') {
        return Err("INVALID_PROFILE_ENCODING");
    }
    Ok(())
}
impl CoreProfiles {
    pub fn profile(&self, id: &str) -> Result<&FullProfile, &'static str> {
        self.profiles
            .iter()
            .find(|p| p.id == id)
            .ok_or("PROFILE_NOT_FOUND")
    }
    pub fn profile_mut(&mut self, id: &str) -> Result<&mut FullProfile, &'static str> {
        self.profiles
            .iter_mut()
            .find(|p| p.id == id)
            .ok_or("PROFILE_NOT_FOUND")
    }
    pub fn ensure_compatible(&self, id: &str) -> Result<CoreKind, &'static str> {
        let p = self.profile(id)?;
        p.verify()?;
        let required = p.kind.required_core();
        if self.preferred_core.is_some_and(|c| c != required) {
            return Err("UNSUPPORTED_CORE");
        }
        Ok(required)
    }
}

/// Routing hint only, not a YAML parser or validity verdict. Explicit profile import
/// always delegates to native inspection, including flow-style YAML and unknown keys.
pub fn looks_like_mihomo(source: &str) -> bool {
    let text = source.trim_start_matches('\u{feff}').trim();
    const KEYS: &[&str] = &[
        "proxies",
        "proxy-groups",
        "proxy-providers",
        "rule-providers",
    ];
    if let Ok(serde_json::Value::Object(value)) = serde_json::from_str(text) {
        return KEYS.iter().any(|k| value.contains_key(*k));
    }
    text.lines().any(|line| {
        if line.starts_with(char::is_whitespace) || line.starts_with('#') {
            return false;
        }
        line.split_once(':')
            .is_some_and(|(key, _)| KEYS.contains(&key.trim().trim_matches(['\'', '"'])))
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    const SOURCE: &str = "\u{feff}# preserve comments\r\nproxy-providers:\r\n  feed: &feed {type: http, url: https://example.invalid/feed}\r\nproxy-groups:\r\n  - {name: Main, type: select, use: [feed]}\r\nrules: [MATCH,Main]\r\n";
    #[test]
    fn provider_only_source_roundtrip_is_exact() {
        let p =
            FullProfile::new("Providers".into(), ProfileKind::MihomoYaml, SOURCE.into()).unwrap();
        let encoded = serde_json::to_vec(&p).unwrap();
        let decoded: FullProfile = serde_json::from_slice(&encoded).unwrap();
        assert_eq!(decoded.original_text.as_bytes(), SOURCE.as_bytes());
        decoded.verify().unwrap();
        assert!(looks_like_mihomo(SOURCE));
        assert!(!serde_json::to_string(&p.summary())
            .unwrap()
            .contains("example.invalid"));
    }
    #[test]
    fn json_shaped_yaml_and_quoted_provider_key_are_detected_without_flattening() {
        assert!(looks_like_mihomo(
            r#"{"proxy-providers":{},"proxy-groups":[]}"#
        ));
        assert!(looks_like_mihomo("'proxy-providers': {}"));
        assert!(!looks_like_mihomo("vless://fixture@example.invalid:443"));
        assert!(!looks_like_mihomo(r#"{"outbounds":[]}"#));
    }
    #[test]
    fn old_state_keeps_legacy_dispatch() {
        let c: CoreProfiles = serde_json::from_str("{}").unwrap();
        assert!(c.preferred_core.is_none());
        assert!(c.profiles.is_empty());
    }
    #[test]
    fn inspection_is_bound_to_exact_bytes_and_version() {
        let mut p = FullProfile::new("A".into(), ProfileKind::MihomoYaml, SOURCE.into()).unwrap();
        let mut i = Inspection {
            api: WIRE_API,
            source_digest: p.source_digest.clone(),
            ..Default::default()
        };
        p.attach_inspection(i.clone()).unwrap();
        i.api = 2;
        assert_eq!(p.attach_inspection(i), Err("UNSUPPORTED_WIRE_VERSION"));
        p.original_text.push('\n');
        assert_eq!(p.verify(), Err("SOURCE_DIGEST_MISMATCH"));
    }
    #[test]
    fn edits_reject_stale_revision_and_invalidate_projection_and_selection() {
        let mut p = FullProfile::new("A".into(), ProfileKind::MihomoYaml, SOURCE.into()).unwrap();
        p.selections.insert("Main".into(), "DIRECT".into());
        assert_eq!(
            p.replace_source(0, "proxies: []".into()),
            Err("STALE_REVISION")
        );
        assert_eq!(p.original_text, SOURCE);
        p.replace_source(1, "proxies: []".into()).unwrap();
        assert_eq!(p.revision, 2);
        assert!(p.selections.is_empty());
        assert!(p.inspection.is_none());
    }
    #[test]
    fn preference_does_not_coerce_profile_format() {
        let p = FullProfile::new("A".into(), ProfileKind::MihomoYaml, SOURCE.into()).unwrap();
        let id = p.id.clone();
        let mut state = CoreProfiles {
            preferred_core: Some(CoreKind::Xray),
            profiles: vec![p],
            ..Default::default()
        };
        assert_eq!(state.ensure_compatible(&id), Err("UNSUPPORTED_CORE"));
        state.preferred_core = Some(CoreKind::Mihomo);
        assert_eq!(state.ensure_compatible(&id), Ok(CoreKind::Mihomo));
    }
    #[test]
    fn summaries_do_not_expose_native_credentials_or_provider_urls() {
        let mut p = FullProfile::new("A".into(), ProfileKind::MihomoYaml, SOURCE.into()).unwrap();
        p.attach_inspection(Inspection{api:1,source_digest:p.source_digest.clone(),graph:serde_json::json!({
            "proxies":[{"name":"Node", "type":"ss", "password":"secret-marker", "server":"private-host-marker"}],
            "groups":[{"name":"Main","type":"select","hidden":true,"proxies":["Node"]}],
            "providers":{"Feed":{"type":"http","url":"https://private-host-marker/secret-marker","headers":{"Authorization":"secret-marker"}}}
        }),..Default::default()}).unwrap();
        let summary = serde_json::to_string(&p.summary()).unwrap();
        assert!(!summary.contains("secret-marker"));
        assert!(!summary.contains("private-host-marker"));
        assert_eq!(
            p.summary().inspection.unwrap().graph["groups"][0]["hidden"],
            true
        );
        assert_eq!(
            p.inspection.unwrap().graph["proxies"][0]["password"],
            "secret-marker"
        );
    }
    #[test]
    fn invalid_persisted_id_cannot_escape_data_directory() {
        let mut p = FullProfile::new("A".into(), ProfileKind::MihomoYaml, SOURCE.into()).unwrap();
        p.id = "../../outside".into();
        assert_eq!(p.verify(), Err("INVALID_PROFILE_ID"));
    }
    #[test]
    fn bounds_reject_empty_nul_and_oversized_documents() {
        assert_eq!(check_source("\u{feff} \r\n"), Err("EMPTY_PROFILE"));
        assert_eq!(check_source("x\0"), Err("INVALID_PROFILE_ENCODING"));
        assert_eq!(
            check_source(&"a".repeat(MAX_SOURCE_BYTES + 1)),
            Err("PROFILE_TOO_LARGE")
        );
    }
}
