//! Local observations only. No reachability probes, WLAN scans or SSID logging.
use super::policy::{Link, Network, Transport};

pub async fn observe(read_ssid: bool) -> Network {
    #[cfg(windows)]
    {
        tauri::async_runtime::spawn_blocking(move || windows_network(read_ssid))
            .await
            .unwrap_or_default()
    }
    #[cfg(target_os = "linux")]
    {
        tokio::time::timeout(std::time::Duration::from_secs(4), linux_network(read_ssid))
            .await
            .unwrap_or_default()
    }
    #[cfg(not(any(windows, target_os = "linux")))]
    {
        let _ = read_ssid;
        Network::Unknown
    }
}

#[cfg(windows)]
fn windows_network(read_ssid: bool) -> Network {
    use windows_sys::Win32::Foundation::{ERROR_BUFFER_OVERFLOW, ERROR_NO_DATA, ERROR_SUCCESS};
    use windows_sys::Win32::NetworkManagement::{IpHelper::*, Ndis::IfOperStatusUp};
    let flags = GAA_FLAG_INCLUDE_GATEWAYS
        | GAA_FLAG_SKIP_ANYCAST
        | GAA_FLAG_SKIP_MULTICAST
        | GAA_FLAG_SKIP_DNS_SERVER;
    let mut size = 15_000u32;
    for _ in 0..3 {
        if size > 1024 * 1024 {
            return Network::Unknown;
        }
        let mut buffer = vec![0u64; (size as usize).div_ceil(8)];
        let ptr = buffer.as_mut_ptr().cast::<IP_ADAPTER_ADDRESSES_LH>();
        let result = unsafe { GetAdaptersAddresses(0, flags, std::ptr::null(), ptr, &mut size) };
        if result == ERROR_BUFFER_OVERFLOW {
            continue;
        }
        if result == ERROR_NO_DATA {
            return Network::Offline;
        }
        if result != ERROR_SUCCESS {
            return Network::Unknown;
        }
        let mut current = ptr;
        let mut links = Vec::new();
        while !current.is_null() {
            let adapter = unsafe { &*current };
            current = adapter.Next;
            if adapter.OperStatus != IfOperStatusUp || adapter.FirstGatewayAddress.is_null() {
                continue;
            }
            let transport = match adapter.IfType {
                IF_TYPE_IEEE80211 => Transport::Wifi,
                IF_TYPE_ETHERNET_CSMACD => {
                    // Virtual TUN/TAP adapters must not look like a new wired uplink.
                    let mut row: MIB_IF_ROW2 = unsafe { std::mem::zeroed() };
                    row.InterfaceLuid = adapter.Luid;
                    if unsafe { GetIfEntry2(&mut row) } != ERROR_SUCCESS {
                        return Network::Unknown;
                    }
                    if row.InterfaceAndOperStatusFlags._bitfield & 1 == 0 {
                        continue;
                    }
                    Transport::Ethernet
                }
                IF_TYPE_WWANPP | IF_TYPE_WWANPP2 => Transport::Cellular,
                IF_TYPE_TUNNEL | IF_TYPE_SOFTWARE_LOOPBACK => continue,
                _ => return Network::Unknown,
            };
            let ssid = if read_ssid && transport == Transport::Wifi {
                let mut guid = unsafe { std::mem::zeroed() };
                if unsafe { ConvertInterfaceLuidToGuid(&adapter.Luid, &mut guid) } != ERROR_SUCCESS
                {
                    None
                } else {
                    windows_ssid(&guid)
                }
            } else {
                None
            };
            links.push(Link { transport, ssid });
        }
        return if links.is_empty() {
            Network::Offline
        } else {
            Network::Links(links)
        };
    }
    Network::Unknown
}

