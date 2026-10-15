//! Kernel regression, disposable VM only. Hold one synthetic DNS reply across
//! an owned WFP policy change so its inbound reauthorization is observable.
use nimbo_mihomo::{process::Session, wire::VerifiedBinary, FullProfile, ProfileKind};
use std::{
    io::Write,
    net::UdpSocket,
    path::Path,
    ptr,
    time::{Duration, Instant},
};
use windows_sys::{
    core::GUID,
    Win32::{Foundation::HANDLE, NetworkManagement::WindowsFilteringPlatform::*},
};

struct PolicyChange(HANDLE);
impl PolicyChange {
    fn add(sublayer: uuid::Uuid, family: u8) -> Self {
        assert!(super::disposable_fixture());
        let mut engine = ptr::null_mut();
        let mut session: FWPM_SESSION0 = unsafe { std::mem::zeroed() };
        session.flags = FWPM_SESSION_FLAG_DYNAMIC;
        assert_eq!(
            unsafe { FwpmEngineOpen0(ptr::null(), 10, ptr::null(), &session, &mut engine) },
            0
        );
        let owner = Self(engine);
        let mut conditions: [FWPM_FILTER_CONDITION0; 2] = unsafe { std::mem::zeroed() };
        conditions[0].fieldKey = FWPM_CONDITION_IP_PROTOCOL;
        conditions[0].matchType = FWP_MATCH_EQUAL;
        conditions[0].conditionValue.r#type = FWP_UINT8;
        conditions[0].conditionValue.Anonymous.uint8 = 17;
        conditions[1].fieldKey = FWPM_CONDITION_IP_REMOTE_PORT;
        conditions[1].matchType = FWP_MATCH_EQUAL;
        conditions[1].conditionValue.r#type = FWP_UINT16;
        conditions[1].conditionValue.Anonymous.uint16 = 53;
        let mut filter: FWPM_FILTER0 = unsafe { std::mem::zeroed() };
        filter.filterKey = GUID::from_u128(uuid::Uuid::new_v4().as_u128());
        filter.subLayerKey = GUID::from_u128(sublayer.as_u128());
        filter.layerKey = if family == 0 {
            FWPM_LAYER_ALE_AUTH_CONNECT_V4
        } else {
            FWPM_LAYER_ALE_AUTH_CONNECT_V6
        };
        filter.action.r#type = FWP_ACTION_BLOCK;
        // Same low range as the existing catch-all block. This changes policy,
        // not the prevailing decision when a legitimate TUN permit matches.
        filter.weight.r#type = FWP_UINT8;
        filter.weight.Anonymous.uint8 = 0;
        filter.numFilterConditions = conditions.len() as u32;
        filter.filterCondition = conditions.as_mut_ptr();
        assert_eq!(
            unsafe { FwpmFilterAdd0(engine, &filter, ptr::null_mut(), ptr::null_mut()) },
            0
        );
        owner
    }
}
impl Drop for PolicyChange {
    fn drop(&mut self) {
        unsafe {
            FwpmEngineClose0(self.0);
        }
    }
}

fn wait_ready(path: &Path) {
    let deadline = Instant::now() + Duration::from_secs(3);
    while !path.is_file() && Instant::now() < deadline {
        std::thread::sleep(Duration::from_millis(10));
    }
    assert!(
        path.is_file(),
        "synthetic upstream did not receive the reauthorization question"
    );
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
#[ignore = "WFP/network mutation: explicitly disposable GitHub Windows VM only"]
async fn actual_dns_reply_survives_policy_change_reauthorization() {
    assert!(super::disposable_fixture());
    let binary = VerifiedBinary::verify(
        Path::new(&std::env::var_os("NIMBO_TEST_MIHOMO_BINARY").unwrap()),
        &std::env::var("NIMBO_TEST_MIHOMO_SHA256").unwrap(),
    )
    .unwrap();
    let source =
        std::fs::read_to_string(std::env::var_os("NIMBO_TEST_TUN_SOURCE").unwrap()).unwrap();
    let profile = FullProfile::new(
        "Public reauthorization fixture".into(),
        ProfileKind::MihomoYaml,
        source,
    )
    .unwrap();
    let barrier =
        std::path::PathBuf::from(std::env::var_os("NIMBO_TEST_DNS_REAUTH_BARRIER").unwrap());
    for family in 0..2u8 {
        let label = if family == 0 { "reauth4" } else { "reauth6" };
        let mut session = Session::start_tun_options(&binary, &profile, true, true)
            .await
            .unwrap();
        let mut policy = None;
        let outcome = super::fixture_guard::catch_fixture_panic(async {
            assert!(session.info.tun_ready && session.is_running());
            let journal: serde_json::Value = serde_json::from_slice(&std::fs::read(nimbo_ipc::windows::root().unwrap().join("kill-switch/owner.json")).unwrap()).unwrap();
            let sublayer = uuid::Uuid::parse_str(journal["sublayer"].as_str().unwrap()).unwrap();
            let socket = UdpSocket::bind(if family == 0 { "0.0.0.0:0" } else { "[::]:0" }).unwrap();
            socket.set_read_timeout(Some(Duration::from_secs(3))).unwrap();
            let mut query = b"\x6e\x62\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00".to_vec();
            query.push(7); query.extend_from_slice(label.as_bytes());
            query.extend_from_slice(b"\x07fixture\x07invalid\x00\x00\x01\x00\x01");
            let destination = if family == 0 { "198.18.0.10:53" } else { "[2001:db8::10]:53" };
            socket.send_to(&query, destination).unwrap();
            wait_ready(&barrier.join(format!("{label}.ready")));
            eprintln!("DNS_REAUTH family={family}: upstream received; reply held before policy change");
            policy = Some(PolicyChange::add(sublayer, family));
            std::fs::File::create_new(barrier.join(format!("{label}.release"))).unwrap().write_all(b"release synthetic reply\n").unwrap();
            let mut response = [0u8; 2048];
            let (n, peer) = socket.recv_from(&mut response).unwrap_or_else(|error| panic!("DNS_REAUTH family={family}: reply lost after policy change: {error}"));
            assert!(n >= query.len() + 16 && response[..2] == query[..2] && response[n-4..n] == [192,0,2,123]);
            assert_eq!(peer, destination.parse().unwrap());
            eprintln!("DNS_REAUTH family={family}: correct reply survived controlled policy reauthorization");
        }).await;
        if let Err(error) = outcome {
            let _ = super::fixture_guard::catch_fixture_panic(super::capture_fixture_failure(
                &mut session,
                90 + family,
                "DNS reply after controlled policy change",
            ))
            .await;
            drop(policy.take());
            std::panic::resume_unwind(error);
        }
        // Remove the separate exact dynamic test filter before owned policy
        // release. Session/reset never adopts or deletes a foreign filter.
        drop(policy.take());
        session.stop().await.unwrap();
    }
}
