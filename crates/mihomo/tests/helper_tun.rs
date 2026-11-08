//! Executed only by the isolated network+mount namespace acceptance harness.
#![cfg(target_os = "linux")]
use nimbo_mihomo::{process::Session, wire::VerifiedBinary, FullProfile, ProfileKind};
use std::{
    io::{Read, Write},
    path::PathBuf,
    time::Duration,
};

#[tokio::test]
#[ignore = "requires explicitly isolated root broker fixture; never run on host"]
async fn actual_rust_session_owns_native_tun_and_joins_cleanup() {
    assert_eq!(std::env::var("NIMBO_DISPOSABLE_NETNS").as_deref(), Ok("1"));
    assert_eq!(unsafe { libc::geteuid() }, 0);
    assert_ne!(
        std::fs::read_link("/proc/self/ns/net")
            .unwrap()
            .to_str()
            .unwrap(),
        std::env::var("NIMBO_TEST_PARENT_NETNS").unwrap()
    );
    let path = PathBuf::from(std::env::var_os("NIMBO_TEST_MIHOMO_BINARY").unwrap());
    let hash = std::env::var("NIMBO_TEST_MIHOMO_SHA256").unwrap();
    let binary = VerifiedBinary::verify(&path, &hash).unwrap();
    let source =
        std::fs::read_to_string(std::env::var_os("NIMBO_TEST_TUN_SOURCE").unwrap()).unwrap();
    let profile = FullProfile::new(
        "Synthetic namespace".into(),
        ProfileKind::MihomoYaml,
        source,
    )
    .unwrap();
    assert!(nimbo_mihomo::helper::available(&hash));
    nimbo_mihomo::helper::preflight(
        nimbo_mihomo::process::tun_request(&binary, &profile, false).unwrap(),
    )
    .await
    .unwrap();
    let mut session = Session::start_tun(&binary, &profile, false).await.unwrap();
    assert!(session.is_running());
    assert_eq!(session.info.network_owner, "desktop-tun");
    assert!(session.info.tun_ready);
    assert_eq!(session.info.source_sha256, profile.source_digest);
    assert!(session.info.mixed_address.is_empty());
    for address in ["203.0.113.10:18080", "[fdfe:dcba:9901::10]:18080"] {
        let mut tcp =
            std::net::TcpStream::connect_timeout(&address.parse().unwrap(), Duration::from_secs(5))
                .unwrap();
        eprintln!(
            "namespace TCP {address}: local={}",
            tcp.local_addr().unwrap()
        );
        tcp.set_read_timeout(Some(Duration::from_secs(5))).unwrap();
        tcp.write_all(b"GET /fixture HTTP/1.1\r\nHost: fixture\r\nConnection: close\r\n\r\n")
            .unwrap();
        let mut response = Vec::new();
        tcp.read_to_end(&mut response).unwrap();
        assert!(response.ends_with(b"native-tun-fixture"), "{address}");
    }
    session.stop().await.unwrap();
    assert!(!session.is_running());
}
