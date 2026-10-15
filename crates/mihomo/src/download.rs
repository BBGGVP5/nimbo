//! Explicit one-shot import. URLs and response bodies never enter errors or storage.
use crate::profiles::{check_source, MAX_SOURCE_BYTES};
use reqwest::{header, redirect::Policy, Client};
use std::time::Duration;
use url::Url;

const MAX_URL_BYTES: usize = 8192;
const MAX_REDIRECTS: usize = 5;
const DEADLINE: Duration = Duration::from_secs(20);

fn source_url(input: &str) -> Result<Url, &'static str> {
    let input = input.trim();
    if input.len() > MAX_URL_BYTES
        || input.chars().any(char::is_whitespace)
        || input.chars().any(char::is_control)
    {
        return Err("INVALID_SOURCE_URL");
    }
    let url = Url::parse(input).map_err(|_| "INVALID_SOURCE_URL")?;
    if !matches!(url.scheme(), "http" | "https")
        || url.host_str().is_none()
        || !url.username().is_empty()
        || url.password().is_some()
        || url.fragment().is_some()
    {
        return Err("INVALID_SOURCE_URL");
    }
    Ok(url)
}

fn redirected_url(current: &Url, location: &str) -> Result<Url, &'static str> {
    let next = current
        .join(location)
        .map_err(|_| "SOURCE_REDIRECT_BLOCKED")?;
    source_url(next.as_str()).map_err(|_| "SOURCE_REDIRECT_BLOCKED")?;
    // A secret-bearing URL is not automatically forwarded to another origin.
    if next.origin() != current.origin() {
        return Err("SOURCE_REDIRECT_BLOCKED");
    }
    Ok(next)
}

pub async fn fetch_source(input: &str) -> Result<String, &'static str> {
    fetch_with_deadline(input, DEADLINE).await
}

async fn fetch_with_deadline(input: &str, deadline: Duration) -> Result<String, &'static str> {
    let url = source_url(input)?;
    let client = Client::builder()
        .redirect(Policy::none())
        .connect_timeout(Duration::from_secs(5))
        .timeout(deadline)
        .build()
        .map_err(|_| "SOURCE_FETCH_FAILED")?;
    tokio::time::timeout(deadline, download(&client, url))
        .await
        .map_err(|_| "SOURCE_FETCH_TIMEOUT")?
}

fn fetch_error(error: reqwest::Error) -> &'static str {
    if error.is_timeout() {
        "SOURCE_FETCH_TIMEOUT"
    } else {
        "SOURCE_FETCH_FAILED"
    }
}

