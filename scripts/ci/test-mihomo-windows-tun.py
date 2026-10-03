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
from pathlib import Path


def ps(code):
    return subprocess.check_output(['powershell', '-NoProfile', '-NonInteractive', '-Command', code], text=True, encoding='utf-8-sig').strip()


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
        exact(sock, 4)
    elif kind == 4:
        exact(sock, 16)
    elif kind == 3:
        exact(sock, exact(sock, 1)[0])
    else:
        raise ValueError('invalid address')
    exact(sock, 2)


class Fixture:
    def __init__(self):
        self.closed = threading.Event()
        self.tcp_count = self.udp_count = self.dns_count = self.associations = 0
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
        for target in (self.accept, self.echo, self.resolve):
            threading.Thread(target=target, daemon=True).start()

    def accept(self):
        while not self.closed.is_set():
            try:
                connection, _ = self.tcp.accept()
            except OSError:
                continue
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
                socks_address(connection)
                if command == 3:
                    self.associations += 1
                    connection.sendall(b'\x05\x00\x00\x01\x7f\x00\x00\x01' + struct.pack('!H', self.udp.getsockname()[1]))
                    while not self.closed.is_set() and connection.recv(1024):
                        pass
                elif command == 1:
                    connection.sendall(b'\x05\x00\x00\x01\x7f\x00\x00\x01\x00\x01')
                    request = b''
                    while b'\r\n\r\n' not in request and len(request) < 8192:
                        request += exact(connection, 1)
                    assert request.startswith(b'GET /fixture ')
                    self.tcp_count += 1
                    payload = b'nimbo-windows-tun'
                    connection.sendall(b'HTTP/1.1 200 OK\r\nContent-Length: ' + str(len(payload)).encode() + b'\r\nConnection: close\r\n\r\n' + payload)
            except (OSError, EOFError, ValueError, AssertionError):
                pass

    def echo(self):
        while not self.closed.is_set():
            try:
                data, address = self.udp.recvfrom(65535)
                assert data[:3] == b'\x00\x00\x00'
                self.udp.sendto(data, address)
                self.udp_count += 1
            except (OSError, AssertionError):
                pass

    def resolve(self):
        while not self.closed.is_set():
            try:
                data, address = self.dns.recvfrom(4096)
                assert data[12:].startswith(b'\x07fixture\x07invalid\x00')
                end = 12
                while data[end]:
                    end += data[end] + 1
                question = data[12:end + 5]
                reply = data[:2] + b'\x81\x80\x00\x01\x00\x01\x00\x00\x00\x00' + question + b'\xc0\x0c\x00\x01\x00\x01\x00\x00\x00\x1e\x00\x04\xc0\x00\x02\x7b'
                self.dns.sendto(reply, address)
                self.dns_count += 1
            except (OSError, AssertionError):
                pass

    def close(self):
        self.closed.set()
        for sock in (self.tcp, self.udp, self.dns):
            sock.close()


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
        try:
            subprocess.run([str(staged), '--install'], check=True, timeout=45)
            installed = True
            environment.update(NIMBO_TEST_MIHOMO_BINARY=str(binary), NIMBO_TEST_MIHOMO_SHA256=manifest['sha256'], NIMBO_TEST_TUN_SOURCE=str(source), NIMBO_TEST_PHYSICAL_INDEX=physical_index, NIMBO_TEST_BYPASS_TARGET=target, NIMBO_TEST_PHYSICAL_DNS=physical_dns)
            subprocess.run([str(driver), '--ignored', '--test-threads=1', '--nocapture'], env=environment, check=True, timeout=240)
            assert fixture.tcp_count >= 6 and fixture.udp_count >= 2 and fixture.dns_count >= 1, 'native fixture traffic absent'
        finally:
            reset = None
            try:
                if installed:
                    reset = subprocess.run([str(driver), '--ignored', '--test-threads=1', 'windows_fixture_emergency_reset'], env=environment, check=False, timeout=20)
            finally:
                # Timeout/OS errors must not strand this disposable runner behind
                # static WFP filters. Uninstall never counts as passing acceptance.
                print(f'Public fixture counters: tcp={fixture.tcp_count}, associations={fixture.associations}, udp={fixture.udp_count}, dns={fixture.dns_count}', flush=True)
                fixture.close()
                subprocess.run([str(staged), '--uninstall'], check=True, timeout=45)
            assert reset is None or reset.returncode == 0, 'explicit owner reset failed (uninstall cleanup does not count as successful acceptance)'
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline and ps("@(Get-NetAdapter | Where-Object Name -eq 'nimbo-mh0').Count") != '0':
                time.sleep(.2)
            assert ps("@(Get-NetAdapter | Where-Object Name -eq 'nimbo-mh0').Count") == '0', 'native adapter retained'
            assert ps(r"$k=Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings'; [ordered]@{ProxyEnable=$k.ProxyEnable;ProxyServer=$k.ProxyServer;ProxyOverride=$k.ProxyOverride} | ConvertTo-Json -Compress") == proxy_before, 'user proxy not restored'
            assert ps("Get-NetFirewallProfile | Sort-Object Name | Select-Object Name,Enabled,DefaultOutboundAction | ConvertTo-Json -Compress") == firewall_before, 'global firewall profile policy changed'
            assert not (Path(os.environ['ProgramFiles']) / 'NimboNativeTun/kill-switch/owner.json').exists(), 'retained WFP journal after explicit reset'
            assert snapshot() == before, 'physical DNS/routes were not restored'
    print('PASS: authenticated SCM broker; native TCP4/6 UDP DNS; Both mixed/proxy snapshot; physical KS denial + native/helper crash/reset; global firewall and physical DNS/routes unchanged')


if __name__ == '__main__':
    main()
