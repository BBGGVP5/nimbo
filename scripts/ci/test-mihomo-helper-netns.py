#!/usr/bin/env python3
"""Root broker acceptance ONLY in fresh network AND private mount namespaces.

Uses a synthetic local peer, not a VPN subscription or public network. /run and
/usr/local/lib are private tmpfs mounts: never installs on the user's machine.
"""
import argparse
import hashlib
import http.client
import importlib.util
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import sys
import time
import tempfile

spec = importlib.util.spec_from_file_location('fixture', Path(__file__).with_name('test-mihomo-desktop-netns.py'))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
SOCKET = '/run/nimbo/helper.sock'


def receive(stream, size):
    result = b''
    while len(result) < size:
        part = stream.recv(size-len(result))
        assert part, 'broker connection closed before reply'
        result += part
    return result


def call(stream, command, **fields):
    body = json.dumps(dict(type=command, **fields)).encode()
    stream.sendall(struct.pack('!I', len(body))+body)
    size = struct.unpack('!I', receive(stream, 4))[0]
    assert size <= 8*1024*1024
    return json.loads(receive(stream, size))


def connect():
    stream = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    stream.settimeout(40)
    stream.connect(SOCKET)
    return stream


def wait(predicate, message, timeout=10):
    deadline = time.monotonic()+timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(.05)
    raise AssertionError(message)


def no_device():
    return all(row['ifname'] != fixture.INTERFACE for row in json.loads(fixture.ip('-j', 'link', 'show', capture=True)))


def trace_tail(path):
    try:
        with path.open('rb') as stream:
            stream.seek(0, os.SEEK_END)
            stream.seek(max(0, stream.tell() - 16384))
            return stream.read(16384).decode('utf-8', errors='replace')
    except OSError:
        return 'trace unavailable'


