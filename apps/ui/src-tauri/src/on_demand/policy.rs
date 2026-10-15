use serde::{Deserialize, Serialize};
use std::time::{Duration, Instant};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub enabled: bool,
    pub wifi: bool,
    pub ethernet: bool,
    pub cellular: bool,
    pub trusted_ssids: Vec<String>,
}
impl Default for Settings {
    fn default() -> Self {
        Self {
            enabled: false,
            wifi: true,
            ethernet: false,
            cellular: true,
            trusted_ssids: vec![],
        }
    }
}
impl Settings {
    pub fn validated(mut self) -> Result<Self, &'static str> {
        if self.trusted_ssids.len() > 256 {
            return Err("TOO_MANY_SSIDS");
        }
        let mut unique = Vec::new();
        for raw in self.trusted_ssids {
            let ssid = raw.trim();
            if ssid.is_empty() {
                continue;
            }
            if ssid.len() > 32 || ssid.chars().any(char::is_control) {
                return Err("INVALID_SSID");
            }
            if !unique.iter().any(|item| item == ssid) {
                unique.push(ssid.to_string());
            }
        }
        if unique.len() > 32 {
            return Err("TOO_MANY_SSIDS");
        }
        if self.enabled && !(self.wifi || self.ethernet || self.cellular) {
            return Err("NO_TRANSPORT");
        }
        self.trusted_ssids = unique;
        Ok(self)
    }
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Transport {
    Wifi,
    Ethernet,
    Cellular,
}
#[derive(Clone, PartialEq, Eq)]
pub struct Link {
    pub transport: Transport,
    pub ssid: Option<String>,
}
#[derive(Clone, Default, PartialEq, Eq)]
pub enum Network {
    #[default]
    Unknown,
    Offline,
    Links(Vec<Link>),
}
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Action {
    Hold,
    Connect,
    Disconnect,
}

pub fn decide(settings: &Settings, paused: bool, network: &Network) -> Action {
    if !settings.enabled || paused {
        return Action::Hold;
    }
    let Network::Links(links) = network else {
        return Action::Hold;
    };
    if links.is_empty() {
        return Action::Hold;
    }
    let trusted = |link: &Link| {
        link.transport == Transport::Wifi
            && link
                .ssid
                .as_ref()
                .is_some_and(|name| settings.trusted_ssids.contains(name))
    };
    // Do not disconnect on a trusted secondary Wi-Fi while another uplink is active.
    if links.iter().all(trusted) {
        return Action::Disconnect;
    }
    if links.iter().any(|link| {
        !trusted(link)
            && match link.transport {
                Transport::Wifi => settings.wifi,
                Transport::Ethernet => settings.ethernet,
                Transport::Cellular => settings.cellular,
            }
    }) {
        Action::Connect
    } else {
        Action::Hold
    }
}

#[derive(Default)]
pub struct Schedule {
    network: Network,
    samples: u8,
    failures: u32,
    retry_at: Option<Instant>,
}
impl Schedule {
    pub fn observe(&mut self, network: Network, now: Instant) -> bool {
        if network != self.network {
            self.network = network;
            self.samples = 1;
            self.failures = 0;
            self.retry_at = None;
            return false;
        }
        self.samples = self.samples.saturating_add(1);
        self.samples >= 2 && self.retry_at.map_or(true, |time| now >= time)
    }
    pub fn failed(&mut self, now: Instant) {
        let seconds = 15u64.saturating_mul(1 << self.failures.min(5)).min(300);
        self.failures = self.failures.saturating_add(1);
        self.retry_at = Some(now + Duration::from_secs(seconds));
    }
    pub fn succeeded(&mut self) {
        self.failures = 0;
        self.retry_at = None;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn wifi(ssid: Option<&str>) -> Link {
        Link {
            transport: Transport::Wifi,
            ssid: ssid.map(str::to_string),
        }
    }
    fn settings() -> Settings {
        Settings {
            enabled: true,
            trusted_ssids: vec!["Home".into()],
            ..Settings::default()
        }
    }
    #[test]
    fn defaults_and_validation() {
        assert!(!serde_json::from_str::<Settings>("{}").unwrap().enabled);
        let s = Settings {
            trusted_ssids: vec![" Home ".into(), "Home".into(), "home".into(), "".into()],
            ..settings()
        }
        .validated()
        .unwrap();
        assert_eq!(s.trusted_ssids, ["Home", "home"]);
        for name in ["x".repeat(33), "я".repeat(17), "a\0b".into()] {
            assert_eq!(
                Settings {
                    trusted_ssids: vec![name],
                    ..settings()
                }
                .validated()
                .unwrap_err(),
                "INVALID_SSID"
            );
        }
        assert!(Settings {
            trusted_ssids: (0..33).map(|i| i.to_string()).collect(),
            ..settings()
        }
        .validated()
        .is_err());
        assert_eq!(
            Settings {
                wifi: false,
                cellular: false,
                ..settings()
            }
            .validated()
            .unwrap_err(),
            "NO_TRANSPORT"
        );
    }
    #[test]
    fn trusted_unknown_and_mixed_links() {
        let s = settings();
        assert_eq!(
            decide(&s, false, &Network::Links(vec![wifi(Some("Home"))])),
            Action::Disconnect
        );
        for ssid in [Some("home"), Some("Public"), None] {
            assert_eq!(
                decide(&s, false, &Network::Links(vec![wifi(ssid)])),
                Action::Connect
            );
        }
        let wired = Link {
            transport: Transport::Ethernet,
            ssid: None,
        };
        assert_eq!(
            decide(
                &s,
                false,
                &Network::Links(vec![wifi(Some("Home")), wired.clone()])
            ),
            Action::Hold
        );
        assert_eq!(
            decide(
                &Settings {
                    ethernet: true,
                    ..s.clone()
                },
                false,
                &Network::Links(vec![wifi(Some("Home")), wired])
            ),
            Action::Connect
        );
        assert_eq!(
            decide(
                &s,
                false,
                &Network::Links(vec![wifi(Some("Home")), wifi(Some("Public"))])
            ),
            Action::Connect
        );
        for net in [Network::Unknown, Network::Offline, Network::Links(vec![])] {
            assert_eq!(decide(&s, false, &net), Action::Hold);
        }
    }
    #[test]
    fn pause_and_disable_never_change_current_connection() {
        for name in ["Home", "Public"] {
            let n = Network::Links(vec![wifi(Some(name))]);
            assert_eq!(decide(&settings(), true, &n), Action::Hold);
            assert_eq!(decide(&Settings::default(), false, &n), Action::Hold);
        }
    }
    #[test]
    fn debounce_backoff_and_network_change() {
        let now = Instant::now();
        let mut timer = Schedule::default();
        let n = Network::Links(vec![wifi(Some("Public"))]);
        assert!(!timer.observe(n.clone(), now));
        assert!(timer.observe(n.clone(), now + Duration::from_secs(5)));
        timer.failed(now);
        assert!(!timer.observe(n.clone(), now + Duration::from_secs(14)));
        assert!(timer.observe(n.clone(), now + Duration::from_secs(15)));
        timer.failed(now);
        assert!(!timer.observe(n.clone(), now + Duration::from_secs(29)));
        for _ in 0..10 {
            timer.failed(now);
        }
        assert!(!timer.observe(n.clone(), now + Duration::from_secs(299)));
        assert!(timer.observe(n.clone(), now + Duration::from_secs(300)));
        assert!(!timer.observe(Network::Offline, now));
        assert!(!timer.observe(n.clone(), now));
        assert!(timer.observe(n, now));
    }
}
