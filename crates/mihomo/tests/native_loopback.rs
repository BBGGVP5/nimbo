//! Opt-in actual pinned helper tests. Only controlled loopback traffic, no Tauri
//! app startup or host network settings. Run explicitly with --ignored.
use nimbo_mihomo::{
    process::{inspect, Session},
    wire::VerifiedBinary,
    FullProfile, ProfileKind,
};
use std::{
    path::PathBuf,
    sync::{Arc, RwLock},
    time::Duration,
};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::TcpListener,
};

struct Fixture {
    address: String,
    body: Arc<RwLock<String>>,
    task: tokio::task::JoinHandle<()>,
}
impl Fixture {
    async fn new(body: &str) -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap().to_string();
        let body = Arc::new(RwLock::new(body.to_owned()));
        let content = body.clone();
        let task = tokio::spawn(async move {
            loop {
                let Ok((mut stream, _)) = listener.accept().await else {
                    break;
                };
                let content = content.clone();
                tokio::spawn(async move {
                    let mut bytes = Vec::new();
                    let mut b = [0u8; 1];
                    while bytes.len() < 32768 && !bytes.ends_with(b"\r\n\r\n") {
                        if tokio::time::timeout(Duration::from_secs(3), stream.read_exact(&mut b))
                            .await
                            .ok()
                            .and_then(Result::ok)
                            .is_none()
                        {
                            return;
                        }
                        bytes.push(b[0]);
                    }
                    let body = content.read().unwrap().clone();
                    let response=format!("HTTP/1.1 200 OK\r\nContent-Length: {}\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n{}",body.len(),body);
                    let _ = stream.write_all(response.as_bytes()).await;
                });
            }
        });
        Self {
            address,
            body,
            task,
        }
    }
    fn url(&self) -> String {
        format!("http://{}/fixture", self.address)
    }
    fn replace(&self, body: &str) {
        *self.body.write().unwrap() = body.into();
    }
}
impl Drop for Fixture {
    fn drop(&mut self) {
        self.task.abort();
    }
}
struct TempDir(PathBuf);
impl TempDir {
    fn new() -> Self {
        let p =
            std::env::temp_dir().join(format!("nimbo-native-loopback-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir(&p).unwrap();
        Self(p)
    }
}
impl Drop for TempDir {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}
fn binary() -> VerifiedBinary {
    let p =
        std::env::var_os("NIMBO_TEST_MIHOMO_BINARY").expect("explicit pinned helper path required");
    let sha =
        std::env::var("NIMBO_TEST_MIHOMO_SHA256").expect("explicit verified build SHA256 required");
    VerifiedBinary::verify(&PathBuf::from(p), &sha).expect("test executable hash verification")
}
async fn request_through(
    session: &Session,
    target: &Fixture,
) -> Result<reqwest::Response, reqwest::Error> {
    reqwest::Client::builder()
        .no_proxy()
        .proxy(reqwest::Proxy::all(format!("http://{}", session.info.mixed_address)).unwrap())
        .timeout(Duration::from_secs(2))
        .build()
        .unwrap()
        .get(target.url())
        .send()
        .await
}
#[tokio::test]
#[ignore = "requires explicitly staged source-built helper; no system settings changed"]
async fn actual_source_inspection_proxy_selection_delay_and_owned_stop() {
    let bin = binary();
    let dir = TempDir::new();
    let destination = Fixture::new("loopback-native-real").await;
    let source="# exact CRLF\r\nmode: rule\r\nproxies:\r\n  - {name: Local, type: direct}\r\nproxy-groups:\r\n  - {name: Choice, type: select, proxies: [Local, DIRECT, REJECT]}\r\nrules: [\"MATCH,Choice\"]\r\n";
    let mut profile =
        FullProfile::new("Fixture".into(), ProfileKind::MihomoYaml, source.into()).unwrap();
    let projection = inspect(&bin, &profile).await.unwrap();
    assert!(projection.issues.is_empty());
    assert!(!projection.native_validated);
    assert_eq!(profile.original_text, source);
    let mut session = Session::start(&bin, &profile, &dir.0).await.unwrap();
    assert!(session.is_running());
    let old_id = session.session_id.clone();
    let old_endpoint = session.info.controller_address.clone();
    let c = session.controller();
    let unauthorized = reqwest::Client::builder()
        .no_proxy()
        .build()
        .unwrap()
        .post(format!("http://{old_endpoint}/v1/invoke"))
        .header("Content-Type", "application/json")
        .body(r#"{"apiVersion":1,"operation":"status"}"#)
        .send()
        .await
        .unwrap();
    assert_eq!(unauthorized.status(), 401);
    assert_eq!(
        request_through(&session, &destination)
            .await
            .unwrap()
            .text()
            .await
            .unwrap(),
        "loopback-native-real"
    );
    let selected = c.select("Choice", "REJECT").await.unwrap();
    assert_eq!(selected.groups["Choice"]["now"], "REJECT");
    let rejected = request_through(&session, &destination).await;
    assert!(
        rejected.is_err() || !rejected.unwrap().status().is_success(),
        "REJECT must change actual traffic"
    );
    c.select("Choice", "DIRECT").await.unwrap();
    let delay = c
        .delay("DIRECT", &destination.url(), 2000, Some("200"))
        .await
        .unwrap();
    assert!(delay["delayMs"].is_number());
    profile.selections.insert("Choice".into(), "DIRECT".into());
    session.stop().await.unwrap();
    assert!(!session.is_running());
    assert!(tokio::net::TcpStream::connect(&old_endpoint).await.is_err());
    assert!(tokio::net::TcpStream::connect(&session.info.mixed_address)
        .await
        .is_err());
    drop(session);
    let mut next = Session::start(&bin, &profile, &dir.0).await.unwrap();
    assert!(!next.owns(&profile.id, &old_id));
    assert_eq!(
        next.controller().snapshot().await.unwrap().groups["Choice"]["now"],
        "DIRECT"
    );
    let endpoint = next.info.mixed_address.clone();
    assert!(next.is_running());
    drop(next);
    assert!(
        tokio::net::TcpStream::connect(endpoint).await.is_err(),
        "drop owns and reaps process"
    );
}
#[tokio::test]
#[ignore = "requires explicitly staged source-built helper; controlled local provider only"]
async fn actual_provider_refresh_is_atomic() {
    let bin = binary();
    let dir = TempDir::new();
    let feed = Fixture::new("proxies: [{name: first, type: direct}]\n").await;
    let source=format!("proxy-providers:\n  Remote:\n    type: http\n    url: {:?}\nproxy-groups: [{{name: Choice, type: select, include-all-providers: true}}]\nrules: [\"MATCH,Choice\"]\n",feed.url());
    let profile = FullProfile::new("Provider".into(), ProfileKind::MihomoYaml, source).unwrap();
    let mut session = Session::start(&bin, &profile, &dir.0).await.unwrap();
    let c = session.controller();
    let before = c.snapshot().await.unwrap();
    assert!(before.groups["Choice"]["all"]
        .as_array()
        .unwrap()
        .contains(&serde_json::json!("first")));
    feed.replace("proxies: [{name: second, type: direct}]\n");
    c.refresh_provider("Remote").await.unwrap();
    c.select("Choice", "second").await.unwrap();
    feed.replace("proxies: [{name: bad, type: direct, unknown-field: true}]\n");
    assert!(c.refresh_provider("Remote").await.is_err());
    let after = c.snapshot().await.unwrap();
    assert_eq!(after.groups["Choice"]["now"], "second");
    assert!(!after.groups["Choice"]["all"]
        .as_array()
        .unwrap()
        .contains(&serde_json::json!("bad")));
    session.stop().await.unwrap();
}

#[tokio::test]
#[ignore = "requires pinned helper; only loopback rules and a synthetic feed"]
async fn actual_rule_provider_refresh_controls_traffic_and_rejects_invalid_replacement() {
    let bin = binary();
    let dir = TempDir::new();
    let feed = Fixture::new("payload: [127.0.0.0/8]\n").await;
    let target = Fixture::new("rule-provider-applied").await;
    let source=format!("rule-providers:\n  Localnet:\n    type: http\n    behavior: ipcidr\n    url: {:?}\nrules: [\"RULE-SET,Localnet,DIRECT,no-resolve\",\"MATCH,REJECT\"]\n",feed.url());
    let profile = FullProfile::new("Rules".into(), ProfileKind::MihomoYaml, source).unwrap();
    let mut session = Session::start(&bin, &profile, &dir.0).await.unwrap();
    let c = session.controller();
    assert_eq!(
        request_through(&session, &target)
            .await
            .unwrap()
            .text()
            .await
            .unwrap(),
        "rule-provider-applied"
    );
    assert_eq!(
        c.snapshot().await.unwrap().rule_providers["Localnet"]["ruleCount"],
        1
    );
    feed.replace("payload: [192.0.2.0/24]\n");
    c.refresh_rule_provider("Localnet").await.unwrap();
    let blocked = request_through(&session, &target).await;
    assert!(blocked.is_err() || !blocked.unwrap().status().is_success());
    feed.replace("payload: [127.0.0.0/8]\n");
    c.refresh_rule_provider("Localnet").await.unwrap();
    feed.replace("payload: [not-an-ip-prefix]\n");
    assert!(c.refresh_rule_provider("Localnet").await.is_err());
    assert_eq!(
        request_through(&session, &target)
            .await
            .unwrap()
            .text()
            .await
            .unwrap(),
        "rule-provider-applied"
    );
    session.stop().await.unwrap();
}
#[tokio::test]
#[ignore = "requires pinned helper; cancellation closes controlled hanging provider connection"]
async fn actual_cancelled_start_reaps_child_and_closes_provider_socket() {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let dir = TempDir::new();
    let path = dir.0.clone();
    let bin = binary();
    let source=format!("proxy-providers:\n  Slow:\n    type: http\n    url: http://{address}/fixture\nproxy-groups: [{{name: Choice, type: select, use: [Slow]}}]\nrules: [\"MATCH,Choice\"]\n");
    let profile = FullProfile::new("Cancel".into(), ProfileKind::MihomoYaml, source).unwrap();
    let task = tokio::spawn(async move { Session::start(&bin, &profile, &path).await });
    let (mut socket, _) = tokio::time::timeout(Duration::from_secs(10), listener.accept())
        .await
        .unwrap()
        .unwrap();
    let mut header = Vec::new();
    let mut one = [0u8; 1];
    while !header.ends_with(b"\r\n\r\n") {
        tokio::time::timeout(Duration::from_secs(2), socket.read_exact(&mut one))
            .await
            .unwrap()
            .unwrap();
        header.push(one[0]);
        assert!(header.len() < 32768);
    }
    task.abort();
    let result = task.await;
    assert!(result.is_err());
    let closed = tokio::time::timeout(Duration::from_secs(2), socket.read(&mut one))
        .await
        .expect("provider socket survived cancelled start");
    assert!(closed.is_err() || closed.unwrap() == 0);
}
