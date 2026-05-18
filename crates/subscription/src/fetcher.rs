use std::collections::HashMap;
use std::time::Duration;

use base64::Engine;
use percent_encoding::percent_decode_str;
use serde_json::Value;
use thiserror::Error;

use nimbo_device::{DeviceInfo, device_info};

use crate::USER_AGENT;
use crate::model::{Protocol, Server, Subscription, SubscriptionMeta};
use crate::parser::{ParseError, parse_aggregate};
use crate::userinfo::{SubscriptionInfo, parse_subscription_userinfo};

pub const HAPP_COMPAT_USER_AGENT: &str = "Happ/2.0.0";
pub const HAPP_COMPAT_DEVICE_OS: &str = "Android";
pub const HAPP_COMPAT_OS_VERSION: &str = "14";
pub const HAPP_COMPAT_DEVICE_MODEL: &str = "Nimbo";
const DEFAULT_XRAY_TEMPLATE_KEY: &str = "default";
const NIMBO_CLIENT_NAME: &str = "Nimbo";
const NIMBO_CLIENT_VERSION: &str = env!("CARGO_PKG_VERSION");

#[derive(Debug, Clone)]
pub struct FetchOptions {
    pub timeout: Duration,
    pub user_agent: Option<String>,
    pub device: DeviceInfo,
}

impl Default for FetchOptions {
    fn default() -> Self {
        Self {
            timeout: Duration::from_secs(15),
            user_agent: None,
            device: device_info(),
        }
    }
}

impl FetchOptions {
    fn effective_user_agent(&self) -> String {
        match &self.user_agent {
            Some(ua) => ua.clone(),
            None => {
                if self.device.user_agent.is_empty() {
                    USER_AGENT.to_string()
                } else {
                    self.device.user_agent.clone()
                }
            }
        }
    }
}

pub fn happ_compatible_user_agent(app_user_agent: &str) -> String {
    let app_user_agent = app_user_agent.trim();
    if app_user_agent.is_empty() {
        return HAPP_COMPAT_USER_AGENT.into();
    }
    if app_user_agent.to_ascii_lowercase().contains("happ") {
        return app_user_agent.into();
    }
    format!("{HAPP_COMPAT_USER_AGENT} {app_user_agent}")
}

