use crate::wire::{loopback_address, request, Envelope, Snapshot, MAX_RESPONSE};
use serde_json::Value;
use std::time::Duration;

/// Only Nimbo's authenticated bridge is reachable. No arbitrary upstream REST,
/// file/config reload/debug/upgrade endpoint is exposed through this client.
#[derive(Clone)]
pub struct Controller {
    client: reqwest::Client,
    endpoint: String,
    secret: String,
    generation: u64,
}
/// Dropping an HTTP future does not cancel Mihomo's outbound. Tie the actual
/// native probe to this future, including disconnect/intent cancellation. The
/// bounded cleanup task keeps the same token and generation; never a new session.
struct ProbeCancellation {
    controller: Controller,
    request_id: String,
    armed: bool,
}
impl Drop for ProbeCancellation {
    fn drop(&mut self) {
        if !self.armed {
            return;
        }
        let Ok(runtime) = tokio::runtime::Handle::try_current() else {
            return;
        };
        let controller = self.controller.clone();
        let id = self.request_id.clone();
        runtime.spawn(async move {
            let mut body = request("cancel", controller.generation);
            body["targetRequestId"] = id.into();
            let _ =
                tokio::time::timeout(Duration::from_secs(3), controller.invoke(body, true)).await;
        });
    }
}

