//! Pure desktop selection policy: no processes, network access or configuration conversion.
use crate::CoreKind;
use serde::{Deserialize, Deserializer, Serialize, Serializer};

#[derive(Debug, Default, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum CorePreference {
    #[default]
    Auto,
    Xray,
    Awg,
    Mihomo,
}

impl CorePreference {
    pub fn core(self) -> Option<CoreKind> {
        match self {
            Self::Auto => None,
            Self::Xray => Some(CoreKind::Xray),
            Self::Awg => Some(CoreKind::Awg),
            Self::Mihomo => Some(CoreKind::Mihomo),
        }
    }
}

impl From<Option<CoreKind>> for CorePreference {
    fn from(core: Option<CoreKind>) -> Self {
        match core {
            None => Self::Auto,
            Some(CoreKind::Xray) => Self::Xray,
            Some(CoreKind::Awg) => Self::Awg,
            Some(CoreKind::Mihomo) => Self::Mihomo,
        }
    }
}

/// Recovery and rotation inherit the session choice, including an explicit Auto
/// snapshot. Only a new manual connection reads the pending preference directly.
pub fn recovery_preference(
    pending: Option<CoreKind>,
    session: Option<CorePreference>,
) -> Option<CoreKind> {
    session.map(CorePreference::core).unwrap_or(pending)
}

/// Managed desktop proxy support never implies ownership of TUN, DNS or a kill switch.
pub fn ensure_mihomo_network(
    system_proxy_only: bool,
    kill_switch: bool,
    platform_supported: bool,
) -> Result<(), String> {
    if !system_proxy_only {
        return Err("MIHOMO_TUN_UNAVAILABLE".into());
    }
    if kill_switch {
        return Err("MIHOMO_KILL_SWITCH_UNAVAILABLE".into());
    }
    if !platform_supported {
        return Err("SYSTEM_PROXY_PLATFORM_UNAVAILABLE".into());
    }
    Ok(())
}

// Keep Option<CoreKind> internally for existing dispatch callers, but persist an
// explicit `auto`. Missing fields and legacy null states still mean Auto.
pub mod persisted {
    use super::*;
    pub fn serialize<S: Serializer>(core: &Option<CoreKind>, s: S) -> Result<S::Ok, S::Error> {
        CorePreference::from(*core).serialize(s)
    }
    pub fn deserialize<'de, D: Deserializer<'de>>(d: D) -> Result<Option<CoreKind>, D::Error> {
        Ok(Option::<CorePreference>::deserialize(d)?
            .unwrap_or_default()
            .core())
    }
}

pub fn ensure_compatible(preferred: Option<CoreKind>, required: CoreKind) -> Result<(), String> {
    if let Some(core) = preferred.filter(|core| *core != required) {
        return Err(format!(
            "CORE_MISMATCH: selected {}; this profile requires {}. Choose Auto or {} in Settings > Connection > Core. The current connection was not changed.",
            name(core), name(required), name(required)
        ));
    }
    Ok(())
}

fn name(core: CoreKind) -> &'static str {
    match core {
        CoreKind::Xray => "Xray",
        CoreKind::Awg => "AWG",
        CoreKind::Mihomo => "Mihomo",
    }
}

