//! Only a disposable hosted Windows VM may install the helper/create a TUN.
#![cfg(windows)]
use nimbo_mihomo::{
    process::{tun_request, Session},
    wire::VerifiedBinary,
    FullProfile, ProfileKind,
};
use std::{
    io::{Read, Write},
    net::{TcpStream, UdpSocket},
    time::Duration,
};
fn tcp(address: &str) -> Vec<u8> {
    let mut stream =
        TcpStream::connect_timeout(&address.parse().unwrap(), Duration::from_secs(3)).unwrap();
    stream
        .set_read_timeout(Some(Duration::from_secs(3)))
        .unwrap();
    stream
        .write_all(b"GET /fixture HTTP/1.1\r\nHost: fixture\r\nConnection: close\r\n\r\n")
        .unwrap();
    let mut bytes = Vec::new();
    stream.read_to_end(&mut bytes).unwrap();
    bytes
}
#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
#[ignore = "installs TUN only in an explicitly disposable Windows VM; never run on host"]
async fn actual_windows_broker_session_tcp_udp_dns_selection_and_cleanup() {
    assert_eq!(
        std::env::var("NIMBO_DISPOSABLE_WINDOWS").as_deref(),
        Ok("1")
    );
    assert_eq!(std::env::var("GITHUB_ACTIONS").as_deref(), Ok("true"));
    let path = std::env::var_os("NIMBO_TEST_MIHOMO_BINARY").unwrap();
    let hash = std::env::var("NIMBO_TEST_MIHOMO_SHA256").unwrap();
    let binary = VerifiedBinary::verify(std::path::Path::new(&path), &hash).unwrap();
    let source =
        std::fs::read_to_string(std::env::var_os("NIMBO_TEST_TUN_SOURCE").unwrap()).unwrap();
    let profile =
        FullProfile::new("Public VM fixture".into(), ProfileKind::MihomoYaml, source).unwrap();
    assert!(
        nimbo_mihomo::helper::available(&hash),
        "protected SCM service/native SHA unavailable"
    );
    nimbo_mihomo::helper::preflight(tun_request(&binary, &profile, false).unwrap())
        .await
        .unwrap();
    for cycle in 0..2 {
        let mut session = Session::start_tun(&binary, &profile, false).await.unwrap();
        assert!(session.info.tun_ready && session.is_running());
        assert!(session.info.mixed_address.is_empty());
        assert_eq!(session.info.network_owner, "desktop-tun");
        assert!(tcp("198.18.0.10:18080").ends_with(b"nimbo-windows-tun"));
        assert!(tcp("[2001:db8::10]:18080").ends_with(b"nimbo-windows-tun"));
        let udp = UdpSocket::bind("0.0.0.0:0").unwrap();
        udp.set_read_timeout(Some(Duration::from_secs(3))).unwrap();
        udp.send_to(b"native-udp", "198.18.0.10:18081").unwrap();
        let mut data = [0u8; 2048];
        let (n, _) = udp.recv_from(&mut data).unwrap();
        assert_eq!(&data[..n], b"native-udp");
        // UDP DNS sent to a fake external resolver must be hijacked by native DNS.
        let query=b"\x12\x34\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00\x07fixture\x07invalid\x00\x00\x01\x00\x01";
        udp.send_to(query, "198.18.0.10:53").unwrap();
        let (n, _) = udp.recv_from(&mut data).unwrap();
        assert!(
            n >= query.len() + 16
                && data[..2] == [0x12, 0x34]
                && data[n - 4..n] == [192, 0, 2, 123]
        );
        let mut wrong = tun_request(&binary, &profile, false).unwrap();
        wrong.binary_sha256 = "0".repeat(64);
        assert!(nimbo_mihomo::helper::preflight(wrong).await.is_err());
        // Another pipe cannot acquire this session or detach it.
        assert!(Session::start_tun(&binary, &profile, false).await.is_err());
        session
            .controller()
            .select("FixtureChoice", "REJECT")
            .await
            .unwrap();
        let rejected = TcpStream::connect_timeout(
            &"198.18.0.10:18080".parse().unwrap(),
            Duration::from_secs(2),
        );
        if let Ok(mut stream) = rejected {
            stream
                .set_read_timeout(Some(Duration::from_secs(2)))
                .unwrap();
            let _ = stream.write_all(b"GET /fixture HTTP/1.0\r\n\r\n");
            let mut b = [0; 64];
            assert!(!matches!(stream.read(&mut b),Ok(n) if n>0));
        }
        session
            .controller()
            .select("FixtureChoice", "FixtureSocks")
            .await
            .unwrap();
        assert!(tcp("198.18.0.10:18080").ends_with(b"nimbo-windows-tun"));
        if cycle == 0 {
            session.stop().await.unwrap();
            assert!(!session.is_running());
        } else {
            drop(session);
        } // EOF/lease drop must join the same native cleanup.
    }
}
