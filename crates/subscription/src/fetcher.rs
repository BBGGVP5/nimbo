use std::time::Duration;

use thiserror::Error;

use crate::USER_AGENT;
use crate::model::{Server, Subscription};
use crate::parser::{ParseError, parse_aggregate};
use crate::userinfo::{SubscriptionInfo, parse_subscription_userinfo};

#[derive(Debug, Clone)]
pub struct FetchOptions {
    pub timeout: Duration,
    pub user_agent: String,
}

impl Default for FetchOptions {
    fn default() -> Self {
        Self {
            timeout: Duration::from_secs(15),
            user_agent: USER_AGENT.to_string(),
        }
    }
}

#[derive(Debug, Error)]
pub enum FetchError {
    #[error("http: {0}")]
    Http(String),
    #[error("status {status}: {body}")]
    Status { status: u16, body: String },
    #[error(transparent)]
    Parse(#[from] ParseError),
}

#[derive(Debug, Clone)]
pub struct Fetched {
    pub raw_body: String,
    pub servers: Vec<Server>,
    pub info: Option<SubscriptionInfo>,
}

pub async fn fetch_subscription(url: &str, opts: &FetchOptions) -> Result<Fetched, FetchError> {
    let client = reqwest::Client::builder()
        .timeout(opts.timeout)
        .user_agent(&opts.user_agent)
        .build()
        .map_err(|e| FetchError::Http(e.to_string()))?;

    let resp = client
        .get(url)
        .send()
        .await
        .map_err(|e| FetchError::Http(e.to_string()))?;

    let status = resp.status();
    let info = resp
        .headers()
        .get("subscription-userinfo")
        .and_then(|v| v.to_str().ok())
        .map(parse_subscription_userinfo);

    let body = resp
        .text()
        .await
        .map_err(|e| FetchError::Http(e.to_string()))?;

    if !status.is_success() {
        return Err(FetchError::Status {
            status: status.as_u16(),
            body: truncate(&body, 200),
        });
    }

    let servers = parse_aggregate(&body)?;
    Ok(Fetched {
        raw_body: body,
        servers,
        info,
    })
}

pub fn build_subscription(url: &str, fetched: Fetched, name: Option<String>) -> Subscription {
    Subscription {
        url: url.to_string(),
        name,
        servers: fetched.servers,
        info: fetched.info,
        fetched_at: now_unix(),
    }
}

fn now_unix() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

fn truncate(s: &str, max: usize) -> String {
    if s.len() <= max {
        s.to_string()
    } else {
        format!("{}…", &s[..max])
    }
}
