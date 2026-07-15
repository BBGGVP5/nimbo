use nimbo_subscription::parser::aggregate::parse_single;
use nimbo_xray_config::{
    build_config, build_config_with_ports, AppRoutingMode, AppRoutingRule, ConfigBuilder,
    ProxyPorts,
};

#[test]
fn vless_reality_xhttp_full_config() {
    let url = "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@1.2.3.4:443?type=xhttp&security=reality&pbk=KEY&fp=chrome&sni=microsoft.com&sid=01ab&flow=xtls-rprx-vision&mode=auto#srv";
    let server = parse_single(url).unwrap();
    let config = build_config(&server);
    let v = serde_json::to_value(&config).unwrap();

    assert_eq!(v["log"]["loglevel"], "warning");
    assert_eq!(v["dns"]["servers"][0], "1.1.1.1");
    assert_eq!(v["dns"]["queryStrategy"], "UseIP");

    assert_eq!(v["inbounds"][0]["tag"], "socks-in");
    assert_eq!(v["inbounds"][0]["port"], 10808);
    assert_eq!(v["inbounds"][0]["protocol"], "socks");
    assert_eq!(v["inbounds"][0]["sniffing"]["routeOnly"], false);
    assert!(v["inbounds"][0]["sniffing"]["destOverride"]
        .as_array()
        .unwrap()
        .contains(&"quic".into()));
    assert!(v["inbounds"][0]["sniffing"]["destOverride"]
        .as_array()
        .unwrap()
        .contains(&"fakedns".into()));
    assert_eq!(v["inbounds"][1]["tag"], "http-in");
    assert_eq!(v["inbounds"][1]["port"], 10809);
    assert_eq!(v["inbounds"][2]["tag"], "api");
    assert_eq!(v["inbounds"][2]["port"], 10810);

    let proxy = &v["outbounds"][0];
    assert_eq!(proxy["tag"], "proxy");
    assert_eq!(proxy["protocol"], "vless");
    assert_eq!(proxy["settings"]["vnext"][0]["address"], "1.2.3.4");
    assert_eq!(proxy["settings"]["vnext"][0]["port"], 443);
    assert_eq!(
        proxy["settings"]["vnext"][0]["users"][0]["id"],
        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    );
    assert_eq!(
        proxy["settings"]["vnext"][0]["users"][0]["flow"],
        "xtls-rprx-vision"
    );

    assert_eq!(proxy["streamSettings"]["network"], "xhttp");
    assert_eq!(proxy["streamSettings"]["security"], "reality");
    assert_eq!(
        proxy["streamSettings"]["realitySettings"]["serverName"],
        "microsoft.com"
    );
    assert_eq!(
        proxy["streamSettings"]["realitySettings"]["publicKey"],
        "KEY"
    );
    assert_eq!(proxy["streamSettings"]["xhttpSettings"]["mode"], "auto");

    assert_eq!(v["outbounds"][1]["tag"], "direct");
    assert_eq!(v["outbounds"][1]["protocol"], "freedom");
    assert_eq!(v["outbounds"][2]["tag"], "block");
    assert_eq!(v["outbounds"][2]["protocol"], "blackhole");

    assert_eq!(v["routing"]["domainStrategy"], "IPIfNonMatch");
    let rules = v["routing"]["rules"].as_array().unwrap();
    assert!(rules.iter().any(|r| r["outboundTag"] == "api"));
    assert!(rules.iter().any(|r| r["outboundTag"] == "direct"
        && r["ip"]
            .as_array()
            .is_some_and(|a| a.contains(&"geoip:private".into()))));

    assert_eq!(v["api"]["tag"], "api");
    assert_eq!(v["api"]["services"][0], "StatsService");
    assert_eq!(v["policy"]["system"]["statsOutboundUplink"], true);
}

