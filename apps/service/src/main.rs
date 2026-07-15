// Nimbo helper service.
//
// Runs as LocalSystem. Listens on a named pipe and currently handles process
// kill requests so Nimbo can terminate SYSTEM-owned services (Cloudflare WARP,
// Clash Verge service, FlClash helper) without showing a UAC prompt every
// time. The protocol is defined in `nimbo-ipc`.

// Windows subsystem so neither the SCM-launched service nor the
// installer-spawned `--install` flash a console window. For dev
// (`--run-foreground`) we re-attach to the parent terminal if there is one.
#![cfg_attr(windows, windows_subsystem = "windows")]

#[cfg(windows)]
mod platform;

#[cfg(windows)]
fn main() -> anyhow::Result<()> {
    platform::run()
}

#[cfg(not(windows))]
fn main() -> anyhow::Result<()> {
    eprintln!("nimbo-svc is Windows-only.");
    std::process::exit(1);
}
