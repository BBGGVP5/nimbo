//! Мультидомен подписки: панель отдаёт список зеркал сабпейджа, клиент сам
//! переключается на живой домен, когда основной заблокирован или лёг.
//!
//! Транспорт — HTTP-заголовок ответа подписки, в стиле уже существующих
//! `nimbo-logo` / `nimbo-theme`:
//!
//! ```text
//! nimbo-mirrors: sub2.example.com, sub3.example.org:8443, https://backup.example.net
//! ```
//!
//! Разделители — запятая, точка с запятой, пробел или перевод строки. Элементом
//! может быть голый хост, `host:port` или полный URL. Схема, путь и query берутся
//! у исходной ссылки подписки, если зеркало их не задало.
//!
//! Логика намеренно повторяет `SubscriptionMirrors.kt` из Android-клиента: панель
//! отдаёт один и тот же заголовок обеим платформам.

use url::Url;

/// Заголовок, который стоит отдавать панели. Остальные — совместимость.
pub const PRIMARY_HEADER: &str = "nimbo-mirrors";

/// Имена заголовков в порядке приоритета.
pub const HEADER_NAMES: &[&str] = &[
    PRIMARY_HEADER,
    "x-nimbo-mirrors",
    "subscription-mirrors",
    "x-subscription-mirrors",
    "profile-mirrors",
    "dropweb-mirrors",
];

/// Больше восьми доменов перебирать бессмысленно: это минуты ожидания на таймаутах.
pub const MAX_MIRRORS: usize = 8;

/// Разбирает значение заголовка в список зеркал. Мусорные элементы отбрасываются
/// молча: заголовок приходит извне, ломать из-за него обновление подписки нельзя.
pub fn parse(raw: &str) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();

    for chunk in raw.split(|c: char| c == ',' || c == ';' || c.is_whitespace()) {
        let entry = chunk.trim().trim_matches(['"', '\'']).trim_end_matches('/');
        if entry.is_empty() || !is_plausible_entry(entry) {
            continue;
        }
        if out.iter().any(|existing| existing.eq_ignore_ascii_case(entry)) {
            continue;
        }
        out.push(entry.to_string());
        if out.len() >= MAX_MIRRORS {
            break;
        }
    }

    out
}

fn is_plausible_entry(entry: &str) -> bool {
    if entry.contains("://") {
        return match Url::parse(entry) {
            Ok(url) => matches!(url.scheme(), "http" | "https") && url.host_str().is_some(),
            Err(_) => false,
        };
    }
    is_plausible_host(entry)
}

/// Голый хост: буквы/цифры/дефис, минимум одна точка, необязательный порт.
fn is_plausible_host(entry: &str) -> bool {
    let (host, port) = match entry.rsplit_once(':') {
        Some((host, port)) => (host, Some(port)),
        None => (entry, None),
    };

    if let Some(port) = port {
        if port.is_empty() || port.len() > 5 || !port.chars().all(|c| c.is_ascii_digit()) {
            return false;
        }
    }

    if !host.contains('.') || host.starts_with('.') || host.ends_with('.') {
        return false;
    }

    host.chars()
        .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_' | '~'))
}

/// Подставляет зеркало в ссылку подписки.
///
/// Если зеркало задано без пути, меняются только схема/хост/порт, а путь и query
/// остаются от исходной ссылки (там лежит токен подписки). Если у зеркала есть
/// собственный путь, оно используется целиком, а query подставляется из исходной
/// ссылки, когда своего нет.
pub fn rewrite(primary_url: &str, mirror: &str) -> Option<String> {
    let primary = Url::parse(primary_url.trim()).ok()?;
    let primary_scheme = primary.scheme();

    let normalized = if mirror.contains("://") {
        mirror.trim().to_string()
    } else {
        format!("{}://{}", primary_scheme, mirror.trim())
    };
    let mirror_url = Url::parse(&normalized).ok()?;
    mirror_url.host_str()?;

    let mirror_path = mirror_url.path().trim_end_matches('/');
    let has_own_path = !mirror_path.is_empty();

    let mut result = mirror_url.clone();
    if has_own_path {
        if mirror_url.query().is_none() {
            result.set_query(primary.query());
        }
    } else {
        result.set_path(primary.path());
        result.set_query(primary.query());
    }
    result.set_fragment(primary.fragment());

    Some(result.to_string())
}

