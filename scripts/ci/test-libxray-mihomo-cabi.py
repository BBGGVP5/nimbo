#!/usr/bin/env python3
"""Real merged-library V1 ownership/callback test. Localhost only; no mobile TUN.

Run alongside test-libxray-cabi.py against the SAME library, never a Mihomo-only
test library. Apple CI also executes the existing API3/AWG/diagnostic tests.
"""
import ctypes
import http.client
import http.server
import json
from pathlib import Path
import sys
import tempfile
import threading

FIXTURE = '# exact source\r\nmode: rule\r\nproxies: [{name: local, type: direct}]\r\nproxy-groups: [{name: Choice, type: select, proxies: [local, DIRECT, REJECT]}]\r\nrules: ["MATCH,Choice"]\r\n'


def main():
    library = ctypes.CDLL(str(Path(sys.argv[1]).resolve()))
    for symbol in ['CGoInvoke', 'CGoFree', 'NimboAWGStart', 'NimboAWGStop', 'NimboAWGStats',
                   'NimboDiagnosticRun', 'NimboDiagnosticCancel']:
        assert getattr(library, symbol), 'Missing existing export: ' + symbol
    for symbol in ['CGoInvoke', 'NimboMihomoInvokeV1', 'NimboMihomoCancelV1']:
        fn = getattr(library, symbol)
        fn.argtypes, fn.restype = [ctypes.c_char_p], ctypes.c_void_p
    for symbol in ['CGoFree', 'NimboMihomoFreeV1']:
        getattr(library, symbol).argtypes = [ctypes.c_void_p]
        getattr(library, symbol).restype = None
    library.NimboMihomoStartIOSPacketFlowV1.argtypes = [ctypes.c_char_p]
    library.NimboMihomoStartIOSPacketFlowV1.restype = ctypes.c_void_p
    library.NimboMihomoWriteIOSPacketV1.argtypes = [ctypes.c_uint64, ctypes.c_void_p, ctypes.c_int]
    library.NimboMihomoWriteIOSPacketV1.restype = ctypes.c_int
    library.NimboMihomoReadIOSPacketV1.argtypes = [ctypes.c_uint64, ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
    library.NimboMihomoReadIOSPacketV1.restype = ctypes.c_int
    library.NimboMihomoStartIOSV1.argtypes = [ctypes.c_char_p, ctypes.c_int64]
    library.NimboMihomoStartIOSV1.restype = ctypes.c_void_p
    callback_type = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_int64, ctypes.c_void_p)
    library.NimboMihomoSetSocketProtectorV1.argtypes = [callback_type, ctypes.c_void_p]
    library.NimboMihomoSetSocketProtectorV1.restype = ctypes.c_void_p

    def response(pointer, native_free=False):
        assert pointer, 'NULL response'
        try:
            return json.loads(ctypes.string_at(pointer))
        finally:
            (library.NimboMihomoFreeV1 if native_free else library.CGoFree)(pointer)

    def call(operation, **fields):
        request = dict(apiVersion=1, requestId='cabi-' + operation, operation=operation, **fields)
        return response(library.NimboMihomoInvokeV1(json.dumps(request).encode()))

    xray = response(library.CGoInvoke(b'{"apiVersion":3,"method":"xrayVersion","payload":{}}'))
    assert xray['success'] and xray['data']['version'] == '26.9.30'
    for invalid in [None, b'x' * (8 * 1024 * 1024 + 1), b'{"apiVersion":9,"operation":"status"}']:
        assert not response(library.NimboMihomoInvokeV1(invalid), native_free=True)['success']
    inspected = call('inspect', yaml=FIXTURE)
    assert inspected['success'] and inspected['data']['originalYAML'] == FIXTURE
    assert call('validate', yaml=FIXTURE)['success']
    for fd, error in [(0, 'INVALID_FD'), (42, 'PLATFORM_UNAVAILABLE')]:
        result = response(library.NimboMihomoStartIOSV1(b'{"requestId":"ios"}', fd))
        assert not result['success'] and result['error']['code'] == error
    assert not response(library.NimboMihomoCancelV1(b'{"apiVersion":1,"operation":"start"}'))['success']
    assert response(library.NimboMihomoCancelV1(b'{"apiVersion":1,"requestId":"cancel","operation":"cancel","targetRequestId":"never-start"}'))['success']

    packet_gate = response(library.NimboMihomoStartIOSPacketFlowV1(b'{"requestId":"packet-ios"}'))
    assert not packet_gate['success'] and packet_gate['error']['code'] == 'PLATFORM_UNAVAILABLE'
    assert packet_gate['requestId'] == 'packet-ios'
    output = ctypes.create_string_buffer(1500)
    assert library.NimboMihomoWriteIOSPacketV1(0, None, 20) == -2
    assert library.NimboMihomoReadIOSPacketV1(0, output, 1500, 50) == -1
    assert library.NimboMihomoReadIOSPacketV1(0, output, 10, 50) == -2
    calls = []
    token = ctypes.c_int(37)
    @callback_type
    def deny(fd, context):
        assert ctypes.cast(context, ctypes.POINTER(ctypes.c_int)).contents.value == 37
        calls.append(fd)
        return 0
    # Retain deny and token until the runtime has stopped and unregister succeeds.
    assert response(library.NimboMihomoSetSocketProtectorV1(deny, ctypes.byref(token)))['success']
    class LocalServer(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(204)
            self.end_headers()
        def log_message(self, *args):
            pass
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), LocalServer)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        with tempfile.TemporaryDirectory(prefix='nimbo-mihomo-cabi-') as directory:
            started = call('start', yaml=FIXTURE, options=dict(dataDir=directory, networkOwner='desktop-proxy',
                           mixedAddress='127.0.0.1:0', controllerAddress='127.0.0.1:0', secret='local-cabi-secret-' * 4))
            assert started['success'], started
            try:
                busy = response(library.NimboMihomoSetSocketProtectorV1(callback_type(), None))
                assert not busy['success'] and busy['error']['code'] == 'BUSY'
                assert call('snapshot')['success']
                assert call('select', group='Choice', name='local')['success']
                host, port = started['data']['mixedAddress'].rsplit(':', 1)
                connection = http.client.HTTPConnection(host, int(port), timeout=5)
                try:
                    connection.request('GET', 'http://127.0.0.1:%d/' % server.server_port)
                    reply = connection.getresponse()
                    assert reply.status != 204, 'Denied socket reached its target'
                    reply.read()
                except (OSError, http.client.HTTPException):
                    pass
                finally:
                    connection.close()
                assert calls and all(fd >= 0 for fd in calls), 'Protection callback was not invoked'
            finally:
                assert call('stop', generation=started['generation'])['success']
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)
        assert response(library.NimboMihomoSetSocketProtectorV1(callback_type(), None))['success']
    print('Merged API3 + Mihomo V1: source identity, lifecycle, cancel, free and denied socket callback passed; host cannot open an iOS packet tunnel')


if __name__ == '__main__':
    main()
