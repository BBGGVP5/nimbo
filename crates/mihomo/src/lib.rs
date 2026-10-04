//! Native Mihomo is a full-profile engine, never a synthetic Server protocol.
pub mod controller;
pub mod download;
#[cfg(target_os = "linux")]
pub mod helper;
#[cfg(windows)]
#[path = "helper_windows.rs"]
pub mod helper;
pub mod process;
pub mod profiles;
pub mod selection;
pub mod subscription;
pub mod wire;
pub use profiles::*;
