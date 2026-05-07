use nimbo_ipc::{PIPE_NAME, PROTOCOL_VERSION};
use tracing::info;

const VERSION: &str = env!("CARGO_PKG_VERSION");

fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("info")),
        )
        .init();

    info!(version = VERSION, protocol = PROTOCOL_VERSION, pipe = PIPE_NAME, "nimbo-svc starting");
    info!("skeleton — windows-service integration pending (Этап 4)");

    Ok(())
}