#[derive(Debug, Error)]
pub enum FetchError {
    #[error("http: {0}")]
    Http(String),
    #[error("status {status}: {body}")]
    Status { status: u16, body: String },
    #[error(transparent)]
    Parse(#[from] ParseError),
}

#[derive(Debug, Clone)]
pub struct Fetched {
    pub raw_body: String,
    pub servers: Vec<Server>,
    pub info: Option<SubscriptionInfo>,
    pub suggested_name: Option<String>,
    pub description: Option<String>,
    pub support_url: Option<String>,
    pub website_url: Option<String>,
    /// Xray-config templates, извлечённые из тела подписки.
    /// Ключ — xrayJsonTemplateUuid/uuid/id, если есть; иначе `default`.
    pub xray_templates: HashMap<String, Value>,
}

pub async fn fetch_subscription(url: &str, opts: &FetchOptions) -> Result<Fetched, FetchError> {
    let mut headers = reqwest::header::HeaderMap::new();
    let app_user_agent = opts.effective_user_agent();

    insert_header(&mut headers, "X-Hwid", &opts.device.hwid);
    insert_header(&mut headers, "X-Device-Os", &opts.device.os);
    insert_header(&mut headers, "X-Device-Os-Version", &opts.device.os_version);
    insert_header(&mut headers, "X-Ver-Os", &opts.device.os_version);
    insert_header(&mut headers, "X-Device-Model", &opts.device.hostname);
    insert_header(&mut headers, "X-Happ-Device-Os", HAPP_COMPAT_DEVICE_OS);
    insert_header(&mut headers, "X-Happ-Device-Os-Version", HAPP_COMPAT_OS_VERSION);
    insert_header(&mut headers, "X-Happ-Device-Model", HAPP_COMPAT_DEVICE_MODEL);
    insert_header(&mut headers, "X-Nimbo-User-Agent", &app_user_agent);
    insert_header(&mut headers, "X-Client-User-Agent", &app_user_agent);
    insert_header(&mut headers, "X-Client-Name", NIMBO_CLIENT_NAME);
    insert_header(&mut headers, "X-Client-Version", NIMBO_CLIENT_VERSION);
    insert_header(&mut headers, "X-Nimbo-Device-Os", &opts.device.os);
    insert_header(&mut headers, "X-Nimbo-Device-Os-Version", &opts.device.os_version);
    insert_header(&mut headers, "X-Nimbo-Device-Model", &opts.device.hostname);

    let client = reqwest::Client::builder()
        .timeout(opts.timeout)
        .user_agent(happ_compatible_user_agent(&app_user_agent))
        .default_headers(headers)
        .build()
        .map_err(|e| FetchError::Http(e.to_string()))?;

    let resp = client
        .get(url)
        .send()
        .await
        .map_err(|e| FetchError::Http(e.to_string()))?;

    let status = resp.status();
    for (name, value) in resp.headers() {
        let value = value
            .to_str()
            .map(ToString::to_string)
            .unwrap_or_else(|_| format!("{:?}", value.as_bytes()));
        println!("subscription header: {}: {}", name.as_str(), value);
    }

    let info = resp
        .headers()
        .get("subscription-userinfo")
        .and_then(|v| v.to_str().ok())
        .map(parse_subscription_userinfo);
    let announce = extract_announce_header(resp.headers());
    let support_url = extract_header_url(resp.headers(), &[
        "support-url",
        "profile-support-url",
        "x-support-url",
    ]);
    let website_url = extract_header_url(resp.headers(), &[
        "profile-web-page-url",
        "profile-page-url",
        "website-url",
        "web-page-url",
        "x-web-page-url",
    ]);
    let suggested_name = extract_subscription_name(resp.headers(), url);

    let body = resp
        .text()
        .await
        .map_err(|e| FetchError::Http(e.to_string()))?;

    if !status.is_success() {
        return Err(FetchError::Status {
            status: status.as_u16(),
            body: truncate(&body, 200),
        });
    }

    let mut servers = parse_aggregate(&body)?;
    let remnawave = merge_remnawave_info(
        fetch_subscription_api_info(&client, url).await,
        parse_subscription_json_info(&body),
    );
    let applied_server_descriptions =
        apply_server_descriptions(&mut servers, &remnawave.server_descriptions);
    if !remnawave.server_descriptions.is_empty() {
        println!(
            "subscription server descriptions: candidates={}, applied to {}/{} servers",
            remnawave.server_descriptions.len(),
            applied_server_descriptions,
            servers.len()
        );
    }

    let xray_templates = extract_xray_templates_from_body(&body);

    Ok(Fetched {
        raw_body: body,
        servers,
        info: merge_info(info, remnawave.info),
        suggested_name: remnawave.username.or(suggested_name),
        description: remnawave.description.or(announce),
        support_url: remnawave.support_url.or(support_url),
        website_url: remnawave.website_url.or(website_url),
        xray_templates,
    })
}

/// Если body содержит XRAY_JSON config/template, возвращает все найденные шаблоны.
/// Ключ — xrayJsonTemplateUuid/uuid/id, если он есть; иначе `default`.
fn extract_xray_templates_from_body(body: &str) -> HashMap<String, Value> {
    let trimmed = body.trim();
    if !trimmed.starts_with('[') && !trimmed.starts_with('{') {
        return HashMap::new();
    }
    let Ok(parsed) = serde_json::from_str::<Value>(trimmed) else {
        return HashMap::new();
    };
    extract_xray_templates_from_value(&parsed)
}

pub fn extract_xray_templates_from_value(value: &Value) -> HashMap<String, Value> {
    let mut out = HashMap::new();
    collect_xray_templates(value, &mut out);
    out
}

fn collect_xray_templates(value: &Value, out: &mut HashMap<String, Value>) {
    if is_xray_template(value) {
        insert_xray_template(out, xray_template_key(value), value.clone());
        return;
    }

    match value {
        Value::Object(map) => {
            let root = value
                .get("response")
                .or_else(|| value.get("data"))
                .unwrap_or(value);
            if !std::ptr::eq(root, value) {
                collect_xray_templates(root, out);
            }

            for key in [
                "templateJson",
                "template_json",
                "xrayJsonTemplate",
                "xray_json_template",
                "config",
                "json",
                "template",
            ] {
                if let Some(raw_template) = get_case_insensitive(value, key) {
                    if let Some(template) = parse_template_value(raw_template) {
                        insert_xray_template(
                            out,
                            xray_template_key(value).or_else(|| xray_template_key(&template)),
                            template,
                        );
                    }
                }
            }

            for nested in map.values() {
                collect_xray_templates(nested, out);
            }
        }
        Value::Array(items) => {
            for nested in items {
                collect_xray_templates(nested, out);
            }
        }
        _ => {}
    }
}

fn insert_xray_template(out: &mut HashMap<String, Value>, key: Option<String>, template: Value) {
    let key = key.unwrap_or_else(|| DEFAULT_XRAY_TEMPLATE_KEY.into());
    out.entry(key).or_insert(template);
}

fn is_xray_template(value: &Value) -> bool {
    value
        .get("outbounds")
        .and_then(Value::as_array)
        .is_some_and(|outbounds| !outbounds.is_empty())
}

fn parse_template_value(value: &Value) -> Option<Value> {
    if is_xray_template(value) {
        return Some(value.clone());
    }
    if let Some(text) = value.as_str() {
        return serde_json::from_str::<Value>(text)
            .ok()
            .filter(is_xray_template);
    }
    None
}

fn xray_template_key(value: &Value) -> Option<String> {
    host_local_string(
        value,
        &[
            "xrayJsonTemplateUuid",
            "xray_json_template_uuid",
            "xray-json-template-uuid",
            "templateUuid",
            "template_uuid",
            "uuid",
            "id",
        ],
    )
    .filter(|key| !key.eq_ignore_ascii_case("null"))
}

pub fn build_subscription(url: &str, fetched: Fetched, name: Option<String>) -> Subscription {
    let Fetched {
        raw_body: _,
        servers,
        info,
        suggested_name,
        description,
        support_url,
        website_url,
        xray_templates: _,
    } = fetched;
    let resolved_name = sanitize_name(name).or_else(|| sanitize_name(suggested_name));

    Subscription {
        url: url.to_string(),
        name: resolved_name,
        meta: SubscriptionMeta {
            description: sanitize_name(description),
            support_url: sanitize_name(support_url),
            website_url: sanitize_name(website_url),
            show_on_home: Some(true),
            update_interval_minutes: None,
        },
        servers,
        info,
        fetched_at: now_unix(),
    }
}

fn extract_subscription_name(headers: &reqwest::header::HeaderMap, url: &str) -> Option<String> {
    if let Some(value) = headers.get("profile-title").and_then(|v| v.to_str().ok()) {
        if let Some(name) = parse_profile_title(value) {
            return Some(name);
        }
    }

    if let Some(value) = headers
        .get(reqwest::header::CONTENT_DISPOSITION)
        .and_then(|v| v.to_str().ok())
    {
        if let Some(name) = parse_content_disposition_filename(value) {
            return Some(name);
        }
    }

    infer_name_from_url(url)
}

#[derive(Debug, Default)]
struct RemnawaveInfo {
    username: Option<String>,
    description: Option<String>,
    support_url: Option<String>,
    website_url: Option<String>,
    info: Option<SubscriptionInfo>,
    server_descriptions: Vec<ServerDescriptionCandidate>,
}

impl RemnawaveInfo {
    fn is_empty(&self) -> bool {
        self.username.is_none()
            && self.description.is_none()
            && self.support_url.is_none()
            && self.website_url.is_none()
            && self.info.is_none()
            && self.server_descriptions.is_empty()
    }
}

async fn fetch_subscription_api_info(
    client: &reqwest::Client,
    subscription_url: &str,
) -> RemnawaveInfo {
    let mut merged = RemnawaveInfo::default();

    for info_url in subscription_api_info_urls(subscription_url) {
        let request = client
            .get(&info_url)
            .header(reqwest::header::ACCEPT, "application/json");
        let Ok(resp) = request.send().await else {
            println!("subscription api probe: {info_url} -> request failed");
            continue;
        };
        let status = resp.status();
        let content_type = resp
            .headers()
            .get(reqwest::header::CONTENT_TYPE)
            .and_then(|value| value.to_str().ok())
            .unwrap_or("-")
            .to_string();

        if !status.is_success() {
            if status.as_u16() != 404 {
                println!(
                    "subscription api probe: {} -> {} ({})",
                    info_url,
                    status.as_u16(),
                    content_type
                );
            }
            continue;
        }
        let Ok(text) = resp.text().await else {
            if status.as_u16() != 404 {
                println!("subscription api probe: {info_url} -> body read failed");
            }
            continue;
        };
        let Ok(json) = serde_json::from_str::<Value>(&text) else {
            if status.as_u16() != 404 {
                println!(
                    "subscription api probe: {} -> not json: {}",
                    info_url,
                    truncate(&text, 120)
                );
            }
            continue;
        };
        println!(
            "subscription api probe: {} -> 200 ({})",
            info_url,
            content_type
        );
        let info = parse_remnawave_info(&json);
        if !info.is_empty() {
            merged = merge_remnawave_info(merged, info);
        }
    }

    merged
}

fn parse_subscription_json_info(body: &str) -> RemnawaveInfo {
    let Ok(json) = serde_json::from_str::<Value>(body.trim()) else {
        return RemnawaveInfo::default();
    };

    parse_remnawave_info(&json)
}

fn subscription_api_info_urls(subscription_url: &str) -> Vec<String> {
    let mut urls = Vec::new();
    let Ok(parsed) = url::Url::parse(subscription_url) else {
        return urls;
    };
    let segments = parsed
        .path_segments()
        .map(|s| s.filter(|segment| !segment.is_empty()).map(ToString::to_string).collect::<Vec<_>>())
        .unwrap_or_default();

    if segments.last().map(|segment| segment.as_str()) == Some("info")
        && segments.iter().any(|segment| segment == "api")
    {
        urls.push(parsed.to_string());
    }

    let short_uuid = if let Some(api_pos) = segments.iter().position(|segment| segment == "api") {
        if segments.get(api_pos + 1).map(|segment| segment.as_str()) == Some("sub") {
            segments.get(api_pos + 2).cloned()
        } else {
            None
        }
    } else {
        segments.last().cloned()
    };

    if let Some(short_uuid) = short_uuid {
        let mut base = parsed;
        base.set_fragment(None);

        for suffix in [
            "happ",
            "info",
            "",
            "raw",
            "xray-json",
            "xray",
            "json",
            "nimbo",
        ] {
            let path = if suffix.is_empty() {
                format!("/api/sub/{short_uuid}")
            } else {
                format!("/api/sub/{short_uuid}/{suffix}")
            };
            base.set_path(&path);
            urls.push(base.to_string());
        }

        let public_base = subscription_public_base_path(&segments, &short_uuid);

        for suffix in ["happ", "info", "raw", "xray-json", "xray", "json", "nimbo"] {
            base.set_path(&format!("{public_base}/{suffix}"));
            urls.push(base.to_string());
        }
    }

    let mut deduped = Vec::new();
    for url in urls {
        if !deduped.contains(&url) {
            deduped.push(url);
        }
    }
    deduped
}

fn subscription_public_base_path(segments: &[String], short_uuid: &str) -> String {
    let mut public_segments = segments.to_vec();
    if let Some(pos) = public_segments.iter().rposition(|segment| segment == short_uuid) {
        public_segments.truncate(pos + 1);
    } else {
        public_segments.push(short_uuid.to_string());
    }
    format!("/{}", public_segments.join("/"))
}

fn parse_remnawave_info(json: &Value) -> RemnawaveInfo {
    let root = json.get("response").or_else(|| json.get("data")).unwrap_or(json);
    let user = root.get("user").unwrap_or(root);
    let settings = root
        .get("subscriptionSettings")
        .or_else(|| root.get("subscription_settings"))
        .or_else(|| root.get("subscription"))
        .or_else(|| root.get("settings"))
        .unwrap_or(root);

    let username = first_string(settings, &["profileTitle", "profile_title"])
        .or_else(|| {
            first_string(
                user,
                &["username", "name", "email", "shortUuid", "short_uuid"],
            )
        })
        .or_else(|| first_string(root, &["username", "name"]));

    let description = first_string(settings, &["happAnnounce", "happ_announce", "announce"])
        .or_else(|| {
            settings.get("happ").and_then(|happ| {
                first_string(happ, &["happAnnounce", "happ_announce", "announce"])
            })
        })
        .or_else(|| {
            settings.get("hwidSettings").and_then(|hwid| {
                first_string(hwid, &["maxDevicesAnnounce", "max_devices_announce"])
            })
        })
        .or_else(|| {
            root.get("happ").and_then(|happ| {
                first_string(happ, &["happAnnounce", "happ_announce", "announce"])
            })
        })
        .or_else(|| deep_find_string(settings, &["happAnnounce", "happ_announce", "announce"]))
        .or_else(|| deep_find_string(root, &["happAnnounce", "happ_announce", "announce"]));

    let support_url = first_url(
        settings,
        &["supportLink", "support_link", "supportUrl", "support_url"],
    )
    .or_else(|| first_url(
        root,
        &["supportUrl", "support_url", "support", "telegramUrl", "telegram_url"],
    ))
    .or_else(|| first_url(user, &["supportUrl", "support_url", "telegramUrl", "telegram_url"]));

    let website_url = first_url(
        settings,
        &["websiteUrl", "website_url", "webPageUrl", "web_page_url", "profileWebPageUrl", "profile_web_page_url"],
    )
    .or_else(|| first_url(settings, &["webpageUrl", "webpage_url"]))
    .or_else(|| first_url(
        root,
        &["websiteUrl", "website_url", "webPageUrl", "web_page_url", "profileWebPageUrl"],
    ));

    RemnawaveInfo {
        username,
        description,
        support_url,
        website_url,
        info: parse_remnawave_userinfo(user).or_else(|| parse_remnawave_userinfo(root)),
        server_descriptions: collect_server_descriptions(root),
    }
}

#[derive(Debug, Clone)]
struct ServerDescriptionCandidate {
    description: Option<String>,
    address: Option<String>,
    port: Option<u16>,
    uuid: Option<String>,
    xray_json_template_uuid: Option<String>,
}

fn collect_server_descriptions(value: &Value) -> Vec<ServerDescriptionCandidate> {
    let mut out = Vec::new();
    collect_server_descriptions_inner(value, &mut out);
    out
}

fn collect_server_descriptions_inner(
    value: &Value,
    out: &mut Vec<ServerDescriptionCandidate>,
) {
    match value {
        Value::Object(map) => {
            if let Some(description) = server_description_from_object(value) {
                out.push(ServerDescriptionCandidate {
                    description: Some(description),
                    address: host_local_string(
                        value,
                        &["address", "host", "serverAddress", "server_address"],
                    ),
                    port: host_local_u64(value, &["port", "serverPort", "server_port"])
                        .and_then(|p| u16::try_from(p).ok()),
                    uuid: host_local_string(
                        value,
                        &[
                            "uuid",
                            "hostUuid",
                            "host_uuid",
                            "host-uuid",
                            "inboundUuid",
                            "inbound_uuid",
                            "inbound-uuid",
                            "serverUuid",
                            "server_uuid",
                            "server-uuid",
                            "id",
                        ],
                    ),
                    xray_json_template_uuid: host_local_string(
                        value,
                        &["xrayJsonTemplateUuid", "xray_json_template_uuid"],
                    )
                    .filter(|uuid| !uuid.eq_ignore_ascii_case("null")),
                });
            }

            for (key, nested) in map {
                let normalized = normalize_json_key(key);
                // host-local lookups уже подобрали поля из meta/clientOverrides,
                // повторно туда не лезем — иначе захватим чужие name/uuid из дочерних объектов.
                if normalized == "meta"
                    || normalized == "clientoverrides"
                {
                    continue;
                }
                collect_server_descriptions_inner(nested, out);
            }
        }
        Value::Array(items) => {
            for nested in items {
                collect_server_descriptions_inner(nested, out);
            }
        }
        _ => {}
    }
}

fn host_local_string(value: &Value, keys: &[&str]) -> Option<String> {
    if let Some(found) = first_string_any_case(value, keys) {
        return Some(found);
    }
    if let Some(meta) = get_case_insensitive(value, "meta") {
        if let Some(found) = first_string_any_case(meta, keys) {
            return Some(found);
        }
    }
    if let Some(overrides) = get_case_insensitive(value, "clientOverrides")
        .or_else(|| get_case_insensitive(value, "client_overrides"))
    {
        if let Some(found) = first_string_any_case(overrides, keys) {
            return Some(found);
        }
    }
    None
}

fn host_local_u64(value: &Value, keys: &[&str]) -> Option<u64> {
    if let Some(found) = first_u64_any_case(value, keys) {
        return Some(found);
    }
    if let Some(meta) = get_case_insensitive(value, "meta") {
        if let Some(found) = first_u64_any_case(meta, keys) {
            return Some(found);
        }
    }
    None
}

fn server_description_from_object(value: &Value) -> Option<String> {
    first_encoded_string_any_case(
        value,
        &[
            "serverDescription",
            "server_description",
            "server-description",
            "serverDesc",
            "server_desc",
            "hostDescription",
            "host_description",
        ],
    )
    .or_else(|| {
        get_case_insensitive(value, "clientOverrides")
            .or_else(|| get_case_insensitive(value, "client_overrides"))
            .and_then(|overrides| {
                first_encoded_string_any_case(
                    overrides,
                    &["serverDescription", "server_description", "server-description", "serverDesc", "server_desc"],
                )
            })
    })
    .or_else(|| {
        get_case_insensitive(value, "meta").and_then(|meta| {
            first_encoded_string_any_case(
                meta,
                &[
                    "serverDescription",
                    "server_description",
                    "server-description",
                    "serverDesc",
                    "server_desc",
                ],
            )
        })
    })
}

fn apply_server_descriptions(
    servers: &mut [Server],
    candidates: &[ServerDescriptionCandidate],
) -> usize {
    if candidates.is_empty() {
        return 0;
    }

    let address_counts = server_address_counts(servers);
    let mut applied = 0;
    for server in servers.iter_mut() {
        let matched = candidates
            .iter()
            .find(|candidate| server_description_matches(candidate, server))
            .or_else(|| {
                candidates
                    .iter()
                    .find(|candidate| {
                        server_description_matches_unique_address(candidate, server, &address_counts)
                    })
            });

        let Some(candidate) = matched else {
            continue;
        };

        let mut changed = false;
        if let Some(host_uuid) = candidate
            .uuid
            .as_deref()
            .map(str::trim)
            .filter(|value| !value.is_empty())
        {
            if server.host_uuid.is_none() {
                server.host_uuid = Some(host_uuid.to_string());
                changed = true;
            }
        }
        if let Some(template_uuid) = &candidate.xray_json_template_uuid {
            if server.xray_json_template_uuid.is_none() {
                server.xray_json_template_uuid = Some(template_uuid.clone());
                changed = true;
            }
        }
        if server
            .server_description
            .as_ref()
            .and_then(|value| sanitize_name(Some(value.clone())))
            .is_none()
        {
            if let Some(description) = &candidate.description {
                if !same_text(description, &server.name) || server.name.is_empty() {
                    server.server_description = Some(description.clone());
                    changed = true;
                }
            }
        }

        if changed {
            applied += 1;
        }
    }

    applied
}

fn server_description_matches(candidate: &ServerDescriptionCandidate, server: &Server) -> bool {
    let (address, port, uuid) = server_identity(server);

    if let (Some(candidate_template_uuid), Some(server_template_uuid)) = (
        &candidate.xray_json_template_uuid,
        server.xray_json_template_uuid.as_deref(),
    ) {
        if same_text(candidate_template_uuid, server_template_uuid) {
            return true;
        }
    }

    if let (Some(candidate_uuid), Some(server_uuid)) = (&candidate.uuid, uuid) {
        if same_text(candidate_uuid, server_uuid) {
            return true;
        }
    }

    if let (Some(candidate_uuid), Some(server_host_uuid)) =
        (&candidate.uuid, server.host_uuid.as_deref())
    {
        if same_text(candidate_uuid, server_host_uuid) {
            return true;
        }
    }

    if let (Some(candidate_address), Some(candidate_port)) = (&candidate.address, candidate.port) {
        if same_text(candidate_address, address) && candidate_port == port {
            return true;
        }
    }

    false
}

fn server_description_matches_unique_address(
    candidate: &ServerDescriptionCandidate,
    server: &Server,
    address_counts: &HashMap<String, usize>,
) -> bool {
    let Some(candidate_address) = &candidate.address else {
        return false;
    };
    if candidate.port.is_some() {
        return false;
    }

    let (address, _, _) = server_identity(server);
    if !same_text(candidate_address, address) {
        return false;
    }

    address_counts
        .get(&normalized_identity_text(address))
        .copied()
        .unwrap_or_default()
        == 1
}

fn server_address_counts(servers: &[Server]) -> HashMap<String, usize> {
    let mut counts = HashMap::new();
    for server in servers {
        let (address, _, _) = server_identity(server);
        *counts.entry(normalized_identity_text(address)).or_insert(0) += 1;
    }
    counts
}

fn server_identity(server: &Server) -> (&str, u16, Option<&str>) {
    match &server.protocol {
        Protocol::Vless(cfg) => (&cfg.address, cfg.port, Some(cfg.uuid.as_str())),
        Protocol::Vmess(cfg) => (&cfg.address, cfg.port, Some(cfg.uuid.as_str())),
        Protocol::Trojan(cfg) => (&cfg.address, cfg.port, None),
        Protocol::Shadowsocks(cfg) => (&cfg.address, cfg.port, None),
    }
}

fn same_text(left: &str, right: &str) -> bool {
    normalized_identity_text(left) == normalized_identity_text(right)
}

fn normalized_identity_text(value: &str) -> String {
    value.trim().to_lowercase()
}

fn parse_remnawave_userinfo(value: &Value) -> Option<SubscriptionInfo> {
    let upload = first_u64(value, &["upload", "up", "trafficUpload", "traffic_upload"]);
    let download = first_u64(value, &[
        "download",
        "down",
        "trafficDownload",
        "traffic_download",
        "usedTrafficBytes",
        "used_traffic_bytes",
        "trafficUsedBytes",
        "traffic_used_bytes",
    ]);
    let total = first_u64(value, &[
        "total",
        "trafficLimitBytes",
        "traffic_limit_bytes",
        "trafficLimit",
        "traffic_limit",
    ]);
    let expire = first_u64(value, &["expire", "expiresAtTimestamp", "expires_at_timestamp"])
        .or_else(|| first_string(value, &["expire", "expiresAt", "expires_at"]).and_then(parse_unix_like))
        .and_then(|value| i64::try_from(value).ok());

    if upload.is_none() && download.is_none() && total.is_none() && expire.is_none() {
        None
    } else {
        Some(SubscriptionInfo {
            upload,
            download,
            total,
            expire,
        })
    }
}

fn first_string(value: &Value, keys: &[&str]) -> Option<String> {
    for key in keys {
        if let Some(text) = value.get(*key).and_then(Value::as_str) {
            if let Some(cleaned) = sanitize_name(Some(text.to_string())) {
                return Some(cleaned);
            }
        }
    }
    None
}

fn first_string_any_case(value: &Value, keys: &[&str]) -> Option<String> {
    for key in keys {
        if let Some(text) = get_case_insensitive(value, key).and_then(Value::as_str) {
            if let Some(cleaned) = sanitize_name(Some(text.to_string())) {
                return Some(cleaned);
            }
        }
    }
    None
}

fn first_encoded_string_any_case(value: &Value, keys: &[&str]) -> Option<String> {
    for key in keys {
        if let Some(text) = get_case_insensitive(value, key).and_then(Value::as_str) {
            if let Some(cleaned) = parse_header_value(text) {
                return Some(cleaned);
            }
        }
    }
    None
}

fn deep_find_string(value: &Value, keys: &[&str]) -> Option<String> {
    if let Some(found) = first_string(value, keys) {
        return Some(found);
    }

    match value {
        Value::Object(map) => {
            for nested in map.values() {
                if let Some(found) = deep_find_string(nested, keys) {
                    return Some(found);
                }
            }
            None
        }
        Value::Array(items) => {
            for nested in items {
                if let Some(found) = deep_find_string(nested, keys) {
                    return Some(found);
                }
            }
            None
        }
        _ => None,
    }
}

fn first_url(value: &Value, keys: &[&str]) -> Option<String> {
    let text = first_string(value, keys)?;
    if url::Url::parse(&text).is_ok() {
        Some(text)
    } else {
        None
    }
}

fn first_u64(value: &Value, keys: &[&str]) -> Option<u64> {
    for key in keys {
        if let Some(raw) = value.get(*key) {
            if let Some(n) = raw.as_u64() {
                return Some(n);
            }
            if let Some(s) = raw.as_str().and_then(|s| s.trim().parse::<u64>().ok()) {
                return Some(s);
            }
        }
    }
    None
}

fn first_u64_any_case(value: &Value, keys: &[&str]) -> Option<u64> {
    for key in keys {
        if let Some(raw) = get_case_insensitive(value, key) {
            if let Some(n) = raw.as_u64() {
                return Some(n);
            }
            if let Some(s) = raw.as_str().and_then(|s| s.trim().parse::<u64>().ok()) {
                return Some(s);
            }
        }
    }
    None
}

fn get_case_insensitive<'a>(value: &'a Value, key: &str) -> Option<&'a Value> {
    let Value::Object(map) = value else {
        return None;
    };
    map.get(key).or_else(|| {
        let normalized = normalize_json_key(key);
        map.iter()
            .find(|(candidate, _)| normalize_json_key(candidate) == normalized)
            .map(|(_, value)| value)
    })
}

