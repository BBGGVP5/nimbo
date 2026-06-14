use std::fs::OpenOptions;
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use tracing_subscriber::EnvFilter;
use tracing_subscriber::fmt::MakeWriter;

const MAX_LOG_BYTES: u64 = 5 * 1024 * 1024;

#[derive(Clone)]
struct AppLogFactory {
    path: PathBuf,
    write_lock: Arc<Mutex<()>>,
}

struct AppLogWriter {
    path: PathBuf,
    write_lock: Arc<Mutex<()>>,
    buffer: Vec<u8>,
}

impl Write for AppLogWriter {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        self.buffer.extend_from_slice(bytes);
        Ok(bytes.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

impl Drop for AppLogWriter {
    fn drop(&mut self) {
        if self.buffer.is_empty() {
            return;
        }

        let Ok(_guard) = self.write_lock.lock() else {
            return;
        };
        let _ = rotate_if_needed(&self.path);
        if let Ok(mut file) = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.path)
        {
            let _ = file.write_all(&self.buffer);
            let _ = file.flush();
        }
    }
}

impl<'a> MakeWriter<'a> for AppLogFactory {
    type Writer = AppLogWriter;

    fn make_writer(&'a self) -> Self::Writer {
        AppLogWriter {
            path: self.path.clone(),
            write_lock: Arc::clone(&self.write_lock),
            buffer: Vec::with_capacity(512),
        }
    }
}

pub fn app_log_path() -> Option<PathBuf> {
    dirs::data_dir().map(|base| base.join("Nimbo").join("logs").join("nimbo.log"))
}

pub fn init() {
    let Some(path) = app_log_path() else {
        eprintln!("failed to resolve Nimbo log directory");
        return;
    };

    if let Some(parent) = path.parent() {
        if let Err(error) = std::fs::create_dir_all(parent) {
            eprintln!("failed to create Nimbo log directory: {error}");
            return;
        }
    }
    let _ = rotate_if_needed(&path);

    let filter = EnvFilter::try_from_default_env().unwrap_or_else(|_| {
        EnvFilter::new("warn,nimbo_ui=info,nimbo_subscription=info,nimbo_device=info")
    });
    let factory = AppLogFactory {
        path,
        write_lock: Arc::new(Mutex::new(())),
    };
    let _ = tracing_subscriber::fmt()
        .with_env_filter(filter)
        .with_ansi(false)
        .with_target(true)
        .compact()
        .with_writer(factory)
        .try_init();

    let previous_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        tracing::error!(panic = %info, "unhandled panic");
        previous_hook(info);
    }));
}

fn rotate_if_needed(path: &Path) -> io::Result<()> {
    let Ok(metadata) = std::fs::metadata(path) else {
        return Ok(());
    };
    if metadata.len() < MAX_LOG_BYTES {
        return Ok(());
    }

    let rotated = path.with_extension("log.1");
    if rotated.exists() {
        std::fs::remove_file(&rotated)?;
    }
    std::fs::rename(path, rotated)
}
