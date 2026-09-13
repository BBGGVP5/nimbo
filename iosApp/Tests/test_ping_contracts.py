#!/usr/bin/env python3
"""Windows source contracts; --swift compiles and runs local-only Apple native tests.
No Gradle, Go, IPA build, workflows, signing or remote endpoints are invoked.
"""
import argparse
import contextlib
import os
import pathlib
import platform
import shlex
import socket
import socketserver
import ssl
import subprocess
import tempfile
import threading
import time
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCES = [
    "iosApp/Shared/NimboPingPolicy.swift",
    "iosApp/Shared/NimboPingCompletion.swift",
    "iosApp/Shared/NimboHTTPProbe.swift",
    "iosApp/Shared/NimboSOCKSTunnel.swift",
    "iosApp/Shared/NimboRoutingOptions.swift",
    "iosApp/Nimbo/NimboICMPProbe.swift",
    "iosApp/Nimbo/NimboActiveRouteProbe.swift",
    "iosApp/Nimbo/NimboPingService.swift",
    "iosApp/Nimbo/NimboDiagnosticProbe.swift",
    "iosApp/PacketTunnel/NimboPingRoute.swift",
    "iosApp/PacketTunnel/XrayConfiguration.swift",
    "iosApp/Tests/PingConfigurationStubs.swift",
    "iosApp/Tests/PingDiagnosticTests.swift",
    "iosApp/Tests/PingPolicyTests.swift",
]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8-sig")

