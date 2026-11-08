# Nimbo TUN diagnostics for Windows.
# Run while Nimbo is CONNECTED in TUN mode (the "no internet" state):
#   powershell -ExecutionPolicy Bypass -File nimbo-tun-diag.ps1
# Writes nimbo-tun-diag.txt to the Desktop. No keys, UUIDs or server addresses
# from the subscription are collected; logs are tail-only.
# Kept ASCII-only so Windows PowerShell 5.1 parses it without a BOM.

$ErrorActionPreference = 'SilentlyContinue'
$out = Join-Path ([Environment]::GetFolderPath('Desktop')) 'nimbo-tun-diag.txt'
$data = Join-Path $env:APPDATA 'Nimbo'
$lines = New-Object System.Collections.Generic.List[string]

function Section($title) { $lines.Add(''); $lines.Add("===== $title =====") }
function Add($value) {
    $first = @($value)[0]
    if ($first -is [string] -or $first -is [Microsoft.PowerShell.Commands.MatchInfo] -or $null -eq $first) {
        $lines.Add(($value | Out-String -Width 250).TrimEnd())
    } else {
        $lines.Add(($value | Format-Table -AutoSize -Wrap | Out-String -Width 250).TrimEnd())
    }
}

function Probe($name, [scriptblock]$body) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    try { $r = & $body; $lines.Add(("{0,-34} OK   {1,5} ms  {2}" -f $name, $sw.ElapsedMilliseconds, $r)) }
    catch { $lines.Add(("{0,-34} FAIL {1,5} ms  {2}" -f $name, $sw.ElapsedMilliseconds, $_.Exception.Message)) }
}

function Http($url) {
    $req = [Net.HttpWebRequest]::Create($url)
    $req.Timeout = 6000
    $req.AllowAutoRedirect = $false
    $resp = $req.GetResponse()
    $code = [int]$resp.StatusCode
    $resp.Close()
    "HTTP $code"
}

function Tcp($ip, $port) {
    $c = New-Object Net.Sockets.TcpClient
    $task = $c.ConnectAsync($ip, $port)
    if (-not $task.Wait(5000)) { $c.Close(); throw 'timeout' }
    $c.Close()
    'connected'
}

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
$os = Get-CimInstance Win32_OperatingSystem
$lines.Add("Nimbo TUN diagnostics  $(Get-Date -Format s)")
$lines.Add("Windows: $($os.Caption) $($os.Version) build $($os.BuildNumber)  admin-shell=$admin")
$started = Select-String -Path (Join-Path $data 'logs\nimbo.log') -Pattern 'nimbo-ui starting version="([^"]+)"' | Select-Object -Last 1
if ($started) { $lines.Add("Nimbo: $($started.Matches[0].Groups[1].Value)") }
$xray = Join-Path $data 'bin\xray.exe'
if (Test-Path $xray) { $lines.Add("Xray: $(& $xray version | Select-Object -First 1)") }

Section 'Connectivity probes (through whatever routes are active now)'
Probe 'TCP 1.1.1.1:443 (no DNS)'      { Tcp '1.1.1.1' 443 }
Probe 'TCP 8.8.8.8:443 (no DNS)'      { Tcp '8.8.8.8' 443 }
Probe 'HTTP http://1.1.1.1 (no DNS)'  { Http 'http://1.1.1.1/' }
Probe 'DNS google.com (system)'       { (Resolve-DnsName google.com -Type A -DnsOnly -QuickTimeout -ErrorAction Stop | Where-Object IPAddress | Select-Object -First 1).IPAddress }
Probe 'DNS google.com via 1.1.1.1'    { (Resolve-DnsName google.com -Type A -Server 1.1.1.1 -DnsOnly -QuickTimeout -ErrorAction Stop | Where-Object IPAddress | Select-Object -First 1).IPAddress }
Probe 'HTTPS gstatic generate_204'    { Http 'https://www.gstatic.com/generate_204' }
Probe 'HTTPS cloudflare generate_204' { Http 'https://cp.cloudflare.com/generate_204' }

Section 'Nimbo processes'
Add (Get-Process | Where-Object { $_.Name -match 'nimbo|xray|tun2socks|mihomo|naive' } | Select-Object Name, Id, Path)
Add (Get-Service | Where-Object { $_.Name -match 'nimbo' } | Select-Object Name, Status, StartType)