fn normalize_json_key(value: &str) -> String {
    value
        .chars()
        .filter(|ch| *ch != '-' && *ch != '_')
        .flat_map(char::to_lowercase)
        .collect()
}

fn parse_unix_like(value: String) -> Option<u64> {
    let trimmed = value.trim();
    if let Ok(n) = trimmed.parse::<u64>() {
        return Some(n);
    }
    None
}

fn merge_info(primary: Option<SubscriptionInfo>, secondary: Option<SubscriptionInfo>) -> Option<SubscriptionInfo> {
    match (primary, secondary) {
        (Some(mut a), Some(b)) => {
            a.upload = a.upload.or(b.upload);
            a.download = a.download.or(b.download);
            a.total = a.total.or(b.total);
            a.expire = a.expire.or(b.expire);
            Some(a)
        }
        (Some(a), None) => Some(a),
        (None, Some(b)) => Some(b),
        (None, None) => None,
    }
}

fn merge_remnawave_info(primary: RemnawaveInfo, secondary: RemnawaveInfo) -> RemnawaveInfo {
    let RemnawaveInfo {
        username,
        description,
        support_url,
        website_url,
        info,
        mut server_descriptions,
    } = primary;
    let RemnawaveInfo {
        username: secondary_username,
        description: secondary_description,
        support_url: secondary_support_url,
        website_url: secondary_website_url,
        info: secondary_info,
        server_descriptions: secondary_server_descriptions,
    } = secondary;

    server_descriptions.extend(secondary_server_descriptions);

    RemnawaveInfo {
        username: username.or(secondary_username),
        description: description.or(secondary_description),
        support_url: support_url.or(secondary_support_url),
        website_url: website_url.or(secondary_website_url),
        info: merge_info(info, secondary_info),
        server_descriptions,
    }
}