#[cfg(windows)]
fn windows_ssid(guid: &windows_sys::core::GUID) -> Option<String> {
    use windows_sys::Win32::NetworkManagement::WiFi::*;
    // Modern Windows may deny this without location consent. Never bypass it or
    // treat an unknown name as trusted. Query only when exceptions are configured.
    let mut handle = std::ptr::null_mut();
    let mut version = 0;
    if unsafe { WlanOpenHandle(2, std::ptr::null(), &mut version, &mut handle) } != 0 {
        return None;
    }
    let mut data = std::ptr::null_mut();
    let mut size = 0;
    let result = unsafe {
        WlanQueryInterface(
            handle,
            guid,
            wlan_intf_opcode_current_connection,
            std::ptr::null(),
            &mut size,
            &mut data,
            std::ptr::null_mut(),
        )
    };
    let name = if result == 0
        && !data.is_null()
        && size as usize >= std::mem::size_of::<WLAN_CONNECTION_ATTRIBUTES>()
    {
        let attributes = unsafe { &*data.cast::<WLAN_CONNECTION_ATTRIBUTES>() };
        let ssid = &attributes.wlanAssociationAttributes.dot11Ssid;
        if attributes.isState == wlan_interface_state_connected && ssid.uSSIDLength <= 32 {
            std::str::from_utf8(&ssid.ucSSID[..ssid.uSSIDLength as usize])
                .ok()
                .map(str::to_string)
        } else {
            None
        }
    } else {
        None
    };
    unsafe {
        if !data.is_null() {
            WlanFreeMemory(data);
        }
        WlanCloseHandle(handle, std::ptr::null());
    }
    name
}

// nmcli's terse mode escapes both separators and backslashes. Never split SSIDs
// on a raw colon or interpret them as shell arguments.
#[cfg(any(target_os = "linux", test))]
fn fields(line: &str) -> Vec<String> {
    let mut fields = vec![String::new()];
    let mut escaped = false;
    for ch in line.chars() {
        if escaped {
            fields.last_mut().unwrap().push(ch);
            escaped = false;
        } else if ch == '\\' {
            escaped = true;
        } else if ch == ':' {
            fields.push(String::new());
        } else {
            fields.last_mut().unwrap().push(ch);
        }
    }
    if escaped {
        fields.last_mut().unwrap().push('\\');
    }
    fields
}

#[cfg(target_os = "linux")]
async fn nmcli(args: &[&str]) -> Option<String> {
    use tokio::io::AsyncReadExt;
    let mut command = tokio::process::Command::new("nmcli");
    command
        .args(args)
        .env("LC_ALL", "C")
        .kill_on_drop(true)
        .stdin(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .stdout(std::process::Stdio::piped());
    let mut child = command.spawn().ok()?;
    let stdout = child.stdout.take()?;
    tokio::time::timeout(std::time::Duration::from_secs(2), async move {
        let mut bytes = Vec::new();
        stdout.take(65_537).read_to_end(&mut bytes).await.ok()?;
        if bytes.len() > 65_536 {
            return None;
        }
        if !child.wait().await.ok()?.success() {
            return None;
        }
        String::from_utf8(bytes).ok()
    })
    .await
    .ok()
    .flatten()
}

#[cfg(target_os = "linux")]
async fn linux_network(read_ssid: bool) -> Network {
    let Some(text) = nmcli(&[
        "-t",
        "--escape",
        "yes",
        "-f",
        "DEVICE,TYPE,STATE",
        "device",
        "status",
    ])
    .await
    else {
        return Network::Unknown;
    };
    if text.lines().count() > 64 {
        return Network::Unknown;
    }
    let mut links = Vec::new();
    for line in text.lines() {
        let values = fields(line);
        if values.len() != 3 {
            return Network::Unknown;
        }
        if values[2] != "connected" && values[2] != "connected (externally)" {
            continue;
        }
        let transport = match values[1].as_str() {
            "wifi" => Transport::Wifi,
            "ethernet" => Transport::Ethernet,
            "gsm" | "cdma" => Transport::Cellular,
            "tun" | "wireguard" | "loopback" => continue,
            _ => return Network::Unknown,
        };
        let ssid = if transport == Transport::Wifi && read_ssid {
            nmcli(&[
                "-t",
                "--escape",
                "yes",
                "-f",
                "IN-USE,SSID",
                "device",
                "wifi",
                "list",
                "--rescan",
                "no",
                "ifname",
                &values[0],
            ])
            .await
            .and_then(|list| {
                list.lines()
                    .map(fields)
                    .find(|row| row.len() == 2 && row[0] == "*")
                    .map(|row| row[1].clone())
            })
        } else {
            None
        };
        links.push(Link { transport, ssid });
    }
    if links.is_empty() {
        Network::Offline
    } else {
        Network::Links(links)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn terse_names_preserve_literal_colons_backslashes_and_spaces() {
        assert_eq!(fields(r"*:Cafe\: Guest\\5G"), ["*", r"Cafe: Guest\5G"]);
        assert_eq!(
            fields("wlan0:wifi:connected"),
            ["wlan0", "wifi", "connected"]
        );
        assert_eq!(fields(":"), ["", ""]);
    }
}