Section 'Adapters'
Add (Get-NetAdapter -IncludeHidden | Select-Object Name, InterfaceDescription, Status, ifIndex, MtuSize | Sort-Object ifIndex)

Section 'IP interfaces (metric, forwarding)'
Add (Get-NetIPInterface | Where-Object ConnectionState -eq 'Connected' | Select-Object InterfaceAlias, AddressFamily, InterfaceMetric, AutomaticMetric, NlMtu, Forwarding, WeakHostSend, WeakHostReceive | Sort-Object InterfaceMetric)

Section 'Addresses'
Add (Get-NetIPAddress | Where-Object { $_.AddressState -eq 'Preferred' } | Select-Object InterfaceAlias, AddressFamily, IPAddress, PrefixLength)

Section 'Default and split routes'
Add (Get-NetRoute | Where-Object { $_.DestinationPrefix -in @('0.0.0.0/0','0.0.0.0/1','128.0.0.0/1','::/0','::/1','8000::/1') } | Select-Object DestinationPrefix, InterfaceAlias, ifIndex, NextHop, RouteMetric, @{n='IfMetric';e={$_.InterfaceMetric}} | Sort-Object DestinationPrefix, RouteMetric)

Section 'DNS servers per interface'
Add (Get-DnsClientServerAddress | Where-Object ServerAddresses | Select-Object InterfaceAlias, AddressFamily, @{n='Servers';e={$_.ServerAddresses -join ', '}})
Add (Get-DnsClientNrptPolicy)

Section 'Network profile and firewall'
Add (Get-NetConnectionProfile | Select-Object Name, InterfaceAlias, NetworkCategory, IPv4Connectivity, IPv6Connectivity)
Add (Get-NetFirewallProfile | Select-Object Name, Enabled, DefaultInboundAction, DefaultOutboundAction)
Add (Get-NetFirewallRule -DisplayGroup '*Nimbo*' | Select-Object DisplayName, Direction, Action, Enabled)
$block = Get-NetFirewallRule -Direction Outbound -Action Block -Enabled True | Select-Object -First 20 DisplayName, Profile
if ($block) { $lines.Add('Enabled outbound BLOCK rules:'); Add $block }

Section 'Antivirus / third-party firewall'
Add (Get-CimInstance -Namespace root/SecurityCenter2 -ClassName AntiVirusProduct | Select-Object displayName, productState)
Add (Get-CimInstance -Namespace root/SecurityCenter2 -ClassName FirewallProduct | Select-Object displayName, productState)
Add (Get-MpComputerStatus | Select-Object AMRunningMode, RealTimeProtectionEnabled)
$sac = Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\CI\Policy' -Name VerifiedAndReputablePolicyState
if ($sac) { $lines.Add("Smart App Control state: $($sac.VerifiedAndReputablePolicyState) (0=off 1=on 2=eval)") }

Section 'Wintun driver and devices'
Add (pnputil /enum-drivers | Select-String -Context 0,6 -Pattern 'wintun.inf')
Add (Get-PnpDevice | Where-Object { $_.FriendlyName -match 'Wintun' -or $_.InstanceId -match 'WINTUN' } | Select-Object Status, FriendlyName, InstanceId, Problem)

Section 'Proxy settings'
Add (Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' | Select-Object ProxyEnable, ProxyServer, AutoConfigURL)
Add (netsh winhttp show proxy)

Section 'Nimbo files'
Add (Get-ChildItem (Join-Path $data 'bin') | Select-Object Name, Length, LastWriteTime)
$cfg = Join-Path $data 'runtime\xray-config.json'
if (Test-Path $cfg) {
    try {
        $tun = (Get-Content $cfg -Raw | ConvertFrom-Json).inbounds | Where-Object { $_.tag -eq 'tun-in' }
        $lines.Add('xray tun-in inbound:')
        Add ($tun | ConvertTo-Json -Depth 6)
    } catch { $lines.Add("xray config unreadable: $($_.Exception.Message)") }
}

foreach ($log in @('runtime\xray.log', 'runtime\tun2socks.log', 'logs\nimbo.log')) {
    $p = Join-Path $data $log
    if (Test-Path $p) { Section "tail $log"; Add (Get-Content $p -Tail 120) }
}
$helper = Join-Path $env:ProgramData 'Nimbo\helper.log'
if (Test-Path $helper) { Section 'tail helper.log'; Add (Get-Content $helper -Tail 60) }

$lines | Set-Content -Path $out -Encoding UTF8
Write-Host "Saved: $out"
