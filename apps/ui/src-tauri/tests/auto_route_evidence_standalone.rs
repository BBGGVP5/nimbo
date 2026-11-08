// Also runnable without Tauri: rustc --edition=2021 --test <this file>.
#[path = "../src/auto_route/evidence.rs"]
mod evidence;

use evidence::Sample;
use std::time::{Duration, Instant};

// Operation, probe cancellation generation, authenticated proxy session.
type Context = (u64, u64, u64);
const CONTEXT: Context = (1, 2, 3);
const CONFIG: [u8; 32] = [1; 32];

fn sample(at: Instant, latency_ms: Option<u64>) -> Sample<Context> {
    Sample {
        context: CONTEXT,
        config: CONFIG,
        at,
        latency_ms,
    }
}

#[test]
fn verified_success_expires_at_thirty_seconds() {
    let now = Instant::now();
    let success = sample(now, Some(0));
    assert_eq!(success.rank(&CONTEXT, &CONFIG, now), Some((0, 0)));
    assert_eq!(
        success.rank(&CONTEXT, &CONFIG, now + Duration::from_secs(29)),
        Some((0, 0))
    );
    assert_eq!(
        success.rank(&CONTEXT, &CONFIG, now + Duration::from_secs(30)),
        None
    );
}

#[test]
fn future_clocks_never_create_evidence() {
    let now = Instant::now();
    for latency in [Some(1), None] {
        assert_eq!(
            sample(now + Duration::from_secs(1), latency).rank(&CONTEXT, &CONFIG, now),
            None
        );
    }
}

#[test]
fn stale_success_does_not_beat_untested_or_hide_new_failure() {
    let now = Instant::now();
    let mut recorded = sample(now, Some(1));
    let later = now + Duration::from_secs(31);
    assert_eq!(
        recorded.rank(&CONTEXT, &CONFIG, later).unwrap_or((1, 0)),
        (1, 0)
    );
    recorded = sample(later, None);
    assert!(recorded.rank(&CONTEXT, &CONFIG, later).unwrap() > (1, 0));
}

#[test]
fn failure_cooldown_precedes_success_and_expires_at_five_minutes() {
    let now = Instant::now();
    let failed = sample(now, None);
    assert_eq!(
        failed.rank(&CONTEXT, &CONFIG, now + Duration::from_secs(299)),
        Some((2, 0))
    );
    assert_eq!(
        failed.rank(&CONTEXT, &CONFIG, now + Duration::from_secs(300)),
        None
    );
    assert!(
        failed.rank(&CONTEXT, &CONFIG, now).unwrap()
            > sample(now, Some(900)).rank(&CONTEXT, &CONFIG, now).unwrap()
    );
}

#[test]
fn same_id_with_changed_configuration_has_no_inherited_success_or_failure() {
    let now = Instant::now();
    for latency in [Some(1), None] {
        assert_eq!(sample(now, latency).rank(&CONTEXT, &[2; 32], now), None);
    }
}

#[test]
fn stale_operation_cancelled_probe_and_replaced_session_are_rejected() {
    let now = Instant::now();
    for changed in [(2, 2, 3), (1, 3, 3), (1, 2, 4)] {
        for latency in [Some(1), None] {
            assert_eq!(sample(now, latency).rank(&changed, &CONFIG, now), None);
        }
    }
}

#[test]
fn service_owned_core_can_be_probed_without_a_local_child() {
    assert!(evidence::can_probe_owned_route(false, true));
    assert!(evidence::can_probe_owned_route(true, false));
    assert!(!evidence::can_probe_owned_route(false, false));
    // This predicate only admits an HTTP probe. No success sample is created.
}

#[test]
fn cache_is_bounded_with_oldest_then_lexical_eviction_and_expiry() {
    let now = Instant::now();
    let mut cache = std::collections::HashMap::new();
    for index in 0..256 {
        evidence::record(&mut cache, format!("node-{index:03}"), sample(now, Some(1)));
    }
    evidence::record(&mut cache, "new".into(), sample(now, None));
    assert_eq!(cache.len(), 256);
    assert!(!cache.contains_key("node-000"));
    assert!(cache.contains_key("new"));
    evidence::record(&mut cache, "new".into(), sample(now, Some(2)));
    assert_eq!(cache.len(), 256);
    assert!(
        cache.contains_key("node-001"),
        "updating a node must not evict another"
    );
    evidence::record(
        &mut cache,
        "later".into(),
        sample(now + Duration::from_secs(300), None),
    );
    assert_eq!(cache.len(), 1);
}
