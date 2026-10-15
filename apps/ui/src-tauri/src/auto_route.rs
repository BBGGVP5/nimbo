//! Service-owned Auto selection. The WebView may close while this task keeps the
//! verified route alive; latency changes alone never trigger a switch.
use std::collections::HashMap;
use std::sync::atomic::Ordering;
use std::time::{Duration, Instant};

use tauri::{AppHandle, Manager, State};

use crate::commands::{self, CONNECTION_INTENT, CONNECTION_OPERATION};
use crate::state::{AppState, PersistedState};

pub(crate) mod evidence;

// Network type/SSID are not reliable network identities. Never reuse successes
// beyond 30 seconds or across an operation, cancellation, or proxy session.
// An unobserved network change within that window remains possible; ranking is
// only a trial order, and every connection still requires live verification.
#[derive(Clone, PartialEq, Eq)]
pub(crate) struct ProbeContext {
    intent: u64,
    ping_generation: u64,
    connected: bool,
    route: Option<crate::latency::PingRoute>,
}

pub(crate) fn probe_context(state: &AppState, snapshot: &PersistedState) -> ProbeContext {
    ProbeContext {
        intent: CONNECTION_INTENT.load(Ordering::SeqCst),
        ping_generation: commands::ping_generation(),
        connected: snapshot.connected,
        route: state.runtime(|runtime| runtime.ping_route.clone()),
    }
}

type Samples = HashMap<String, evidence::Sample<ProbeContext>>;
type Failures = HashMap<String, ([u8; 32], Instant)>;

const CHECK_INTERVAL: Duration = Duration::from_secs(30);
const MIN_DWELL: Duration = Duration::from_secs(90);
const FAILED_CHECKS: u8 = 3;
const NODE_COOLDOWN: Duration = evidence::FAILURE_COOLDOWN;
const RETRY_DISCONNECTED: Duration = Duration::from_secs(5 * 60);
const MAX_CANDIDATES_PER_CYCLE: usize = 4;
const PROBE_URLS: [&str; 4] = [
    "https://www.gstatic.com/generate_204",
    "https://captive.apple.com/hotspot-detect.html",
    "https://cp.cloudflare.com/generate_204",
    "https://telegram.org/",
];

fn should_failover(failures: u8, dwell: Duration) -> bool {
    failures >= FAILED_CHECKS && dwell >= MIN_DWELL
}

struct RouteHealthHistory {
    observed_id: Option<String>,
    observed_route: Option<crate::latency::PingRoute>,
    since: Instant,
    failures: u8,
}

impl RouteHealthHistory {
    fn new(now: Instant) -> Self {
        Self {
            observed_id: None,
            observed_route: None,
            since: now,
            failures: 0,
        }
    }

    fn observe(&mut self, id: &str, route: Option<crate::latency::PingRoute>, now: Instant) {
        // A reconnect to the same node owns new credentials/port. Its failures
        // and minimum dwell must not inherit the previous session's history.
        if self.observed_id.as_deref() != Some(id) || self.observed_route != route {
            self.observed_id = Some(id.to_string());
            self.observed_route = route;
            self.since = now;
            self.failures = 0;
        }
    }

    fn record(&mut self, healthy: bool, now: Instant) -> bool {
        self.failures = if healthy {
            0
        } else {
            self.failures.saturating_add(1)
        };
        should_failover(self.failures, now.duration_since(self.since))
    }
}

fn same_auto_context(
    before: &PersistedState,
    after: &PersistedState,
    before_route: Option<&crate::latency::PingRoute>,
    after_route: Option<&crate::latency::PingRoute>,
) -> bool {
    before.auto_subscription_url == after.auto_subscription_url
        && before.active_server_id == after.active_server_id
        && before.connected == after.connected
        && before.core_profiles.preferred_core == after.core_profiles.preferred_core
        && before_route == after_route
        && before.active_server_id.as_deref().is_none_or(|id| {
            server_identity(before, id).is_some()
                && server_identity(before, id) == server_identity(after, id)
        })
}

