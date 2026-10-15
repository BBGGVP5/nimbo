//! Runtime-only protected-route evidence. No wall clocks or persisted pings.
use std::collections::HashMap;
use std::time::{Duration, Instant};

pub(crate) const SUCCESS_LIFETIME: Duration = Duration::from_secs(30);
pub(crate) const FAILURE_COOLDOWN: Duration = Duration::from_secs(5 * 60);

// Ownership only authorizes a protected HTTP check; it is never route evidence.
pub(crate) fn can_probe_owned_route(local_core_live: bool, helper_session_owned: bool) -> bool {
    local_core_live || helper_session_owned
}

pub(crate) fn record<S>(samples: &mut HashMap<String, Sample<S>>, id: String, sample: Sample<S>) {
    samples.retain(|_, previous| {
        sample
            .at
            .checked_duration_since(previous.at)
            .is_some_and(|age| age < FAILURE_COOLDOWN)
    });
    if !samples.contains_key(&id) && samples.len() >= 256 {
        let oldest = samples
            .iter()
            .min_by_key(|(id, sample)| (sample.at, *id))
            .map(|(id, _)| id.clone());
        if let Some(oldest) = oldest {
            samples.remove(&oldest);
        }
    }
    samples.insert(id, sample);
}

pub(crate) struct Sample<S> {
    pub context: S,
    pub config: [u8; 32],
    pub at: Instant,
    pub latency_ms: Option<u64>,
}

impl<S: PartialEq> Sample<S> {
    // Unknown candidates use (1, 0): fresh successes first, failures last.
    pub fn rank(&self, context: &S, config: &[u8; 32], now: Instant) -> Option<(u8, u64)> {
        if &self.context != context || &self.config != config {
            return None;
        }
        let age = now.checked_duration_since(self.at)?;
        match self.latency_ms {
            Some(ms) if age < SUCCESS_LIFETIME => Some((0, ms)),
            None if age < FAILURE_COOLDOWN => Some((2, 0)),
            _ => None,
        }
    }
}
