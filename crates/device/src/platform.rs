#[cfg(windows)]
mod imp {
    use winreg::enums::{HKEY_LOCAL_MACHINE, KEY_READ};
    use winreg::RegKey;

    pub fn os_name() -> &'static str {
        "Windows"
    }

    pub fn os_version() -> String {
        if let Some(version) = cmd_windows_version() {
            return version;
        }

        let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);
        let key = match hklm
            .open_subkey_with_flags(r"SOFTWARE\Microsoft\Windows NT\CurrentVersion", KEY_READ)
        {
            Ok(k) => k,
            Err(_) => return "unknown".into(),
        };

        let product: String = key
            .get_value("ProductName")
            .unwrap_or_else(|_| "Windows".into());
        let display: String = key.get_value("DisplayVersion").unwrap_or_default();
        let build: String = key.get_value("CurrentBuildNumber").unwrap_or_default();
        let ubr: u32 = key.get_value("UBR").unwrap_or(0);

        let mut parts: Vec<String> = vec![product];
        if !display.is_empty() {
            parts.push(display);
        }
        let build_full = if !build.is_empty() && ubr != 0 {
            format!("{}.{}", build, ubr)
        } else {
            build
        };
        if !build_full.is_empty() {
            parts.push(format!("build {}", build_full));
        }
        parts.join(" ")
    }

    fn cmd_windows_version() -> Option<String> {
        let mut command = std::process::Command::new("cmd");
        command
            .args(["/C", "ver"])
            .stdin(std::process::Stdio::null())
            .stderr(std::process::Stdio::null());

        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            command.creation_flags(0x08000000);
        }

        let output = command.output().ok()?;
        if !output.status.success() {
            return None;
        }

        let text = String::from_utf8_lossy(&output.stdout);
        let version = text.trim();
        if version.is_empty() {
            None
        } else {
            Some(version.to_string())
        }
    }

    pub fn hostname() -> String {
        std::env::var("COMPUTERNAME").unwrap_or_else(|_| "unknown-host".into())
    }

    pub fn machine_guid() -> Option<String> {
        let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);
        let key = hklm
            .open_subkey_with_flags(r"SOFTWARE\Microsoft\Cryptography", KEY_READ)
            .ok()?;
        let value: String = key.get_value("MachineGuid").ok()?;
        if value.trim().is_empty() {
            None
        } else {
            Some(value)
        }
    }
}

#[cfg(not(windows))]
mod imp {
    pub fn os_name() -> &'static str {
        if cfg!(target_os = "macos") {
            "macOS"
        } else if cfg!(target_os = "linux") {
            "Linux"
        } else {
            "unknown"
        }
    }

    pub fn os_version() -> String {
        std::env::consts::OS.into()
    }

    pub fn hostname() -> String {
        std::env::var("HOSTNAME")
            .or_else(|_| std::env::var("HOST"))
            .unwrap_or_else(|_| "unknown-host".into())
    }

    pub fn machine_guid() -> Option<String> {
        None
    }
}

pub use imp::{hostname, machine_guid, os_name, os_version};
