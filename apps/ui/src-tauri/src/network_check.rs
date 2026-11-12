//! «Проверка сети»: доступность контрольных адресов, пинг и скорость.
//!
//! Трафик идёт тем же путём, что и у браузера: при подключении в режиме
//! системного прокси — через локальный HTTP-вход Nimbo, в TUN — напрямую,
//! системный маршрут уже ведёт в туннель.

use std::time::{Duration, Instant};

use base64::Engine;
use serde::Serialize;
use tauri::State;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;

use crate::commands::{get_status, ConnectionState};
use crate::state::AppState;

const USER_AGENT: &str = concat!("Nimbo/", env!("CARGO_PKG_VERSION"), "/desktop");
const DOWNLOAD_BYTES: u64 = 25_000_000;
const UPLOAD_BYTES: usize = 8_000_000;
const PING_ATTEMPTS: usize = 5;

#[derive(Clone)]
struct ProxyRoute {
    port: u16,
    auth: Option<(String, String)>,
}

/// Local HTTP proxy only when the connection is proxy-only; TUN already
/// captures ordinary traffic.
fn route(state: &State<'_, AppState>) -> Option<ProxyRoute> {
    let status = get_status(state.clone());
    let connected = matches!(status.state, ConnectionState::Connected);
    if !connected || status.connection_mode.uses_tun() {
        return None;
    }
    let auth = (status.require_socks_auth && !status.socks_username.is_empty())
        .then(|| (status.socks_username.clone(), status.socks_password.clone()));
    Some(ProxyRoute {
        port: status.http_port,
        auth,
    })
}

fn client(route: &Option<ProxyRoute>, timeout: Duration) -> Result<reqwest::Client, String> {
    let mut builder = reqwest::Client::builder()
        .no_proxy()
        .timeout(timeout)
        .connect_timeout(Duration::from_secs(4))
        .user_agent(USER_AGENT);
    if let Some(route) = route {
        let mut proxy = reqwest::Proxy::all(format!("http://127.0.0.1:{}", route.port))
            .map_err(|_| "Некорректный локальный прокси")?;
        if let Some((user, pass)) = &route.auth {
            proxy = proxy.basic_auth(user, pass);
        }
        builder = builder.proxy(proxy);
    }
    builder
        .build()
        .map_err(|_| "Не удалось подготовить HTTP-клиент".into())
}

#[derive(Clone, Copy)]
enum Probe {
    Https {
        url: &'static str,
        exact: Option<u16>,
        client_errors: bool,
    },
    Tcp {
        host: &'static str,
        port: u16,
    },
}

const TARGETS: &[(&str, &str, Probe)] = &[
    (
        "Google",
        "international",
        Probe::Https {
            url: "https://www.google.com/generate_204",
            exact: Some(204),
            client_errors: false,
        },
    ),
    (
        "Cloudflare",
        "international",
        Probe::Https {
            url: "https://cp.cloudflare.com/generate_204",
            exact: Some(204),
            client_errors: false,
        },
    ),
    (
        "GitHub",
        "international",
        Probe::Https {
            url: "https://github.com/",
            exact: None,
            client_errors: false,
        },
    ),
    (
        "Telegram",
        "international",
        Probe::Https {
            url: "https://telegram.org/",
            exact: None,
            client_errors: false,
        },
    ),
    (
        "Яндекс",
        "local",
        Probe::Https {
            url: "https://ya.ru/",
            exact: None,
            client_errors: false,
        },
    ),
    (
        "VK",
        "local",
        Probe::Https {
            url: "https://vk.com/",
            exact: None,
            client_errors: false,
        },
    ),
    (
        "Google Analytics",
        "telemetry",
        Probe::Https {
            url: "https://www.google-analytics.com/g/collect",
            exact: None,
            client_errors: true,
        },
    ),
    (
        "Google Static",
        "telemetry",
        Probe::Https {
            url: "https://www.gstatic.com/generate_204",
            exact: Some(204),
            client_errors: false,
        },
    ),
    (
        "Яндекс Метрика",
        "telemetry",
        Probe::Https {
            url: "https://mc.yandex.ru/watch/0",
            exact: None,
            client_errors: true,
        },
    ),
    (
        "DNS Google",
        "infra",
        Probe::Tcp {
            host: "8.8.8.8",
            port: 53,
        },
    ),
    (
        "DNS Cloudflare",
        "infra",
        Probe::Tcp {
            host: "1.1.1.1",
            port: 53,
        },
    ),
];

