#!/usr/bin/env python3
"""Actual Mihomo TUN data-plane/lease acceptance in disposable Linux namespaces.

No Internet access, credentials, physical routes or host DNS changes. This is
not a live provider/transport acceptance claim. Requires explicit opt-in.
"""
import argparse
import http.client
import json
import os
from pathlib import Path
import selectors
import signal
import socket
import struct
import subprocess
import sys
import tempfile
import threading
import time

INTERFACE = 'nimbo-mh0'
TABLE = '52888'
RULE = 22888
PEER = '198.18.0.2'
TARGET = '203.0.113.10'
TARGET6 = 'fdfe:dcba:9901::10'
SECRET = 'fixture-only-token-' + '0' * 64


def ip(*args, capture=False):
    return subprocess.check_output(['ip', *args], text=True) if capture else subprocess.check_call(['ip', *args], stdout=subprocess.DEVNULL)


def dns_query():
    return struct.pack('!HHHHHH', 0x5123, 0x100, 1, 0, 0, 0) + b'\x07fixture\x04test\x00' + struct.pack('!HH', 1, 1)


def dns_reply(data):
    # Single known question, no dynamic zone/server or public DNS fallback.
    if data != dns_query():
        raise ValueError('unexpected fixture DNS query')
    return data[:2] + struct.pack('!HHHHH', 0x8180, 1, 1, 0, 0) + data[12:] + b'\xc0\x0c' + struct.pack('!HHIH', 1, 1, 60, 4) + socket.inet_aton('10.11.12.13')


def serve_peer():
    def tcp(host, family):
        server = socket.socket(family, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        if family == socket.AF_INET6:
            server.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 1)
        server.bind((host, 18080)); server.listen()
        while True:
            client, _ = server.accept()
            with client:
                client.settimeout(5)
                request = b''
                while b'\r\n\r\n' not in request and len(request) < 8192:
                    part = client.recv(8192 - len(request))
                    if not part:
                        break
                    request += part
                if not request.startswith(b'GET /fixture '):
                    continue
                body = b'native-tun-fixture'
                client.sendall(b'HTTP/1.1 200 OK\r\nContent-Length: ' + str(len(body)).encode() + b'\r\nConnection: close\r\n\r\n' + body)
    def udp(port, dns=False):
        server = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        server.bind(('0.0.0.0', port))
        while True:
            data, sender = server.recvfrom(2048)
            try:
                response = dns_reply(data) if dns else b'udp:' + data
                server.sendto(response, sender)
            except ValueError:
                pass
    for host, family in [('0.0.0.0', socket.AF_INET), ('::', socket.AF_INET6)]:
        threading.Thread(target=tcp, args=(host, family), daemon=True).start()
    threading.Thread(target=udp, args=(15353, False), daemon=True).start()
    threading.Thread(target=udp, args=(5353, True), daemon=True).start()
    print('fixture-ready', flush=True)
    signal.pause()


def line(process, timeout=10):
    with selectors.DefaultSelector() as selector:
        selector.register(process.stdout, selectors.EVENT_READ)
        if not selector.select(timeout):
            raise AssertionError('native readiness deadline exceeded')
        value = process.stdout.readline(12 * 1024 * 1024 + 1)
    if not value or len(value) > 12 * 1024 * 1024:
        raise AssertionError('invalid native readiness frame')
    return value


def request(controller, operation, generation, **fields):
    body = json.dumps(dict(apiVersion=1, operation=operation, requestId='fixture-'+operation, generation=generation, **fields))
    client = http.client.HTTPConnection(controller, timeout=8)
    try:
        client.request('POST', '/v1/invoke', body=body, headers={'Authorization': 'Bearer '+SECRET, 'Content-Type': 'application/json'})
        response = client.getresponse()
        assert response.status == 200
        return json.loads(response.read())
    finally:
        client.close()


def snapshot_network():
    return {key: json.loads(ip(*args, capture=True)) for key, args in {
        'v4routes': ('-j', '-4', 'route', 'show', 'table', 'all'),
        'v6routes': ('-j', '-6', 'route', 'show', 'table', 'all'),
        'v4rules': ('-j', '-4', 'rule', 'show'),
        'v6rules': ('-j', '-6', 'rule', 'show'),
    }.items()}


def verify_restored(before):
    assert all(row['ifname'] != INTERFACE for row in json.loads(ip('-j', 'link', 'show', capture=True))), 'TUN device retained after lease close'
    after=snapshot_network()
    assert after == before, 'namespace routes/rules were not restored exactly: '+json.dumps({'before':before,'after':after},sort_keys=True)


