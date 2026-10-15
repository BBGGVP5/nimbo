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
    let mut stream = TcpStream::connect_timeout(&address.parse().unwrap(), Duration::from_secs(3))
        .unwrap_or_else(|error| {
            eprintln!("Native TCP connect failed for public fixture {address}: {error}");
            panic!("native routed TCP handshake failed; preserve Kill Switch and inspect VM diagnostics")
        });
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
        println!("cycle={cycle}: native TCP4");
        assert!(tcp("198.18.0.10:18080").ends_with(b"nimbo-windows-tun"));
        println!("cycle={cycle}: native TCP6");
        assert!(tcp("[2001:db8::10]:18080").ends_with(b"nimbo-windows-tun"));
        println!("cycle={cycle}: native UDP");
        let udp = UdpSocket::bind("0.0.0.0:0").unwrap();
        udp.set_read_timeout(Some(Duration::from_secs(3))).unwrap();
        udp.send_to(b"native-udp", "198.18.0.10:18081").unwrap();
        let mut data = [0u8; 2048];
        let (n, _) = udp.recv_from(&mut data).unwrap();
        assert_eq!(&data[..n], b"native-udp");
        // UDP DNS sent to a fake external resolver must be hijacked by native DNS.
        println!("cycle={cycle}: native DNS hijack");
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

// Compile the actual GUI proxy implementation, not a test-only registry writer.
#[path = "../../../apps/ui/src-tauri/src/mihomo_proxy.rs"]
mod owned_proxy;
mod state {
    #[derive(Clone, Debug, PartialEq, Eq)]
    pub struct SystemProxySnapshot {
        pub proxy_enable: Option<u32>,
        pub proxy_server: Option<String>,
        pub proxy_override: Option<String>,
    }
}

