//! A subscription companion retains the complete provider source. It is not a synthetic Server protocol.
use crate::{looks_like_mihomo, FullProfile, ProfileKind};

pub fn prepare(name: &str, source: &str, previous: Option<&FullProfile>) -> Result<FullProfile, &'static str> {
    if !looks_like_mihomo(source) { return Err("SUBSCRIPTION_CORE_UNSUPPORTED"); }
    if let Some(previous) = previous {
        previous.verify()?;
        if previous.kind != ProfileKind::MihomoYaml { return Err("CORE_MISMATCH"); }
        let mut next = previous.clone();
        if next.original_text != source { next.replace_source(next.revision, source.to_owned())?; }
        next.name = name.trim().chars().take(512).collect();
        return Ok(next);
    }
    FullProfile::new(name.to_owned(), ProfileKind::MihomoYaml, source.to_owned())
}

#[cfg(test)]
mod tests {
    use super::*;
    const YAML: &str = "\u{feff}# exact source\r\nproxy-providers:\r\n  Feed: {type: http, url: https://private.invalid/token}\r\nproxy-groups:\r\n  - {name: Main, type: select, use: [Feed]}\r\nrules: [MATCH,Main]\r\n";
    #[test]
    fn complete_source_and_identity_are_preserved() {
        let mut original = prepare("Subscription", YAML, None).unwrap();
        original.selections.insert("Main".into(), "node".into());
        let same = prepare("Renamed", YAML, Some(&original)).unwrap();
        assert_eq!(same.original_text.as_bytes(), YAML.as_bytes());
        assert_eq!(same.id, original.id);
        assert_eq!(same.revision, 1);
        assert_eq!(same.selections, original.selections);
        assert!(!serde_json::to_string(&same.summary()).unwrap().contains("private.invalid"));
        let changed = prepare("Renamed", &YAML.replace("Main", "New"), Some(&same)).unwrap();
        assert_eq!(changed.id, same.id);
        assert_eq!(changed.revision, 2);
        assert!(changed.selections.is_empty());
        assert!(changed.inspection.is_none());
    }
    #[test]
    fn unsupported_payload_cannot_replace_a_valid_profile() {
        let previous = prepare("Subscription", YAML, None).unwrap();
        assert_eq!(prepare("Subscription", "vless://private", Some(&previous)).err(), Some("SUBSCRIPTION_CORE_UNSUPPORTED"));
        assert_eq!(previous.original_text, YAML);
        previous.verify().unwrap();
    }
}
