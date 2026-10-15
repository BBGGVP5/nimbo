//! Loopback-only representation tests. Never contact a subscription or change host networking.
use nimbo_subscription::{fetch_subscription, FetchOptions, SubscriptionFormat};
use std::{
    io::{Read, Write},
    net::TcpListener,
    thread,
    time::{Duration, Instant},
};

fn serve(status: &str, mime: &str, body: &[u8]) -> (String, thread::JoinHandle<String>) {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    listener.set_nonblocking(true).unwrap();
    let url = format!(
        "http://{}/subscription?token=fixture-secret",
        listener.local_addr().unwrap()
    );
    let response = [format!("HTTP/1.1 {status}\r\nContent-Type: {mime}\r\nContent-Length: {}\r\nConnection: close\r\nProfile-Title: Fixture\r\n\r\n", body.len()).into_bytes(), body.to_vec()].concat();
    let handle = thread::spawn(move || {
        let deadline = Instant::now() + Duration::from_secs(10);
        let mut stream = loop {
            if let Ok((stream, _)) = listener.accept() {
                break stream;
            }
            assert!(Instant::now() < deadline, "loopback request did not arrive");
            thread::sleep(Duration::from_millis(5));
        };
        // Accepted sockets inherit the listener's nonblocking mode on Windows.
        // The request can arrive in later packets; a readiness race must not
        // turn a valid loopback response into SOURCE_FETCH_FAILED.
        stream.set_nonblocking(false).unwrap();
        stream
            .set_read_timeout(Some(Duration::from_secs(3)))
            .unwrap();
        stream
            .set_write_timeout(Some(Duration::from_secs(3)))
            .unwrap();
        let mut request = Vec::new();
        let mut buffer = [0; 1024];
        while !request.windows(4).any(|bytes| bytes == b"\r\n\r\n") {
            let len = stream.read(&mut buffer).unwrap();
            if len == 0 {
                break;
            }
            request.extend_from_slice(&buffer[..len]);
            assert!(request.len() < 65536);
        }
        let _ = stream.write_all(&response);
        String::from_utf8(request).unwrap()
    });
    (url, handle)
}

#[tokio::test]
async fn mihomo_negotiates_yaml_and_preserves_source_exactly() {
    let yaml = "\u{feff}proxy-providers:\r\n  feed: {type: http, url: 'https://fixture.invalid/feed'}\r\nproxy-groups:\r\n  - {name: VPN, type: select, use: [feed]}\r\nrules:\r\n  - MATCH,VPN\r\n";
    let (url, request) = serve("200 OK", "application/yaml", yaml.as_bytes());
    let opts = FetchOptions {
        format: SubscriptionFormat::Mihomo,
        ..Default::default()
    };
    let result = fetch_subscription(&url, &opts).await.unwrap();
    assert_eq!(result.raw_body, yaml);
    assert!(result.servers.is_empty());
    assert_eq!(result.suggested_name.as_deref(), Some("Fixture"));
    let request = request.join().unwrap().to_ascii_lowercase();
    assert!(request.contains("user-agent: mihomo/"));
    assert!(request.contains("accept: application/yaml"));
    assert!(request.contains("x-nimbo-core: mihomo"));
}

#[tokio::test]
async fn http_and_invalid_utf8_errors_are_redacted() {
    for (status, mime, body, expected) in [
        (
            "403 Forbidden",
            "text/plain",
            b"secret-token".as_slice(),
            "SOURCE_HTTP_ERROR",
        ),
        (
            "200 OK",
            "text/html",
            b"<html>secret-token</html>".as_slice(),
            "SOURCE_NOT_PROFILE",
        ),
        (
            "200 OK",
            "text/plain",
            [0xff, 0xfe].as_slice(),
            "SOURCE_INVALID_UTF8",
        ),
    ] {
        let (url, request) = serve(status, mime, body);
        let opts = FetchOptions {
            format: SubscriptionFormat::Mihomo,
            ..Default::default()
        };
        let error = fetch_subscription(&url, &opts)
            .await
            .unwrap_err()
            .to_string();
        assert_eq!(error, expected);
        assert!(!error.contains("secret"));
        request.join().unwrap();
    }
}

#[tokio::test]
async fn oversized_advertised_source_is_rejected_before_reading() {
    let body = vec![b' '; 4 * 1024 * 1024 + 1];
    let (url, request) = serve("200 OK", "application/yaml", &body);
    let opts = FetchOptions {
        format: SubscriptionFormat::Mihomo,
        ..Default::default()
    };
    assert_eq!(
        fetch_subscription(&url, &opts)
            .await
            .unwrap_err()
            .to_string(),
        "SOURCE_TOO_LARGE"
    );
    // The client may close early once it has checked the advertised length.
    let _ = request.join();
}