fn extract_header_text(headers: &reqwest::header::HeaderMap, keys: &[&str]) -> Option<String> {
    for key in keys {
        if let Some(raw) = headers.get(*key).and_then(|v| v.to_str().ok()) {
            if let Some(text) = parse_header_value(raw) {
                return Some(text);
            }
        }
    }
    None
}

fn extract_announce_header(headers: &reqwest::header::HeaderMap) -> Option<String> {
    const ANNOUNCE_HEADERS: &[&str] = &[
        "announce",
        "profile-announce",
        "subscription-announce",
        "happ-announce",
        "happannounce",
        "x-announce",
        "x-profile-announce",
        "x-subscription-announce",
        "x-happ-announce",
    ];

    if let Some(value) = extract_header_text(headers, ANNOUNCE_HEADERS) {
        return Some(value);
    }

    for (name, raw) in headers {
        let lower = name.as_str().to_ascii_lowercase();
        if !lower.contains("announce") {
            continue;
        }
        if let Ok(value) = raw.to_str() {
            if let Some(text) = parse_header_value(value) {
                return Some(text);
            }
        }
    }

    None
}

fn extract_header_url(headers: &reqwest::header::HeaderMap, keys: &[&str]) -> Option<String> {
    let value = extract_header_text(headers, keys)?;
    if url::Url::parse(&value).is_ok() {
        Some(value)
    } else {
        None
    }
}

