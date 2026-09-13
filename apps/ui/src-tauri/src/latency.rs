//! HTTP latency is scoped to one owned core session, never the machine's route.
use nimbo_subscription::Server;
use serde_json::{json, Value};
use std::time::{Duration, Instant};

#[derive(Clone, PartialEq, Eq)]
pub(crate) struct PingRoute {
    pub server_id: String,
    pub port: u16,
    username: String,
    password: String,
}

impl PingRoute {
    pub fn prepare(server: &Server, config: &mut Value) -> Result<Self, String> {
        let listener = std::net::TcpListener::bind(("127.0.0.1", 0))
            .map_err(|_| "Cannot allocate Nimbo Ping route")?;
        let route = Self {
            server_id: server.id.clone(),
            port: listener.local_addr().map_err(|_| "Cannot read Nimbo Ping port")?.port(),
            username: uuid::Uuid::new_v4().to_string(),
            password: uuid::Uuid::new_v4().to_string(),
        };
        let mut candidate = config.clone();
        route.install(server, &mut candidate)?;
        *config = candidate;
        Ok(route)
    }

    fn install(&self, server: &Server, config: &mut Value) -> Result<(), String> {
        // Random tags avoid collisions with provider templates/modules. Build the
        // outbound from the prepared server, not a template's potentially foreign
        // proxy/balancer. AWG/Naive already contain their owned local SOCKS route.
        let inbound = format!("nimbo-ping-{}", self.username);
        let outbound = format!("{inbound}-out");
        let proxy = serde_json::to_value(
            nimbo_xray_config::outbound::server_to_outbound(server, &outbound),
        ).map_err(|_| "Cannot build Nimbo Ping outbound")?;
        // Keep target DNS remote even when a user's routing profile resolves IPs
        // locally. This direct inbound rule matches before those profile rules.
        config["outbounds"].as_array_mut().ok_or("Missing core outbounds")?.push(proxy);
        config["inbounds"].as_array_mut().ok_or("Missing core inbounds")?.push(json!({
            "tag": inbound, "listen": "127.0.0.1", "port": self.port,
            "protocol": "http", "settings": { "accounts": [{
                "user": self.username, "pass": self.password
            }] }, "sniffing": { "enabled": false }
        }));
        let routing = config.as_object_mut().ok_or("Invalid core config")?
            .entry("routing").or_insert_with(|| json!({})).as_object_mut().ok_or("Invalid core routing")?;
        routing.entry("rules").or_insert_with(|| json!([])).as_array_mut().ok_or("Invalid core routing rules")?
            .insert(0, json!({ "type": "field", "inboundTag": [inbound], "outboundTag": outbound }));
        Ok(())
    }

    pub fn accepts(&self, connected: bool, active_id: Option<&str>, requested_id: &str) -> bool {
        connected && active_id == Some(self.server_id.as_str()) && requested_id == self.server_id
    }
}

pub(crate) fn http_method(protocol: &str) -> Option<reqwest::Method> {
    match protocol {
        "nimbo" | "http_get" => Some(reqwest::Method::GET),
        "http_head" => Some(reqwest::Method::HEAD),
        _ => None,
    }
}

// Parse an actual echo reply, not process duration or an unreachable message
// (Windows can exit successfully for the latter). TTL is stable across locales.
pub(crate) fn icmp_rtt(output: &str) -> Option<u64> {
    for line in output.lines().filter(|line| line.to_ascii_lowercase().contains("ttl=")) {
        for token in line.split_whitespace() {
            let Some((name, value)) = token.split_once('=').or_else(|| token.split_once('<')) else { continue };
            if name.eq_ignore_ascii_case("ttl") { continue; }
            let number: String = value.chars().take_while(|c| c.is_ascii_digit() || *c == '.' || *c == ',').collect();
            // RTT has an ms suffix (Windows) or a separate unit (Unix). Byte
            // counts and sequence numbers are deliberately excluded.
            let lower = name.to_lowercase();
            if !(lower.contains("time") || lower.contains("время") || value.ends_with("ms") || value.ends_with("мс")) { continue; }
            if let Ok(ms) = number.replace(',', ".").parse::<f64>() {
                if ms.is_finite() && ms >= 0.0 { return Some(ms.round() as u64); }
            }
        }
    }
    None
}

