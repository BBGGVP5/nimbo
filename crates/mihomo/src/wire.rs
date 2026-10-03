use crate::{FieldIssue, FullProfile, Inspection, CORE_COMMIT, CORE_VERSION};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{
    net::{Ipv4Addr, SocketAddr},
    path::{Path, PathBuf},
};

pub const MAX_RESPONSE: usize = 12 * 1024 * 1024;
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Envelope {
    pub api_version: u32,
    #[serde(default)]
    pub request_id: String,
    pub success: bool,
    #[serde(default)]
    pub generation: u64,
    #[serde(default)]
    pub data: Value,
    #[serde(default)]
    pub error: Option<NativeError>,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NativeError {
    pub code: String,
    #[serde(default)]
    pub path: Option<String>,
}
impl Envelope {
    pub fn decode(bytes: &[u8], expected_request: Option<&str>) -> Result<Self, String> {
        if bytes.len() > MAX_RESPONSE {
            return Err("NATIVE_RESPONSE_TOO_LARGE".into());
        }
        let value: Self = serde_json::from_slice(bytes).map_err(|_| "INVALID_NATIVE_RESPONSE")?;
        if value.api_version != 1 {
            return Err("UNSUPPORTED_WIRE_VERSION".into());
        }
        if expected_request.is_some_and(|id| id != value.request_id) {
            return Err("NATIVE_REQUEST_MISMATCH".into());
        }
        if !value.success {
            // Do not echo native message/path: they may contain user-controlled names/URLs.
            return Err(value
                .error
                .as_ref()
                .map(|e| safe_code(&e.code))
                .unwrap_or("NATIVE_FAILED")
                .into());
        }
        Ok(value)
    }
}
pub fn safe_code(code: &str) -> &str {
    match code {
        "UNSUPPORTED_FIELD"
        | "UNSUPPORTED_CORE"
        | "INVALID_GRAPH"
        | "NATIVE_VALIDATION_FAILED"
        | "CORE_UNAVAILABLE"
        | "STALE_GENERATION"
        | "TUN_SETUP_FAILED"
        | "PROVIDER_UPDATE_FAILED"
        | "PLATFORM_UNAVAILABLE"
        | "INVALID_REQUEST"
        | "NOT_RUNNING"
        | "ALREADY_RUNNING"
        | "UNSUPPORTED_CONFIG"
        | "INVALID_YAML"
        | "INVALID_CONFIG"
        | "BUSY"
        | "START_CANCELLED"
        | "PROVIDER_INITIALIZATION"
        | "PROVIDER_REFRESH"
        | "RULE_PROVIDER_INITIALIZATION"
        | "INVALID_SELECTION"
        | "NOT_SELECTABLE"
        | "NOT_REFRESHABLE"
        | "NOT_FOUND"
        | "DELAY_FAILED"
        | "LISTEN_FAILED"
        | "AMBIGUOUS_PROXY" => code,
        _ => "NATIVE_FAILED",
    }
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeInfo {
    pub state: String,
    pub mixed_address: String,
    pub controller_address: String,
    pub network_owner: String,
    #[serde(rename = "sourceSHA256")]
    pub source_sha256: String,
    pub core_version: String,
    pub core_commit: String,
    pub api_version: u32,
    #[serde(default)]
    pub tun_ready: bool,
}
impl RuntimeInfo {
    pub fn validate_tun(&self, digest: &str, mixed: bool) -> Result<(), String> {
        if self.state != "running"
            || self.network_owner != "desktop-tun"
            || !self.tun_ready
            || self.source_sha256 != digest
            || self.core_version != CORE_VERSION
            || self.core_commit != CORE_COMMIT
            || self.api_version != 1
        {
            return Err("INVALID_NATIVE_READINESS".into());
        }
        loopback_address(&self.controller_address)?;
        if mixed {
            loopback_address(&self.mixed_address)?;
        } else if !self.mixed_address.is_empty() {
            return Err("INVALID_NATIVE_READINESS".into());
        }
        if self.controller_address == self.mixed_address {
            return Err("INVALID_NATIVE_READINESS".into());
        }
        Ok(())
    }

    pub fn validate(&self, digest: &str) -> Result<(), String> {
        if self.state != "running"
            || self.network_owner != "desktop-proxy"
            || self.source_sha256 != digest
            || self.core_version != CORE_VERSION
            || self.core_commit != CORE_COMMIT
            || self.api_version != 1
        {
            return Err("INVALID_NATIVE_READINESS".into());
        }
        loopback_address(&self.controller_address)?;
        loopback_address(&self.mixed_address)?;
        if self.controller_address == self.mixed_address {
            return Err("INVALID_NATIVE_READINESS".into());
        }
        Ok(())
    }
}
pub fn loopback_address(value: &str) -> Result<SocketAddr, String> {
    let addr: SocketAddr = value.parse().map_err(|_| "INVALID_LOOPBACK_ENDPOINT")?;
    if addr.ip() != Ipv4Addr::LOCALHOST || addr.port() == 0 {
        return Err("INVALID_LOOPBACK_ENDPOINT".into());
    }
    Ok(addr)
}
pub fn decode_inspection(envelope: Envelope, profile: &FullProfile) -> Result<Inspection, String> {
    let data = envelope.data;
    if data.get("sourceSHA256").and_then(Value::as_str) != Some(profile.source_digest.as_str())
        || data.get("originalYAML").and_then(Value::as_str) != Some(profile.original_text.as_str())
    {
        return Err("SOURCE_DIGEST_MISMATCH".into());
    }
    let issues = data
        .get("strictIssues")
        .and_then(Value::as_array)
        .ok_or("INVALID_NATIVE_INSPECTION")?;
    let issues = issues
        .iter()
        .map(|v| FieldIssue {
            code: v
                .get("code")
                .and_then(Value::as_str)
                .map(safe_code)
                .unwrap_or("NATIVE_VALIDATION_FAILED")
                .into(),
            // Native issue paths are displayed only if bounded; never its message/source.
            path: v
                .get("path")
                .and_then(Value::as_str)
                .filter(|s| s.len() < 512)
                .unwrap_or("")
                .into(),
        })
        .collect();
    Ok(Inspection {
        api: 1,
        source_digest: profile.source_digest.clone(),
        graph: data.get("declaredGraph").cloned().unwrap_or(Value::Null),
        issues,
        // inspect is not runtime validation; start performs the second native gate.
        native_validated: false,
    })
}

/// Expected digest comes from the desktop binary's build, never from a user-supplied
/// adjacent manifest. No path overrides bypass the embedded trust anchor.
#[derive(Clone)]
pub struct VerifiedBinary {
    path: PathBuf,
    expected_sha256: String,
}
impl VerifiedBinary {
    pub fn verify(path: &Path, expected_sha256: &str) -> Result<Self, String> {
        if expected_sha256.len() != 64 || !expected_sha256.bytes().all(|b| b.is_ascii_hexdigit()) {
            return Err("CORE_UNAVAILABLE".into());
        }
        let bytes = std::fs::read(path).map_err(|_| "CORE_UNAVAILABLE")?;
        use sha2::{Digest, Sha256};
        let got = format!("{:x}", Sha256::digest(&bytes));
        if got != expected_sha256.to_ascii_lowercase() {
            return Err("CORE_HASH_MISMATCH".into());
        }
        Ok(Self {
            path: path.to_owned(),
            expected_sha256: got,
        })
    }
    pub fn reverify(&self) -> Result<(), String> {
        Self::verify(&self.path, &self.expected_sha256).map(|_| ())
    }
    pub fn path(&self) -> &Path {
        &self.path
    }
    pub fn identity(&self) -> Value {
        json!({"version":CORE_VERSION,"commit":CORE_COMMIT,"sha256":self.expected_sha256})
    }
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct Snapshot {
    pub groups: std::collections::BTreeMap<String, Value>,
    pub providers: std::collections::BTreeMap<String, Value>,
    #[serde(default, rename = "ruleProviders")]
    pub rule_providers: std::collections::BTreeMap<String, Value>,
}
pub fn request(operation: &str, generation: u64) -> Value {
    json!({"apiVersion":1,"requestId":uuid::Uuid::new_v4().to_string(),"operation":operation,"generation":generation})
}
pub fn start_request(
    profile: &FullProfile,
    data_dir: &Path,
    secret: &str,
) -> Result<Value, String> {
    profile.verify().map_err(String::from)?;
    if !data_dir.is_absolute() || secret.len() < 32 {
        return Err("INVALID_START_OPTIONS".into());
    }
    Ok(
        json!({"apiVersion":1,"requestId":uuid::Uuid::new_v4().to_string(),"operation":"start",
        "yaml":profile.original_text,"options":{"dataDir":data_dir,"networkOwner":"desktop-proxy",
        "mixedAddress":"127.0.0.1:0","controllerAddress":"127.0.0.1:0","secret":secret}}),
    )
}
pub fn fresh_secret() -> String {
    format!(
        "{}{}",
        uuid::Uuid::new_v4().simple(),
        uuid::Uuid::new_v4().simple()
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_digest;
    #[test]
    fn tun_readiness_is_an_explicit_owner_with_native_device_not_a_proxy_flag() {
        let mut value = json!({"state":"running","networkOwner":"desktop-tun","tunReady":true,"sourceSHA256":"source","coreVersion":CORE_VERSION,"coreCommit":CORE_COMMIT,"apiVersion":1,"controllerAddress":"127.0.0.1:9999","mixedAddress":""});
        let info: RuntimeInfo = serde_json::from_value(value.clone()).unwrap();
        assert!(info.validate_tun("source", false).is_ok());
        assert!(info.validate("source").is_err());
        assert!(info.validate_tun("different", false).is_err());
        assert!(info.validate_tun("source", true).is_err());
        value["tunReady"] = json!(false);
        assert!(serde_json::from_value::<RuntimeInfo>(value)
            .unwrap()
            .validate_tun("source", false)
            .is_err());
    }
    #[test]
    fn readiness_refuses_lan_ipv6_zero_port_and_digest_substitution() {
        for bad in [
            "0.0.0.0:9999",
            "192.0.2.1:9999",
            "localhost:9999",
            "127.0.0.1:0",
            "[::1]:9999",
        ] {
            assert!(loopback_address(bad).is_err());
        }
        assert!(loopback_address("127.0.0.1:9999").is_ok());
    }
    #[test]
    fn envelope_rejects_wrong_version_identity_and_redacts_error_message() {
        let b=br#"{"apiVersion":1,"requestId":"x","success":false,"error":{"code":"oops-secret","message":"password"}} "#;
        assert_eq!(Envelope::decode(b, Some("x")).unwrap_err(), "NATIVE_FAILED");
        let b = br#"{"apiVersion":2,"requestId":"x","success":true}"#;
        assert_eq!(
            Envelope::decode(b, None).unwrap_err(),
            "UNSUPPORTED_WIRE_VERSION"
        );
        let b = br#"{"apiVersion":1,"requestId":"old","success":true}"#;
        assert_eq!(
            Envelope::decode(b, Some("new")).unwrap_err(),
            "NATIVE_REQUEST_MISMATCH"
        );
    }
    #[test]
    fn start_options_are_explicit_and_source_is_not_reserialized() {
        let p = FullProfile::new(
            "A".into(),
            crate::ProfileKind::MihomoYaml,
            "# comment\nproxies: []\n".into(),
        )
        .unwrap();
        let dir = std::env::temp_dir();
        let secret = fresh_secret();
        assert_eq!(secret.len(), 64);
        assert_ne!(secret, fresh_secret());
        let r = start_request(&p, &dir, &secret).unwrap();
        assert_eq!(r["yaml"], p.original_text);
        assert_eq!(r["options"]["networkOwner"], "desktop-proxy");
        assert_eq!(r["options"]["controllerAddress"], "127.0.0.1:0");
        assert!(start_request(&p, Path::new("relative"), &secret).is_err());
    }
    #[test]
    fn inspection_is_exact_and_not_native_activation() {
        let p = FullProfile::new(
            "A".into(),
            crate::ProfileKind::MihomoYaml,
            "proxies: []".into(),
        )
        .unwrap();
        let response = json!({"apiVersion":1,"success":true,"data":{"sourceSHA256":source_digest(&p.original_text),"originalYAML":p.original_text,"declaredGraph":{},"strictIssues":[]}});
        let i = decode_inspection(
            Envelope::decode(&serde_json::to_vec(&response).unwrap(), None).unwrap(),
            &p,
        )
        .unwrap();
        assert!(!i.native_validated);
        assert!(i.issues.is_empty());
    }
}
