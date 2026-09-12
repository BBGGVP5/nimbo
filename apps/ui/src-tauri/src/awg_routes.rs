//! Own only routes created for the AWG process, preserving pre-existing host routes.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, PartialEq, Eq)]
pub struct BypassRoute {
    pub ip: std::net::Ipv4Addr,
    pub interface_index: u32,
    pub gateway: std::net::Ipv4Addr,
}

pub struct AwgBypass {
    #[cfg(windows)]
    routes: Vec<BypassRoute>,
}

impl AwgBypass {
    pub fn snapshot(&self) -> Vec<BypassRoute> {
        #[cfg(windows)]
        {
            self.routes.clone()
        }
        #[cfg(not(windows))]
        {
            Vec::new()
        }
    }

    pub fn install(ips: &[String]) -> Result<Self, String> {
        if ips.is_empty()
            || ips
                .iter()
                .any(|ip| ip.parse::<std::net::Ipv4Addr>().is_err())
        {
            return Err("AWG: не удалось определить IPv4 маршрут сервера".into());
        }
        #[cfg(windows)]
        {
            let mut owner = Self { routes: Vec::new() };
            for ip in ips {
                // The only interpolation is a validated IPv4 literal. Use Find-NetRoute
                // before TUN changes the routing table, supporting on-link gateways too.
                let script = format!(
                    r#"$ErrorActionPreference='Stop'
$r = Find-NetRoute -RemoteIPAddress '{ip}' | Where-Object {{ $_.PSObject.Properties.Name -contains 'NextHop' }} | Select-Object -First 1
if (!$r -or !$r.InterfaceIndex -or $r.InterfaceAlias -match 'wintun|nimbo') {{ exit 2 }}
$existing = Get-NetRoute -DestinationPrefix '{ip}/32' -ErrorAction SilentlyContinue
if ($existing) {{
  if (!($existing | Where-Object {{ $_.InterfaceIndex -eq $r.InterfaceIndex -and $_.NextHop -eq $r.NextHop }})) {{ exit 3 }}
  Write-Output 'existing'
}} else {{
  New-NetRoute -DestinationPrefix '{ip}/32' -InterfaceIndex $r.InterfaceIndex -NextHop $r.NextHop -RouteMetric 1 -PolicyStore ActiveStore | Out-Null
  Write-Output ("{{0}}|{{1}}" -f $r.InterfaceIndex,$r.NextHop)
}}"#
                );
                let output = powershell(&script)
                    .output()
                    .map_err(|_| "AWG: не удалось установить маршрут сервера")?;
                if !output.status.success() {
                    return Err("AWG: не удалось установить обход TUN до сервера".into());
                }
                let reply = String::from_utf8_lossy(&output.stdout);
                if reply.trim() == "existing" {
                    continue;
                }
                let (index, gateway) = reply
                    .trim()
                    .split_once('|')
                    .ok_or("AWG: некорректный ответ настройки маршрута")?;
                let index = index
                    .parse::<u32>()
                    .map_err(|_| "AWG: некорректный интерфейс маршрута")?;
                let gateway = gateway
                    .parse::<std::net::Ipv4Addr>()
                    .map_err(|_| "AWG: некорректный шлюз маршрута")?;
                owner.routes.push(BypassRoute {
                    ip: ip.parse().unwrap(),
                    interface_index: index,
                    gateway,
                });
            }
            Ok(owner)
        }
        #[cfg(target_os = "linux")]
        {
            // The unprivileged GUI cannot install routes. TunSession::up sends the
            // pinned IPs to the helper, which applies and verifies them before Xray.
            Ok(Self {})
        }
        #[cfg(not(any(windows, target_os = "linux")))]
        {
            Err("AWG TUN не поддерживается на этой платформе".into())
        }
    }
}

#[cfg(windows)]
fn powershell(script: &str) -> std::process::Command {
    use std::os::windows::process::CommandExt;
    let mut command = std::process::Command::new("powershell.exe");
    command.args(["-NoProfile", "-NonInteractive", "-Command", script]);
    command
        .creation_flags(0x08000000)
        .stdin(std::process::Stdio::null())
        .stderr(std::process::Stdio::null());
    command
}

pub fn cleanup(routes: &[BypassRoute]) {
    #[cfg(windows)]
    for route in routes {
        let (ip, index, gateway) = (route.ip, route.interface_index, route.gateway);
        let _ = powershell(&format!("Remove-NetRoute -DestinationPrefix '{ip}/32' -InterfaceIndex {index} -NextHop '{gateway}' -Confirm:$false -ErrorAction SilentlyContinue"))
            .stdout(std::process::Stdio::null()).status();
    }
    #[cfg(not(windows))]
    let _ = routes;
}

impl Drop for AwgBypass {
    fn drop(&mut self) {
        cleanup(&self.snapshot());
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_non_ip_route_arguments_before_running_commands() {
        for ips in [
            vec![],
            vec!["vpn.example".into()],
            vec!["1.2.3.4';exit".into()],
            vec!["::1".into()],
        ] {
            assert!(AwgBypass::install(&ips).is_err());
        }
    }
}