class PingContracts(unittest.TestCase):
    def test_all_native_gate_sources_exist(self):
        for path in SOURCES:
            self.assertTrue((ROOT / path).is_file(), path)

    def test_legacy_protocol_and_default(self):
        s = read(SOURCES[0])
        self.assertIn('case nimbo, tcp, httpGet = "http_get", httpHead = "http_head", icmp', s)
        self.assertIn('stored == "http" ? .httpHead', s)
        self.assertIn('stored == nil ? .nimbo', s)
        self.assertIn('?? .tcp)', s)
        self.assertIn('method: String = "HEAD"', read("iosApp/Nimbo/NimboPingService.swift"))

    def test_route_only_no_fanout_or_authority_rewrite(self):
        s = read("iosApp/Nimbo/NimboPingService.swift")
        self.assertIn('targets.map { ($0.id, -1) }', s)
        self.assertIn('results[sample.id] = sample.latency', s)
        self.assertIn('NimboPingPolicy.checkedURL(settings.rawURL)', s)
        self.assertNotIn('targetURL', s)
        self.assertNotIn('URLSession', s)
        self.assertIn('case .nimbo, .httpGet, .httpHead: return -1', s)

    def test_settings_snapshot_is_sticky(self):
        s = read("iosApp/Nimbo/NimboPingService.swift")
        self.assertIn('UserDefaults.didChangeNotification', s)
        self.assertIn('self.invalidated = true', s)
        self.assertGreaterEqual(s.count('!Task.isCancelled && lease.valid'), 2)
        self.assertNotIn('com.nimbo.ping.display', s)

    def test_per_server_nimbo_has_no_active_vpn_dependency(self):
        s = read("iosApp/Nimbo/NimboPingService.swift")
        self.assertIn('diagnosticProbe(target.id, configuration, url, min(settings.timeout, remaining))', s)
        self.assertIn('configurations[target.id]', s)
        adapter = read("iosApp/Nimbo/NimboDiagnosticProbe.swift")
        for required in ['NimboDiagnosticRun(', 'NimboDiagnosticCancel(', 'CGoFree(response)',
                         'object["requestID"] as? String == requestID', 'object["serverID"] as? String == serverID',
                         '"method": "GET"', 'withTaskCancellationHandler', 'strnlen(response, maximumResponseBytes + 1)']:
            self.assertIn(required, adapter)
        for forbidden in ['NETunnelProvider', 'CGoInvoke(', 'URLSession', '.startVPNTunnel(', '.stopVPNTunnel(']:
            self.assertNotIn(forbidden, adapter)
        self.assertNotIn('NimboDiagnosticRun', read('iosApp/PacketTunnel/PacketTunnelProvider.swift'))
        root = read('iosApp/Nimbo/RootView.swift')
        self.assertIn('configuration: server.rawConfiguration', root)
        self.assertIn('configurations: Dictionary(profile.servers.map', root)

    def test_private_route_precedes_direct_rules(self):
        s = read("iosApp/PacketTunnel/XrayConfiguration.swift")
        self.assertIn('[NimboPingRoute.rule(proxyTag: pingTag)] +', s)
        self.assertIn('pingRouteVerified: pingRoute != nil && pingTag != nil', s)
        r = read("iosApp/PacketTunnel/NimboPingRoute.swift")
        self.assertIn('guard !balanced', r)
        self.assertIn('first["proxySettings"] == nil', r)
        self.assertIn('sockopt["dialerProxy"] == nil', r)
        self.assertIn('"listen": "127.0.0.1"', r)
        self.assertIn('"auth": "password"', r)
        self.assertIn('"outboundTag": proxyTag', r)

    def test_serial_diagnostic_budget_and_completed_only_progress(self):
        s = read("iosApp/Nimbo/NimboPingService.swift")
        self.assertIn('min(30 * 60, Double(max(0, count)) * (timeout + 1))', s)
        self.assertIn('diagnosticBatchBudget(count: targets.count, timeout: settings.timeout)', s)
        self.assertIn('results = [:] // Unattempted targets are not failed measurements.', s)
        self.assertIn('return !Task.isCancelled && lease.valid ? results : nil', s)
        self.assertIn('guard !Task.isCancelled, lease.valid else { return }', s)
        tests = read('iosApp/Tests/PingDiagnosticTests.swift')
        for assertion in ['clock.now == 276', 'results?.count == 92', 'capped?.count == 600',
                          'cancelClock.progress.count == 20', 'cancelled == nil',
                          'stale == nil && staleProgress.progress.isEmpty']:
            self.assertIn(assertion, tests)

    def test_diagnostic_legacy_labels_preserve_native_bind(self):
        s = read('iosApp/Nimbo/NimboDiagnosticProbe.swift')
        self.assertIn('format == "xray" ? migratingLegacyLabels(configuration) : configuration', s)
        self.assertIn('"config": diagnosticConfiguration', s)
        self.assertIn('inet_pton(AF_INET,', s)
        self.assertIn('inet_pton(AF_INET6,', s)
        self.assertIn('guard !isIP, !value.contains("%"), !value.contains("/")', s)
        self.assertIn('outbounds[index].removeValue(forKey: "sendThrough")', s)
        tests = read('iosApp/Tests/PingDiagnosticTests.swift')
        self.assertIn('NimboDiagnosticProbe.migratingLegacyLabels(raw) == raw', tests)
        self.assertIn('"fe80::1%en0"', tests)
        self.assertIn('NSDictionary(dictionary: newOutbound).isEqual(to: expectedOutbound)', tests)

    def test_root_begins_requested_rows_and_clears_pending_on_exit(self):
        s = read('iosApp/Nimbo/RootView.swift')
        for ids in ['targets.map(\\.id)', '[serverID]', 'candidates.map(\\.id)']:
            self.assertIn('NimboBeginIosPings(serverIds: ' + ids + ')', s)
        self.assertEqual(s.count('NimboBeginIosPings('), 3)
        self.assertNotIn('serverIds: [], values: [], inProgress: true', s)
        self.assertEqual(s.count('defer {\n            IosComposeControllerKt.NimboUpdateIosPings(serverIds: [], values: [], inProgress: false)'), 3)

    def test_provider_identity_generation_method_and_health(self):
        s = read("iosApp/PacketTunnel/PacketTunnelProvider.swift")
        for text in ['targets.contains(pingServerID)', 'generation == self.pingGeneration',
                     'pending &&', 'self.pingServerID = pingServerID', '!self.awg.suspended',
                     'socks: route.socks', '"nimboPing" ? "GET"', 'invalidatePingSamples()']:
            self.assertIn(text, s)
        app = read("iosApp/Nimbo/VpnController.swift")
        self.assertIn('stagingData(for: server) == data', app)
        self.assertIn('"pingServerID": verifiedPingServerID(for: data)', app)

    def test_disconnected_ipc_never_probes_direct(self):
        s = read("iosApp/Nimbo/NimboActiveRouteProbe.swift")
        self.assertIn('session.status == .connected', s)
        self.assertIn('NEVPNStatusDidChange', s)
        self.assertIn('cancelNimboPing', s)
        self.assertNotIn('URLSession', s)
        self.assertNotIn('NimboHTTPProbe', s)

    def test_http_security_and_bounds(self):
        s = read("iosApp/Shared/NimboHTTPProbe.swift")
        self.assertNotIn('kCFStreamPropertySOCKSProxy', s)
        self.assertNotIn('rawValue: kCFStreamPropertyProxyLocalBypass', s)
        self.assertIn('CFStreamCreatePairWithSocketToHost', s)
        self.assertIn('CFStreamCreatePairWithSocket(kCFAllocatorDefault, tunnel.descriptor', s)
        self.assertIn('guard let connected = await NimboSOCKSTunnel.connect', s)
        self.assertIn('kCFStreamSSLPeerName as String: bare', s)
        self.assertIn('guard CFReadStreamSetProperty(read, CFStreamPropertyKey(rawValue: kCFStreamPropertySSLSettings), tls as CFDictionary)', s)
        self.assertIn('maximumHeaderBytes', s)
        self.assertIn('guard (200...299).contains(status)', s)
        self.assertIn('withTaskCancellationHandler', s)
        for forbidden in ['allowsAnyHTTPSCertificate', 'kCFStreamSSLValidatesCertificateChain',
                          'kCFStreamSSLAllowsAnyRoot', 'URLCredential(trust:', 'sec_protocol_options_set_verify_block']:
            self.assertNotIn(forbidden, s)

    def test_socks_wire_auth_deadline_remote_dns_and_cleanup(self):
        s = read('iosApp/Shared/NimboSOCKSTunnel.swift')
        for required in ['SO_NOSIGPIPE', 'O_NONBLOCK', 'Darwin.poll(', 'io.read(2) == [5, 2]',
                         'io.read(2) == [1, 0]', 'reply[0] == 5, reply[1] == 0, reply[2] == 0',
                         'return [3, UInt8(name.count)] + name', 'UInt32(0x7f000001).bigEndian',
                         'deinit { Darwin.close(descriptor) }', '!cancellation.isFinished',
                         'ProcessInfo.processInfo.systemUptime < deadline']:
            self.assertIn(required, s)
        for forbidden in ['getaddrinfo', 'URLSession', 'kCFStreamPropertySOCKSProxy']:
            self.assertNotIn(forbidden, s)

    def test_icmp_is_datagram_and_correlated(self):
        s = read("iosApp/Nimbo/NimboICMPProbe.swift")
        self.assertIn('SOCK_DGRAM, ipv6 ? IPPROTO_ICMPV6 : IPPROTO_ICMP', s)
        self.assertNotIn('SOCK_RAW', s)
        self.assertIn('DispatchSource.makeReadSource', s)
        self.assertIn('NimboICMPPacket.matches', s)
        self.assertIn('source.setCancelHandler { Darwin.close(fd) }', s)
        self.assertIn('withTaskCancellationHandler', s)

    def test_display_backup_and_zero(self):
        s = read("iosApp/Nimbo/NimboBackup.swift")
        self.assertIn('"com.nimbo.ping.display"', s)
        self.assertIn('"protocol", "display", "url"', s)
        root = read("iosApp/Nimbo/RootView.swift")
        self.assertIn('results.filter({ $0.value >= 0 })', root)
        self.assertEqual(root.count('session: vpn.manager?.connection as? NETunnelProviderSession'), 3)

    def test_deadlines_and_cleanup(self):
        s = read("iosApp/Nimbo/NimboPingService.swift")
        for text in ['min(60,', 'min(settings.timeout, remaining)', 'connection.cancel()', 'timer.cancel()']:
            self.assertIn(text, s)
        c = read("iosApp/Shared/NimboPingCompletion.swift")
        self.assertLess(c.index('lock.unlock()\n        callback?'), c.index('callback?(value)'))

    def test_heap_remains_six_gib(self):
        self.assertIn('-Dorg.gradle.jvmargs=-Xmx6g', read("scripts/ci/build-unsigned-ios.sh"))

class FixtureServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True
    def handle_error(self, request, client_address):
        # Exception type only; no TLS data or SOCKS credentials.
        import sys
        self.errors.append(type(sys.exception()).__name__)

class HTTPFixture(socketserver.StreamRequestHandler):
    def handle(self):
        line = self.rfile.readline(8192).decode('ascii', 'replace').strip()
        if not line:
            return
        while self.rfile.readline(8192) not in (b'\r\n', b'\n', b''):
            pass
        method, path, _ = line.split(' ', 2)
        self.server.requests.append((method, path))
        if path == '/wait-second-socks-stall':
            ready = self.server.second_stall.wait(timeout=2)
            self.wfile.write(b'HTTP/1.1 204 No Content\r\n\r\n' if ready else b'HTTP/1.1 503 Unavailable\r\n\r\n')
            self.wfile.flush()
            return
        if path in ('/stall', '/cancel'):
            time.sleep(3)
            return
        if path == '/malformed':
            response = b'NOT HTTP\r\n\r\n'
        elif path == '/large-header':
            response = b'HTTP/1.1 200 OK\r\nX-Big: ' + b'a' * 17000 + b'\r\n\r\n'
        elif path == '/redirect':
            response = b'HTTP/1.1 302 Found\r\nLocation: /must-not-follow\r\nContent-Length: 0\r\n\r\n'
        elif path == '/error':
            response = b'HTTP/1.1 503 Unavailable\r\nContent-Length: 0\r\n\r\n'
        elif path == '/forbidden':
            response = b'HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n'
        elif path == '/upgrade':
            response = b'HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n\r\n'
        elif path == '/interim':
            response = b'HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 204 No Content\r\n\r\n'
        elif path == '/large-body':
            response = b'HTTP/1.1 200 OK\r\nContent-Length: 1000000000\r\n\r\nsmall prefix'
        else:
            response = b'HTTP/1.1 204 No Content\r\n\r\n'
        self.wfile.write(response)
        self.wfile.flush()
        if path == '/large-body':
            time.sleep(3) # Headers must complete without downloading the body.