pub(crate) async fn measure_http(route: &PingRoute, protocol: &str, test_url: &str, timeout_ms: u32) -> Result<u64, String> {
    let method = http_method(protocol).ok_or("Unsupported HTTP ping method")?;
    let url = url::Url::parse(test_url).map_err(|_| "Invalid test URL")?;
    if !matches!(url.scheme(), "http" | "https") || url.host_str().is_none()
        || !url.username().is_empty() || url.password().is_some() {
        return Err("Test URL must be HTTP(S) without credentials".into());
    }
    let proxy = reqwest::Proxy::all(format!("http://127.0.0.1:{}", route.port))
        .map_err(|_| "Invalid Nimbo Ping route")?.basic_auth(&route.username, &route.password);
    let client = reqwest::Client::builder().no_proxy().proxy(proxy)
        .redirect(reqwest::redirect::Policy::none())
        .timeout(Duration::from_millis(u64::from(timeout_ms)))
        .pool_max_idle_per_host(0)
        .build().map_err(|_| "Cannot create Nimbo Ping client")?;
    let start = Instant::now();
    let response = client.request(method, url).header(reqwest::header::CACHE_CONTROL, "no-cache")
        .send().await.map_err(|error| if error.is_timeout() { "timeout" } else { "HTTP probe failed through VPN route" })?;
    if !response.status().is_success() {
        return Err(format!("HTTP {}", response.status().as_u16()));
    }
    Ok(start.elapsed().as_millis().min(u128::from(u64::MAX)) as u64)
}

