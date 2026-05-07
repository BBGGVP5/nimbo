use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct SubscriptionInfo {
    pub upload: Option<u64>,
    pub download: Option<u64>,
    pub total: Option<u64>,
    pub expire: Option<i64>,
}

impl SubscriptionInfo {
    pub fn used(&self) -> Option<u64> {
        match (self.upload, self.download) {
            (Some(u), Some(d)) => Some(u + d),
            (Some(u), None) => Some(u),
            (None, Some(d)) => Some(d),
            _ => None,
        }
    }

    pub fn remaining(&self) -> Option<u64> {
        let used = self.used()?;
        let total = self.total?;
        Some(total.saturating_sub(used))
    }

    pub fn is_empty(&self) -> bool {
        self.upload.is_none()
            && self.download.is_none()
            && self.total.is_none()
            && self.expire.is_none()
    }
}

pub fn parse_subscription_userinfo(header_value: &str) -> SubscriptionInfo {
    let mut info = SubscriptionInfo::default();
    for part in header_value.split(';') {
        let Some((k, v)) = part.split_once('=') else {
            continue;
        };
        let key = k.trim().to_ascii_lowercase();
        let val = v.trim();
        match key.as_str() {
            "upload" => info.upload = val.parse().ok(),
            "download" => info.download = val.parse().ok(),
            "total" => info.total = val.parse().ok(),
            "expire" => info.expire = val.parse().ok(),
            _ => {}
        }
    }
    info
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_full_header() {
        let h = "upload=1024; download=2048; total=10737418240; expire=1734567890";
        let info = parse_subscription_userinfo(h);
        assert_eq!(info.upload, Some(1024));
        assert_eq!(info.download, Some(2048));
        assert_eq!(info.total, Some(10737418240));
        assert_eq!(info.expire, Some(1734567890));
        assert_eq!(info.used(), Some(3072));
        assert_eq!(info.remaining(), Some(10737418240 - 3072));
    }

    #[test]
    fn handles_partial_and_unknown() {
        let h = "total=100; weird-key=zzz";
        let info = parse_subscription_userinfo(h);
        assert!(info.upload.is_none());
        assert_eq!(info.total, Some(100));
    }

    #[test]
    fn empty_header_yields_empty_info() {
        let info = parse_subscription_userinfo("");
        assert!(info.is_empty());
    }
}
