use std::path::PathBuf;
use std::sync::Mutex;

use serde::{Deserialize, Serialize};

use nimbo_subscription::Subscription;

const STORAGE_FILE: &str = "subscriptions.json";

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct PersistedState {
    pub subscriptions: Vec<Subscription>,
    pub active_server_id: Option<String>,
}

pub struct AppState {
    inner: Mutex<PersistedState>,
    storage_path: PathBuf,
}

impl AppState {
    pub fn load() -> anyhow::Result<Self> {
        let storage_path = storage_path()?;
        let inner = if storage_path.exists() {
            let bytes = std::fs::read(&storage_path)?;
            match serde_json::from_slice::<PersistedState>(&bytes) {
                Ok(s) => s,
                Err(e) => {
                    tracing::warn!(?e, "subscriptions.json corrupted, starting fresh");
                    PersistedState::default()
                }
            }
        } else {
            PersistedState::default()
        };
        Ok(Self {
            inner: Mutex::new(inner),
            storage_path,
        })
    }

    pub fn snapshot(&self) -> PersistedState {
        self.inner.lock().expect("state poisoned").clone()
    }

    pub fn mutate<F, R>(&self, f: F) -> anyhow::Result<R>
    where
        F: FnOnce(&mut PersistedState) -> R,
    {
        let result = {
            let mut guard = self.inner.lock().expect("state poisoned");
            f(&mut guard)
        };
        self.persist()?;
        Ok(result)
    }

    fn persist(&self) -> anyhow::Result<()> {
        if let Some(parent) = self.storage_path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let snapshot = self.inner.lock().expect("state poisoned").clone();
        let json = serde_json::to_vec_pretty(&snapshot)?;
        std::fs::write(&self.storage_path, json)?;
        Ok(())
    }
}

fn storage_path() -> anyhow::Result<PathBuf> {
    let base = dirs::data_dir().ok_or_else(|| anyhow::anyhow!("APPDATA not available"))?;
    Ok(base.join("Nimbo").join(STORAGE_FILE))
}