/// Порядок обхода доменов: сначала зеркало, которое сработало в прошлый раз
/// (иначе клиент каждый раз упирался бы в заблокированный основной домен и ждал
/// таймаут), затем основная ссылка, затем остальные зеркала.
pub fn candidates(primary_url: &str, mirrors: &[String], preferred_url: Option<&str>) -> Vec<String> {
    let primary = primary_url.trim();
    if primary.is_empty() {
        return Vec::new();
    }

    let rewritten: Vec<String> = mirrors
        .iter()
        .filter_map(|mirror| rewrite(primary, mirror))
        .filter(|candidate| !candidate.eq_ignore_ascii_case(primary))
        .collect();

    let mut ordered: Vec<String> = Vec::new();
    let push = |value: &str, ordered: &mut Vec<String>| {
        if !ordered.iter().any(|existing| existing.eq_ignore_ascii_case(value)) {
            ordered.push(value.to_string());
        }
    };

    if let Some(preferred) = preferred_url.map(str::trim).filter(|v| !v.is_empty()) {
        let known = preferred.eq_ignore_ascii_case(primary)
            || rewritten.iter().any(|c| c.eq_ignore_ascii_case(preferred));
        if known {
            push(preferred, &mut ordered);
        }
    }

    push(primary, &mut ordered);
    for candidate in &rewritten {
        push(candidate, &mut ordered);
    }

    ordered
}

/// Параметры ссылки, которыми можно передать зеркала при импорте.
const URL_PARAM_NAMES: &[&str] = &["mirrors", "nimbo-mirrors", "nimbo_mirrors"];

/// Ссылка подписки без служебных параметров + зеркала, которые в ней лежали.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LinkWithMirrors {
    pub url: String,
    pub mirrors: Vec<String>,
}

/// Достаёт зеркала прямо из ссылки подписки:
///
/// ```text
/// https://sub.example.com/sub/abc123?mirrors=sub2.example.com,sub3.example.net
/// ```
///
/// Единственный способ пережить блокировку основного домена, случившуюся до первой
/// удачной загрузки: заголовок ответа прочитать уже неоткуда. Служебный параметр из
/// ссылки вырезается, чтобы он не попал в сохранённый URL подписки.
pub fn extract_from_url(raw_url: &str) -> LinkWithMirrors {
    let trimmed = raw_url.trim();
    let Ok(parsed) = Url::parse(trimmed) else {
        return LinkWithMirrors { url: trimmed.to_string(), mirrors: Vec::new() };
    };

    let mut kept: Vec<(String, String)> = Vec::new();
    let mut found: Vec<String> = Vec::new();
    for (key, value) in parsed.query_pairs() {
        if URL_PARAM_NAMES.contains(&key.to_ascii_lowercase().as_str()) {
            found.extend(parse(&value));
        } else {
            kept.push((key.into_owned(), value.into_owned()));
        }
    }

    if found.is_empty() {
        return LinkWithMirrors { url: trimmed.to_string(), mirrors: Vec::new() };
    }

    let mut clean = parsed.clone();
    if kept.is_empty() {
        clean.set_query(None);
    } else {
        clean.query_pairs_mut().clear().extend_pairs(kept.iter().map(|(k, v)| (k, v)));
    }

    found.truncate(MAX_MIRRORS);
    LinkWithMirrors { url: clean.to_string(), mirrors: found }
}

/// Объединяет уже известные зеркала с новыми, сохраняя порядок и лимит.
pub fn merge(known: &[String], added: &[String]) -> Vec<String> {
    let mut merged: Vec<String> = Vec::new();
    for entry in known.iter().chain(added.iter()) {
        if entry.trim().is_empty() {
            continue;
        }
        if !merged.iter().any(|existing| existing.eq_ignore_ascii_case(entry)) {
            merged.push(entry.clone());
        }
        if merged.len() >= MAX_MIRRORS {
            break;
        }
    }
    merged
}

/// Хост ссылки — для логов и коротких подписей в интерфейсе.
pub fn host_of(url: &str) -> Option<String> {
    Url::parse(url.trim())
        .ok()
        .and_then(|parsed| parsed.host_str().map(ToString::to_string))
}

#[cfg(test)]
mod tests {
    use super::*;

    const PRIMARY: &str = "https://sub.example.com/api/sub/abc123?format=json";

