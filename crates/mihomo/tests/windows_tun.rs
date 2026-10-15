//! Only a disposable hosted Windows VM may install the helper/create a TUN.
#![cfg(windows)]
use nimbo_mihomo::{
    process::{tun_request, Session},
    wire::VerifiedBinary,
    FullProfile, ProfileKind,
};
use std::{
    cell::Cell,
    io::{Read, Write},
    net::{TcpStream, UdpSocket},
    time::Duration,
};
#[path = "support/windows_proxy_guard.rs"]
mod fixture_guard;
#[path = "support/windows_dns_reauthorization.rs"]
mod dns_reauthorization;

fn stage(case: u8, current: &Cell<&'static str>, name: &'static str) {
    current.set(name);
    eprintln!(
        "WINDOWS_FIXTURE {}",
        serde_json::json!({
            "case":case,"stage":name,
            "unix_ms":std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis()
        })
    );
}

fn probe_step<T>(
    case: u8,
    current: &Cell<&'static str>,
    name: &'static str,
    target: &str,
    local: Option<std::net::SocketAddr>,
    action: impl FnOnce() -> std::io::Result<T>,
) -> T {
    stage(case, current, name);
    let start = std::time::Instant::now();
    action().unwrap_or_else(|error| {
        eprintln!(
            "WINDOWS_FIXTURE {}",
            serde_json::json!({
                "case":case,"stage":name,"target":target,"local":local.map(|a|a.to_string()),
                "elapsed_ms":start.elapsed().as_millis(),"os_error":error.raw_os_error(),
                "kind":format!("{:?}",error.kind()),"dns_txid":0x1234
            })
        );
        panic!("Both/WFP case {case}: {name} target={target} failed: {error}");
    })
}
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
struct RealProxy;
impl fixture_guard::ProxyBackend for RealProxy {
    type Snapshot = state::SystemProxySnapshot;
    fn snapshot(&self) -> Result<Self::Snapshot, String> {
        owned_proxy::snapshot()
    }
    fn owns(&self, port: u16) -> Result<bool, String> {
        owned_proxy::owns(port)
    }
    fn restore(&self, saved: Self::Snapshot) -> Result<(), String> {
        owned_proxy::restore(Some(saved))
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
fn native_udp_dns(case: u8, current: &Cell<&'static str>) {
    let udp = probe_step(case, current, "UDP bind", "0.0.0.0:0", None, || {
        UdpSocket::bind("0.0.0.0:0")
    });
    udp.set_read_timeout(Some(Duration::from_secs(3))).unwrap();
    let local = udp.local_addr().ok();
    probe_step(
        case,
        current,
        "UDP echo send",
        "198.18.0.10:18081",
        local,
        || udp.send_to(b"ks-native-udp", "198.18.0.10:18081"),
    );
    let mut b = [0u8; 2048];
    let (n, _) = probe_step(
        case,
        current,
        "UDP echo receive",
        "198.18.0.10:18081",
        local,
        || udp.recv_from(&mut b),
    );
    assert_eq!(&b[..n], b"ks-native-udp");
    let query=b"\x12\x34\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00\x07fixture\x07invalid\x00\x00\x01\x00\x01";
    probe_step(case, current, "DNS send", "198.18.0.10:53", local, || {
        udp.send_to(query, "198.18.0.10:53")
    });
    let (n, _) = probe_step(
        case,
        current,
        "DNS receive",
        "198.18.0.10:53",
        local,
        || udp.recv_from(&mut b),
    );
    assert_eq!(&b[n - 4..n], &[192, 0, 2, 123]);
}

fn disposable_fixture() -> bool {
    std::env::var("NIMBO_DISPOSABLE_WINDOWS").as_deref() == Ok("1")
        && std::env::var("GITHUB_ACTIONS").as_deref() == Ok("true")
}

fn fixture_journal() -> serde_json::Value {
    use serde_json::json;
    let read = || -> Option<serde_json::Value> {
        let file = std::fs::File::open(
            nimbo_ipc::windows::root()
                .ok()?
                .join("kill-switch/owner.json"),
        )
        .ok()?;
        let mut bytes = Vec::new();
        file.take(8193).read_to_end(&mut bytes).ok()?;
        if bytes.len() > 8192 {
            return None;
        }
        let value: serde_json::Value = serde_json::from_slice(&bytes).ok()?;
        let uuid = |value: &serde_json::Value| {
            value
                .as_str()
                .and_then(|s| uuid::Uuid::parse_str(s).ok())
                .map(|id| id.to_string())
        };
        // Never dump the whole journal/config, SID, source YAML or bearer secret.
        Some(json!({
            "version":value["version"].as_u64(),"sublayer":uuid(&value["sublayer"]),
            "native":{"pid":value["native"]["pid"].as_u64(),"created":value["native"]["created"].as_u64()},
            "device":{"interface":uuid(&value["device"]["interface"]),"luid":value["device"]["luid"].as_u64(),
                      "instance":value["device"]["instance"].as_str().map(|s|s.chars().take(256).collect::<String>())}
        }))
    };
    read().unwrap_or_else(|| json!({"error":"journal unreadable, invalid or over 8192 bytes"}))
}

fn bounded_diagnostic(command: &mut std::process::Command, label: &str, seconds: u64, cap: u64) {
    use std::{os::windows::process::CommandExt, process::Stdio};
    let result = (|| -> std::io::Result<()> {
        let mut child = command
            .creation_flags(0x08000000)
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let stdout = child.stdout.take().expect("piped diagnostic stdout");
        let (sender, output) = std::sync::mpsc::sync_channel(1);
        std::thread::spawn(move || {
            let mut bytes = Vec::new();
            let _ = stdout.take(cap + 1).read_to_end(&mut bytes);
            let _ = sender.send(bytes);
        });
        let deadline = std::time::Instant::now() + Duration::from_secs(seconds);
        let status = loop {
            match child.try_wait() {
                Ok(Some(status)) => break Some(status),
                Ok(None) if std::time::Instant::now() < deadline => {
                    std::thread::sleep(Duration::from_millis(25))
                }
                other => {
                    // Kill/reap only this diagnostic process, never helper/native.
                    let _ = child.kill();
                    let _ = child.wait();
                    eprintln!("{label}: diagnostic timeout/wait error: {other:?}");
                    break None;
                }
            }
        };
        // An inherited stdout handle must not make diagnostics wait indefinitely.
        let bytes = output
            .recv_timeout(Duration::from_millis(250))
            .unwrap_or_else(|_| {
                eprintln!("{label}: diagnostic output did not close within the capture bound");
                Vec::new()
            });
        let count = bytes.len().min(cap as usize);
        eprintln!(
            "{label}: status={status:?} truncated={}\n{}",
            bytes.len() > count,
            String::from_utf8_lossy(&bytes[..count])
        );
        Ok(())
    })();
    if let Err(error) = result {
        eprintln!("{label}: diagnostic unavailable: {error}");
    }
}

async fn capture_fixture_failure(session: &mut Session, case: u8, failed_stage: &str) {
    if !disposable_fixture() {
        eprintln!("REFUSED: native diagnostics require disposable GitHub Windows VM");
        return;
    }
    eprintln!(
        "WINDOWS_FIXTURE_FAILURE {}",
        serde_json::json!({
            "case":case,"stage":failed_stage,"lease_running":session.is_running(),
            "generation":session.controller().generation(),"tun_ready":session.info.tun_ready,
            "journal":fixture_journal()
        })
    );
    match tokio::time::timeout(Duration::from_secs(1), session.controller().status()).await {
        Ok(Ok(value)) => eprintln!(
            "NATIVE_STATUS {}",
            serde_json::json!({
                "state":value["state"],"tunReady":value["tunReady"],"networkOwner":value["networkOwner"]
            })
        ),
        _ => eprintln!("NATIVE_STATUS unavailable within 1s"),
    }
    // The only child test invoked here reads WFP objects; it cannot reset them.
    if let Ok(executable) = std::env::current_exe() {
        bounded_diagnostic(
            std::process::Command::new(executable).args([
                "--ignored",
                "--exact",
                "windows_fixture_readonly_diagnostics",
                "--nocapture",
                "--test-threads=1",
            ]),
            "OWNED_WFP",
            4,
            65536,
        );
    }
    let journal = fixture_journal();
    let native_pid = journal["native"]["pid"]
        .as_u64()
        .filter(|id| *id <= u32::MAX as u64)
        .unwrap_or(0);
    let interface = journal["device"]["interface"].as_str().unwrap_or("");
    // Read-only public fixture metadata. GUID/PID interpolation is type-checked.
    let script = format!(
        r#"
$ErrorActionPreference='Stop'
$a=@(Get-NetAdapter -IncludeHidden | Select-Object -First 16 Name,ifIndex,InterfaceGuid,NetLuid,Status)
$indices=@($a | Where-Object {{$_.Name -eq 'nimbo-mh0' -or $_.InterfaceGuid -eq '{interface}'}} | Select-Object -ExpandProperty ifIndex)
[ordered]@{{
 service=@(Get-CimInstance Win32_Service -Filter "Name='NimboHelper'" | Select-Object Name,State,ProcessId)
 native=@(Get-Process -Id {native_pid} -ErrorAction SilentlyContinue | Select-Object Id,StartTime,Responding)
 adapters=$a
 addresses=@(Get-NetIPAddress | Select-Object -First 32 InterfaceIndex,InterfaceAlias,IPAddress,AddressState)
 routes=@(Get-NetRoute | Where-Object {{$_.InterfaceIndex -in $indices -or $_.DestinationPrefix -in @('0.0.0.0/0','::/0')}} | Select-Object -First 32 DestinationPrefix,InterfaceIndex,NextHop,RouteMetric)
 udp=@(Get-NetUDPEndpoint -OwningProcess {parent} -ErrorAction SilentlyContinue | Select-Object -First 16 LocalAddress,LocalPort,OwningProcess)
}} | ConvertTo-Json -Compress -Depth 5
"#,
        parent = std::process::id()
    );
    bounded_diagnostic(
        std::process::Command::new("powershell").args([
            "-NoProfile",
            "-NonInteractive",
            "-Command",
            &script,
        ]),
        "ADAPTER_IDENTITIES",
        3,
        16384,
    );
}

/// Read-only APIs only. Executed as a bounded child while parent retains Session.
#[test]
#[ignore = "read-only WFP diagnostics: explicitly disposable GitHub VM only"]
fn windows_fixture_readonly_diagnostics() {
    assert!(disposable_fixture());
    use serde_json::{json, Value};
    use std::ptr;
    use windows_sys::{core::GUID, Win32::NetworkManagement::WindowsFilteringPlatform::*};
    fn id(g: GUID) -> String {
        uuid::Uuid::from_fields(g.data1, g.data2, g.data3, &g.data4).to_string()
    }
    unsafe fn text(pointer: *const u16) -> String {
        if pointer.is_null() {
            return String::new();
        }
        let mut units = Vec::new();
        for i in 0..160 {
            let unit = *pointer.add(i);
            if unit == 0 {
                break;
            }
            units.push(unit);
        }
        String::from_utf16_lossy(&units)
    }
    unsafe fn filter(value: &FWPM_FILTER0) -> Value {
        let mut conditions = Vec::new();
        if !value.filterCondition.is_null() {
            for condition in std::slice::from_raw_parts(
                value.filterCondition,
                value.numFilterConditions.min(8) as usize,
            ) {
                let v = &condition.conditionValue;
                let data = match v.r#type {
                    FWP_UINT8 => json!(v.Anonymous.uint8),
                    FWP_UINT16 => json!(v.Anonymous.uint16),
                    FWP_UINT32 => json!(v.Anonymous.uint32),
                    FWP_UINT64 if !v.Anonymous.uint64.is_null() => json!(*v.Anonymous.uint64),
                    _ => Value::Null, // No application blobs, tokens or arbitrary strings.
                };
                conditions.push(
                    json!({"field":id(condition.fieldKey),"match":condition.matchType,
                                       "type":v.r#type,"value":data}),
                );
            }
        }
        json!({"id":value.filterId,"key":id(value.filterKey),"sublayer":id(value.subLayerKey),
               "layer":id(value.layerKey),"action":value.action.r#type,"flags":value.flags,
               "name":text(value.displayData.name),"description":text(value.displayData.description),
               "conditions":conditions,"condition_count":value.numFilterConditions})
    }
    unsafe {
        let mut engine = ptr::null_mut();
        let code = FwpmEngineOpen0(ptr::null(), 10, ptr::null(), ptr::null(), &mut engine);
        if code != 0 {
            println!("WFP_OPEN error={code}");
            return;
        }
        let journal = fixture_journal();
        if let Some(owner) = journal["sublayer"]
            .as_str()
            .and_then(|s| uuid::Uuid::parse_str(s).ok())
        {
            for family in 0..2u8 {
                for role in 0..7u8 {
                    let mut bytes = *owner.as_bytes();
                    bytes[0] ^= 0x80;
                    bytes[14] ^= family;
                    bytes[15] ^= role;
                    let key = GUID::from_u128(u128::from_be_bytes(bytes));
                    let mut found = ptr::null_mut();
                    let code = FwpmFilterGetByKey0(engine, &key, &mut found);
                    if code == 0 && !found.is_null() {
                        println!("NIMBO_WFP family={family} role={role} {}", filter(&*found));
                        FwpmFreeMemory0((&mut found as *mut *mut FWPM_FILTER0).cast());
                    } else {
                        println!("NIMBO_WFP family={family} role={role} error={code}");
                    }
                }
            }
        }
        let mut native_layers = Vec::new();
        let mut enumeration = ptr::null_mut();
        let code = FwpmSubLayerCreateEnumHandle0(engine, ptr::null(), &mut enumeration);
        if code == 0 {
            let mut complete = false;
            for _ in 0..4 {
                let mut entries = ptr::null_mut();
                let mut count = 0;
                let code = FwpmSubLayerEnum0(engine, enumeration, 64, &mut entries, &mut count);
                if code != 0 {
                    println!("NATIVE_SUBLAYERS error={code}");
                    break;
                }
                if !entries.is_null() {
                    for entry in std::slice::from_raw_parts(entries, count as usize) {
                        if entry.is_null() {
                            continue;
                        }
                        let layer = &**entry;
                        if text(layer.displayData.description) == "auto-route rules" {
                            if native_layers.len() < 8 {
                                native_layers.push(id(layer.subLayerKey));
                            }
                            println!(
                                "NATIVE_SUBLAYER {}",
                                json!({"key":id(layer.subLayerKey),
                                "weight":layer.weight,"flags":layer.flags,"name":text(layer.displayData.name)})
                            );
                        }
                    }
                    FwpmFreeMemory0((&mut entries as *mut *mut *mut FWPM_SUBLAYER0).cast());
                }
                if count < 64 {
                    complete = true;
                    break;
                }
            }
            println!(
                "NATIVE_SUBLAYERS complete={complete} selected={}",
                native_layers.len()
            );
            FwpmSubLayerDestroyEnumHandle0(engine, enumeration);
        } else {
            println!("NATIVE_SUBLAYERS error={code}");
        }
        for layer in [
            FWPM_LAYER_ALE_AUTH_CONNECT_V4,
            FWPM_LAYER_ALE_AUTH_CONNECT_V6,
        ] {
            let mut template: FWPM_FILTER_ENUM_TEMPLATE0 = std::mem::zeroed();
            template.layerKey = layer;
            template.actionMask = u32::MAX;
            template.enumType = FWP_FILTER_ENUM_FULLY_CONTAINED;
            let mut enumeration = ptr::null_mut();
            let code = FwpmFilterCreateEnumHandle0(engine, &template, &mut enumeration);
            if code != 0 {
                println!("NATIVE_FILTER_ENUM error={code}");
                continue;
            }
            let mut scanned = 0;
            let mut emitted = 0;
            let mut complete = false;
            for _ in 0..32 {
                let mut entries = ptr::null_mut();
                let mut count = 0;
                let code = FwpmFilterEnum0(engine, enumeration, 128, &mut entries, &mut count);
                if code != 0 {
                    println!("NATIVE_FILTER_ENUM error={code}");
                    break;
                }
                scanned += count;
                if !entries.is_null() {
                    for entry in std::slice::from_raw_parts(entries, count as usize) {
                        if !entry.is_null()
                            && native_layers.contains(&id((**entry).subLayerKey))
                            && emitted < 32
                        {
                            println!("NATIVE_WFP {}", filter(&**entry));
                            emitted += 1;
                        }
                    }
                    FwpmFreeMemory0((&mut entries as *mut *mut *mut FWPM_FILTER0).cast());
                }
                if count < 128 {
                    complete = true;
                    break;
                }
            }
            println!("NATIVE_FILTER_ENUM layer={} scanned={scanned} emitted={emitted} complete={complete}",id(layer));
            FwpmFilterDestroyEnumHandle0(engine, enumeration);
        }
        FwpmEngineClose0(engine);
    }
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
        let current = Cell::new("owned session readiness");
        // Both owners outlive the caught future: diagnostics see pre-cleanup
        // proxy state and native policy before explicit failure cleanup begins.
        let mut proxy_guard = None;
        let outcome = fixture_guard::catch_fixture_panic(async {
        assert_registered_reboot_policy();
        let mixed = nimbo_mihomo::wire::loopback_address(&session.info.mixed_address).unwrap();
        eprintln!("Both/WFP case {failure}: routed TCP4");
        stage(failure, &current, "routed TCP4");
        assert!(tcp("198.18.0.10:18080").ends_with(b"nimbo-windows-tun"));
        eprintln!("Both/WFP case {failure}: routed TCP6");
        stage(failure, &current, "routed TCP6");
        assert!(tcp("[2001:db8::10]:18080").ends_with(b"nimbo-windows-tun"));
        // Apply/restore the actual GUI's per-user proxy and ownership check.
        stage(failure, &current, "proxy apply");
        proxy_guard = Some(fixture_guard::ProxyGuard::new(&RealProxy, mixed.port()).unwrap());
        let saved_proxy = proxy_guard.as_ref().unwrap().saved().clone();
        owned_proxy::apply(mixed.port()).unwrap();
        assert!(owned_proxy::owns(mixed.port()).unwrap());
        assert_eq!(owned_proxy::snapshot().unwrap().proxy_enable, Some(1));
        // Real mixed listener, not a cosmetic Both flag.
        stage(failure, &current, "mixed HTTP");
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
        stage(failure, &current, "physical TCP denial");
        assert!(
            physical_probe().is_err(),
            "plaintext bypass was permitted while KS armed"
        );
        native_udp_dns(failure, &current);
        stage(failure, &current, "existing physical DNS denial");
        assert!(
            physical_dns(&dns).is_err(),
            "existing UDP DNS flow escaped protection"
        );
        stage(failure, &current, "new physical DNS denial");
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
                stage(failure, &current, "GUI lease abandon");
                // Same path as GUI/Windows exit, without an explicit Disconnect.
                session.abandon_now();
            } else {
                stage(failure, &current, if failure == 1 {"native process death"} else if failure == 3 {"SCM Stop-Service"} else {"helper process death"});
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
            stage(failure, &current, "explicit owner reset");
            nimbo_mihomo::helper::reset_kill_switch().await.unwrap();
        } else {
            session.stop().await.unwrap();
        }
        stage(failure, &current, "proxy restore");
        proxy_guard.as_mut().unwrap().finish().unwrap();
        assert_eq!(owned_proxy::snapshot().unwrap(), saved_proxy);
        physical_probe().expect("explicit release did not restore physical traffic");
        physical_dns_socket()
            .and_then(|s| physical_dns(&s))
            .expect("explicit release did not restore UDP DNS");
        eprintln!("Both/WFP case {failure}: physical TCP and UDP DNS restored");
        }).await;
        if let Err(error) = outcome {
            let (diagnostic_panicked, cleanup) = fixture_guard::capture_before_proxy_cleanup(
                capture_fixture_failure(&mut session, failure, current.get()),
                &mut proxy_guard,
            )
            .await;
            if diagnostic_panicked {
                let _ = writeln!(
                    std::io::stderr().lock(),
                    "Fixture diagnostics failed; preserving original panic"
                );
            }
            if let Err(cleanup_error) = cleanup {
                let _ = writeln!(
                    std::io::stderr().lock(),
                    "Fixture proxy cleanup failed after diagnostics: {cleanup_error}"
                );
            }
            std::panic::resume_unwind(error);
        }
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
