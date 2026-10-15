#!/usr/bin/env python3
"""Real Windows TUN acceptance; ONLY an explicitly disposable GitHub VM."""
import argparse
import ctypes
import hashlib
import json
import os
import shutil
import socket
import struct
import subprocess
import tempfile
import threading
import time
from collections import deque
from pathlib import Path


def ps(code, timeout=10):
    return subprocess.check_output(['powershell', '-NoProfile', '-NonInteractive', '-Command', code],
                                   text=True, encoding='utf-8-sig', timeout=timeout).strip()


class FixtureTrace:
    """Bounded metadata only; no packet contents, config or arbitrary fields."""
    fields = frozenset(("peer", "command", "atyp", "target", "port", "txid", "qtype",
                        "bytes", "error", "os_error"))

    def __init__(self, max_events=64):
        self.lock = threading.Lock()
        self.events = deque(maxlen=max(1, min(max_events, 64)))
        self.counts = dict.fromkeys(("tcp", "udp", "dns", "associations", "errors"), 0)
        self.total = 0

    def record(self, stage, increment=None, **fields):
        event = {"stage": stage[:64], "unix_ms": time.time_ns() // 1_000_000}
        event.update({k: v[:96] if isinstance(v, str) else v for k, v in fields.items()
                      if k in self.fields and isinstance(v, (str, int, type(None)))})
        with self.lock:
            if increment in self.counts:
                self.counts[increment] += 1
            self.total += 1
            event['sequence'] = self.total
            self.events.append(event)

    def snapshot(self):
        with self.lock:
            return {"counts": dict(self.counts), "events": [dict(e) for e in self.events],
                    "dropped": self.total - len(self.events)}


def fixture_dns_reply(data):
    # Same synthetic A response as the live fixture, with safe length checks.
    name = b'\x07fixture\x07invalid\x00'
    end = 12 + len(name)
    if len(data) < end + 4 or data[12:end] != name:
        raise ValueError('invalid synthetic DNS question')
    question = data[12:end + 4]
    return (data[:2] + b'\x81\x80\x00\x01\x00\x01\x00\x00\x00\x00' + question +
            b'\xc0\x0c\x00\x01\x00\x01\x00\x00\x00\x1e\x00\x04\xc0\x00\x02\x7b')


def finish_with_cleanup(primary, steps):
    """Every independent observation runs; cleanup cannot erase driver exit 101."""
    errors = [primary] if primary is not None else []
    for label, action in steps:
        try:
            action()
        except BaseException as error:
            error.add_note('Windows fixture cleanup step: ' + label)
            errors.append(error)
    if len(errors) == 1:
        raise errors[0]
    if errors:
        raise BaseExceptionGroup('Windows acceptance and cleanup failures (primary first)', errors)


def check_equal(actual, expected, message):
    assert actual == expected, message


def make_cleanup_steps(installed, driver, environment, fixture, staged, before,
                       proxy_before, firewall_before, journal):
    def reset():
        if installed:
            result = subprocess.run([str(driver), '--ignored', '--exact',
                                     'windows_fixture_emergency_reset', '--test-threads=1'],
                                    env=environment, check=False, timeout=20)
            assert result.returncode == 0, 'explicit owner reset failed (uninstall cleanup does not count as successful acceptance)'

    def adapter_absent():
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline and ps("@(Get-NetAdapter | Where-Object Name -eq 'nimbo-mh0').Count") != '0':
            time.sleep(.2)
        assert ps("@(Get-NetAdapter | Where-Object Name -eq 'nimbo-mh0').Count") == '0', 'native adapter retained'

    return [
        ('explicit owner reset', reset),
        ('fixture counters', lambda: print('Public fixture trace: ' +
             json.dumps(fixture.diagnostics(), separators=(',', ':')), flush=True)),
        ('fixture close', fixture.close),
        ('uninstall', lambda: subprocess.run([str(staged), '--uninstall'], check=True, timeout=45)),
        ('adapter absence', adapter_absent),
        ('user proxy', lambda: check_equal(ps(r"$k=Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings'; [ordered]@{ProxyEnable=$k.ProxyEnable;ProxyServer=$k.ProxyServer;ProxyOverride=$k.ProxyOverride} | ConvertTo-Json -Compress"),
                                          proxy_before, 'user proxy not restored')),
        ('global firewall', lambda: check_equal(ps("Get-NetFirewallProfile | Sort-Object Name | Select-Object Name,Enabled,DefaultOutboundAction | ConvertTo-Json -Compress"),
                                               firewall_before, 'global firewall profile policy changed')),
        ('WFP journal absence', lambda: check_equal(journal.exists(), False, 'retained WFP journal after explicit reset')),
        ('physical DNS/routes', lambda: check_equal(snapshot(), before, 'physical DNS/routes were not restored')),
    ]


def snapshot():
    # Stable physical state only: omit lease lifetimes and managed adapter.
    return json.loads(ps("$a=@(Get-NetAdapter | Where-Object {$_.Name -ne 'nimbo-mh0'} | Select-Object -ExpandProperty ifIndex); [ordered]@{dns=@(Get-DnsClientServerAddress | Where-Object {$_.InterfaceIndex -in $a} | Sort-Object InterfaceIndex,AddressFamily | Select-Object InterfaceIndex,AddressFamily,ServerAddresses); routes=@(Get-NetRoute | Where-Object {$_.InterfaceIndex -in $a} | Sort-Object InterfaceIndex,DestinationPrefix,NextHop | Select-Object InterfaceIndex,DestinationPrefix,NextHop,RouteMetric,Protocol)} | ConvertTo-Json -Compress -Depth 6"))


def exact(sock, n):
    result = b''
    while len(result) < n:
        part = sock.recv(n - len(result))
        if not part:
            raise EOFError()
        result += part
    return result


def socks_address(sock):
    kind = exact(sock, 1)[0]
    if kind == 1:
        address = socket.inet_ntop(socket.AF_INET, exact(sock, 4))
    elif kind == 4:
        address = socket.inet_ntop(socket.AF_INET6, exact(sock, 16))
    elif kind == 3:
        address = exact(sock, exact(sock, 1)[0]).decode('ascii', errors='replace')
    else:
        raise ValueError('invalid address')
    port = struct.unpack('!H', exact(sock, 2))[0]
    return kind, address if address in ('198.18.0.10', '2001:db8::10', 'fixture.invalid', '0.0.0.0') else 'other', port


class Fixture:
    def __init__(self):
        self.closed = threading.Event()
        self.trace = FixtureTrace()
        self.tcp = socket.socket()
        self.tcp.bind(('127.0.0.1', 0))
        self.tcp.listen()
        self.tcp.settimeout(.2)
        self.udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.udp.bind(('127.0.0.1', 0))
        self.udp.settimeout(.2)
        self.dns = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.dns.bind(('127.0.0.1', 0))
        self.dns.settimeout(.2)
        self.workers = [threading.Thread(target=target, name=target.__name__, daemon=True)
                        for target in (self.accept, self.echo, self.resolve)]
        for worker in self.workers:
            worker.start()

    @property
    def tcp_count(self):
        return self.trace.snapshot()['counts']['tcp']

    @property
    def udp_count(self):
        return self.trace.snapshot()['counts']['udp']

    @property
    def dns_count(self):
        return self.trace.snapshot()['counts']['dns']

    def diagnostics(self):
        return dict(self.trace.snapshot(),
                    workers={worker.name: worker.is_alive() for worker in self.workers})

    def worker_error(self, stage, error):
        if not self.closed.is_set():
            self.trace.record(stage, increment='errors', error=type(error).__name__,
                              os_error=getattr(error, 'winerror', None) or getattr(error, 'errno', None))

    def accept(self):
        while not self.closed.is_set():
            try:
                connection, _ = self.tcp.accept()
            except socket.timeout:
                continue
            except OSError as error:
                self.worker_error('accept-error', error)
                continue
            self.trace.record('socks-accepted')
            threading.Thread(target=self.serve, args=(connection,), daemon=True).start()

    def serve(self, connection):
        with connection:
            connection.settimeout(20)
            try:
                assert exact(connection, 1) == b'\x05'
                exact(connection, exact(connection, 1)[0])
                connection.sendall(b'\x05\x00')
                version, command, _ = exact(connection, 3)
                assert version == 5
                atyp, target, port = socks_address(connection)
                self.trace.record('socks-command', command=command, atyp=atyp, target=target, port=port)
                if command == 3:
                    self.trace.record('udp-association', increment='associations')
                    connection.sendall(b'\x05\x00\x00\x01\x7f\x00\x00\x01' + struct.pack('!H', self.udp.getsockname()[1]))
                    while not self.closed.is_set() and connection.recv(1024):
                        pass
                elif command == 1:
                    connection.sendall(b'\x05\x00\x00\x01\x7f\x00\x00\x01\x00\x01')
                    request = b''
                    while b'\r\n\r\n' not in request and len(request) < 8192:
                        request += exact(connection, 1)
                    assert request.startswith(b'GET /fixture ')
                    self.trace.record('http-request', increment='tcp')
                    payload = b'nimbo-windows-tun'
                    connection.sendall(b'HTTP/1.1 200 OK\r\nContent-Length: ' + str(len(payload)).encode() + b'\r\nConnection: close\r\n\r\n' + payload)
            except (OSError, EOFError, ValueError, AssertionError) as error:
                self.worker_error('socks-error', error)

    def echo(self):
        while not self.closed.is_set():
            try:
                data, address = self.udp.recvfrom(65535)
                self.trace.record('udp-request', bytes=len(data), peer=str(address))
                assert data[:3] == b'\x00\x00\x00'
                self.udp.sendto(data, address)
                self.trace.record('udp-response', increment='udp', bytes=len(data), peer=str(address))
            except socket.timeout:
                continue
            except (OSError, AssertionError) as error:
                self.worker_error('udp-error', error)

    def resolve(self):
        while not self.closed.is_set():
            try:
                data, address = self.dns.recvfrom(4096)
                self.trace.record('dns-request', bytes=len(data), peer=str(address),
                                  txid=int.from_bytes(data[:2], 'big'))
                reply = fixture_dns_reply(data)
                self.dns.sendto(reply, address)
                self.trace.record('dns-response', increment='dns', bytes=len(reply), peer=str(address),
                                  txid=int.from_bytes(data[:2], 'big'),
                                  qtype=int.from_bytes(reply[29:31], 'big'))
            except socket.timeout:
                continue
            except (OSError, ValueError, AssertionError) as error:
                self.worker_error('dns-error', error)

    def close(self):
        self.closed.set()
        finish_with_cleanup(None, [(name, sock.close) for name, sock in
                                   (('tcp socket', self.tcp), ('udp socket', self.udp), ('dns socket', self.dns))])


def main():
    if os.name != 'nt' or os.environ.get('NIMBO_DISPOSABLE_WINDOWS') != '1' or os.environ.get('GITHUB_ACTIONS') != 'true':
        raise SystemExit('REFUSED: live TUN tests require an explicitly disposable GitHub Windows VM')
    if not ctypes.windll.shell32.IsUserAnAdmin():
        raise SystemExit('Disposable runner must be elevated')
    parser = argparse.ArgumentParser()
    parser.add_argument('--native', type=Path, required=True)
    parser.add_argument('--helper', type=Path, required=True)
    parser.add_argument('--rust-test', type=Path, required=True)
    args = parser.parse_args()
    native, helper, driver = args.native.resolve(), args.helper.resolve(), args.rust_test.resolve()
    manifest = json.loads((native / 'build-manifest.json').read_text())
    binary = native / 'nimbo-mihomo.exe'
    assert hashlib.sha256(binary.read_bytes()).hexdigest() == manifest['sha256']
    assert manifest['windowsTunOwnership'] == 'exclusive-adapter-rollback-v1'
    assert ps("@(Get-NetAdapter | Where-Object Name -eq 'nimbo-mh0').Count") == '0'
    assert ps("@(Get-Service NimboHelper -ErrorAction SilentlyContinue).Count") == '0', 'never replace an existing service'
    before = snapshot()
    # A remote TCP-only control proves actual physical bypass. No HTTP/token is
    # sent; local self-address traffic would be WFP loopback and is not evidence.
    target = socket.gethostbyname('github.com') + ':443'
    physical_index = ps("Get-NetRoute -DestinationPrefix '0.0.0.0/0' | Sort-Object RouteMetric | Select-Object -First 1 -ExpandProperty InterfaceIndex")
    physical_dns = ps("(Get-DnsClientServerAddress -InterfaceIndex " + physical_index + " -AddressFamily IPv4).ServerAddresses | Select-Object -First 1") + ':53'
    import ipaddress
    assert not ipaddress.ip_address(physical_dns.rsplit(':', 1)[0]).is_loopback, 'DNS control must be physical, not loopback'
    proxy_before = ps(r"$k=Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings'; [ordered]@{ProxyEnable=$k.ProxyEnable;ProxyServer=$k.ProxyServer;ProxyOverride=$k.ProxyOverride} | ConvertTo-Json -Compress")
    firewall_before = ps("Get-NetFirewallProfile | Sort-Object Name | Select-Object Name,Enabled,DefaultOutboundAction | ConvertTo-Json -Compress")
    fixture = Fixture()
    with tempfile.TemporaryDirectory(prefix='nimbo-windows-acceptance-') as temporary:
        directory = Path(temporary)
        staged = directory / 'nimbo-svc.exe'
        shutil.copyfile(helper, staged)
        destination = directory / 'resources/mihomo/windows-x64'
        destination.mkdir(parents=True)
        shutil.copyfile(binary, destination / binary.name)
        source = directory / 'public-fixture.yaml'
        source.write_text(f'''ipv6: true
mode: rule
dns:
  enable: true
  enhanced-mode: normal
  nameserver: [udp://127.0.0.1:{fixture.dns.getsockname()[1]}]
proxies:
  - {{name: FixtureSocks, type: socks5, server: 127.0.0.1, port: {fixture.tcp.getsockname()[1]}, udp: true}}
proxy-groups:
  - {{name: FixtureChoice, type: select, proxies: [FixtureSocks, REJECT]}}
rules: ["MATCH,FixtureChoice"]
''', encoding='utf-8', newline='\n')
        environment = os.environ.copy()
        installed = False
        primary = None
        try:
            subprocess.run([str(staged), '--install'], check=True, timeout=45)
            installed = True
            environment.update(NIMBO_TEST_MIHOMO_BINARY=str(binary), NIMBO_TEST_MIHOMO_SHA256=manifest['sha256'], NIMBO_TEST_TUN_SOURCE=str(source), NIMBO_TEST_PHYSICAL_INDEX=physical_index, NIMBO_TEST_BYPASS_TARGET=target, NIMBO_TEST_PHYSICAL_DNS=physical_dns)
            # Isolate plain TUN before crash-retained protection. Otherwise an
            # earlier Both failure produces a misleading second failure in TUN
            # preflight (RESET_REQUIRED). Every original acceptance case stays
            # mandatory; emergency reset is only the explicit finally cleanup.
            for case in ('actual_windows_broker_session_tcp_udp_dns_selection_and_cleanup',
                         'actual_both_kill_switch_denies_physical_bypass_and_survives_native_death'):
                print('Mandatory Windows gate: ' + case, flush=True)
                print('Public fixture phase before ' + case + ': ' +
                      json.dumps(fixture.trace.snapshot()['counts'], separators=(',', ':')), flush=True)
                subprocess.run([str(driver), '--ignored', '--exact', case, '--test-threads=1', '--nocapture'],
                               env=environment, check=True, timeout=240)
                print('Public fixture phase after ' + case + ': ' +
                      json.dumps(fixture.trace.snapshot()['counts'], separators=(',', ':')), flush=True)
            assert fixture.tcp_count >= 6 and fixture.udp_count >= 2 and fixture.dns_count >= 1, 'native fixture traffic absent'
        except BaseException as error:
            primary = error
            # This entry is guarded at main() by BOTH explicit disposable-VM
            # flags. Public fixture/network metadata only, never config/keys.
            try:
                print('Public fixture failure trace: ' + json.dumps(fixture.diagnostics(), separators=(',', ':')), flush=True)
                print('DISPOSABLE VM network state before explicit cleanup: ' + ps(
                    "[ordered]@{adapters=@(Get-NetAdapter -IncludeHidden | Select-Object -First 16 Name,ifIndex,Status); "
                    "addresses=@(Get-NetIPAddress | Select-Object -First 32 InterfaceAlias,IPAddress,AddressState); "
                    "routes=@(Get-NetRoute | Where-Object {$_.InterfaceAlias -eq 'nimbo-mh0'} | "
                    "Select-Object -First 32 DestinationPrefix,InterfaceIndex,NextHop,RouteMetric)} | ConvertTo-Json -Compress -Depth 5"), flush=True)
            except Exception as diagnostic_error:
                print('VM diagnostic read failed: ' + type(diagnostic_error).__name__, flush=True)
        finally:
            finish_with_cleanup(primary, make_cleanup_steps(
                installed, driver, environment, fixture, staged, before, proxy_before, firewall_before,
                Path(os.environ['ProgramFiles']) / 'NimboNativeTun/kill-switch/owner.json'))
    print('PASS: authenticated SCM broker; native TCP4/6 UDP DNS; Both mixed/proxy snapshot; physical KS denial + native/helper crash, friendly SCM stop, GUI lease abandon and registered persistent/boot policy/reset; global firewall and physical DNS/routes unchanged')


if __name__ == '__main__':
    main()