fn server_identity(snapshot: &PersistedState, id: &str) -> Option<[u8; 32]> {
    let server = snapshot
        .subscriptions
        .iter()
        .flat_map(|sub| &sub.servers)
        .find(|server| server.id == id)?;
    commands::diagnostic_identity(snapshot, server)
}

fn candidates(
    snapshot: &PersistedState,
    subscription_url: &str,
    current_id: Option<&str>,
    failed_at: &Failures,
    samples: &Samples,
    context: &ProbeContext,
    now: Instant,
) -> Vec<String> {
    let Some(subscription) = snapshot
        .subscriptions
        .iter()
        .find(|sub| sub.url == subscription_url)
    else {
        return Vec::new();
    };
    let mut servers: Vec<_> = subscription
        .servers
        .iter()
        .enumerate()
        .filter(|(_, server)| Some(server.id.as_str()) != current_id)
        .filter(|(_, server)| match snapshot.core_profiles.preferred_core {
            Some(nimbo_mihomo::CoreKind::Mihomo) => false,
            Some(nimbo_mihomo::CoreKind::Awg) => {
                matches!(server.protocol, nimbo_subscription::Protocol::Awg(_))
            }
            Some(nimbo_mihomo::CoreKind::Xray) => {
                !matches!(server.protocol, nimbo_subscription::Protocol::Awg(_))
            }
            None => true,
        })
        .collect();
    servers.sort_by_cached_key(|(position, server)| {
        let identity = commands::diagnostic_identity(snapshot, server);
        let cooling = failed_at.get(&server.id).is_some_and(|(config, at)| {
            Some(*config) == identity
                && now
                    .checked_duration_since(*at)
                    .is_some_and(|age| age < NODE_COOLDOWN)
        });
        let rank = if cooling {
            (2, 0)
        } else {
            identity
                .and_then(|config| samples.get(&server.id)?.rank(context, &config, now))
                .unwrap_or((1, 0))
        };
        (rank, *position)
    });
    servers
        .into_iter()
        .map(|(_, server)| server.id.clone())
        .collect()
}

/// The diagnostic inbound has per-session random credentials and a rule pinned
/// to the selected outbound. A direct OS request would falsely report success
/// even if the VPN route were dead, so never fall back to one here.
async fn active_route_responds(state: &AppState, server_id: &str) -> Option<bool> {
    let intent = CONNECTION_INTENT.load(Ordering::SeqCst);
    let before = state.snapshot();
    if !before.connected || before.active_server_id.as_deref() != Some(server_id) {
        return None;
    }
    let config = server_identity(&before, server_id)?;
    // A subscription edit may keep the ID while the running core still uses
    // the old endpoint/credentials. Such a session cannot verify the new config.
    if state.runtime(|runtime| runtime.ping_config_identity != Some(config)) {
        return None;
    }
    let route = state.runtime(|runtime| {
        let running = runtime
            .xray
            .as_mut()
            .is_some_and(|child| matches!(child.try_wait(), Ok(None)));
        #[cfg(target_os = "linux")]
        let helper_owned = runtime.tun_session.is_some();
        #[cfg(not(target_os = "linux"))]
        let helper_owned = false;
        if evidence::can_probe_owned_route(running, helper_owned) {
            runtime.ping_route.clone()
        } else {
            None
        }
    });
    let Some(route) = route.filter(|route| route.accepts(true, Some(server_id), server_id)) else {
        return Some(false);
    };
    let valid = || {
        intent == CONNECTION_INTENT.load(Ordering::SeqCst)
            && state.runtime(|runtime| runtime.ping_config_identity == Some(config))
            && same_auto_context(
                &before,
                &state.snapshot(),
                Some(&route),
                state.runtime(|runtime| runtime.ping_route.clone()).as_ref(),
            )
    };
    let (google, apple, cloudflare, telegram) = tokio::join!(
        crate::latency::measure_http_guarded(&route, "http_get", PROBE_URLS[0], 3_500, &valid),
        crate::latency::measure_http_guarded(&route, "http_get", PROBE_URLS[1], 3_500, &valid),
        crate::latency::measure_http_guarded(&route, "http_get", PROBE_URLS[2], 3_500, &valid),
        crate::latency::measure_http_guarded(&route, "http_get", PROBE_URLS[3], 3_500, &valid)
    );
    if !valid() {
        return None;
    }
    Some(google.is_ok() || apple.is_ok() || cloudflare.is_ok() || telegram.is_ok())
}