    #[test]
    fn parses_comma_and_space_separated_entries() {
        assert_eq!(
            parse("sub2.example.com, sub3.example.org:8443 https://backup.example.net"),
            vec![
                "sub2.example.com".to_string(),
                "sub3.example.org:8443".to_string(),
                "https://backup.example.net".to_string()
            ]
        );
    }

    #[test]
    fn drops_duplicates_and_garbage() {
        assert_eq!(
            parse("sub2.example.com\nsub2.example.com\nlocalhost\nftp://x.example.com\nok.example.com"),
            vec!["sub2.example.com".to_string(), "ok.example.com".to_string()]
        );
    }

    #[test]
    fn limits_mirror_count() {
        let many: Vec<String> = (1..=20).map(|i| format!("sub{i}.example.com")).collect();
        assert_eq!(parse(&many.join(",")).len(), MAX_MIRRORS);
    }

    #[test]
    fn bare_host_keeps_path_and_query() {
        assert_eq!(
            rewrite(PRIMARY, "sub2.example.com").as_deref(),
            Some("https://sub2.example.com/api/sub/abc123?format=json")
        );
    }

    #[test]
    fn host_with_port_keeps_path_and_query() {
        assert_eq!(
            rewrite(PRIMARY, "sub3.example.org:8443").as_deref(),
            Some("https://sub3.example.org:8443/api/sub/abc123?format=json")
        );
    }

    #[test]
    fn mirror_with_own_path_inherits_query() {
        assert_eq!(
            rewrite(PRIMARY, "https://backup.example.net/s/abc123").as_deref(),
            Some("https://backup.example.net/s/abc123?format=json")
        );
    }

    #[test]
    fn candidates_start_with_primary() {
        let mirrors = vec!["sub2.example.com".to_string(), "sub3.example.com".to_string()];
        let list = candidates(PRIMARY, &mirrors, None);
        assert_eq!(list[0], PRIMARY);
        assert_eq!(list.len(), 3);
    }

    #[test]
    fn last_working_mirror_goes_first() {
        let mirrors = vec!["sub2.example.com".to_string(), "sub3.example.com".to_string()];
        let working = "https://sub3.example.com/api/sub/abc123?format=json";
        let list = candidates(PRIMARY, &mirrors, Some(working));
        assert_eq!(list[0], working);
        assert_eq!(list[1], PRIMARY);
        assert_eq!(list.len(), 3);
    }

    #[test]
    fn unknown_preferred_is_ignored() {
        let mirrors = vec!["sub2.example.com".to_string()];
        let list = candidates(PRIMARY, &mirrors, Some("https://stranger.example.com/api"));
        assert_eq!(list[0], PRIMARY);
        assert_eq!(list.len(), 2);
    }

    #[test]
    fn extracts_mirrors_from_link_and_cleans_url() {
        let link = extract_from_url(
            "https://sub.example.com/sub/abc123?format=json&mirrors=sub2.example.com,sub3.example.net",
        );
        assert_eq!(link.url, "https://sub.example.com/sub/abc123?format=json");
        assert_eq!(
            link.mirrors,
            vec!["sub2.example.com".to_string(), "sub3.example.net".to_string()]
        );
    }

    #[test]
    fn drops_query_entirely_when_only_mirrors_were_there() {
        let link = extract_from_url("https://sub.example.com/sub/abc123?mirrors=sub2.example.com");
        assert_eq!(link.url, "https://sub.example.com/sub/abc123");
        assert_eq!(link.mirrors, vec!["sub2.example.com".to_string()]);
    }

    #[test]
    fn link_without_mirrors_is_untouched() {
        let link = extract_from_url(PRIMARY);
        assert_eq!(link.url, PRIMARY);
        assert!(link.mirrors.is_empty());
    }

    #[test]
    fn merge_keeps_order_and_drops_duplicates() {
        let merged = merge(
            &["sub2.example.com".to_string()],
            &["SUB2.example.com".to_string(), "sub3.example.com".to_string()],
        );
        assert_eq!(
            merged,
            vec!["sub2.example.com".to_string(), "sub3.example.com".to_string()]
        );
    }

    #[test]
    fn host_of_extracts_domain() {
        assert_eq!(host_of(PRIMARY).as_deref(), Some("sub.example.com"));
        assert!(host_of("not a url").is_none());
    }
}
