//! Service-owned Auto selection. The WebView may close while this task keeps the
//! verified route alive; latency changes alone never trigger a switch.
use std::collections::HashMap;
use std::sync::atomic::Ordering;
use std::time::{Duration, Instant};

use tauri::{AppHandle, Manager, State};

use crate::commands::{self, CONNECTION_INTENT, CONNECTION_OPERATION};
use crate::state::{AppState, PersistedState};

const CHECK_INTERVAL: Duration = Duration::from_secs(30);
const MIN_DWELL: Duration = Duration::from_secs(90);
const FAILED_CHECKS: u8 = 3;
const NODE_COOLDOWN: Duration = Duration::from_secs(5 * 60);
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
}

fn candidates(
    snapshot: &PersistedState,
    subscription_url: &str,
    current_id: Option<&str>,
    failed_at: &HashMap<String, Instant>,
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
    servers.sort_by_key(|(position, server)| {
        let cooling = failed_at
            .get(&server.id)
            .is_some_and(|at| now.duration_since(*at) < NODE_COOLDOWN);
        let ping = snapshot.server_pings.get(&server.id).copied();
        (cooling, ping.is_none(), ping.unwrap_or(u64::MAX), *position)
    });
    servers
        .into_iter()
        .map(|(_, server)| server.id.clone())
        .collect()
}

/// The diagnostic inbound has per-session random credentials and a rule pinned
/// to the selected outbound. A direct OS request would falsely report success
/// even if the VPN route were dead, so never fall back to one here.
async fn active_route_responds(state: &AppState, server_id: &str) -> bool {
    let intent = CONNECTION_INTENT.load(Ordering::SeqCst);
    let before = state.snapshot();
    if !before.connected || before.active_server_id.as_deref() != Some(server_id) {
        return false;
    }
    let route = state.runtime(|runtime| {
        let running = runtime
            .xray
            .as_mut()
            .is_some_and(|child| matches!(child.try_wait(), Ok(None)));
        if running {
            runtime.ping_route.clone()
        } else {
            None
        }
    });
    let Some(route) = route.filter(|route| route.accepts(true, Some(server_id), server_id)) else {
        return false;
    };
    let valid = || {
        intent == CONNECTION_INTENT.load(Ordering::SeqCst)
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
    let after = state.snapshot();
    intent == CONNECTION_INTENT.load(Ordering::SeqCst)
        && after.connected
        && after.active_server_id.as_deref() == Some(server_id)
        && state.runtime(|runtime| runtime.ping_route.as_ref() == Some(&route))
        && (google.is_ok() || apple.is_ok() || cloudflare.is_ok() || telegram.is_ok())
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
    if candidates(
        &snapshot,
        &subscription.url,
        None,
        &HashMap::new(),
        Instant::now(),
    )
    .len()
        < 2
    {
        return Err("Для Авто нужны минимум два совместимых сервера в одной подписке".into());
    }
    let url = subscription.url.clone();
    // A new Auto request replaces the previous mode atomically. If no candidate
    // verifies, a stale subscription must not silently resume in the monitor.
    state
        .mutate(|saved| saved.auto_subscription_url = None)
        .map_err(|error| format!("Не удалось сменить режим Авто: {error}"))?;
    let mut choices = vec![server_id.clone()];
    choices.extend(candidates(
        &snapshot,
        &url,
        Some(&server_id),
        &HashMap::new(),
        Instant::now(),
    ));
    for id in choices.into_iter().take(MAX_CANDIDATES_PER_CYCLE) {
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
        let healthy = active_route_responds(&state, &id).await;
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
            let _ = crate::tray::refresh_tray_menu(&app);
            return Ok(state.snapshot());
        }
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
        let mut failed_at: HashMap<String, Instant> = HashMap::new();
        let mut last_retry: Option<Instant> = None;
        loop {
            tokio::time::sleep(CHECK_INTERVAL).await;
            let state = app.state::<AppState>();
            // Capture intent BEFORE probing. A manual action during a slow
            // check invalidates that observation even for the same server ID.
            let ticket = CONNECTION_INTENT.load(Ordering::SeqCst);
            let snap = state.session_snapshot();
            let checked_route = state.runtime(|runtime| runtime.ping_route.clone());
            let Some(url) = snap.auto_subscription_url.clone() else {
                health = RouteHealthHistory::new(Instant::now());
                failed_at.clear();
                last_retry = None;
                continue;
            };
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
                let healthy = active_route_responds(&state, current).await;
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
                failed_at.insert(current.to_string(), Instant::now());
                tracing::warn!("Auto: three complete protected-route failures; trying fallback");
            }

            let _operation = CONNECTION_OPERATION.lock().await;
            let current = state.session_snapshot();
            if ticket != CONNECTION_INTENT.load(Ordering::SeqCst)
                || !same_auto_context(
                    &snap,
                    &current,
                    checked_route.as_ref(),
                    state.runtime(|runtime| runtime.ping_route.clone()).as_ref(),
                )
            {
                continue;
            }
            let mut fallback = candidates(
                &current,
                &url,
                if current.connected {
                    current.active_server_id.as_deref()
                } else {
                    None
                },
                &failed_at,
                Instant::now(),
            );
            // After app restart there is no live route, but the last verified
            // node remains the preferred first attempt if it is not cooling.
            if !current.connected {
                if let Some(previous) = current.active_server_id.as_deref() {
                    let cooling = failed_at
                        .get(previous)
                        .is_some_and(|at| at.elapsed() < NODE_COOLDOWN);
                    if !cooling {
                        if let Some(index) = fallback.iter().position(|id| id == previous) {
                            let preferred = fallback.remove(index);
                            fallback.insert(0, preferred);
                        }
                    }
                }
            }
            if fallback.is_empty() {
                health.failures = 0;
                continue;
            }
            let mut recovered = false;
            let mut attempted = false;
            for id in fallback.into_iter().take(MAX_CANDIDATES_PER_CYCLE) {
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
                .is_ok()
                    && active_route_responds(&state, &id).await;
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
                failed_at.insert(id, Instant::now());
            }
            // A cancelled observation is neither a failed node nor permission
            // to tear down the session while a newer manual action is waiting.
            if ticket != CONNECTION_INTENT.load(Ordering::SeqCst) {
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
        assert!(candidates(&snapshot, "missing", None, &HashMap::new(), Instant::now()).is_empty());
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
                now
            ),
            vec!["fast", "unknown"]
        );
        let failures = HashMap::from([("fast".into(), now)]);
        assert_eq!(
            candidates(
                &state,
                "https://example.com/sub",
                Some("slow"),
                &failures,
                now
            ),
            vec!["unknown", "fast"]
        );
    }

    #[test]
    fn auto_mode_defaults_to_off_for_old_state_files() {
        let state: PersistedState = serde_json::from_str("{}").unwrap();
        assert!(state.auto_subscription_url.is_none());
    }
}
