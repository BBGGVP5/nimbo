use super::*;
use std::sync::{Arc, atomic::{AtomicUsize, Ordering}};

fn server(port: u16) -> Server {
    Server { id:format!("node-{port}"), name:"fixture".into(), server_description:None, host_uuid:None,xray_json_template_uuid:None,
        protocol: Protocol::Vless(nimbo_subscription::VlessConfig {address:"127.0.0.1".into(),port,
            uuid:"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee".into(),flow:None,encryption:"none".into(),stream:Default::default()}) }
}
fn parent() -> PathBuf { std::env::temp_dir().join(format!("nimbo-probe-test-{}",uuid::Uuid::new_v4())) }

#[tokio::test]
async fn global_concurrency_is_three_and_queue_is_in_deadline() {
    let active=Arc::new(AtomicUsize::new(0));let peak=Arc::new(AtomicUsize::new(0));
    let mut tasks=Vec::new();
    for _ in 0..12 {
        let active=active.clone();let peak=peak.clone();
        tasks.push(tokio::spawn(async move {
            let resolve=async {
                let count=active.fetch_add(1,Ordering::SeqCst)+1;peak.fetch_max(count,Ordering::SeqCst);
                tokio::time::sleep(Duration::from_millis(15)).await;
                active.fetch_sub(1,Ordering::SeqCst);
                Err("fixture resolution stopped".into())
            };
            assert!(measure(server(1),resolve,&parent(),"http://probe.invalid",2000,||true,None).await.is_err());
        }));
    }
    for task in tasks { task.await.unwrap(); }
    assert!(peak.load(Ordering::SeqCst)>1);
    assert!(peak.load(Ordering::SeqCst)<=MAX_PROBES);
    let held=slots().acquire_many_owned(MAX_PROBES as u32).await.unwrap();
    let result=measure(server(1),async {panic!("queued probe must not resolve")},&parent(),"http://probe.invalid",25,||true,None).await;
    assert!(result.unwrap_err().contains("timeout"));drop(held);
}

#[test]
#[ignore = "subprocess fixture only; invoked by resource ownership tests"]
fn child_wait_fixture() {
    if std::env::var("NIMBO_CHILD_WAIT_FIXTURE").as_deref()==Ok("1") {
        loop { std::thread::sleep(Duration::from_secs(60)); }
    }
}

fn sleeping_child(resources:&mut Resources) {
    let mut command=Command::new(std::env::current_exe().unwrap());
    command.args(["--ignored","--exact","diagnostics::integration::child_wait_fixture"])
        .env("NIMBO_CHILD_WAIT_FIXTURE","1").stdin(Stdio::null()).stdout(Stdio::null());
    resources.spawn(&mut command).unwrap();
}

#[tokio::test]
async fn hung_startup_and_caller_drop_kill_reap_and_remove_private_files() {
    for drop_caller in [false,true] {
        let root=parent();let mut resources=Resources::default();
        resources.directory(&root).unwrap();let private=resources.directory.clone().unwrap();
        resources.write_config("xray.json",&json!({"secret":"fixture"})).unwrap();
        sleeping_child(&mut resources);
        assert!(resources.alive());
        let held=std::net::TcpListener::bind(("127.0.0.1",0)).unwrap();let port=held.local_addr().unwrap().port();drop(held);
        assert!(tokio::time::timeout(Duration::from_millis(40),ready(&mut resources,port)).await.is_err());
        if drop_caller { drop(resources); }
        else { resources.cleanup().await;assert!(resources.children.is_empty()); }
        tokio::time::timeout(Duration::from_secs(3),async {
            while private.exists() { tokio::time::sleep(Duration::from_millis(10)).await; }
        }).await.unwrap();
        std::fs::remove_dir(root).unwrap();
    }
}

#[test]
fn executor_shutdown_does_not_recursively_spawn_cleanup_or_leave_files() {
    let root=parent();let private;
    let runtime=tokio::runtime::Builder::new_current_thread().enable_all().build().unwrap();
    private=runtime.block_on(async {
        let mut resources=Resources::default();resources.directory(&root).unwrap();
        let private=resources.directory.clone().unwrap();
        resources.write_config("xray.json",&json!({})).unwrap();sleeping_child(&mut resources);
        tokio::spawn(async move { let _resources=resources; std::future::pending::<()>().await; });
        tokio::task::yield_now().await;
        private
    });
    drop(runtime);
    assert!(!private.exists());std::fs::remove_dir(root).unwrap();
}

async fn http_fixture(status:u16,delay:u64) -> (u16,Arc<AtomicUsize>,tokio::task::JoinHandle<()>) {
    let listener=tokio::net::TcpListener::bind(("127.0.0.1",0)).await.unwrap();
    let port=listener.local_addr().unwrap().port();let hits=Arc::new(AtomicUsize::new(0));let count=hits.clone();
    let task=tokio::spawn(async move {
        loop {
            let (mut socket,_)=listener.accept().await.unwrap();
            let mut request=Vec::new();
            while !request.ends_with(b"\r\n\r\n") {
                match socket.read_u8().await { Ok(byte)=>request.push(byte),Err(_)=>break }
            }
            if request.starts_with(b"GET ") {
                count.fetch_add(1,Ordering::SeqCst);
                tokio::time::sleep(Duration::from_millis(delay)).await;
                let _=socket.write_all(format!("HTTP/1.1 {status} Fixture\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").as_bytes()).await;
            }
        }
    });
    (port,hits,task)
}

