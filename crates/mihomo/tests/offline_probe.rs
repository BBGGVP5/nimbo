use nimbo_mihomo::{
    wire::{decode_offline_probe, offline_probe_request, Envelope},
    FullProfile, ProfileKind,
};
use serde_json::json;

fn profile() -> FullProfile {
    FullProfile::new(
        "Fixture".into(),
        ProfileKind::MihomoYaml,
        "proxies: [{name: local, type: direct}]\n".into(),
    )
    .unwrap()
}
#[test]
fn offline_probe_request_is_readonly_and_identity_bound() {
    let p = profile();
    let r = offline_probe_request(&p, "local", "http://127.0.0.1:9/204", 1000).unwrap();
    assert_eq!(r["operation"], "probeDesktop");
    assert_eq!(r["yaml"], p.original_text);
    assert_eq!(r["generation"], 0);
    assert!(r.get("options").is_none());
    assert!(r.get("dataDir").is_none());
    for (name, url, time) in [
        ("", "http://127.0.0.1", 1000),
        ("local", "file:///bad", 1000),
        ("local", "https://user:secret@example.invalid/", 1000),
        ("local", "https://example.invalid/#fragment", 1000),
        ("local", "http://127.0.0.1", 99),
        ("local", "http://127.0.0.1", 30001),
    ] {
        assert!(offline_probe_request(&p, name, url, time).is_err());
    }
}
#[test]
fn offline_probe_reply_requires_no_vpn_and_exact_source() {
    let p = profile();
    let data = json!({"delayMs":0,"sourceSHA256":p.source_digest,"scope":"desktop-offline-probe","vpnStarted":false});
    let envelope = |data| Envelope {
        api_version: 1,
        request_id: "probe".into(),
        success: true,
        generation: 0,
        data,
        error: None,
    };
    assert_eq!(
        decode_offline_probe(envelope(data.clone()), &p)
            .unwrap()
            .delay_ms,
        0
    );
    for (key, value) in [
        ("vpnStarted", json!(true)),
        ("sourceSHA256", json!("other")),
        ("scope", json!("running")),
        ("delayMs", json!(-1)),
        ("delayMs", json!(65536)),
        ("delayMs", json!(1.5)),
        ("secret", json!("must not cross IPC")),
    ] {
        let mut bad = data.clone();
        bad[key] = value;
        assert!(decode_offline_probe(envelope(bad), &p).is_err());
    }
    let mut bad = envelope(data);
    bad.generation = 1;
    assert!(decode_offline_probe(bad, &p).is_err());
}

// Explicit opt-in, controlled loopback only. Never starts a Session or desktop app.
fn binary() -> nimbo_mihomo::wire::VerifiedBinary {
    let path =
        std::env::var_os("NIMBO_OFFLINE_PROBE_BINARY").expect("set source-built test binary");
    let sha = std::env::var("NIMBO_OFFLINE_PROBE_SHA256").expect("set verified SHA256");
    nimbo_mihomo::wire::VerifiedBinary::verify(std::path::Path::new(&path), &sha).unwrap()
}
#[tokio::test]
#[ignore = "requires opt-in source-built helper; loopback traffic only"]
async fn actual_offline_helper_returns_latency_without_session_or_tun() {
    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let task = tokio::spawn(async move {
        for _ in 0..2 {
            let (mut stream, _) = listener.accept().await.unwrap();
            let mut request = Vec::new();
            while !request.ends_with(b"\r\n\r\n") {
                let mut byte = [0];
                stream.read_exact(&mut byte).await.unwrap();
                request.push(byte[0]);
            }
            assert!(request.starts_with(b"GET "));
            stream
                .write_all(b"HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n")
                .await
                .unwrap();
        }
    });
    let p=FullProfile::new("Offline fixture".into(),ProfileKind::MihomoYaml,"mixed-port: 1\ntun: {enable: true, auto-route: true}\nproxies: [{name: local, type: direct}]\nproxy-groups: [{name: Choice, type: select, proxies: [local]}]\n".into()).unwrap();
    for name in ["local", "Choice"] {
        let result = nimbo_mihomo::process::probe(
            &binary(),
            &p,
            name,
            &format!("http://{address}/204"),
            2000,
        )
        .await
        .unwrap();
        assert_eq!(result.source_sha256, p.source_digest);
        assert!(!result.vpn_started);
        assert_eq!(result.scope, "desktop-offline-probe");
    }
    task.await.unwrap();
    assert!(p.selections.is_empty());
}
#[tokio::test]
#[ignore = "requires opt-in source-built helper; loopback traffic only"]
async fn dropping_offline_probe_reaps_child_and_closes_its_connection() {
    use tokio::io::AsyncReadExt;
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let (started, wait) = tokio::sync::oneshot::channel();
    let closed = tokio::spawn(async move {
        let (mut stream, _) = listener.accept().await.unwrap();
        let mut request = Vec::new();
        while !request.ends_with(b"\r\n\r\n") {
            let mut b = [0];
            stream.read_exact(&mut b).await.unwrap();
            request.push(b[0]);
        }
        started.send(()).unwrap();
        let mut b = [0];
        match stream.read(&mut b).await {
            Ok(0) => {}
            Err(error)
                if matches!(
                    error.kind(),
                    std::io::ErrorKind::ConnectionReset | std::io::ErrorKind::ConnectionAborted
                ) => {}
            other => panic!("owned probe connection remained open: {other:?}"),
        }
    });
    let probe = tokio::spawn(async move {
        nimbo_mihomo::process::probe(
            &binary(),
            &profile(),
            "local",
            &format!("http://{address}/204"),
            30000,
        )
        .await
    });
    tokio::time::timeout(std::time::Duration::from_secs(5), wait)
        .await
        .unwrap()
        .unwrap();
    probe.abort();
    let _ = probe.await;
    tokio::time::timeout(std::time::Duration::from_secs(3), closed)
        .await
        .unwrap()
        .unwrap();
}