def exact(sock, count):
    data = b''
    while len(data) < count:
        part = sock.recv(count - len(data))
        if not part:
            raise EOFError()
        data += part
    return data

class SOCKSFixture(socketserver.BaseRequestHandler):
    def handle(self):
        self.server.connections += 1
        sock = self.request
        sock.settimeout(3)
        version, count = exact(sock, 2)
        methods = exact(sock, count)
        if version != 5 or 2 not in methods:
            sock.sendall(b'\x05\xff'); return
        sock.sendall(b'\x05\x02')
        version, size = exact(sock, 2)
        user = exact(sock, size)
        password = exact(sock, exact(sock, 1)[0])
        accepted = version == 1 and user in (b'fixture', b'stall') and password == b'fixture-only'
        if accepted and user == b'stall':
            # No auth response: deadline/cancellation must close the owned socket.
            self.server.stall_started += 1
            if self.server.stall_started == 2:
                self.server.second_stall.set()
            if sock.recv(1) == b'':
                self.server.stall_closed += 1
            return
        sock.sendall(b'\x01\x00' if accepted else b'\x01\x01')
        if not accepted:
            self.server.rejected += 1
            return
        version, command, reserved, kind = exact(sock, 4)
        if kind == 1: host = socket.inet_ntop(socket.AF_INET, exact(sock, 4))
        elif kind == 4: host = socket.inet_ntop(socket.AF_INET6, exact(sock, 16))
        elif kind == 3: host = exact(sock, exact(sock, 1)[0]).decode('ascii')
        else: return
        port = int.from_bytes(exact(sock, 2), 'big')
        self.server.targets.append((kind, host, port))
        if version != 5 or command != 1: return
        if host == 'reject.invalid':
            sock.sendall(b'\x05\x05\x00\x01\x00\x00\x00\x00\x00\x00')
            return
        if host == 'bad-reply.invalid':
            sock.sendall(b'\x05\x00\x01\x01\x00\x00\x00\x00\x00\x00')
            return
        if host == 'only-via-proxy.invalid':
            reply = b'\x05\x00\x00\x03\x05bound\x00\x50'
        elif host == 'tls-through.invalid':
            reply = b'\x05\x00\x00\x04' + bytes(16) + b'\x00\x50'
        else:
            reply = b'\x05\x00\x00\x01\x7f\x00\x00\x01\x00\x50'
        for byte in reply: # Fragmented CONNECT replies must be consumed exactly.
            sock.sendall(bytes([byte]))
        if host == 'tls-through.invalid':
            # Emulate the TLS origin reached AFTER CONNECT, not TLS to the proxy.
            try:
                with self.server.target_tls.wrap_socket(sock, server_side=True) as secure:
                    if secure.recv(2048):
                        self.server.tls_http += 1
            except OSError:
                self.server.tls_rejected += 1
            return
        data = b''
        while b'\r\n\r\n' not in data and len(data) < 16384:
            chunk = sock.recv(2048)
            if not chunk: return
            data += chunk
        self.server.proxied.append(data.split(b'\r\n')[0])
        sock.sendall(b'HTTP/1.1 204 No Content\r\n\r\n')

@contextlib.contextmanager
def serving(handler, tls=None):
    server = FixtureServer(('127.0.0.1', 0), handler)
    server.requests, server.proxied, server.rejected = [], [], 0
    server.connections, server.errors = 0, []
    server.targets, server.stall_closed = [], 0
    server.stall_started, server.second_stall = 0, threading.Event()
    server.tls_http, server.tls_rejected = 0, 0
    if tls:
        server.socket = tls.wrap_socket(server.socket, server_side=True)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield server
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)