async fn vless_fixture(binary:&Path,root:&Path,target:u16,tls:bool) -> (Server,Resources) {
    let listener=std::net::TcpListener::bind(("127.0.0.1",0)).unwrap();let port=listener.local_addr().unwrap().port();
    let mut resources=Resources::default();resources.directory(root).unwrap();
    let mut config=json!({"log":{"loglevel":"debug"},"inbounds":[{"listen":"127.0.0.1","port":port,"protocol":"vless",
        "settings":{"decryption":"none","clients":[{"id":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"}]}}],
        "outbounds":[{"protocol":"freedom","settings":{"redirect":format!("127.0.0.1:{target}"),"finalRules":[{"action":"allow","ip":["127.0.0.1/32"],"port":target}]}}]});
    let mut node=server(port);
    if tls {
        let output=Command::new(binary).args(["tls","cert","--domain=localhost"]).output().await.unwrap();
        assert!(output.status.success());let cert:Value=serde_json::from_slice(&output.stdout).unwrap();
        config["inbounds"][0]["streamSettings"]=json!({"security":"tls","tlsSettings":{"certificates":[cert]}});
        if let Protocol::Vless(value)=&mut node.protocol { value.stream.security=nimbo_subscription::Security::Tls;value.stream.sni=Some("localhost".into()); }
    }
    let path=resources.write_config("xray.json",&config).unwrap();
    let mut command=Command::new(binary);command.args(["run","-c"]).arg(path).stdin(Stdio::null()).stdout(Stdio::null());
    command.stdout(Stdio::inherit());
    drop(listener);resources.spawn(&mut command).unwrap();
    tokio::time::timeout(Duration::from_secs(3),ready(&mut resources,port)).await.unwrap().unwrap();
    (node,resources)
}

#[tokio::test]
#[ignore = "requires NIMBO_DIAGNOSTIC_TEST_XRAY pinned CLI; loopback only, no live VPN"]
async fn real_cli_per_node_http_tls_failure_deadline_and_cleanup() {
    let binary=PathBuf::from(std::env::var_os("NIMBO_DIAGNOSTIC_TEST_XRAY").expect("explicit verified CLI path required"));
    let name=if cfg!(windows){"xray.exe"}else{"xray"};
    crate::xray_release::current().unwrap().verify_file(name,&std::fs::read(&binary).unwrap()).unwrap();
    let root=parent();let probes=root.join("probes");
    let (port_a,hits_a,http_a)=http_fixture(200,70).await;
    let (port_b,hits_b,http_b)=http_fixture(503,0).await;
    let (a,mut process_a)=vless_fixture(&binary,&root,port_a,false).await;
    let (b,mut process_b)=vless_fixture(&binary,&root,port_b,false).await;
    let (tls,mut process_tls)=vless_fixture(&binary,&root,port_a,true).await;
    let resolve=||async {Ok(Binaries{xray:binary.clone(),awg:None,naive:None})};
    // SAME unresolvable target URL: only fixture A and B's distinct VLESS routes
    // can reach their HTTP destinations. No active runtime is even constructed.
    let template=json!({"outbounds":[{"tag":"direct","protocol":"freedom"},
        nimbo_xray_config::outbound::server_to_outbound(&a,"selected-template-node")],
        "routing":{"rules":[{"domain":["nimbo-probe.invalid"],"outboundTag":"direct"}]}});
    let (ra,rb)=tokio::join!(
        measure(a.clone(),resolve(),&probes,"http://nimbo-probe.invalid/check",3000,||true,Some(template)),
        measure(b,resolve(),&probes,"http://nimbo-probe.invalid/check",3000,||true,None));
    assert!(ra.unwrap()>=70);assert_eq!(rb.unwrap_err(),"HTTP 503");
    assert_eq!(hits_a.load(Ordering::SeqCst),1);assert_eq!(hits_b.load(Ordering::SeqCst),1);
    let rejected=measure(tls,resolve(),&probes,"http://nimbo-probe.invalid/check",1500,||true,None).await;
    assert!(rejected.is_err(),"self-signed remote VPN TLS must fail");
    assert_eq!(hits_a.load(Ordering::SeqCst),1);
    // Deadline and cancellation during live I/O; no child or private file left.
    let (slow_port,slow_hits,slow_http)=http_fixture(200,3000).await;
    let (slow,mut slow_process)=vless_fixture(&binary,&root,slow_port,false).await;
    assert!(measure(slow.clone(),resolve(),&probes,"http://nimbo-probe.invalid",1500,||true,None).await.unwrap_err().contains("timeout"));
    assert_eq!(slow_hits.load(Ordering::SeqCst),1,"deadline must occur after actual tunneled HTTP I/O");
    // Let fixture finish its first response before accepting the cancellation probe.
    tokio::time::sleep(Duration::from_millis(2100)).await;
    let started=std::time::Instant::now();
    assert!(measure(slow,resolve(),&probes,"http://nimbo-probe.invalid",3000,||slow_hits.load(Ordering::SeqCst)<2,None).await.unwrap_err().contains("cancelled"));
    assert_eq!(slow_hits.load(Ordering::SeqCst),2,"cancel only after a second real tunneled GET");
    assert!(started.elapsed()<Duration::from_secs(3));
    assert_eq!(std::fs::read_dir(&probes).unwrap().count(),0);
    // Dead remote server cannot fall back to a directly reachable HTTP target.
    process_a.cleanup().await;
    let before=hits_a.load(Ordering::SeqCst);
    assert!(measure(a,resolve(),&probes,&format!("http://127.0.0.1:{port_a}"),800,||true,None).await.is_err());
    assert_eq!(hits_a.load(Ordering::SeqCst),before);
    process_b.cleanup().await;process_tls.cleanup().await;slow_process.cleanup().await;
    for task in [http_a,http_b,slow_http] { task.abort();let _=task.await; }
    std::fs::remove_dir(probes).unwrap();std::fs::remove_dir(root).unwrap();
}