async fn download(client: &Client, mut url: Url) -> Result<String, &'static str> {
    for redirect in 0..=MAX_REDIRECTS {
        let mut response = client
            .get(url.clone())
            .header(
                header::ACCEPT,
                "application/yaml, text/yaml, text/plain, */*",
            )
            .send()
            .await
            .map_err(fetch_error)?;
        if matches!(response.status().as_u16(), 301 | 302 | 303 | 307 | 308) {
            if redirect == MAX_REDIRECTS {
                return Err("SOURCE_REDIRECT_BLOCKED");
            }
            let location = response
                .headers()
                .get(header::LOCATION)
                .and_then(|value| value.to_str().ok())
                .ok_or("SOURCE_REDIRECT_BLOCKED")?;
            url = redirected_url(&url, location)?;
            continue;
        }
        if !response.status().is_success() {
            return Err("SOURCE_HTTP_ERROR");
        }
        if response
            .content_length()
            .is_some_and(|length| length > MAX_SOURCE_BYTES as u64)
        {
            return Err("SOURCE_TOO_LARGE");
        }
        if response
            .headers()
            .get(header::CONTENT_TYPE)
            .and_then(|value| value.to_str().ok())
            .is_some_and(|mime| {
                mime.split(';')
                    .next()
                    .unwrap_or("")
                    .trim()
                    .eq_ignore_ascii_case("text/html")
            })
        {
            return Err("SOURCE_NOT_PROFILE");
        }
        let mut bytes = Vec::new();
        while let Some(chunk) = response.chunk().await.map_err(fetch_error)? {
            if chunk.len() > MAX_SOURCE_BYTES - bytes.len() {
                return Err("SOURCE_TOO_LARGE");
            }
            bytes.extend_from_slice(&chunk);
        }
        // String conversion preserves the UTF-8 BOM and every CR/LF byte.
        let source = String::from_utf8(bytes).map_err(|_| "SOURCE_INVALID_UTF8")?;
        check_source(&source).map_err(|error| match error {
            "EMPTY_PROFILE" => "EMPTY_SOURCE",
            "PROFILE_TOO_LARGE" => "SOURCE_TOO_LARGE",
            _ => "SOURCE_NOT_PROFILE",
        })?;
        let prefix: String = source
            .trim_start_matches('\u{feff}')
            .trim_start()
            .chars()
            .take(15)
            .collect();
        let prefix = prefix.to_ascii_lowercase();
        if prefix.starts_with("<!doctype html") || prefix.starts_with("<html") {
            return Err("SOURCE_NOT_PROFILE");
        }
        return Ok(source);
    }
    Err("SOURCE_REDIRECT_BLOCKED")
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    use tokio::net::TcpListener;

    async fn server(responses: Vec<Vec<u8>>) -> String {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        tokio::spawn(async move {
            for response in responses {
                let (mut socket, _) = listener.accept().await.unwrap();
                let mut input = [0; 4096];
                let _ = socket.read(&mut input).await;
                let _ = socket.write_all(&response).await;
            }
        });
        format!("http://{address}/profile?token=private")
    }

    fn ok(body: &[u8]) -> Vec<u8> {
        let mut response = format!(
            "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
            body.len()
        )
        .into_bytes();
        response.extend_from_slice(body);
        response
    }

    #[test]
    fn accepts_only_explicit_http_urls_without_credentials_or_fragments() {
        for input in [
            "file:///private",
            "ftp://example.test/",
            "https://user:private@example.test/",
            "https://example.test/#private",
            "https://example.test/a b",
            "https://example.test/a\nprivate",
        ] {
            assert_eq!(source_url(input).unwrap_err(), "INVALID_SOURCE_URL");
        }
        assert_eq!(
            source_url(&format!(
                "https://example.test/{}",
                "x".repeat(MAX_URL_BYTES)
            ))
            .unwrap_err(),
            "INVALID_SOURCE_URL"
        );
        assert!(source_url(" https://example.test/?token=private ").is_ok());
    }

    #[test]
    fn redirects_must_stay_on_the_same_origin_and_never_downgrade_tls() {
        let url = source_url("https://example.test/profile?token=private").unwrap();
        assert_eq!(redirected_url(&url, "/next").unwrap().path(), "/next");
        for location in [
            "http://example.test/",
            "https://other.test/",
            "https://user:private@example.test/",
            "/next#private",
        ] {
            assert_eq!(
                redirected_url(&url, location).unwrap_err(),
                "SOURCE_REDIRECT_BLOCKED"
            );
        }
    }

    #[tokio::test]
    async fn preserves_bom_crlf_and_follows_relative_redirect() {
        let original = "\u{feff}# untouched\r\nproxy-groups: []\r\n";
        let url = server(vec![b"HTTP/1.1 302 Found\r\nLocation: /next\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".to_vec(), ok(original.as_bytes())]).await;
        assert_eq!(fetch_source(&url).await.unwrap(), original);
    }

    #[tokio::test]
    async fn rejects_bad_status_utf8_empty_and_html_without_returning_body_or_url() {
        let cases = vec![
            (b"HTTP/1.1 403 Forbidden\r\nContent-Length: 7\r\nConnection: close\r\n\r\nprivate".to_vec(), "SOURCE_HTTP_ERROR"),
            (ok(&[0xff, 0xfe]), "SOURCE_INVALID_UTF8"), (ok(b" \r\n"), "EMPTY_SOURCE"),
            (ok(b"<!DOCTYPE html><title>private</title>"), "SOURCE_NOT_PROFILE"),
            (b"HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: 0\r\n\r\n".to_vec(), "SOURCE_NOT_PROFILE"),
        ];
        for (response, expected) in cases {
            let url = server(vec![response]).await;
            assert_eq!(fetch_source(&url).await.unwrap_err(), expected);
        }
    }

    #[tokio::test]
    async fn bounds_declared_and_chunked_bodies() {
        let declared = format!(
            "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
            MAX_SOURCE_BYTES + 1
        );
        let url = server(vec![declared.into_bytes()]).await;
        assert_eq!(fetch_source(&url).await.unwrap_err(), "SOURCE_TOO_LARGE");
        let mut chunked =
            b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n".to_vec();
        chunked.extend_from_slice(format!("{:x}\r\n", MAX_SOURCE_BYTES + 1).as_bytes());
        chunked.extend(vec![b'x'; MAX_SOURCE_BYTES + 1]);
        chunked.extend_from_slice(b"\r\n0\r\n\r\n");
        let url = server(vec![chunked]).await;
        assert_eq!(fetch_source(&url).await.unwrap_err(), "SOURCE_TOO_LARGE");
        let body = vec![b'x'; MAX_SOURCE_BYTES];
        let url = server(vec![ok(&body)]).await;
        assert_eq!(fetch_source(&url).await.unwrap().len(), MAX_SOURCE_BYTES);
    }

    #[tokio::test]
    async fn bounds_redirect_loops_and_blocks_cross_origin() {
        let response = b"HTTP/1.1 302 Found\r\nLocation: /loop\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".to_vec();
        let url = server(vec![response; MAX_REDIRECTS + 1]).await;
        assert_eq!(
            fetch_source(&url).await.unwrap_err(),
            "SOURCE_REDIRECT_BLOCKED"
        );
        let url = server(vec![b"HTTP/1.1 302 Found\r\nLocation: https://other.test/?token=private\r\nContent-Length: 0\r\n\r\n".to_vec()]).await;
        assert_eq!(
            fetch_source(&url).await.unwrap_err(),
            "SOURCE_REDIRECT_BLOCKED"
        );
    }

    #[tokio::test]
    async fn total_deadline_bounds_stalled_headers() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}/private", listener.local_addr().unwrap());
        let server = tokio::spawn(async move {
            let (_socket, _) = listener.accept().await.unwrap();
            tokio::time::sleep(Duration::from_secs(5)).await;
        });
        assert_eq!(
            fetch_with_deadline(&url, Duration::from_millis(80))
                .await
                .unwrap_err(),
            "SOURCE_FETCH_TIMEOUT"
        );
        server.abort();
    }
}