#[tauri::command]
pub async fn connect_auto_server(
    app: AppHandle,
    state: State<'_, AppState>,
    server_id: String,
) -> Result<PersistedState, String> {
    commands::preflight_server_connection(&app, &state.snapshot(), &server_id)?;
    let ticket = CONNECTION_INTENT.fetch_add(1, Ordering::SeqCst) + 1;
    let _operation = CONNECTION_OPERATION.lock().await;
    if ticket != CONNECTION_INTENT.load(Ordering::SeqCst) {
        return Err("Выбор Авто отменён новым действием".into());
    }
    let snapshot = state.snapshot();
    commands::preflight_server_connection(&app, &snapshot, &server_id)?;
    let subscription = snapshot
        .subscriptions
        .iter()
        .find(|sub| sub.servers.iter().any(|server| server.id == server_id))
        .ok_or("Сервер не найден в подписке")?;
    if snapshot.core_profiles.preferred_core == Some(nimbo_mihomo::CoreKind::Mihomo) {
        return Err("Авто для профилей Mihomo пока недоступно; выберите группу в профиле".into());
    }
    let url = subscription.url.clone();
    // Adopt only the immediate predecessor's fresh samples for this initial
    // ordering, under the operation lock and with the SAME current session.
    // They cannot be reused once a candidate changes the session.
    let mut initial_context = probe_context(&state, &snapshot);
    initial_context.intent = ticket - 1;
    let mut choices = state.runtime(|runtime| {
        candidates(
            &snapshot,
            &url,
            None,
            &HashMap::new(),
            &runtime.auto_route_evidence,
            &initial_context,
            Instant::now(),
        )
    });
    if choices.len() < 2 {
        return Err("Для Авто нужны минимум два совместимых сервера в одной подписке".into());
    }
    // The UI's server_id is a subscription hint, often chosen from legacy raw
    // pings. Only an actually connected route gets a first verification attempt.
    if snapshot.connected {
        if let Some(index) = choices
            .iter()
            .position(|id| Some(id) == snapshot.active_server_id.as_ref())
        {
            let active = choices.remove(index);
            choices.insert(0, active);
        }
    }
    // A new Auto request replaces the previous mode atomically. If no candidate
    // verifies, a stale subscription must not silently resume in the monitor.
    state
        .mutate(|saved| saved.auto_subscription_url = None)
        .map_err(|error| format!("Не удалось сменить режим Авто: {error}"))?;
    let mut tried: Vec<String> = Vec::new();
    for attempt in 0..MAX_CANDIDATES_PER_CYCLE {
        if attempt > 0 {
            // Startup may replace the session and consume the evidence lifetime.
            // Re-rank remaining trials instead of carrying the old priority on.
            let latest = state.snapshot();
            let context = probe_context(&state, &latest);
            choices = state.runtime(|runtime| {
                candidates(
                    &latest,
                    &url,
                    None,
                    &HashMap::new(),
                    &runtime.auto_route_evidence,
                    &context,
                    Instant::now(),
                )
            });
        }
        let Some(id) = choices.iter().find(|id| !tried.contains(id)).cloned() else {
            break;
        };
        tried.push(id.clone());
        if ticket != CONNECTION_INTENT.load(Ordering::SeqCst) {
            return Err("Выбор Авто отменён новым действием".into());
        }
        let already_active =
            state.snapshot().connected && state.snapshot().active_server_id.as_deref() == Some(&id);
        if !already_active
            && commands::connect_server_inner(
                app.clone(),
                state.clone(),
                id.clone(),
                snapshot.core_profiles.preferred_core,
            )
            .await
            .is_err()
        {
            continue;
        }
        let Some(healthy) = active_route_responds(&state, &id).await else {
            return Err("Контекст проверки Авто изменился; повторите выбор".into());
        };
        if ticket != CONNECTION_INTENT.load(Ordering::SeqCst) {
            return Err("Выбор Авто отменён новым действием".into());
        }
        if healthy {
            state
                .mutate(|saved| {
                    saved.auto_subscription_url = Some(url.clone());
                    saved.session_core_preference =
                        Some(nimbo_mihomo::selection::CorePreference::from(
                            snapshot.core_profiles.preferred_core,
                        ));
                })
                .map_err(|error| format!("Не удалось сохранить режим Авто: {error}"))?;
            crate::on_demand::manual_connected(&state, ticket)?;
            let _ = crate::tray::refresh_tray_menu(&app);
            return Ok(state.snapshot());
        }
    }
    if ticket != CONNECTION_INTENT.load(Ordering::SeqCst) {
        return Err("Выбор Авто отменён новым действием".into());
    }
    let _ = commands::stop_runtime(&state);
    let _ = state.mutate(|saved| {
        saved.connected = false;
        saved.connected_at = None;
    });
    Err("Ни один проверенный узел не провёл HTTPS-запрос через туннель".into())
}