#[derive(Serialize)]
pub struct NetworkCheckItem {
    id: String,
    group: String,
    available: bool,
    elapsed_ms: Option<u64>,
    detail: String,
}

#[derive(Serialize)]
pub struct NetworkCheckReport {
    via_proxy: bool,
    items: Vec<NetworkCheckItem>,
}

fn elapsed(start: Instant) -> u64 {
    (start.elapsed().as_millis() as u64).max(1)
}

async fn probe_https(
    client: &reqwest::Client,
    url: &str,
    exact: Option<u16>,
    client_errors: bool,
) -> (bool, Option<u64>, String) {
    let start = Instant::now();
    match client
        .get(url)
        .header("Cache-Control", "no-cache")
        .send()
        .await
    {
        Ok(response) => {
            let code = response.status().as_u16();
            let same_host = response.url().host_str()
                == reqwest::Url::parse(url)
                    .ok()
                    .as_ref()
                    .and_then(|u| u.host_str());
            let accepted = match exact {
                Some(expected) => code == expected,
                None => (200..400).contains(&code) || (client_errors && (400..500).contains(&code)),
            };
            let ok = same_host && accepted;
            let detail = if same_host {
                format!("HTTP {code}")
            } else {
                "Переадресация".into()
            };
            (ok, ok.then(|| elapsed(start)), detail)
        }
        Err(error) => (
            false,
            None,
            if error.is_timeout() {
                "Таймаут".into()
            } else {
                "Нет ответа".into()
            },
        ),
    }
}

/// TCP handshake to host:port; through the proxy this is an HTTP CONNECT,
/// so the time covers the VPN path rather than the local network.
async fn tcp_time(
    route: &Option<ProxyRoute>,
    host: &str,
    port: u16,
    timeout: Duration,
) -> Option<u64> {
    let start = Instant::now();
    let attempt = async {
        match route {
            None => TcpStream::connect((host, port)).await.ok().map(|_| ()),
            Some(route) => {
                let mut stream = TcpStream::connect(("127.0.0.1", route.port)).await.ok()?;
                let mut request =
                    format!("CONNECT {host}:{port} HTTP/1.1\r\nHost: {host}:{port}\r\n");
                if let Some((user, pass)) = &route.auth {
                    let token =
                        base64::engine::general_purpose::STANDARD.encode(format!("{user}:{pass}"));
                    request.push_str(&format!("Proxy-Authorization: Basic {token}\r\n"));
                }
                request.push_str("\r\n");
                stream.write_all(request.as_bytes()).await.ok()?;
                let mut buffer = [0u8; 512];
                let mut read = 0;
                while read < buffer.len() {
                    let n = stream.read(&mut buffer[read..]).await.ok()?;
                    if n == 0 {
                        return None;
                    }
                    read += n;
                    if buffer[..read].windows(4).any(|w| w == b"\r\n\r\n") {
                        break;
                    }
                }
                let head = String::from_utf8_lossy(&buffer[..read]);
                head.split_whitespace()
                    .nth(1)
                    .filter(|code| *code == "200")
                    .map(|_| ())
            }
        }
    };
    tokio::time::timeout(timeout, attempt)
        .await
        .ok()
        .flatten()
        .map(|_| elapsed(start))
}

#[tauri::command]
pub async fn network_check_run(state: State<'_, AppState>) -> Result<NetworkCheckReport, String> {
    let route = route(&state);
    let client = client(&route, Duration::from_millis(3500))?;
    let tasks = TARGETS.iter().map(|(id, group, probe)| {
        let client = client.clone();
        let route = route.clone();
        async move {
            let (available, elapsed_ms, detail) = match *probe {
                Probe::Https {
                    url,
                    exact,
                    client_errors,
                } => probe_https(&client, url, exact, client_errors).await,
                Probe::Tcp { host, port } => {
                    match tcp_time(&route, host, port, Duration::from_secs(3)).await {
                        Some(ms) => (true, Some(ms), "TCP".into()),
                        None => (false, None, "Нет ответа".into()),
                    }
                }
            };
            NetworkCheckItem {
                id: (*id).into(),
                group: (*group).into(),
                available,
                elapsed_ms,
                detail,
            }
        }
    });
    let items = futures_join_all(tasks).await;
    Ok(NetworkCheckReport {
        via_proxy: route.is_some(),
        items,
    })
}

async fn futures_join_all<
    F: std::future::Future<Output = T> + Send + 'static,
    T: Send + 'static,
