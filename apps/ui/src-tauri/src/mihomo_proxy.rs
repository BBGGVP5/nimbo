//! Per-user proxy ownership only. Unlike the legacy helper, never imports or resets
//! machine-wide WinHTTP configuration. No routes, DNS or firewall operations.
use crate::state::SystemProxySnapshot;
#[cfg(windows)]
fn key() -> Result<winreg::RegKey, String> {
    use winreg::{
        enums::{HKEY_CURRENT_USER, KEY_READ, KEY_WRITE},
        RegKey,
    };
    RegKey::predef(HKEY_CURRENT_USER)
        .open_subkey_with_flags(
            r"Software\Microsoft\Windows\CurrentVersion\Internet Settings",
            KEY_READ | KEY_WRITE,
        )
        .map_err(|_| "SYSTEM_PROXY_ACCESS_FAILED".into())
}
#[cfg(windows)]
pub fn snapshot() -> Result<SystemProxySnapshot, String> {
    let k = key()?;
    Ok(SystemProxySnapshot {
        proxy_enable: k.get_value("ProxyEnable").ok(),
        proxy_server: k.get_value("ProxyServer").ok(),
        proxy_override: k.get_value("ProxyOverride").ok(),
    })
}
#[cfg(windows)]
pub fn apply(port: u16) -> Result<(), String> {
    if port == 0 {
        return Err("INVALID_LOOPBACK_ENDPOINT".into());
    }
    let k = key()?;
    // Address first: the ownership journal can identify an interrupted apply.
    k.set_value(
        "ProxyServer",
        &format!("http=127.0.0.1:{port};https=127.0.0.1:{port};socks=127.0.0.1:{port}"),
    )
    .map_err(|_| "SYSTEM_PROXY_WRITE_FAILED")?;
    k.set_value("ProxyOverride", &"<local>")
        .map_err(|_| "SYSTEM_PROXY_WRITE_FAILED")?;
    k.set_value("ProxyEnable", &1u32)
        .map_err(|_| "SYSTEM_PROXY_WRITE_FAILED")?;
    notify();
    Ok(())
}
#[cfg(windows)]
pub fn restore(snapshot: Option<SystemProxySnapshot>) -> Result<(), String> {
    let Some(s) = snapshot else { return Ok(()) };
    let k = key()?;
    fn remove(k: &winreg::RegKey, name: &str) -> Result<(), String> {
        match k.delete_value(name) {
            Ok(()) => Ok(()),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(()),
            Err(_) => Err("SYSTEM_PROXY_RESTORE_FAILED".into()),
        }
    }
    // Restore all values even if one operation fails. Caller keeps recovery journal.
    let a = if let Some(v) = s.proxy_server {
        k.set_value("ProxyServer", &v)
            .map_err(|_| "SYSTEM_PROXY_RESTORE_FAILED".to_string())
    } else {
        remove(&k, "ProxyServer")
    };
    let b = if let Some(v) = s.proxy_override {
        k.set_value("ProxyOverride", &v)
            .map_err(|_| "SYSTEM_PROXY_RESTORE_FAILED".to_string())
    } else {
        remove(&k, "ProxyOverride")
    };
    let c = if let Some(v) = s.proxy_enable {
        k.set_value("ProxyEnable", &v)
            .map_err(|_| "SYSTEM_PROXY_RESTORE_FAILED".to_string())
    } else {
        remove(&k, "ProxyEnable")
    };
    notify();
    a?;
    b?;
    c
}
#[cfg(windows)]
fn notify() {
    #[link(name = "wininet")]
    extern "system" {
        fn InternetSetOptionW(
            handle: *mut std::ffi::c_void,
            option: u32,
            buffer: *mut std::ffi::c_void,
            length: u32,
        ) -> i32;
    }
    unsafe {
        InternetSetOptionW(std::ptr::null_mut(), 39, std::ptr::null_mut(), 0);
        InternetSetOptionW(std::ptr::null_mut(), 37, std::ptr::null_mut(), 0);
    }
}
#[cfg(not(windows))]
pub fn snapshot() -> Result<SystemProxySnapshot, String> {
    Err("SYSTEM_PROXY_PLATFORM_UNAVAILABLE".into())
}
#[cfg(not(windows))]
pub fn apply(_: u16) -> Result<(), String> {
    Err("SYSTEM_PROXY_PLATFORM_UNAVAILABLE".into())
}
#[cfg(not(windows))]
pub fn restore(_: Option<SystemProxySnapshot>) -> Result<(), String> {
    Err("SYSTEM_PROXY_PLATFORM_UNAVAILABLE".into())
}

/// Address identity also covers a crash after address write but before enable.
#[cfg(windows)]
pub fn owns(port: u16) -> Result<bool, String> {
    Ok(snapshot()?.proxy_server.as_deref()
        == Some(
            format!("http=127.0.0.1:{port};https=127.0.0.1:{port};socks=127.0.0.1:{port}").as_str(),
        ))
}
#[cfg(not(windows))]
pub fn owns(_: u16) -> Result<bool, String> {
    Err("SYSTEM_PROXY_PLATFORM_UNAVAILABLE".into())
}
