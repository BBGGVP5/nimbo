pub fn may_recover(ticket: u64, current: u64, server: &str, active: Option<&str>, attempt: usize) -> bool {
    ticket == current && active == Some(server) && attempt < 3
}

pub fn retry_delay_ms(attempt: usize) -> u64 {
    [2_000, 5_000, 10_000][attempt.min(2)]
}

pub fn accepts_wake(now: u64, previous: u64) -> bool {
    previous == 0 || now.saturating_sub(previous) >= 30_000
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn duplicate_wake_is_ignored() {
        assert!(accepts_wake(100_000, 0));
        assert!(!accepts_wake(110_000, 100_000));
        assert!(!accepts_wake(90_000, 100_000));
        assert!(accepts_wake(135_000, 100_000));
    }
    #[test]
    fn manual_action_invalidates_ticket() {
        assert!(!may_recover(3, 4, "a", Some("a"), 0));
    }
    #[test]
    fn server_change_invalidates_ticket() {
        assert!(!may_recover(3, 3, "a", Some("b"), 0));
        assert!(!may_recover(3, 3, "a", None, 0));
    }
    #[test]
    fn recovery_is_bounded() {
        assert!(may_recover(3, 3, "a", Some("a"), 0));
        assert!(may_recover(3, 3, "a", Some("a"), 2));
        assert!(!may_recover(3, 3, "a", Some("a"), 3));
    }
    #[test]
    fn delay_is_bounded_and_allows_network_to_settle() {
        assert_eq!(retry_delay_ms(0), 2_000);
        assert_eq!(retry_delay_ms(1), 5_000);
        assert_eq!(retry_delay_ms(2), 10_000);
    }
}