>(
    tasks: impl Iterator<Item = F>,
) -> Vec<T> {
    let handles: Vec<_> = tasks.map(tokio::spawn).collect();
    let mut out = Vec::with_capacity(handles.len());
    for handle in handles {
        if let Ok(value) = handle.await {
            out.push(value);
        }
    }
    out
}

fn clean_host(input: &str) -> Result<String, String> {
    let trimmed = input.trim();
    let without_scheme = trimmed.split("://").last().unwrap_or(trimmed);
    let host = without_scheme
        .split(['/', '?', '#'])
        .next()
        .unwrap_or("")
        .trim();
    let host = host.rsplit_once('@').map(|(_, h)| h).unwrap_or(host);
    let host = if host.starts_with('[') {
        host
    } else {
        host.split(':').next().unwrap_or(host)
    };
    if host.is_empty()
        || host.len() > 253
        || !host
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || ".-[]:".contains(c))
    {
        return Err("Введите домен или IP-адрес".into());
    }
    Ok(host.trim_matches(['[', ']']).to_string())
}

#[derive(Serialize)]
pub struct NetworkPingReport {
    host: String,
    via_proxy: bool,
    samples: Vec<Option<u64>>,
}

#[tauri::command]
pub async fn network_ping(
    state: State<'_, AppState>,
    target: String,
) -> Result<NetworkPingReport, String> {
    let host = clean_host(&target)?;
    let route = route(&state);
    let mut samples = Vec::with_capacity(PING_ATTEMPTS);
    for attempt in 0..PING_ATTEMPTS {
        samples.push(tcp_time(&route, &host, 443, Duration::from_secs(3)).await);
        if attempt + 1 < PING_ATTEMPTS {
            tokio::time::sleep(Duration::from_millis(200)).await;
        }
    }
    Ok(NetworkPingReport {
        host,
        via_proxy: route.is_some(),
        samples,
    })
}

/// One speed-test stage: "latency" → best of three in ms, "download" and
/// "upload" → Mbit/s through speed.cloudflare.com.
#[tauri::command]
pub async fn network_speed(state: State<'_, AppState>, stage: String) -> Result<f64, String> {
    let route = route(&state);
    let client = client(&route, Duration::from_secs(40))?;
    let failed = |e: reqwest::Error| {
        if e.is_timeout() {
            "Сервер теста не ответил вовремя".to_string()
        } else {
            "Не удалось связаться с сервером теста".to_string()
        }
    };
    match stage.as_str() {
        "latency" => {
            let mut best = u64::MAX;
            for _ in 0..3 {
                let start = Instant::now();
                client
                    .get("https://speed.cloudflare.com/__down?bytes=0")
                    .send()
                    .await
                    .map_err(failed)?;
                best = best.min(elapsed(start));
            }
            Ok(best as f64)
        }
        "download" => {
            let start = Instant::now();
            let mut response = client
                .get(format!(
                    "https://speed.cloudflare.com/__down?bytes={DOWNLOAD_BYTES}"
                ))
                .send()
                .await
                .map_err(failed)?;
            let mut total = 0u64;
            while let Some(chunk) = response.chunk().await.map_err(failed)? {
                total += chunk.len() as u64;
            }
            Ok(mbps(total, start))
        }
        "upload" => {
            let start = Instant::now();
            client
                .post("https://speed.cloudflare.com/__up")
                .header("Content-Type", "application/octet-stream")
                .body(vec![0u8; UPLOAD_BYTES])
                .send()
                .await
                .map_err(failed)?;
            Ok(mbps(UPLOAD_BYTES as u64, start))
        }
        _ => Err("Неизвестный этап теста".into()),
    }
}

fn mbps(bytes: u64, start: Instant) -> f64 {
    let seconds = start.elapsed().as_secs_f64().max(0.001);
    (bytes as f64 * 8.0 / seconds / 1_000_000.0 * 10.0).round() / 10.0
}

#[cfg(test)]
mod tests {
    use super::clean_host;

    #[test]
    fn ping_target_accepts_hosts_urls_and_ips() {
        assert_eq!(clean_host(" google.com ").unwrap(), "google.com");
        assert_eq!(clean_host("https://ya.ru/path?q=1").unwrap(), "ya.ru");
        assert_eq!(clean_host("1.1.1.1:443").unwrap(), "1.1.1.1");
        assert_eq!(clean_host("user@telegram.org").unwrap(), "telegram.org");
        assert!(clean_host("").is_err());
        assert!(clean_host("bad host; rm").is_err());
    }
}