def native_tests():
    if platform.system() != 'Darwin':
        raise SystemExit('--swift requires macOS + Xcode (Foundation/Network/NetworkExtension/Darwin). It never silently skips.')
    with tempfile.TemporaryDirectory(prefix='nimbo-ping-') as temporary:
        tmp = pathlib.Path(temporary)
        sdk = subprocess.check_output(['xcrun', '--sdk', 'macosx', '--show-sdk-path'], text=True).strip()
        arch = 'arm64' if platform.machine() == 'arm64' else 'x86_64'
        command = ['xcrun', '--sdk', 'macosx', 'swiftc', '-swift-version', '5', '-D', 'NIMBO_PING_TESTS', '-parse-as-library',
                   '-target', arch + '-apple-macosx13.0', '-sdk', sdk,
                   *SOURCES, '-o', str(tmp / 'PingPolicyTests')]
        print('NATIVE COMPILER:', shlex.join(command), flush=True)
        subprocess.run(command, cwd=ROOT, check=True, timeout=180)
        ios_sdk = subprocess.check_output(['xcrun', '--sdk', 'iphoneos', '--show-sdk-path'], text=True).strip()
        ios_command = ['xcrun', '--sdk', 'iphoneos', 'swiftc', '-swift-version', '5', '-D', 'NIMBO_PING_TESTS', '-typecheck',
                       '-target', 'arm64-apple-ios16.0', '-sdk', ios_sdk, *SOURCES[:-1]]
        print('IOS 16 TYPECHECK:', shlex.join(ios_command), flush=True)
        subprocess.run(ios_command, cwd=ROOT, check=True, timeout=180)
        # Parse modified integration files as well. Their real LibXray/Kotlin module
        # dependencies are typechecked by the full app build, not test stubs.
        for path in ['iosApp/PacketTunnel/PacketTunnelProvider.swift', 'iosApp/Nimbo/VpnController.swift',
                     'iosApp/Nimbo/RootView.swift', 'iosApp/Nimbo/NimboBackup.swift']:
            subprocess.run(['xcrun', 'swiftc', '-frontend', '-parse', path], cwd=ROOT, check=True, timeout=30)
        cert, key = tmp / 'cert.pem', tmp / 'key.pem'
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                        '-subj', '/CN=localhost', '-keyout', str(key), '-out', str(cert)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.load_cert_chain(cert, key)
        peer_names = []
        tls.set_servername_callback(lambda connection, name, context: peer_names.append(name))
        with serving(HTTPFixture) as http, serving(SOCKSFixture) as socks, serving(HTTPFixture, tls) as secure:
            socks.target_tls = tls
            http.second_stall = socks.second_stall
            env = dict(os.environ, NIMBO_TEST_HTTP_PORT=str(http.server_address[1]),
                       NIMBO_TEST_SOCKS_PORT=str(socks.server_address[1]), NIMBO_TEST_TLS_PORT=str(secure.server_address[1]))
            try:
                subprocess.run([str(tmp / 'PingPolicyTests')], env=env, check=True, timeout=45)
            finally:
                # Preserve local fixture evidence even when a Swift precondition traps.
                print('HTTP fixture requests:', http.requests, flush=True)
                print('SOCKS fixture:', {'accepted': socks.proxied, 'rejected': socks.rejected,
                                        'connections': socks.connections, 'errors': socks.errors,
                                        'targets': socks.targets, 'stall_closed': socks.stall_closed,
                                        'tls_rejected': socks.tls_rejected, 'tls_peer_names': peer_names}, flush=True)
            assert ('GET', '/method/GET') in http.requests
            assert ('HEAD', '/method/HEAD') in http.requests
            assert all(path not in ('/must-not-hit-origin', '/must-not-follow') for _, path in http.requests), http.requests
            assert socks.proxied == [b'GET /must-not-hit-origin HTTP/1.1', b'HEAD /remote-dns?exact=1 HTTP/1.1'], socks.proxied
            assert socks.rejected == 1
            assert (3, 'only-via-proxy.invalid', 8443) in socks.targets, socks.targets
            assert socks.stall_closed == 2, socks.stall_closed
            assert 'tls-through.invalid' in peer_names and 'localhost' in peer_names, peer_names
            assert socks.tls_rejected == 1 and socks.tls_http == 0
            assert not secure.requests, 'Untrusted certificate must fail before an HTTP request'
        print('PASS: local HTTP methods, no redirect, no SOCKS bypass/fallback, untrusted TLS rejection', flush=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--swift', action='store_true', help='Compile + execute native tests on macOS; fail elsewhere')
    args = parser.parse_args()
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(PingContracts))
    if not result.wasSuccessful():
        raise SystemExit(1)
    if args.swift:
        native_tests()

if __name__ == '__main__':
    main()