fn parse_header_value(value: &str) -> Option<String> {
    let raw = value.trim().trim_matches('"');
    if raw.is_empty() {
        return None;
    }

    if let Some(encoded) = raw.strip_prefix("base64:") {
        if let Some(decoded) = decode_base64_string(encoded) {
            return sanitize_name(Some(decoded));
        }
    }

    let decoded = percent_decode_str(raw).decode_utf8_lossy().to_string();
    sanitize_name(Some(decoded))
}

fn parse_profile_title(value: &str) -> Option<String> {
    let raw = value.trim().trim_matches('"');
    if raw.is_empty() {
        return None;
    }

    if let Some(encoded) = raw.strip_prefix("base64:") {
        if let Some(decoded) = decode_base64_string(encoded) {
            return sanitize_name(Some(decoded));
        }
    }

    if let Some(decoded) = decode_base64_string(raw) {
        if let Some(name) = sanitize_name(Some(decoded)) {
            return Some(name);
        }
    }

    sanitize_name(Some(percent_decode_str(raw).decode_utf8_lossy().to_string()))
}

fn parse_content_disposition_filename(value: &str) -> Option<String> {
    // Examples: attachment; filename="name.txt"
    for part in value.split(';') {
        let trimmed = part.trim();
        if let Some(name) = trimmed.strip_prefix("filename=") {
            let decoded = percent_decode_str(name.trim_matches('"'))
                .decode_utf8_lossy()
                .to_string();
            if let Some(cleaned) = sanitize_name(Some(strip_known_extensions(&decoded))) {
                return Some(cleaned);
            }
        }
    }
    None
}