fn physical_probe() -> std::io::Result<()> {
    use socket2::{Domain, Protocol, Socket, Type};
    use std::os::windows::io::AsRawSocket;
    use windows_sys::Win32::Networking::WinSock::*;
    let socket = Socket::new(Domain::IPV4, Type::STREAM, Some(Protocol::TCP))?;
    let index = std::env::var("NIMBO_TEST_PHYSICAL_INDEX")
        .unwrap()
        .parse::<u32>()
        .unwrap()
        .to_be();
    if unsafe {
        setsockopt(
            socket.as_raw_socket() as _,
            IPPROTO_IP,
            IP_UNICAST_IF,
            (&index as *const u32).cast(),
            4,
        )
    } != 0
    {
        return Err(std::io::Error::from_raw_os_error(unsafe {
            WSAGetLastError()
        }));
    }
    let target = std::env::var("NIMBO_TEST_BYPASS_TARGET")
        .unwrap()
        .parse::<std::net::SocketAddr>()
        .unwrap();
    socket.connect_timeout(&target.into(), Duration::from_secs(3))
}
fn physical_dns_socket() -> std::io::Result<socket2::Socket> {
    use std::os::windows::io::AsRawSocket;
    use windows_sys::Win32::Networking::WinSock::*;
    let socket = socket2::Socket::new(
        socket2::Domain::IPV4,
        socket2::Type::DGRAM,
        Some(socket2::Protocol::UDP),
    )
    .unwrap();
    let index = std::env::var("NIMBO_TEST_PHYSICAL_INDEX")
        .unwrap()
        .parse::<u32>()
        .unwrap()
        .to_be();
    assert_eq!(
        unsafe {
            setsockopt(
                socket.as_raw_socket() as _,
                IPPROTO_IP,
                IP_UNICAST_IF,
                (&index as *const u32).cast(),
                4,
            )
        },
        0
    );
    let target = std::env::var("NIMBO_TEST_PHYSICAL_DNS")
        .unwrap()
        .parse::<std::net::SocketAddr>()
        .unwrap();
    socket.connect(&target.into())?;
    socket
        .set_read_timeout(Some(Duration::from_secs(3)))
        .unwrap();
    Ok(socket)
}
fn physical_dns(socket: &socket2::Socket) -> std::io::Result<()> {
    let query=b"\x47\x53\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00\x07fixture\x07invalid\x00\x00\x01\x00\x01";
    socket.send(query)?;
    let mut b = [std::mem::MaybeUninit::uninit(); 512];
    let n = socket.recv(&mut b)?;
    if n < 12 || unsafe { b[0].assume_init() != 0x47 || b[1].assume_init() != 0x53 } {
        return Err(std::io::Error::other("DNS control response absent"));
    }
    Ok(())
}
fn native_udp_dns() {
    let udp = UdpSocket::bind("0.0.0.0:0").unwrap();
    udp.set_read_timeout(Some(Duration::from_secs(3))).unwrap();
    udp.send_to(b"ks-native-udp", "198.18.0.10:18081").unwrap();
    let mut b = [0u8; 2048];
    let (n, _) = udp.recv_from(&mut b).unwrap();
    assert_eq!(&b[..n], b"ks-native-udp");
    let query=b"\x12\x34\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00\x07fixture\x07invalid\x00\x00\x01\x00\x01";
    udp.send_to(query, "198.18.0.10:53").unwrap();
    let (n, _) = udp.recv_from(&mut b).unwrap();
    assert_eq!(&b[n - 4..n], &[192, 0, 2, 123]);
}
fn assert_registered_reboot_policy() {
    use windows_sys::{core::GUID, Win32::NetworkManagement::WindowsFilteringPlatform::*};
    let journal: serde_json::Value = serde_json::from_slice(
        &std::fs::read(
            nimbo_ipc::windows::root()
                .unwrap()
                .join("kill-switch/owner.json"),
        )
        .unwrap(),
    )
    .unwrap();
    assert_eq!(journal["version"], 2);
    let id = uuid::Uuid::parse_str(journal["sublayer"].as_str().unwrap()).unwrap();
    let key = GUID::from_u128(id.as_u128());
    unsafe {
        let mut engine = std::ptr::null_mut();
        assert_eq!(
            FwpmEngineOpen0(
                std::ptr::null(),
                10,
                std::ptr::null(),
                std::ptr::null(),
                &mut engine
            ),
            0
        );
        let mut provider = std::ptr::null_mut();
        assert_eq!(FwpmProviderGetByKey0(engine, &key, &mut provider), 0);
        assert_eq!(
            (*provider).flags & FWPM_PROVIDER_FLAG_PERSISTENT,
            FWPM_PROVIDER_FLAG_PERSISTENT
        );
        assert!(
            (*provider).serviceName.is_null(),
            "helper disable must not disable protection"
        );
        FwpmFreeMemory0((&mut provider as *mut *mut FWPM_PROVIDER0).cast());
        let mut layer = std::ptr::null_mut();
        assert_eq!(FwpmSubLayerGetByKey0(engine, &key, &mut layer), 0);
        assert_eq!(
            (*layer).flags & FWPM_SUBLAYER_FLAG_PERSISTENT,
            FWPM_SUBLAYER_FLAG_PERSISTENT
        );
        FwpmFreeMemory0((&mut layer as *mut *mut FWPM_SUBLAYER0).cast());
        for family in 0..2u8 {
            for role in 0..7u8 {
                let mut bytes = *id.as_bytes();
                bytes[0] ^= 0x80;
                bytes[14] ^= family;
                bytes[15] ^= role;
                let k = GUID::from_u128(u128::from_be_bytes(bytes));
                let mut f = std::ptr::null_mut();
                assert_eq!(
                    FwpmFilterGetByKey0(engine, &k, &mut f),
                    0,
                    "persistent/boot filter absent"
                );
                let flags = match role {
                    0 | 2 | 3 => FWPM_FILTER_FLAG_PERSISTENT,
                    5 | 6 => FWPM_FILTER_FLAG_BOOTTIME,
                    _ => 0,
                };
                assert_eq!(
                    (*f).flags & (FWPM_FILTER_FLAG_BOOTTIME | FWPM_FILTER_FLAG_PERSISTENT),
                    flags
                );
                assert_eq!(
                    (*f).flags & FWPM_FILTER_FLAG_DISABLED,
                    0,
                    "saved block must remain enabled"
                );
                if role == 4 {
                    assert_eq!((*f).numFilterConditions, 1);
                    let condition = &*(*f).filterCondition;
                    let key = |g: GUID| (g.data1, g.data2, g.data3, g.data4);
                    assert_eq!(
                        key(condition.fieldKey),
                        key(FWPM_CONDITION_IP_NEXTHOP_INTERFACE)
                    );
                    assert_eq!(condition.matchType, FWP_MATCH_EQUAL);
                    assert_eq!(condition.conditionValue.r#type, FWP_UINT64);
                }
                FwpmFreeMemory0((&mut f as *mut *mut FWPM_FILTER0).cast());
            }
        }
        FwpmEngineClose0(engine);
    }
}
#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
#[ignore = "WFP/network mutations: explicitly disposable GitHub VM only"]
async fn actual_both_kill_switch_denies_physical_bypass_and_survives_native_death() {
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
        FullProfile::new("Public WFP fixture".into(), ProfileKind::MihomoYaml, source).unwrap();
    assert_eq!(
        nimbo_mihomo::helper::capabilities(&hash),
        (true, true, true)
    );
    physical_probe().expect("physical baseline is required, never skip a denied-bypass test");
    for failure in 0..5 {
        eprintln!("Both/WFP case {failure}: baseline then owned start");
        let dns = physical_dns_socket().expect("physical DNS socket baseline");
        physical_dns(&dns).expect("physical DNS baseline must pass");
        let mut session = Session::start_tun_options(&binary, &profile, true, true)
            .await
            .unwrap();
        assert!(session.info.tun_ready && session.is_running());
        assert_registered_reboot_policy();
        let mixed = nimbo_mihomo::wire::loopback_address(&session.info.mixed_address).unwrap();
        eprintln!("Both/WFP case {failure}: routed TCP4");
        assert!(tcp("198.18.0.10:18080").ends_with(b"nimbo-windows-tun"));
        eprintln!("Both/WFP case {failure}: routed TCP6");
        assert!(tcp("[2001:db8::10]:18080").ends_with(b"nimbo-windows-tun"));
        // Apply/restore the actual GUI's per-user proxy and ownership check.
        let saved_proxy = owned_proxy::snapshot().unwrap();
        owned_proxy::apply(mixed.port()).unwrap();
        assert!(owned_proxy::owns(mixed.port()).unwrap());
        assert_eq!(owned_proxy::snapshot().unwrap().proxy_enable, Some(1));
        // Real mixed listener, not a cosmetic Both flag.
        let client = reqwest::Client::builder()
            .proxy(reqwest::Proxy::all(format!("http://{mixed}")).unwrap())
            .timeout(Duration::from_secs(4))
            .build()
            .unwrap();
        let body = client
            .get("http://198.18.0.10:18080/fixture")
            .send()
            .await
            .unwrap()
            .bytes()
            .await
            .unwrap();
        assert_eq!(&body[..], b"nimbo-windows-tun");
        assert!(
            physical_probe().is_err(),
            "plaintext bypass was permitted while KS armed"
        );
        native_udp_dns();
        assert!(
            physical_dns(&dns).is_err(),
            "existing UDP DNS flow escaped protection"
        );
        assert!(
            physical_dns_socket()
                .and_then(|s| physical_dns(&s))
                .is_err(),
            "new UDP DNS flow escaped protection"
        );
        if failure > 0 {
            let core = nimbo_ipc::windows::root().unwrap().join("nimbo-mihomo.exe");
            let script = if failure == 1 {
                format!("$p=@(Get-CimInstance Win32_Process | Where-Object ExecutablePath -eq '{}'); if($p.Count -ne 1){{throw 'fixed native child missing'}}; Stop-Process -Id $p[0].ProcessId -Force",core.display().to_string().replace('\'',"''"))
            } else if failure == 3 {
                "Stop-Service NimboHelper -ErrorAction Stop".into()
            } else {
                "$s=Get-CimInstance Win32_Service -Filter \"Name='NimboHelper'\"; if(!$s.ProcessId){throw 'SCM helper missing'}; Stop-Process -Id $s.ProcessId -Force".into()
            };
            if failure == 4 {
                // Same path as GUI/Windows exit, without an explicit Disconnect.
                session.abandon_now();
            } else {
                let output = std::process::Command::new("powershell")
                    .args(["-NoProfile", "-NonInteractive", "-Command", &script])
                    .output()
                    .unwrap();
                assert!(
                    output.status.success(),
                    "owned crash/service-stop injection failed"
                );
            }
            let deadline = std::time::Instant::now() + Duration::from_secs(8);
            while session.is_running() && std::time::Instant::now() < deadline {
                tokio::time::sleep(Duration::from_millis(50)).await;
            }
            assert!(!session.is_running());
            assert!(
                physical_probe().is_err(),
                "core death removed external protection"
            );
            assert!(
                session.stop().await.is_err(),
                "abnormal native exit reported successful cleanup"
            );
            // Protection must survive the loss of its native process and pipe lease.
            assert!(
                physical_probe().is_err(),
                "failed stop removed external protection"
            );
            assert!(
                physical_dns_socket()
                    .and_then(|s| physical_dns(&s))
                    .is_err(),
                "failure removed physical UDP protection"
            );
            if matches!(failure, 2 | 3) {
                let status=std::process::Command::new("powershell").args(["-NoProfile","-NonInteractive","-Command","if((Get-Service NimboHelper).Status -ne 'Running'){Start-Service NimboHelper}"]).output().unwrap();
                assert!(status.status.success(), "SCM helper restart failed");
                let deadline = std::time::Instant::now() + Duration::from_secs(15);
                while !nimbo_mihomo::helper::available(&hash)
                    && std::time::Instant::now() < deadline
                {
                    tokio::time::sleep(Duration::from_millis(100)).await;
                }
                assert!(nimbo_mihomo::helper::available(&hash));
                assert!(
                    physical_probe().is_err(),
                    "helper restart silently cleared protection"
                );
            }
            assert_registered_reboot_policy();
            eprintln!("Both/WFP case {failure}: protection retained, explicit reset");
            nimbo_mihomo::helper::reset_kill_switch().await.unwrap();
        } else {
            session.stop().await.unwrap();
        }
        owned_proxy::restore(Some(saved_proxy.clone())).unwrap();
        assert_eq!(owned_proxy::snapshot().unwrap(), saved_proxy);
        physical_probe().expect("explicit release did not restore physical traffic");
        physical_dns_socket()
            .and_then(|s| physical_dns(&s))
            .expect("explicit release did not restore UDP DNS");
        eprintln!("Both/WFP case {failure}: physical TCP and UDP DNS restored");
    }
}

#[tokio::test]
#[ignore = "explicit disposable recovery only, never normal test execution"]
async fn windows_fixture_emergency_reset() {
    assert_eq!(
        std::env::var("NIMBO_DISPOSABLE_WINDOWS").as_deref(),
        Ok("1")
    );
    assert_eq!(std::env::var("GITHUB_ACTIONS").as_deref(), Ok("true"));
    nimbo_mihomo::helper::reset_kill_switch().await.unwrap();
}