def inside(binary, parent, extra=None):
    assert os.geteuid() == 0
    assert os.readlink('/proc/self/ns/net') != parent, 'refuse host network namespace'
    assert [row['ifname'] for row in json.loads(ip('-j', 'link', 'show', capture=True))] == ['lo'], 'namespace must be fresh'
    # /run/netns is private to this mount namespace. No host named namespace.
    ip('link', 'set', 'lo', 'up')
    subprocess.check_call(['mount', '--make-rprivate', '/'])
    with tempfile.TemporaryDirectory(prefix='nimbo-tun-fixture-') as temp:
        namespace_root = Path('/run/netns'); namespace_root.mkdir(exist_ok=True)
        subprocess.check_call(['mount', '-t', 'tmpfs', 'tmpfs', str(namespace_root)])
        fixture = None
        try:
            ip('netns', 'add', 'nimbo-fixture')
            ip('link', 'add', 'phys0', 'type', 'veth', 'peer', 'name', 'peer0')
            ip('link', 'set', 'peer0', 'netns', 'nimbo-fixture')
            # No asynchronous link-local DAD route may appear after the baseline.
            # Keep the comparison strict instead of filtering out route changes.
            ip('link', 'set', 'dev', 'phys0', 'addrgenmode', 'none')
            ip('-n', 'nimbo-fixture', 'link', 'set', 'dev', 'peer0', 'addrgenmode', 'none')
            ip('addr', 'add', '198.18.0.1/24', 'dev', 'phys0')
            ip('-6', 'addr', 'add', 'fdfe:dcba:9900::1/64', 'dev', 'phys0', 'nodad')
            ip('link', 'set', 'phys0', 'up')
            ip('route', 'add', 'default', 'via', PEER)
            ip('-6', 'route', 'add', 'default', 'via', 'fdfe:dcba:9900::2')
            def peer_ip(*args):
                ip('-n', 'nimbo-fixture', *args)
            peer_ip('link', 'set', 'lo', 'up')
            peer_ip('addr', 'add', PEER+'/24', 'dev', 'peer0')
            peer_ip('addr', 'add', TARGET+'/32', 'dev', 'lo')
            peer_ip('-6', 'addr', 'add', 'fdfe:dcba:9900::2/64', 'dev', 'peer0', 'nodad')
            peer_ip('-6', 'addr', 'add', TARGET6+'/128', 'dev', 'lo', 'nodad')
            peer_ip('link', 'set', 'peer0', 'up')
            fixture = subprocess.Popen(['ip','netns','exec','nimbo-fixture',sys.executable,str(Path(__file__).resolve()),'--peer'], stdout=subprocess.PIPE)
            assert line(fixture).strip() == b'fixture-ready'
            time.sleep(.3)
            before = snapshot_network()
            source = f"""mode: rule
ipv6: true
find-process-mode: off
proxies: [{{name: Fixture, type: direct}}]
proxy-groups: [{{name: Pick, type: select, proxies: [Fixture, REJECT]}}]
rules: ['MATCH,Pick']
dns:
  enable: true
  ipv6: true
  enhanced-mode: redir-host
  default-nameserver: [{PEER}]
  nameserver: ['{PEER}:5353']
tun: {{enable: true, stack: system, auto-route: true, auto-detect-interface: true, strict-route: true}}
"""
            # Fail after native TUN construction (controller port collision).
            # Startup must unwind routes/device and release the owner before the
            # next successful session. No force-kill is used to make this pass.
            with socket.socket(socket.AF_INET,socket.SOCK_STREAM) as occupied:
                occupied.bind(('127.0.0.1',0)); occupied.listen()
                address='127.0.0.1:'+str(occupied.getsockname()[1])
                failed=subprocess.Popen([str(binary),'serve-tun'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
                try:
                    payload=json.dumps(dict(apiVersion=1,operation='start',requestId='fixture-rollback',yaml=source,options=dict(dataDir=str(Path(temp)/'rollback'),networkOwner='desktop-tun',desktopIPv6=True,controllerAddress=address,secret=SECRET))).encode()
                    failed.stdin.write(struct.pack('!I',len(payload))+payload);failed.stdin.flush()
                    failure=json.loads(line(failed,30))
                    assert not failure['success'] and failure['error']['code']=='LISTEN_FAILED'
                    assert failed.wait(timeout=10)==1, 'failed startup did not join rollback'
                    verify_restored(before)
                    print('PASS partial native startup rollback (controller collision)',flush=True)
                finally:
                    failed.stdin.close()
                    if failed.poll() is None:
                        failed.terminate();failed.wait(timeout=10)
            for ending in ('eof', 'sigterm', 'controller'):
                process = subprocess.Popen([str(binary), 'serve-tun'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
                try:
                    payload = json.dumps(dict(apiVersion=1,operation='start',requestId='fixture-'+ending,yaml=source,options=dict(dataDir=str(Path(temp)/ending),networkOwner='desktop-tun',desktopIPv6=True,controllerAddress='127.0.0.1:0',secret=SECRET))).encode()
                    process.stdin.write(struct.pack('!I', len(payload))+payload); process.stdin.flush()
                    ready = json.loads(line(process, 30))
                    assert ready['success'], 'native TUN startup failed: '+json.dumps(ready.get('error'))
                    info = ready['data']; generation = ready['generation']; controller = info['controllerAddress']
                    assert info['networkOwner'] == 'desktop-tun' and info['tunReady'] and info['mixedAddress'] == ''
                    for host in (TARGET, TARGET6):
                        print("checking TCPv6" if host == TARGET6 else "checking TCPv4", flush=True)
                        client = http.client.HTTPConnection(host,18080,timeout=5)
                        client.request('GET','/fixture'); response = client.getresponse()
                        assert response.status == 200 and response.read() == b'native-tun-fixture'
                        client.close()
                    with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
                        client.settimeout(5); client.sendto(b'hello-tun',(TARGET,15353))
                        assert client.recv(2048) == b'udp:hello-tun'
                        client.sendto(dns_query(),('172.29.255.2',53))
                        assert client.recv(2048).endswith(socket.inet_aton('10.11.12.13'))
                    with socket.create_connection(('172.29.255.2',53),5) as client:
                        query=dns_query(); client.sendall(struct.pack('!H',len(query))+query)
                        length=struct.unpack('!H',client.recv(2))[0]
                        received=b''
                        while len(received)<length:
                            part=client.recv(length-len(received)); assert part; received+=part
                        assert received.endswith(socket.inet_aton('10.11.12.13'))
                    links=json.loads(ip('-j','-s','link','show','dev',INTERFACE,capture=True))
                    assert links[0]['stats64']['rx']['bytes'] > 0, 'requests bypassed the native TUN'
                    selected = request(controller,'select',generation,group='Pick',name='REJECT')
                    assert selected['success'], 'native hot selection failed'
                    rejected=http.client.HTTPConnection(TARGET,18080,timeout=3)
                    reached=False
                    try:
                        rejected.request('GET','/fixture')
                        response=rejected.getresponse()
                        reached=response.read()==b'native-tun-fixture'
                    except (OSError,http.client.HTTPException):
                        pass
                    finally:
                        rejected.close()
                    assert not reached, 'REJECT selection was silently bypassed by DIRECT'
                    stale=request(controller,'status',generation+1)
                    assert not stale['success'] and stale['error']['code']=='STALE_GENERATION'
                    # Switching back does not replace the TUN or native generation.
                    assert request(controller,'select',generation,group='Pick',name='Fixture')['success']
                    resumed=http.client.HTTPConnection(TARGET,18080,timeout=5)
                    resumed.request('GET','/fixture')
                    assert resumed.getresponse().read()==b'native-tun-fixture', 'selection failed to resume new traffic'
                    resumed.close()
                    if ending == 'eof':
                        process.stdin.close()
                    elif ending == 'sigterm':
                        process.send_signal(signal.SIGTERM)
                    else:
                        assert request(controller,'stop',generation)['success']
                    assert process.wait(timeout=10)==0, 'native cleanup did not join'
                    verify_restored(before)
                    print('PASS native TCPv4/TCPv6/UDP/DNS-UDP/DNS-TCP/hot-selection/stale-generation/cleanup '+ending,flush=True)
                finally:
                    if process.poll() is None:
                        process.stdin.close()
                        try: process.wait(timeout=10)
                        except subprocess.TimeoutExpired: process.kill();process.wait()
            if extra is not None:
                extra(source, before)
        finally:
            if fixture is not None:
                fixture.terminate(); fixture.wait(timeout=5)
            ip('netns','del','nimbo-fixture')
            subprocess.check_call(['umount', str(namespace_root)])


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--binary',type=Path)
    parser.add_argument('--inside',action='store_true')
    parser.add_argument('--parent-netns')
    parser.add_argument('--peer',action='store_true')
    args=parser.parse_args()
    if not sys.platform.startswith('linux'):
        parser.error('Linux namespaces required')
    if args.peer:
        serve_peer();return
    if os.environ.get('NIMBO_DISPOSABLE_NETNS')!='1':
        parser.error('explicit NIMBO_DISPOSABLE_NETNS=1 opt-in required')
    if not args.binary or not args.binary.resolve().is_file():
        parser.error('source-built native binary required')
    if args.inside:
        inside(args.binary.resolve(),args.parent_netns);return
    if os.geteuid()!=0:
        parser.error('run only the namespace fixture under root, never the desktop GUI')
    subprocess.check_call(['unshare','--net','--mount','--',sys.executable,str(Path(__file__).resolve()),'--inside','--parent-netns',os.readlink('/proc/self/ns/net'),'--binary',str(args.binary.resolve())])

if __name__=='__main__':
    main()