fn infer_name_from_url(url: &str) -> Option<String> {
    let parsed = url::Url::parse(url).ok()?;

    let fragment = parsed.fragment().and_then(|f| sanitize_name(Some(f.to_string())));
    if fragment.is_some() {
        return fragment;
    }

    let last = parsed
        .path_segments()
        .and_then(|segments| segments.last())
        .map(strip_known_extensions)
        .and_then(|s| sanitize_name(Some(s)));

    last
}

fn decode_base64_string(value: &str) -> Option<String> {
    let clean = value.trim();
    if clean.is_empty() {
        return None;
    }

    let engines = [
        &base64::engine::general_purpose::STANDARD,
        &base64::engine::general_purpose::STANDARD_NO_PAD,
        &base64::engine::general_purpose::URL_SAFE,
        &base64::engine::general_purpose::URL_SAFE_NO_PAD,
    ];

    for engine in engines {
        if let Ok(bytes) = engine.decode(clean) {
            if let Ok(text) = String::from_utf8(bytes) {
                if let Some(name) = sanitize_name(Some(text)) {
                    return Some(name);
                }
            }
        }
    }
    None
}

fn strip_known_extensions(value: &str) -> String {
    let lower = value.to_lowercase();
    for ext in [".txt", ".json", ".yaml", ".yml"] {
        if lower.ends_with(ext) && value.len() > ext.len() {
            return value[..value.len() - ext.len()].to_string();
        }
    }
    value.to_string()
}