#[test]
fn vmess_ws_tls_config() {
    use base64::Engine;
    let json = r#"{"v":"2","ps":"vm","add":"vm.tld","port":"443","id":"u","aid":0,"scy":"auto","net":"ws","tls":"tls","sni":"vm.tld","host":"vm.tld","path":"/v"}"#;
    let url = format!(
        "vmess://{}",
        base64::engine::general_purpose::STANDARD.encode(json)
    );
    let server = parse_single(&url).unwrap();
    let config = build_config(&server);
    let v = serde_json::to_value(&config).unwrap();

    let proxy = &v["outbounds"][0];
    assert_eq!(proxy["protocol"], "vmess");
    assert_eq!(proxy["settings"]["vnext"][0]["address"], "vm.tld");
    assert_eq!(proxy["settings"]["vnext"][0]["users"][0]["alterId"], 0);
    assert_eq!(proxy["streamSettings"]["network"], "ws");
    assert_eq!(proxy["streamSettings"]["wsSettings"]["path"], "/v");
    assert_eq!(
        proxy["streamSettings"]["wsSettings"]["headers"]["Host"],
        "vm.tld"
    );
    assert_eq!(
        proxy["streamSettings"]["tlsSettings"]["serverName"],
        "vm.tld"
    );
}

#[test]
fn trojan_tcp_tls_config() {
    let url = "trojan://my-pwd@tj.tld:443?security=tls&sni=tj.tld&type=tcp#tj";
    let server = parse_single(url).unwrap();
    let config = build_config(&server);
    let v = serde_json::to_value(&config).unwrap();

    let proxy = &v["outbounds"][0];
    assert_eq!(proxy["protocol"], "trojan");
    assert_eq!(proxy["settings"]["servers"][0]["address"], "tj.tld");
    assert_eq!(proxy["settings"]["servers"][0]["password"], "my-pwd");
    assert_eq!(proxy["streamSettings"]["network"], "tcp");
    assert_eq!(proxy["streamSettings"]["security"], "tls");
}

#[test]
fn shadowsocks_no_stream_settings() {
    let url = "ss://aes-256-gcm:p@ss.tld:8388#ss";
    let server = parse_single(url).unwrap();
    let config = build_config(&server);
    let v = serde_json::to_value(&config).unwrap();

    let proxy = &v["outbounds"][0];
    assert_eq!(proxy["protocol"], "shadowsocks");
    assert_eq!(proxy["settings"]["servers"][0]["method"], "aes-256-gcm");
    assert_eq!(proxy["settings"]["servers"][0]["password"], "p");
    assert_eq!(proxy["settings"]["servers"][0]["port"], 8388);
    assert!(proxy.get("streamSettings").is_none() || proxy["streamSettings"].is_null());
}

#[test]
fn custom_ports_propagate() {
    let url = "vless://uuid@host:443?type=tcp&security=tls&sni=host#x";
    let server = parse_single(url).unwrap();
    let config = build_config_with_ports(
        &server,
        ProxyPorts {
            socks: 7890,
            http: 7891,
            api: 7892,
        },
    );
    let v = serde_json::to_value(&config).unwrap();
    assert_eq!(v["inbounds"][0]["port"], 7890);
    assert_eq!(v["inbounds"][1]["port"], 7891);
    assert_eq!(v["inbounds"][2]["port"], 7892);
}

#[test]
fn app_routing_rules_are_emitted_before_default_rules() {
    let url = "vless://uuid@host:443?type=tcp&security=tls&sni=host#x";
    let server = parse_single(url).unwrap();
    let config = ConfigBuilder::new(ProxyPorts::default())
        .server(&server)
        .app_routing_rules(vec![
            AppRoutingRule {
                process: r"C:\Program Files\App\app.exe".into(),
                mode: AppRoutingMode::Proxy,
                enabled: true,
            },
            AppRoutingRule {
                process: "curl".into(),
                mode: AppRoutingMode::Direct,
                enabled: true,
            },
            AppRoutingRule {
                process: "__domain__:www.youtube.com".into(),
                mode: AppRoutingMode::Proxy,
                enabled: true,
            },
        ])
        .build();
    let v = serde_json::to_value(&config).unwrap();
    let rules = v["routing"]["rules"].as_array().unwrap();

    assert_eq!(rules[1]["process"][0], "C:/Program Files/App/app.exe");
    assert_eq!(rules[1]["outboundTag"], "proxy");
    assert_eq!(rules[2]["process"][0], "curl");
    assert_eq!(rules[2]["outboundTag"], "direct");
    assert_eq!(rules[3]["domain"][0], "domain:www.youtube.com");
    assert_eq!(rules[3]["outboundTag"], "proxy");
}