pub fn start_monitor(app: AppHandle) {
    tauri::async_runtime::spawn(async move {
        let mut health = RouteHealthHistory::new(Instant::now());
        let mut failed_at = Failures::new();
        let mut history_context = None;
        let mut last_retry: Option<Instant> = None;
        loop {
            tokio::time::sleep(CHECK_INTERVAL).await;
            let state = app.state::<AppState>();
            // Capture intent BEFORE probing. A manual action during a slow
            // check invalidates that observation even for the same server ID.
            let ticket = CONNECTION_INTENT.load(Ordering::SeqCst);
            let snap = state.session_snapshot();
            if !crate::on_demand::permits_recovery(&snap) {
                continue;
            }
            let checked_route = state.runtime(|runtime| runtime.ping_route.clone());
            let Some(url) = snap.auto_subscription_url.clone() else {
                health = RouteHealthHistory::new(Instant::now());
                failed_at.clear();
                last_retry = None;
                continue;
            };
            if history_context.as_ref() != Some(&(ticket, url.clone())) {
                health = RouteHealthHistory::new(Instant::now());
                failed_at.clear();
                last_retry = None;
                history_context = Some((ticket, url.clone()));
            }
            if snap.core_profiles.preferred_core == Some(nimbo_mihomo::CoreKind::Mihomo) {
                continue;
            }
            if !snap.connected {
                // An explicit Quit leaves the saved selection but must not
                // silently reconnect on next launch. Only retry a failure
                // observed by this running monitor; launch auto-connect is a
                // separate user preference handled by the app startup path.
                let Some(previous_retry) = last_retry else {
                    continue;
                };
                if previous_retry.elapsed() < RETRY_DISCONNECTED {
                    continue;
                }
                last_retry = Some(Instant::now());
            } else {
                let Some(current) = snap.active_server_id.as_deref() else {
                    continue;
                };
                health.observe(current, checked_route.clone(), Instant::now());
                let Some(healthy) = active_route_responds(&state, current).await else {
                    continue;
                };
                if ticket != CONNECTION_INTENT.load(Ordering::SeqCst)
                    || !same_auto_context(
                        &snap,
                        &state.session_snapshot(),
                        checked_route.as_ref(),
                        state.runtime(|runtime| runtime.ping_route.clone()).as_ref(),
                    )
                {
                    continue;
                }
                if !health.record(healthy, Instant::now()) {
                    continue;
                }
                if let Some(config) = server_identity(&snap, current) {
                    failed_at.insert(current.to_string(), (config, Instant::now()));
                }
                tracing::warn!("Auto: three complete protected-route failures; trying fallback");
            }

            let _operation = CONNECTION_OPERATION.lock().await;
            let current = state.session_snapshot();
            if ticket != CONNECTION_INTENT.load(Ordering::SeqCst)
                || !crate::on_demand::permits_recovery(&current)
                || !same_auto_context(
                    &snap,
                    &current,
                    checked_route.as_ref(),
                    state.runtime(|runtime| runtime.ping_route.clone()).as_ref(),
                )
            {
                continue;
            }
            let context = probe_context(&state, &current);
            let mut fallback = state.runtime(|runtime| {
                candidates(
                    &current,
                    &url,
                    if current.connected {
                        current.active_server_id.as_deref()
                    } else {
                        None
                    },
                    &failed_at,
                    &runtime.auto_route_evidence,
                    &context,
                    Instant::now(),
                )
            });
            if fallback.is_empty() {
                health.failures = 0;
                continue;
            }
            let mut recovered = false;
            let mut attempted = false;
            let mut invalidated = false;
            let mut tried: Vec<String> = Vec::new();
            for attempt in 0..MAX_CANDIDATES_PER_CYCLE {
                if attempt > 0 {
                    let latest = state.session_snapshot();
                    let context = probe_context(&state, &latest);
                    fallback = state.runtime(|runtime| {
                        candidates(
                            &latest,
                            &url,
                            if current.connected {
                                current.active_server_id.as_deref()
                            } else {
                                None
                            },
                            &failed_at,
                            &runtime.auto_route_evidence,
                            &context,
                            Instant::now(),
                        )
                    });
                }
                let Some(id) = fallback.iter().find(|id| !tried.contains(id)).cloned() else {
                    break;
                };
                tried.push(id.clone());
                if ticket != CONNECTION_INTENT.load(Ordering::SeqCst) {
                    break;
                }
                // A policy/capability rejection is not a failed runtime start
                // and must never authorize teardown in the cleanup below.
                if commands::preflight_server_connection(&app, &current, &id).is_err() {
                    continue;
                }
                attempted = true;
                tracing::info!("Auto: testing fallback node");
                let healthy = commands::connect_server_inner(
                    app.clone(),
                    state.clone(),
                    id.clone(),
                    current.core_profiles.preferred_core,
                )
                .await
                .is_ok();
                let healthy = if healthy {
                    match active_route_responds(&state, &id).await {
                        Some(healthy) => healthy,
                        None => {
                            invalidated = true;
                            break;
                        }
                    }
                } else {
                    false
                };
                if ticket != CONNECTION_INTENT.load(Ordering::SeqCst) {
                    break;
                }
                if healthy {
                    health = RouteHealthHistory::new(Instant::now());
                    health.observe(
                        &id,
                        state.runtime(|runtime| runtime.ping_route.clone()),
                        Instant::now(),
                    );
                    recovered = true;
                    break;
                }
                if let Some(config) = server_identity(&current, &id) {
                    failed_at.insert(id, (config, Instant::now()));
                }
            }
            // A cancelled observation is neither a failed node nor permission
            // to tear down the session while a newer manual action is waiting.
            if invalidated || ticket != CONNECTION_INTENT.load(Ordering::SeqCst) {
                continue;
            }
            if !recovered && attempted {
                let _ = commands::stop_runtime(&state);
                let _ = state.mutate(|saved| {
                    saved.connected = false;
                    saved.connected_at = None;
                });
                health.failures = 0;
                last_retry = Some(Instant::now());
            }
            let _ = crate::tray::refresh_tray_menu(&app);
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    fn context() -> ProbeContext {
        ProbeContext {
            intent: 1,
            ping_generation: 1,
            connected: false,
            route: None,
        }
    }

    fn server(id: &str) -> nimbo_subscription::Server {
        nimbo_subscription::Server {
            id: id.into(),
            name: id.into(),
            server_description: None,
            host_uuid: None,
            xray_json_template_uuid: None,
            protocol: nimbo_subscription::Protocol::Vless(nimbo_subscription::VlessConfig {
                address: "example.com".into(),
                port: 443,
                uuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee".into(),
                flow: None,
                encryption: "none".into(),
                stream: nimbo_subscription::StreamSettings::default(),
            }),
        }
    }

    fn snapshot() -> PersistedState {
        let mut state = PersistedState::default();
        state.subscriptions.push(nimbo_subscription::Subscription {
            url: "https://example.com/sub".into(),
            name: Some("Test".into()),
            parser_revision: 0,
            meta: nimbo_subscription::SubscriptionMeta::default(),
            servers: vec![server("fast"), server("slow"), server("unknown")],
            info: None,
            fetched_at: 0,
        });
        state.server_pings.insert("fast".into(), 30);
        state.server_pings.insert("slow".into(), 300);
        state
    }

    #[test]
    fn probe_regression_auto_discards_observations_from_replaced_context() {
        let node = server("same");
        let mut config = serde_json::to_value(nimbo_xray_config::build_config(&node)).unwrap();
        let route = crate::latency::PingRoute::prepare(&node, &mut config).unwrap();
        let replacement = crate::latency::PingRoute::prepare(&node, &mut config).unwrap();
        let mut before = snapshot();
        before.subscriptions[0].servers.push(node);
        before.connected = true;
        before.active_server_id = Some("same".into());
        before.auto_subscription_url = Some("https://example.com/sub".into());
        assert!(same_auto_context(
            &before,
            &before,
            Some(&route),
            Some(&route)
        ));
        assert!(!same_auto_context(
            &before,
            &before,
            Some(&route),
            Some(&replacement)
        ));
        let mut after = before.clone();
        after.auto_subscription_url = None;
        assert!(!same_auto_context(
            &before,
            &after,
            Some(&route),
            Some(&route)
        ));
        after = before.clone();
        after.connected = false;
        assert!(!same_auto_context(
            &before,
            &after,
            Some(&route),
            Some(&route)
        ));
    }

    #[test]
    fn probe_regression_same_server_reconnect_resets_auto_failures_and_dwell() {
        let node = server("same");
        let prepare = || {
            let mut config = serde_json::to_value(nimbo_xray_config::build_config(&node)).unwrap();
            crate::latency::PingRoute::prepare(&node, &mut config).unwrap()
        };
        let first = prepare();
        let replacement = prepare();
        let start = Instant::now();
        let mut health = RouteHealthHistory::new(start);
        health.observe("same", Some(first.clone()), start);
        assert!(!health.record(false, start + Duration::from_secs(30)));
        health.observe("same", Some(first), start + Duration::from_secs(60));
        assert!(!health.record(false, start + Duration::from_secs(60)));

        let reconnected = start + Duration::from_secs(100);
        health.observe("same", Some(replacement), reconnected);
        assert!(
            !health.record(false, reconnected),
            "old failures must not disconnect a fresh session"
        );
        assert!(!health.record(false, reconnected + Duration::from_secs(30)));
        assert!(
            !health.record(false, reconnected + Duration::from_secs(60)),
            "fresh session requires full dwell"
        );
        assert!(health.record(false, reconnected + Duration::from_secs(90)));
        assert!(!health.record(true, reconnected + Duration::from_secs(120)));
        assert!(
            !health.record(false, reconnected + Duration::from_secs(150)),
            "success resets consecutive failures"
        );
    }

    #[test]
    fn three_failures_and_minimum_dwell_are_both_required() {
        assert!(!should_failover(1, Duration::from_secs(180)));
        assert!(!should_failover(2, Duration::from_secs(180)));
        assert!(!should_failover(3, Duration::from_secs(89)));
        assert!(should_failover(3, Duration::from_secs(90)));
    }

    #[test]
    fn missing_subscription_has_no_candidates() {
        let snapshot = PersistedState::default();
        assert!(candidates(
            &snapshot,
            "missing",
            None,
            &HashMap::new(),
            &HashMap::new(),
            &context(),
            Instant::now()
        )
        .is_empty());
    }

    #[test]
    fn working_route_is_not_a_fallback_and_recent_failure_goes_last() {
        let state = snapshot();
        let now = Instant::now();
        assert_eq!(
            candidates(
                &state,
                "https://example.com/sub",
                Some("slow"),
                &HashMap::new(),
                &HashMap::new(),
                &context(),
                now
            ),
            vec!["fast", "unknown"]
        );
        let failures = HashMap::from([(
            "fast".into(),
            (server_identity(&state, "fast").unwrap(), now),
        )]);
        assert_eq!(
            candidates(
                &state,
                "https://example.com/sub",
                Some("slow"),
                &failures,
                &HashMap::new(),
                &context(),
                now
            ),
            vec!["unknown", "fast"]
        );
    }

    #[test]
    fn auto_route_raw_pings_never_rank_candidates() {
        let mut state = snapshot();
        state.server_pings.insert("fast".into(), 900);
        state.server_pings.insert("slow".into(), 0);
        state.server_pings.insert("unknown".into(), 1);
        assert_eq!(
            candidates(
                &state,
                "https://example.com/sub",
                None,
                &HashMap::new(),
                &HashMap::new(),
                &context(),
                Instant::now()
            ),
            vec!["fast", "slow", "unknown"]
        );
    }

    #[test]
    fn auto_route_fresh_evidence_cannot_override_failure_cooldown() {
        let state = snapshot();
        let now = Instant::now();
        let config = server_identity(&state, "slow").unwrap();
        let samples = HashMap::from([(
            "slow".into(),
            evidence::Sample {
                context: context(),
                config,
                at: now,
                latency_ms: Some(1),
            },
        )]);
        let order = |failures: &Failures, at| {
            candidates(
                &state,
                "https://example.com/sub",
                None,
                failures,
                &samples,
                &context(),
                at,
            )
        };
        assert_eq!(order(&HashMap::new(), now), vec!["slow", "fast", "unknown"]);
        let failures = HashMap::from([("slow".into(), (config, now))]);
        assert_eq!(order(&failures, now), vec!["fast", "unknown", "slow"]);
        assert_eq!(
            order(&HashMap::new(), now + Duration::from_secs(30)),
            vec!["fast", "slow", "unknown"]
        );
        assert_eq!(
            order(&failures, now + NODE_COOLDOWN),
            vec!["fast", "slow", "unknown"]
        );
    }

    #[test]
    fn auto_route_identity_tracks_credentials_and_transport_not_ui_or_ping() {
        let mut state = snapshot();
        let original = server_identity(&state, "fast").unwrap();
        state.server_pings.insert("fast".into(), 999);
        state.subscriptions[0].servers[0].name = "Renamed".into();
        state.subscriptions[0].servers[0].server_description = Some("UI only".into());
        state.preferences.servers_sorting = "name".into();
        assert_eq!(server_identity(&state, "fast"), Some(original));
        let nimbo_subscription::Protocol::Vless(config) =
            &mut state.subscriptions[0].servers[0].protocol
        else {
            panic!()
        };
        config.uuid = "bbbbbbbb-cccc-dddd-eeee-ffffffffffff".into();
        assert_ne!(server_identity(&state, "fast"), Some(original));
        let credential_change = server_identity(&state, "fast");
        let nimbo_subscription::Protocol::Vless(config) =
            &mut state.subscriptions[0].servers[0].protocol
        else {
            panic!()
        };
        config.port = 8443;
        assert_ne!(server_identity(&state, "fast"), credential_change);
    }

    #[test]
    fn auto_route_changed_config_rejects_completion_and_inherited_cooldown() {
        let mut before = snapshot();
        before.active_server_id = Some("slow".into());
        let now = Instant::now();
        let old = server_identity(&before, "slow").unwrap();
        let failures = HashMap::from([("slow".into(), (old, now))]);
        let mut after = before.clone();
        let nimbo_subscription::Protocol::Vless(config) =
            &mut after.subscriptions[0].servers[1].protocol
        else {
            panic!()
        };
        config.address = "replacement.invalid".into();
        assert!(!same_auto_context(&before, &after, None, None));
        assert_eq!(
            candidates(
                &after,
                "https://example.com/sub",
                None,
                &failures,
                &HashMap::new(),
                &context(),
                now
            ),
            vec!["fast", "slow", "unknown"]
        );
    }

    #[test]
    fn auto_mode_defaults_to_off_for_old_state_files() {
        let state: PersistedState = serde_json::from_str("{}").unwrap();
        assert!(state.auto_subscription_url.is_none());
    }
}