fn sanitize_name(name: Option<String>) -> Option<String> {
    let trimmed = name?.trim().to_string();
    if trimmed.is_empty() {
        None
    } else {
        Some(trimmed)
    }
}

fn insert_header(headers: &mut reqwest::header::HeaderMap, name: &'static str, value: &str) {
    if value.is_empty() {
        return;
    }
    if let Ok(v) = reqwest::header::HeaderValue::from_str(value) {
        headers.insert(name, v);
    } else {
        tracing::debug!(name, "header value contains non-ascii chars; skipping");
    }
}

fn now_unix() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

fn truncate(s: &str, max: usize) -> String {
    if s.len() <= max {
        s.to_string()
    } else {
        format!("{}…", &s[..max])
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn happ_compatible_user_agent_keeps_nimbo_identity() {
        assert_eq!(
            happ_compatible_user_agent("Nimbo/0.1.0"),
            "Happ/2.0.0 Nimbo/0.1.0"
        );
    }

    #[test]
    fn happ_compatible_user_agent_does_not_duplicate_happ() {
        assert_eq!(
            happ_compatible_user_agent("Happ/2.0.0"),
            "Happ/2.0.0"
        );
    }

    #[test]
    fn extracts_multiple_xray_templates_by_uuid() {
        let json = json!({
            "response": {
                "templates": [
                    {
                        "uuid": "tpl-a",
                        "templateJson": {
                            "routing": { "rules": [] },
                            "outbounds": [{ "tag": "proxy", "protocol": "freedom" }]
                        }
                    },
                    {
                        "xrayJsonTemplateUuid": "tpl-b",
                        "outbounds": [{ "tag": "proxy", "protocol": "freedom" }]
                    }
                ]
            }
        });

        let templates = extract_xray_templates_from_value(&json);

        assert!(templates.contains_key("tpl-a"));
        assert!(templates.contains_key("tpl-b"));
    }

    #[test]
    fn remnawave_remark_is_not_used_as_server_description() {
        let mut servers = parse_aggregate(
            "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@example.com:443?type=tcp&security=tls#Node-1",
        )
        .unwrap();
        let json = json!({
            "response": {
                "hosts": [
                    {
                        "uuid": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                        "remark": "VLESS World",
                        "address": "example.com",
                        "port": 443,
                        "serverDescription": null
                    }
                ]
            }
        });

        let info = parse_remnawave_info(&json);
        let applied = apply_server_descriptions(&mut servers, &info.server_descriptions);

        assert_eq!(applied, 0);
        assert_eq!(servers[0].server_description, None);
    }

    #[test]
    fn remnawave_server_description_matches_host_uuid() {
        let mut servers = parse_aggregate(
            "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@example.com:443?type=tcp&security=tls&hostUuid=host-1#Node-1",
        )
        .unwrap();
        let json = json!({
            "response": {
                "hosts": [
                    {
                        "hostUuid": "host-1",
                        "serverDescription": "Custom node description"
                    }
                ]
            }
        });

        let info = parse_remnawave_info(&json);
        let applied = apply_server_descriptions(&mut servers, &info.server_descriptions);

        assert_eq!(applied, 1);
        assert_eq!(
            servers[0].server_description.as_deref(),
            Some("Custom node description")
        );
    }

    #[test]
    fn remnawave_server_description_matches_xray_template_uuid() {
        let mut servers = parse_aggregate(
            "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@example.com:443?type=tcp&security=tls&xrayJsonTemplateUuid=tpl-1#Node-1",
        )
        .unwrap();
        let json = json!({
            "response": {
                "hosts": [
                    {
                        "xrayJsonTemplateUuid": "tpl-1",
                        "clientOverrides": {
                            "serverDescription": "Template-linked description"
                        }
                    }
                ]
            }
        });

        let info = parse_remnawave_info(&json);
        let applied = apply_server_descriptions(&mut servers, &info.server_descriptions);

        assert_eq!(applied, 1);
        assert_eq!(
            servers[0].server_description.as_deref(),
            Some("Template-linked description")
        );
    }

    #[test]
    fn candidate_without_identifying_fields_is_not_applied_by_name() {
        let mut servers = parse_aggregate(
            "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@example.com:443?type=tcp&security=tls#Berlin",
        )
        .unwrap();
        // Кандидат с тем же именем что у сервера, но без uuid/address/port —
        // раньше это давало ложный матч по name.
        let json = json!({
            "response": {
                "items": [
                    {
                        "remarks": "Berlin",
                        "serverDescription": "ВЛЕСС-узел из чужой подписки"
                    }
                ]
            }
        });

        let info = parse_remnawave_info(&json);
        let applied = apply_server_descriptions(&mut servers, &info.server_descriptions);

        assert_eq!(applied, 0);
        assert_eq!(servers[0].server_description, None);
    }

    #[test]
    fn host_local_lookup_does_not_borrow_uuid_from_nested_object() {
        let mut servers = parse_aggregate(
            "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@example.com:443?type=tcp&security=tls&hostUuid=host-1#Node",
        )
        .unwrap();
        // Description у внешнего объекта; uuid `host-1` лежит глубже,
        // в дочернем объекте (settings.user.id). Это НЕ должно матчить.
        let json = json!({
            "response": {
                "hosts": [
                    {
                        "serverDescription": "Чужое описание",
                        "settings": {
                            "user": { "id": "host-1" }
                        }
                    }
                ]
            }
        });

        let info = parse_remnawave_info(&json);
        let applied = apply_server_descriptions(&mut servers, &info.server_descriptions);

        assert_eq!(applied, 0);
        assert_eq!(servers[0].server_description, None);
    }

    #[test]
    fn host_local_lookup_picks_uuid_from_meta() {
        let mut servers = parse_aggregate(
            "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@example.com:443?type=tcp&security=tls&hostUuid=host-1#Node",
        )
        .unwrap();
        let json = json!({
            "response": {
                "hosts": [
                    {
                        "serverDescription": "Описание с meta.hostUuid",
                        "meta": { "hostUuid": "host-1" }
                    }
                ]
            }
        });

        let info = parse_remnawave_info(&json);
        let applied = apply_server_descriptions(&mut servers, &info.server_descriptions);

        assert_eq!(applied, 1);
        assert_eq!(
            servers[0].server_description.as_deref(),
            Some("Описание с meta.hostUuid")
        );
    }

    #[test]
    fn remnawave_info_urls_keep_subscription_query() {
        let urls = subscription_api_info_urls(
            "https://example.com/api/sub/e37b73d0-ec4d-4b49-b4cd-86685d6703ee?token=abc",
        );

        assert_eq!(
            urls.first().map(String::as_str),
            Some("https://example.com/api/sub/e37b73d0-ec4d-4b49-b4cd-86685d6703ee/happ?token=abc")
        );
        assert!(urls.iter().any(|url| {
            url == "https://example.com/api/sub/e37b73d0-ec4d-4b49-b4cd-86685d6703ee/info?token=abc"
        }));
        assert!(urls.iter().any(|url| {
            url == "https://example.com/api/sub/e37b73d0-ec4d-4b49-b4cd-86685d6703ee/json?token=abc"
        }));
        // admin-only endpoints (subscription-settings, by-short-uuid) больше не запрашиваются
        assert!(!urls.iter().any(|url| url.contains("/api/subscription-settings")));
        assert!(!urls.iter().any(|url| url.contains("/api/subscriptions/by-short-uuid/")));
    }

    #[test]
    fn remnawave_info_urls_support_short_public_paths() {
        let urls = subscription_api_info_urls("https://example.com/short-id?token=abc");

        assert!(urls
            .iter()
            .position(|url| url == "https://example.com/short-id/happ?token=abc")
            .is_some_and(|pos| pos < urls.len() - 1));
        assert!(urls
            .iter()
            .any(|url| url == "https://example.com/short-id/info?token=abc"));
        assert!(urls
            .iter()
            .any(|url| url == "https://example.com/short-id/json?token=abc"));
    }
}
