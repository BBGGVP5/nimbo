//! Модули маршрутизации: наборы правил, написанные человеком.
//!
//! Формат тот же, что у Shadowrocket и Surge, — строками вида
//! `DOMAIN-SUFFIX,example.com,DIRECT`. Люди переносят сюда готовые наборы, и
//! требовать переписать их в свой синтаксис значит выбросить чужую работу.
//!
//! Разбор повторяет общий модуль мобильных приложений (Kotlin,
//! `NimboRoutingModules.kt`): один и тот же текст обязан вести себя одинаково
//! на компьютере и на телефоне, иначе перенос набора между устройствами
//! превращается в лотерею.

use serde::{Deserialize, Serialize};

use crate::routing::RoutingRule;
use crate::config::{TAG_BLOCK, TAG_DIRECT, TAG_PROXY};

/// Модуль так, как его хранит приложение.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct RoutingModule {
    pub id: String,
    pub name: String,
    #[serde(default = "default_enabled")]
    pub enabled: bool,
    #[serde(default)]
    pub text: String,
}

fn default_enabled() -> bool {
    true
}

/// Итог разбора: имя из заголовка, правила и число непонятых строк.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct ParsedModule {
    pub name: Option<String>,
    pub description: Option<String>,
    pub rules: Vec<ModuleRule>,
    pub skipped_lines: usize,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ModuleRule {
    pub domains: Vec<String>,
    pub ips: Vec<String>,
    pub outbound_tag: &'static str,
}

/// Разбирает текст модуля.
///
/// Секция `[General]` пропускается намеренно: она описывает DNS и системный
/// стек чужого движка, и переносить её настройки на Xray было бы догадкой.
pub fn parse_module(text: &str) -> ParsedModule {
    let mut parsed = ParsedModule::default();
    let mut in_rules = false;

    for raw_line in text.lines() {
        let line = raw_line.trim();
        if line.is_empty() {
            continue;
        }
        if let Some(value) = strip_prefix_ci(line, "#!name=") {
            parsed.name = non_empty(value);
            continue;
        }
        if let Some(value) = strip_prefix_ci(line, "#!desc=") {
            parsed.description = non_empty(value);
            continue;
        }
        if line.starts_with('#') || line.starts_with("//") || line.starts_with(';') {
            continue;
        }
        if line.starts_with('[') {
            in_rules = line.eq_ignore_ascii_case("[Rule]");
            continue;
        }
        if !in_rules {
            continue;
        }
        match parse_rule(line) {
            Some(rule) => parsed.rules.push(rule),
            None => parsed.skipped_lines += 1,
        }
    }

    parsed
}

/// Правила всех включённых модулей одним списком, в порядке модулей.
pub fn module_routing_rules(modules: &[RoutingModule]) -> Vec<RoutingRule> {
    modules
        .iter()
        .filter(|module| module.enabled)
        .flat_map(|module| parse_module(&module.text).rules)
        .filter(|rule| !rule.domains.is_empty() || !rule.ips.is_empty())
        .map(|rule| RoutingRule {
            rule_type: "field".into(),
            domain: (!rule.domains.is_empty()).then_some(rule.domains),
            ip: (!rule.ips.is_empty()).then_some(rule.ips),
            outbound_tag: rule.outbound_tag.into(),
            ..Default::default()
        })
        .collect()
}

fn parse_rule(line: &str) -> Option<ModuleRule> {
    let parts: Vec<&str> = line.split(',').map(str::trim).collect();
    if parts.len() < 2 {
        return None;
    }
    let kind = parts[0].to_ascii_uppercase();
    // Политика бывает второй (`FINAL,DIRECT`) и третьей — берём ту, что есть.
    let outbound_tag = policy_tag(parts.get(2).copied().or_else(|| parts.get(1).copied()))?;
    let value = parts[1];

    let rule = match kind.as_str() {
        // Точное совпадение домена: в Xray это префикс `full:`.
        "DOMAIN" => ModuleRule {
            domains: vec![format!("full:{value}")],
            ips: Vec::new(),
            outbound_tag,
        },
        "DOMAIN-SUFFIX" => ModuleRule {
            domains: vec![format!("domain:{value}")],
            ips: Vec::new(),
            outbound_tag,
        },
        "DOMAIN-KEYWORD" => ModuleRule {
            domains: vec![value.to_string()],
            ips: Vec::new(),
            outbound_tag,
        },
        "IP-CIDR" | "IP-CIDR6" | "IP6-CIDR" => ModuleRule {
            domains: Vec::new(),
            ips: vec![value.trim_end_matches(",no-resolve").to_string()],
            outbound_tag,
        },
        "GEOIP" => ModuleRule {
            domains: Vec::new(),
            ips: vec![format!("geoip:{}", value.to_ascii_lowercase())],
            outbound_tag,
        },
        "GEOSITE" | "RULE-SET" => ModuleRule {
            domains: vec![format!("geosite:{}", value.to_ascii_lowercase())],
            ips: Vec::new(),
            outbound_tag,
        },
        // `FINAL` описывает поведение по умолчанию, а его задаёт профиль
        // маршрутизации: модуль не должен молча переопределять этот выбор.
        _ => return None,
    };
    Some(rule)
}

fn policy_tag(value: Option<&str>) -> Option<&'static str> {
    match value?.to_ascii_uppercase().as_str() {
        "DIRECT" => Some(TAG_DIRECT),
        "PROXY" => Some(TAG_PROXY),
        "REJECT" | "REJECT-DROP" | "REJECT-TINYGIF" | "BLOCK" => Some(TAG_BLOCK),
        _ => None,
    }
}

fn strip_prefix_ci<'a>(line: &'a str, prefix: &str) -> Option<&'a str> {
    if line.len() < prefix.len() {
        return None;
    }
    line.get(..prefix.len())
        .filter(|head| head.eq_ignore_ascii_case(prefix))
        .map(|_| line[prefix.len()..].trim())
}

fn non_empty(value: &str) -> Option<String> {
    let trimmed = value.trim();
    (!trimmed.is_empty()).then(|| trimmed.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_rules_and_skips_general_section() {
        let text = "#!name=Ru Direct\n\n[General]\nbypass-system = true\n\n[Rule]\n\
                    DOMAIN-SUFFIX,ozon.ru,DIRECT\nIP-CIDR,10.0.0.0/8,DIRECT\n\
                    DOMAIN-KEYWORD,analytics,REJECT\nчепуха\n";
        let parsed = parse_module(text);
        assert_eq!(parsed.name.as_deref(), Some("Ru Direct"));
        assert_eq!(parsed.rules.len(), 3);
        // Настройки [General] не считаются пропущенными правилами: они вне
        // секции правил, и жаловаться на них незачем.
        assert_eq!(parsed.skipped_lines, 1);
    }

    #[test]
    fn disabled_modules_do_not_contribute_rules() {
        let modules = vec![RoutingModule {
            id: "one".into(),
            name: "One".into(),
            enabled: false,
            text: "[Rule]\nDOMAIN-SUFFIX,example.com,DIRECT\n".into(),
        }];
        assert!(module_routing_rules(&modules).is_empty());
    }
}