pub fn ensure_server_runtime(required: CoreKind, awg_available: bool) -> Result<(), String> {
    match required {
        CoreKind::Awg if !awg_available => Err("AWG_UNAVAILABLE: the verified desktop AWG runtime is missing or unsupported. The current connection was not changed.".into()),
        CoreKind::Mihomo => Err("MIHOMO_TUN_UNAVAILABLE: desktop Mihomo TUN is not integrated; full Mihomo profiles cannot be connected as individual servers.".into()),
        _ => Ok(()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{CoreProfiles, FullProfile, ProfileKind};

    #[test]
    fn mihomo_managed_proxy_requires_all_capabilities_without_fallback() {
        for proxy_only in [false, true] {
            for ks in [false, true] {
                for platform in [false, true] {
                    let result = ensure_mihomo_network(proxy_only, ks, platform);
                    assert_eq!(result.is_ok(), proxy_only && !ks && platform);
                    if !proxy_only {
                        assert_eq!(result.unwrap_err(), "MIHOMO_TUN_UNAVAILABLE");
                    } else if ks {
                        assert_eq!(result.unwrap_err(), "MIHOMO_KILL_SWITCH_UNAVAILABLE");
                    }
                }
            }
        }
    }

    #[test]
    fn every_explicit_pair_is_strict_and_auto_uses_required_core() {
        for required in [CoreKind::Xray, CoreKind::Awg, CoreKind::Mihomo] {
            assert!(ensure_compatible(None, required).is_ok());
            for selected in [CoreKind::Xray, CoreKind::Awg, CoreKind::Mihomo] {
                let result = ensure_compatible(Some(selected), required);
                assert_eq!(result.is_ok(), selected == required);
                if let Err(error) = result {
                    assert!(error.starts_with("CORE_MISMATCH:"));
                    assert!(error.contains(name(required)));
                    assert!(error.contains(name(selected)));
                }
            }
        }
    }

    #[test]
    fn legacy_and_new_preferences_roundtrip_with_explicit_auto_default() {
        for input in [
            "{}",
            r#"{"preferred_core":null}"#,
            r#"{"preferred_core":"auto"}"#,
        ] {
            let state: CoreProfiles = serde_json::from_str(input).unwrap();
            assert_eq!(state.preferred_core, None);
            assert_eq!(
                serde_json::to_value(state).unwrap()["preferred_core"],
                "auto"
            );
        }
        for value in ["xray", "awg", "mihomo"] {
            let state: CoreProfiles =
                serde_json::from_value(serde_json::json!({"preferred_core":value})).unwrap();
            assert!(state.preferred_core.is_some());
            assert_eq!(
                serde_json::to_value(state).unwrap()["preferred_core"],
                value
            );
        }
        assert!(serde_json::from_str::<CoreProfiles>(r#"{"preferred_core":"unknown"}"#).is_err());
    }

    #[test]
    fn missing_awg_and_mihomo_server_runtime_fail_closed() {
        assert!(ensure_server_runtime(CoreKind::Xray, false).is_ok());
        assert!(ensure_server_runtime(CoreKind::Awg, true).is_ok());
        assert!(ensure_server_runtime(CoreKind::Awg, false)
            .unwrap_err()
            .starts_with("AWG_UNAVAILABLE:"));
        for available in [false, true] {
            assert!(ensure_server_runtime(CoreKind::Mihomo, available).is_err());
        }
    }

    #[test]
    fn preference_edits_do_not_change_current_session_or_auto_rotation() {
        for session in [
            CorePreference::Auto,
            CorePreference::Xray,
            CorePreference::Awg,
            CorePreference::Mihomo,
        ] {
            for pending in [
                None,
                Some(CoreKind::Xray),
                Some(CoreKind::Awg),
                Some(CoreKind::Mihomo),
            ] {
                let persisted = serde_json::to_string(&session).unwrap();
                let restored = serde_json::from_str(&persisted).unwrap();
                assert_eq!(recovery_preference(pending, Some(restored)), session.core());
                for required in [CoreKind::Xray, CoreKind::Awg, CoreKind::Mihomo] {
                    assert_eq!(
                        ensure_compatible(recovery_preference(pending, Some(restored)), required)
                            .is_ok(),
                        session == CorePreference::Auto || session.core() == Some(required)
                    );
                }
                assert_eq!(recovery_preference(pending, None), pending);
            }
        }
    }

    #[test]
    fn selecting_any_core_preserves_full_native_source_and_active_profile() {
        for (kind, source) in [
            (
                ProfileKind::MihomoYaml,
                "\u{feff}# comment\r\nproxy-providers: {}\r\nrules: [MATCH,DIRECT]\r\n",
            ),
            (
                ProfileKind::XrayJson,
                "{\r\n  \"outbounds\": [], \"routing\": {\"rules\": []}\r\n}",
            ),
            (
                ProfileKind::AwgIni,
                "[Interface]\r\nS4=20\r\nI5=<b 0x1234>\r\n[Peer]\r\n",
            ),
        ] {
            let profile = FullProfile::new("Fixture".into(), kind, source.into()).unwrap();
            let id = profile.id.clone();
            let mut state = CoreProfiles {
                profiles: vec![profile],
                active_profile_id: Some(id.clone()),
                ..Default::default()
            };
            for core in [
                None,
                Some(CoreKind::Xray),
                Some(CoreKind::Awg),
                Some(CoreKind::Mihomo),
            ] {
                state.preferred_core = core;
                let saved = serde_json::to_string(&state).unwrap();
                let restored: CoreProfiles = serde_json::from_str(&saved).unwrap();
                assert_eq!(restored.active_profile_id.as_deref(), Some(id.as_str()));
                assert_eq!(
                    restored.profiles[0].original_text.as_bytes(),
                    source.as_bytes()
                );
                restored.profiles[0].verify().unwrap();
            }
        }
    }
}