pub(crate) async fn measure_http_guarded(
    route: &PingRoute, protocol: &str, test_url: &str, timeout_ms: u32,
    valid: impl Fn() -> bool,
) -> Result<u64, String> {
    if !valid() { return Err("VPN route or ping settings changed".into()); }
    let probe = measure_http(route, protocol, test_url, timeout_ms);
    tokio::pin!(probe);
    loop {
        tokio::select! {
            result = &mut probe => return if valid() { result } else { Err("VPN route or ping settings changed".into()) },
            _ = tokio::time::sleep(Duration::from_millis(25)) => {
                if !valid() { return Err("VPN route or ping settings changed".into()); }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

    fn route(port: u16) -> PingRoute {
        PingRoute { server_id: "active".into(), port, username: "test-user".into(), password: "test-password".into() }
    }

    #[test]
    fn latency_icmp_requires_echo_reply_and_accepts_zero() {
        assert_eq!(icmp_rtt("Reply from 1.1.1.1: bytes=32 time=0ms TTL=57"), Some(0));
        assert_eq!(icmp_rtt("Ответ от 1.1.1.1: число байт=32 время=12мс TTL=57"), Some(12));
        assert_eq!(icmp_rtt("64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=12.3 ms"), Some(12));
        assert_eq!(icmp_rtt("Reply from 192.168.1.1: Destination host unreachable."), None);
        assert_eq!(icmp_rtt("Minimum = 0ms, Maximum = 0ms, Average = 0ms"), None);
    }

    #[test]
    fn latency_route_rejects_disconnected_and_unrelated_servers() {
        let route = route(1);
        assert!(route.accepts(true, Some("active"), "active"));
        assert!(!route.accepts(false, Some("active"), "active"));
        assert!(!route.accepts(true, Some("other"), "active"));
        assert!(!route.accepts(true, Some("active"), "other"));
        assert!(!route.accepts(true, None, "active"));
        assert_ne!(route.password, uuid::Uuid::new_v4().to_string());
    }

    async fn proxy_reply(status: &str) -> (PingRoute, tokio::task::JoinHandle<String>) {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let route = route(listener.local_addr().unwrap().port());
        let response = format!("HTTP/1.1 {status}\r\nContent-Length: 0\r\nConnection: close\r\nLocation: http://must-not-follow.invalid/\r\n\r\n");
        let task = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            let mut bytes = Vec::new();
            while !bytes.ends_with(b"\r\n\r\n") {
                bytes.push(socket.read_u8().await.unwrap());
            }
            socket.write_all(response.as_bytes()).await.unwrap();
            String::from_utf8(bytes).unwrap()
        });
        (route, task)
    }

    #[tokio::test]
    async fn latency_real_proxy_requests_preserve_strict_methods_and_remote_hostname() {
        for (protocol, method) in [("nimbo", "GET"), ("http_get", "GET"), ("http_head", "HEAD")] {
            let (route, request) = proxy_reply("204 No Content").await;
            measure_http(&route, protocol, "http://never-resolve-locally.invalid/check", 1000).await.unwrap();
            let request = request.await.unwrap();
            assert!(request.starts_with(&format!("{method} http://never-resolve-locally.invalid/check HTTP/1.1")));
            assert!(request.to_lowercase().contains("proxy-authorization: basic "));
        }
    }

    #[tokio::test]
    async fn latency_redirects_and_head_rejection_are_errors_without_method_fallback() {
        for status in ["302 Found", "405 Method Not Allowed", "500 Internal Server Error", "407 Proxy Authentication Required"] {
            let (route, request) = proxy_reply(status).await;
            assert!(measure_http(&route, "http_head", "http://test.invalid/", 1000).await.is_err());
            assert!(request.await.unwrap().starts_with("HEAD "));
        }
    }

    #[tokio::test]
    async fn latency_dead_proxy_never_contacts_direct_target() {
        let target = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let dead = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let route = route(dead.local_addr().unwrap().port());
        drop(dead);
        assert!(measure_http(&route, "nimbo", &format!("http://{}/", target.local_addr().unwrap()), 200).await.is_err());
        assert!(tokio::time::timeout(Duration::from_millis(40), target.accept()).await.is_err());
    }

    #[tokio::test]
    async fn latency_timeout_is_bounded() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let route = route(listener.local_addr().unwrap().port());
        let result = measure_http(&route, "nimbo", "https://test.invalid/", 50).await;
        assert_eq!(result.unwrap_err(), "timeout");
    }

    #[tokio::test]
    async fn latency_invalid_context_does_not_connect_and_changed_context_cancels_io() {
        use std::sync::{Arc, atomic::{AtomicBool, Ordering}};
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let route = route(listener.local_addr().unwrap().port());
        assert!(measure_http_guarded(&route, "nimbo", "http://test.invalid/", 1000, || false).await.is_err());
        assert!(tokio::time::timeout(Duration::from_millis(30), listener.accept()).await.is_err());
        let valid = Arc::new(AtomicBool::new(true));
        let changed = valid.clone();
        let peer = tokio::spawn(async move {
            let (mut stream, _) = listener.accept().await.unwrap();
            let mut request = Vec::new();
            while !request.ends_with(b"\r\n\r\n") { request.push(stream.read_u8().await.unwrap()); }
            changed.store(false, Ordering::SeqCst);
            let mut byte = [0];
            assert_eq!(tokio::time::timeout(Duration::from_secs(1), stream.read(&mut byte)).await.unwrap().unwrap(), 0);
        });
        let start = Instant::now();
        let error = measure_http_guarded(&route, "nimbo", "http://test.invalid/", 5000,
            || valid.load(Ordering::SeqCst)).await.unwrap_err();
        assert!(error.contains("changed"));
        assert!(start.elapsed() < Duration::from_secs(1));
        peer.await.unwrap();
    }
}