impl Controller {
    pub fn new(address: &str, secret: String, generation: u64) -> Result<Self, String> {
        let address = loopback_address(address)?;
        if secret.len() < 32 || generation == 0 {
            return Err("INVALID_NATIVE_READINESS".into());
        }
        let client = reqwest::Client::builder()
            .no_proxy()
            .redirect(reqwest::redirect::Policy::none())
            .timeout(Duration::from_secs(40))
            .connect_timeout(Duration::from_secs(2))
            .build()
            .map_err(|_| "CONTROLLER_UNAVAILABLE")?;
        Ok(Self {
            client,
            endpoint: format!("http://{address}/v1/invoke"),
            secret,
            generation,
        })
    }
    pub fn generation(&self) -> u64 {
        self.generation
    }
    async fn invoke(&self, body: Value, expected_generation: bool) -> Result<Envelope, String> {
        let id = body["requestId"].as_str().ok_or("INVALID_REQUEST")?;
        let mut response = self
            .client
            .post(&self.endpoint)
            .bearer_auth(&self.secret)
            .header(reqwest::header::CONTENT_TYPE, "application/json")
            .body(serde_json::to_vec(&body).map_err(|_| "INVALID_REQUEST")?)
            .send()
            .await
            .map_err(|_| "CONTROLLER_UNAVAILABLE")?;
        if !response.status().is_success() {
            return Err("CONTROLLER_REJECTED".into());
        }
        if response
            .content_length()
            .is_some_and(|n| n > MAX_RESPONSE as u64)
        {
            return Err("NATIVE_RESPONSE_TOO_LARGE".into());
        }
        let mut bytes = Vec::new();
        while let Some(chunk) = response
            .chunk()
            .await
            .map_err(|_| "CONTROLLER_UNAVAILABLE")?
        {
            if bytes.len() + chunk.len() > MAX_RESPONSE {
                return Err("NATIVE_RESPONSE_TOO_LARGE".into());
            }
            bytes.extend_from_slice(&chunk);
        }
        let envelope = Envelope::decode(&bytes, Some(id))?;
        if expected_generation && envelope.generation != self.generation {
            return Err("STALE_GENERATION".into());
        }
        Ok(envelope)
    }
    pub async fn status(&self) -> Result<Value, String> {
        Ok(self
            .invoke(request("status", self.generation), true)
            .await?
            .data)
    }
    pub async fn snapshot(&self) -> Result<Snapshot, String> {
        serde_json::from_value(
            self.invoke(request("snapshot", self.generation), true)
                .await?
                .data,
        )
        .map_err(|_| "INVALID_NATIVE_SNAPSHOT".into())
    }
    pub async fn select(&self, group: &str, name: &str) -> Result<Snapshot, String> {
        check_name(group)?;
        check_name(name)?;
        let mut body = request("select", self.generation);
        body["group"] = group.into();
        body["name"] = name.into();
        let snapshot: Snapshot = serde_json::from_value(self.invoke(body, true).await?.data)
            .map_err(|_| "INVALID_NATIVE_SNAPSHOT")?;
        if snapshot
            .groups
            .get(group)
            .and_then(|g| g.get("now"))
            .and_then(Value::as_str)
            != Some(name)
        {
            return Err("SELECTION_NOT_CONFIRMED".into());
        }
        Ok(snapshot)
    }
    pub async fn refresh_provider(&self, name: &str) -> Result<Snapshot, String> {
        check_name(name)?;
        let mut body = request("refreshProvider", self.generation);
        body["name"] = name.into();
        serde_json::from_value(self.invoke(body, true).await?.data)
            .map_err(|_| "INVALID_NATIVE_SNAPSHOT".into())
    }
    pub async fn refresh_rule_provider(&self, name: &str) -> Result<Snapshot, String> {
        check_name(name)?;
        let mut body = request("refreshRuleProvider", self.generation);
        body["name"] = name.into();
        serde_json::from_value(self.invoke(body, true).await?.data)
            .map_err(|_| "INVALID_NATIVE_SNAPSHOT".into())
    }
    pub async fn delay(
        &self,
        name: &str,
        url: &str,
        timeout_ms: u64,
        expected_status: Option<&str>,
    ) -> Result<Value, String> {
        check_name(name)?;
        let parsed = url::Url::parse(url).map_err(|_| "INVALID_DELAY_URL")?;
        if !matches!(parsed.scheme(), "http" | "https")
            || parsed.host_str().is_none()
            || !parsed.username().is_empty()
            || parsed.password().is_some()
            || parsed.fragment().is_some()
        {
            return Err("INVALID_DELAY_URL".into());
        }
        if !(100..=30_000).contains(&timeout_ms) {
            return Err("INVALID_DELAY_TIMEOUT".into());
        }
        let mut body = request("delay", self.generation);
        body["name"] = name.into();
        body["url"] = url.into();
        body["timeoutMs"] = timeout_ms.into();
        if let Some(status) = expected_status {
            if status.len() > 128 {
                return Err("INVALID_EXPECTED_STATUS".into());
            }
            body["expectedStatus"] = status.into();
        }
        let mut cancellation = ProbeCancellation {
            controller: self.clone(),
            request_id: body["requestId"]
                .as_str()
                .ok_or("INVALID_REQUEST")?
                .to_owned(),
            armed: true,
        };
        let response = self.invoke(body, true).await?;
        cancellation.armed = false;
        Ok(response.data)
    }
    pub async fn stop(&self) -> Result<(), String> {
        self.invoke(request("stop", self.generation), false)
            .await
            .map(|_| ())
    }
}
fn check_name(name: &str) -> Result<(), String> {
    if name.is_empty() || name.len() > 1024 || name.contains('\0') {
        Err("INVALID_ENTITY_NAME".into())
    } else {
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::{
        io::{AsyncReadExt, AsyncWriteExt},
        net::TcpListener,
    };
    async fn one_response(generation: u64) -> (String, tokio::task::JoinHandle<Value>) {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap().to_string();
        let task = tokio::spawn(async move {
            let (mut stream, _) = listener.accept().await.unwrap();
            let mut bytes = Vec::new();
            let header_end = loop {
                let mut one = [0u8; 1];
                stream.read_exact(&mut one).await.unwrap();
                bytes.push(one[0]);
                if bytes.ends_with(b"\r\n\r\n") {
                    break bytes.len();
                }
            };
            let headers = String::from_utf8(bytes.clone())
                .unwrap()
                .to_ascii_lowercase();
            assert!(headers.starts_with("post /v1/invoke "));
            assert!(headers.contains(&format!("authorization: bearer {}", "x".repeat(64))));
            let length = headers
                .lines()
                .find_map(|l| l.strip_prefix("content-length: "))
                .unwrap()
                .parse::<usize>()
                .unwrap();
            bytes.resize(header_end + length, 0);
            stream.read_exact(&mut bytes[header_end..]).await.unwrap();
            let body: Value = serde_json::from_slice(&bytes[header_end..]).unwrap();
            let response=serde_json::json!({"apiVersion":1,"requestId":body["requestId"],"success":true,"generation":generation,
            "data":{"groups":{"Main / 日本%?#":{"now":"DIRECT"}},"providers":{}}}).to_string();
            stream
                .write_all(
                    format!(
                        "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                        response.len(),
                        response
                    )
                    .as_bytes(),
                )
                .await
                .unwrap();
            body
        });
        (addr, task)
    }
    #[tokio::test]
    async fn bridge_uses_auth_and_json_names_and_actual_selection_readback() {
        let (addr, task) = one_response(4).await;
        let c = Controller::new(&addr, "x".repeat(64), 4).unwrap();
        c.select("Main / 日本%?#", "DIRECT").await.unwrap();
        let request = task.await.unwrap();
        assert_eq!(request["group"], "Main / 日本%?#");
        assert_eq!(request["operation"], "select");
    }
    #[tokio::test]
    async fn rejects_stale_native_generation() {
        let (addr, task) = one_response(3).await;
        let c = Controller::new(&addr, "x".repeat(64), 4).unwrap();
        assert_eq!(c.snapshot().await.unwrap_err(), "STALE_GENERATION");
        task.await.unwrap();
    }
    #[tokio::test]
    async fn never_persists_unconfirmed_selection() {
        let (addr, task) = one_response(4).await;
        let c = Controller::new(&addr, "x".repeat(64), 4).unwrap();
        assert_eq!(
            c.select("Main / 日本%?#", "REJECT").await.unwrap_err(),
            "SELECTION_NOT_CONFIRMED"
        );
        task.await.unwrap();
    }
    #[test]
    fn rejects_non_loopback_and_weak_secret() {
        assert!(Controller::new("192.0.2.1:1234", "x".repeat(64), 1).is_err());
        assert!(Controller::new("127.0.0.1:1234", "weak".into(), 1).is_err());
    }
    async fn read_body(stream: &mut tokio::net::TcpStream) -> Value {
        let mut bytes = Vec::new();
        loop {
            let mut byte = [0];
            stream.read_exact(&mut byte).await.unwrap();
            bytes.push(byte[0]);
            if bytes.ends_with(b"\r\n\r\n") {
                break;
            }
        }
        let header_end = bytes.len();
        let headers = String::from_utf8(bytes.clone())
            .unwrap()
            .to_ascii_lowercase();
        assert!(headers.contains(&format!("authorization: bearer {}", "x".repeat(64))));
        let size: usize = headers
            .lines()
            .find_map(|line| line.strip_prefix("content-length: "))
            .unwrap()
            .parse()
            .unwrap();
        bytes.resize(header_end + size, 0);
        stream.read_exact(&mut bytes[header_end..]).await.unwrap();
        serde_json::from_slice(&bytes[header_end..]).unwrap()
    }
    #[tokio::test]
    async fn dropped_delay_sends_authenticated_generation_bound_native_cancel() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let c = Controller::new(
            &listener.local_addr().unwrap().to_string(),
            "x".repeat(64),
            4,
        )
        .unwrap();
        let (entered, received) = tokio::sync::oneshot::channel();
        let server = tokio::spawn(async move {
            let (mut probe, _) = listener.accept().await.unwrap();
            let request = read_body(&mut probe).await;
            assert_eq!(request["operation"], "delay");
            entered.send(()).unwrap();
            // Hold the first HTTP response open, just like a stalled node.
            let (mut cancellation, _) = listener.accept().await.unwrap();
            let cancel = read_body(&mut cancellation).await;
            assert_eq!(cancel["operation"], "cancel");
            assert_eq!(cancel["generation"], 4);
            assert_eq!(cancel["targetRequestId"], request["requestId"]);
            let body = serde_json::json!({"apiVersion":1,"requestId":cancel["requestId"],"generation":4,"success":true,"data":{}}).to_string();
            cancellation
                .write_all(
                    format!(
                        "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                        body.len(),
                        body
                    )
                    .as_bytes(),
                )
                .await
                .unwrap();
        });
        let probe = tokio::spawn(async move {
            c.delay("local", "http://127.0.0.1:12345", 10000, Some("204"))
                .await
        });
        received.await.unwrap();
        probe.abort();
        let _ = probe.await;
        tokio::time::timeout(Duration::from_secs(2), server)
            .await
            .expect("native cancel was not sent")
            .unwrap();
    }
}