def trace_native(service, path, parent):
    # This is fixture-only instrumentation, not a product logging switch. The
    # target is the helper we just spawned, after private namespace admission.
    assert os.environ.get('NIMBO_DISPOSABLE_NETNS') == '1' and os.geteuid() == 0
    assert parent and os.readlink('/proc/self/ns/net') != parent
    assert os.stat('/run').st_dev != os.stat('/').st_dev
    trace = subprocess.Popen(['strace', '-f', '-qq', '-s', '96', '-e',
                              'trace=connect,bind,setsockopt,getsockname,getpeername',
                              '-o', str(path), '-p', str(service.pid)],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        def attached():
            assert trace.poll() is None, 'native syscall tracer exited before attachment'
            status = Path('/proc/'+str(service.pid)+'/status').read_text()
            return any(line.startswith('TracerPid:') and int(line.split()[1]) == trace.pid
                       for line in status.splitlines())
        wait(attached, 'native syscall tracer failed to attach', timeout=5)
    except BaseException:
        if trace.poll() is None:
            trace.terminate()
        trace.wait(timeout=5)
        raise
    return trace


def traffic():
    for host in (fixture.TARGET, fixture.TARGET6):
        print('broker traffic: TCP4' if host == fixture.TARGET else 'broker traffic: TCP6', flush=True)
        rx_before = fixture.tun_rx_bytes()
        client = http.client.HTTPConnection(host, 18080, timeout=5)
        try:
            client.request('GET', '/fixture')
            response = client.getresponse()
            assert response.status == 200 and response.read() == b'native-tun-fixture'
        finally:
            client.close()
        assert fixture.tun_rx_bytes() > rx_before, 'broker '+host+' request did not return through TUN'
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as client:
        client.settimeout(5)
        client.sendto(b'broker', (fixture.TARGET, 15353))
        assert client.recv(2048) == b'udp:broker'
        client.sendto(fixture.dns_query(), ('172.29.255.2', 53))
        assert client.recv(2048).endswith(socket.inet_aton('10.11.12.13'))
    links = json.loads(fixture.ip('-j', '-s', 'link', 'show', 'dev', fixture.INTERFACE, capture=True))
    assert links[0]['stats64']['rx']['bytes'] > 0, 'broker traffic bypassed TUN'


def controller(ready, operation, **fields):
    client = http.client.HTTPConnection(ready['info']['controllerAddress'], timeout=5)
    try:
        body = json.dumps(dict(apiVersion=1, requestId='broker-'+operation, operation=operation, generation=ready['generation'], **fields))
        client.request('POST', '/v1/invoke', body, {'Authorization': 'Bearer '+ready['secret'], 'Content-Type': 'application/json'})
        response = client.getresponse()
        assert response.status == 200
        return json.loads(response.read())
    finally:
        client.close()


def broker_checks(helper, binary, source, before, rust_test=None, parent=None, trace_enabled=False):
    # All directories below the installation/runtime roots are on private tmpfs.
    assert os.stat('/usr/local/lib').st_dev != os.stat('/usr/local').st_dev
    assert os.stat('/run').st_dev != os.stat('/').st_dev
    subprocess.check_call([str(helper), '--install-mihomo', str(binary)])
    service = None
    owner = None
    trace_directory = tempfile.TemporaryDirectory(prefix='nimbo-broker-sockets-')
    traces = []
    def start_service():
        process = subprocess.Popen([str(helper)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            wait(lambda: Path(SOCKET).exists(), 'broker failed to listen')
            if trace_enabled:
                path = Path(trace_directory.name) / ('native-'+str(len(traces))+'.trace')
                traces.append((trace_native(process, path, parent), path))
        except BaseException:
            process.terminate(); process.wait(timeout=10)
            raise
        return process
    request = dict(yaml=source, source_sha256=hashlib.sha256(source.encode()).hexdigest(), binary_sha256=hashlib.sha256(binary.read_bytes()).hexdigest())
    try:
        for ending in ('explicit', 'eof', 'native-crash', 'service-crash', 'both-crash'):
            print('broker lifecycle: '+ending, flush=True)
            service = start_service()
            with connect() as status:
                assert call(status, 'ping')['protocol'] == 3
                available = call(status, 'mihomo_status')
                assert available['available'] and available['binary_sha256'] == request['binary_sha256']
                assert call(status, 'mihomo_preflight', **request)['type'] == 'ok'
            fixture.verify_restored(before)
            if ending == 'explicit':
                allowed = Path('/etc/nimbo/helper.uid')
                uid = 65533 if allowed.exists() and allowed.read_text().strip() == '65534' else 65534
                code = "import os,socket,struct,json;os.setgroups([]);os.setgid("+str(uid)+");os.setuid("+str(uid)+");s=socket.socket(socket.AF_UNIX);s.settimeout(3);s.connect('/run/nimbo/helper.sock');h=s.recv(4);n=struct.unpack('!I',h)[0];r=json.loads(s.recv(n));assert r['code']=='permission_denied'"
                subprocess.check_call([sys.executable, '-c', code])
                print('PASS broker rejects unauthorized peer UID', flush=True)
            if rust_test is not None and ending == 'explicit':
                with tempfile.NamedTemporaryFile(mode='w', suffix='.yaml') as yaml:
                    yaml.write(source); yaml.flush()
                    env = dict(os.environ, NIMBO_TEST_PARENT_NETNS=parent, NIMBO_TEST_TUN_SOURCE=yaml.name, NIMBO_TEST_MIHOMO_BINARY=str(binary), NIMBO_TEST_MIHOMO_SHA256=request['binary_sha256'])
                    subprocess.check_call([str(rust_test), '--ignored', '--test-threads=1'], env=env)
                wait(no_device, 'Rust lease failed to release device'); fixture.verify_restored(before)
            owner = connect()
            ready = call(owner, 'mihomo_up', **request)
            assert ready['type'] == 'mihomo_ready', ready
            assert ready['info']['tunReady'] and ready['info']['networkOwner'] == 'desktop-tun'
            assert ready['info']['sourceSHA256'] == request['source_sha256']
            try:
                traffic()
            except Exception:
                print('broker readiness on traffic failure: '+json.dumps(controller(ready, 'status')), flush=True)
                print('isolated kernel state on failure: '+json.dumps(fixture.snapshot_network()), flush=True)
                raise
            # A transient client must neither own nor stop this connection's TUN.
            with connect() as other:
                assert call(other, 'mihomo_down')['type'] == 'ok'
                assert call(other, 'mihomo_status')['running']
                error = call(other, 'mihomo_up', **request)
                assert error['type'] == 'error' and error['message'] == 'TUN_IN_USE', error
                bad = dict(request, source_sha256='0'*64)
                assert call(other, 'mihomo_preflight', **bad)['message'] == 'SOURCE_DIGEST_MISMATCH'
                assert call(other, 'mihomo_up', **dict(request, binary_sha256='0'*64))['message'] == 'CORE_HASH_MISMATCH'
                assert call(other, 'mihomo_preflight', **dict(request, data_dir='/tmp/forbidden'))['message'] == 'INVALID_PAYLOAD'
            traffic()
            assert controller(ready, 'select', group='Pick', name='REJECT')['success']
            for host in (fixture.TARGET, fixture.TARGET6):
                rejected = http.client.HTTPConnection(host, 18080, timeout=2)
                reached = False
                try:
                    rejected.request('GET', '/fixture')
                    reached = rejected.getresponse().read() == b'native-tun-fixture'
                except (OSError, http.client.HTTPException):
                    pass
                finally:
                    rejected.close()
                assert not reached, 'selection '+host+' silently bypassed through DIRECT'
            assert controller(ready, 'select', group='Pick', name='Fixture')['success']
            traffic()
            if ending == 'explicit':
                assert call(owner, 'mihomo_down')['type'] == 'ok'
                # EOF while the native core is still fetching a provider must
                # cancel the real request and join cleanup, not just hide UI.
                with socket.socket() as provider:
                    provider.bind(('127.0.0.1', 0)); provider.listen(); provider.settimeout(10)
                    hanging = source.replace('proxies: [{name: Fixture, type: direct}]', 'proxies: []').replace('proxy-groups: [{name: Pick, type: select, proxies: [Fixture, REJECT]}]', "proxy-providers: {Feed: {type: http, url: 'http://127.0.0.1:"+str(provider.getsockname()[1])+"/feed', interval: 3600}}\nproxy-groups: [{name: Pick, type: select, use: [Feed]}]")
                    attempt = connect()
                    body = json.dumps(dict(type='mihomo_up', yaml=hanging, source_sha256=hashlib.sha256(hanging.encode()).hexdigest(), binary_sha256=request['binary_sha256'])).encode()
                    attempt.sendall(struct.pack('!I', len(body))+body)
                    with provider.accept()[0] as pending:
                        pending.settimeout(10)
                        data = b''
                        while b'\r\n\r\n' not in data:
                            data += pending.recv(4096)
                        attempt.close()
                        assert pending.recv(1) == b'', 'cancellation retained native provider request'
                    wait(no_device, 'cancelled startup retained device', timeout=20)
                    fixture.verify_restored(before)
                    with connect() as probe:
                        assert not call(probe, 'mihomo_status')['running']
                    print('PASS broker EOF cancels real provider startup and releases owner', flush=True)
                # Keep the old connection open while a different owner starts:
                # its later disconnect must not stop the replacement session.
                successor = connect()
                successor_ready = call(successor, 'mihomo_up', **request)
                assert successor_ready['type'] == 'mihomo_ready'
                owner.close(); owner = successor
                traffic()
                assert call(owner, 'mihomo_down')['type'] == 'ok'
                assert not call(owner, 'mihomo_status')['running']
            elif ending in ('native-crash','both-crash'):
                # Native child can be spawned by any service connection thread.
                children=set()
                for task in Path('/proc/'+str(service.pid)+'/task').iterdir():
                    children.update((task/'children').read_text().split())
                native=[int(pid) for pid in children if Path('/proc/'+pid+'/comm').read_text().strip()=='nimbo-mihomo']
                assert len(native)==1, 'cannot identify sole owned native child'
                if ending=='both-crash':
                    # Stop the native child before killing the helper so EOF
                    # cannot turn this into an ordinary graceful-close test.
                    os.kill(native[0],19)
                    service.kill();service.wait(timeout=5)
                os.kill(native[0],9)
                wait(no_device,'killed native child retained device')
                assert Path('/run/nimbo-mihomo-tun-rules.json').exists(), 'hard crash did not retain WAL'
                assert fixture.snapshot_network()!=before, 'hard crash did not leave owned rules'
                if ending=='native-crash':
                    assert not call(owner,'mihomo_status')['running']
                    fixture.verify_restored(before)
                    recovered=call(owner,'mihomo_up',**request)
                    assert recovered['type']=='mihomo_ready',recovered
                    traffic()
                    assert call(owner,'mihomo_down')['type']=='ok'
                else:
                    # Both processes died: next native owner replays its WAL.
                    owner.close();owner=None
                    Path(SOCKET).unlink()
                    service=start_service()
                    owner=connect()
                    blocked=call(owner,'tun_up',config='',interface='fixture-never-created',bypass_ips=[],dns=[],kill_switch=False)
                    assert blocked['type']=='error' and blocked['message']=='TUN_IN_USE', 'legacy engine stole retained native WAL state'
                    recovered=call(owner,'mihomo_up',**request)
                    assert recovered['type']=='mihomo_ready',recovered
                    traffic()
                    assert call(owner,'mihomo_down')['type']=='ok'
            elif ending == 'service-crash':
                service.kill()
                service.wait(timeout=5)
            owner.close()
            owner = None
            wait(no_device, 'broker failed to release actual native TUN')
            fixture.verify_restored(before)
            if service.poll() is None:
                with connect() as status:
                    assert call(status, 'shutdown')['type'] == 'ok'
                service.wait(timeout=5)
            if Path(SOCKET).exists():
                # Service SIGKILL cannot unlink its socket; private namespace only.
                Path(SOCKET).unlink()
            service = None
            print('PASS broker source/hash admission, lease isolation, real TCPv4/TCPv6/UDP/DNS, hot selection and '+ending+' cleanup', flush=True)
        # Root-protected inode modification invalidates cached availability;
        # a subsequent start still rehashes the pinned bytes before execution.
        service = start_service()
        with connect() as status:
            assert call(status, 'mihomo_status')['available']
            with Path('/usr/local/lib/nimbo/nimbo-mihomo').open('r+b') as installed:
                installed.seek(4); installed.write(b'\x00')
            assert not call(status, 'mihomo_status')['available']
            assert call(status, 'mihomo_up', **request)['message'] == 'CORE_UNAVAILABLE'
            subprocess.check_call([str(helper), '--install-mihomo', str(binary)])
            assert call(status, 'mihomo_status')['available']
            assert call(status, 'shutdown')['type'] == 'ok'
        service.wait(timeout=5); service=None
        fixture.verify_restored(before)
        print('PASS protected core modification invalidates status cache and start admission', flush=True)
    except BaseException:
        for _, path in traces:
            print('synthetic native socket trace '+path.name+':\n'+trace_tail(path), flush=True)
        try:
            for label, args in [('addresses', ('-j', '-6', 'addr', 'show')),
                                ('neighbors', ('-j', '-6', 'neigh', 'show'))]:
                print('isolated IPv6 '+label+': '+fixture.ip(*args, capture=True), flush=True)
        except Exception as error:
            print('isolated IPv6 evidence unavailable: '+type(error).__name__, flush=True)
        raise
    finally:
        if owner is not None:
            owner.close()
        if service is not None and service.poll() is None:
            service.terminate()
            service.wait(timeout=10)
        for process, _ in traces:
            if process.poll() is None:
                process.terminate()
            process.wait(timeout=5)
        # Retain evidence even on success: tracing can change race timing. A
        # passing traced run is not proof that the untraced runtime was repaired.
        for _, path in traces:
            print('synthetic native socket trace '+path.name+':\n'+trace_tail(path), flush=True)
        trace_directory.cleanup()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--binary', type=Path, required=True)
    parser.add_argument('--helper', type=Path, required=True)
    parser.add_argument('--rust-test', type=Path)
    parser.add_argument('--trace-native', action='store_true', help='synthetic private-namespace socket syscall evidence only')
    parser.add_argument('--inside', action='store_true')
    parser.add_argument('--parent-netns')
    args = parser.parse_args()
    if not sys.platform.startswith('linux') or os.geteuid() != 0 or os.environ.get('NIMBO_DISPOSABLE_NETNS') != '1':
        parser.error('explicit root Linux NIMBO_DISPOSABLE_NETNS=1 fixture opt-in required')
    binary, helper = args.binary.resolve(strict=True), args.helper.resolve(strict=True)
    if not args.inside:
        subprocess.check_call(['unshare', '--net', '--mount', '--', sys.executable, str(Path(__file__).resolve()), '--inside', '--parent-netns', os.readlink('/proc/self/ns/net'), '--binary', str(binary), '--helper', str(helper), *(['--rust-test', str(args.rust_test.resolve(strict=True))] if args.rust_test else []), *(['--trace-native'] if args.trace_native else [])])
        return
    assert args.parent_netns and os.readlink('/proc/self/ns/net') != args.parent_netns
    assert [r['ifname'] for r in json.loads(fixture.ip('-j', 'link', 'show', capture=True))] == ['lo'], 'refuse non-fresh namespace'
    subprocess.check_call(['mount', '--make-rprivate', '/'])
    for path in ('/run', '/usr/local/lib'):
        assert Path(path).is_dir()
        subprocess.check_call(['mount', '-t', 'tmpfs', '-o', 'mode=0755', 'tmpfs', path])
    fixture.inside(binary, args.parent_netns, lambda source, before: broker_checks(helper, binary, source, before, args.rust_test, args.parent_netns, args.trace_native))


if __name__ == '__main__':
    main()
