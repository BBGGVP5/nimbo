"""Install opt-in Nimbo Auto into an already verified, private Xray source tree.

Never run against the Go module cache. The caller verifies the immutable module
archive, copies it to a new writable staging directory, then invokes this hook.
"""
import hashlib
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
PINNED_HASHES = {
    'app/router/config.go': 'fcb133c67d62538e40b7ac747a865127b4ad9fae2ea9bae4859089d94a679af2',
    'app/observatory/observer.go': '13695e0edc373d198afe7b423b83ae7f19b9cad666856be4d3cf647040a2f434',
}
COPY_TARGETS = {
    'policy.go': 'app/router/nimbo_sticky_policy.go',
    'policy_test.go': 'app/router/nimbo_sticky_policy_test.go',
    'strategy.go': 'app/router/nimbo_sticky_strategy.go',
    'strategy_test.go': 'app/router/nimbo_sticky_strategy_test.go',
    'snapshot_test.go': 'app/observatory/nimbo_sticky_snapshot_test.go',
}


def replace_once(source, old, new):
    if source.count(old) != 1:
        raise ValueError('Pinned Xray patch context is absent or ambiguous')
    return source.replace(old, new, 1)


def patch_router(source):
    return replace_once(source,
        '\tcase "leastping":\n\t\treturn &Balancer{\n\t\t\tselectors:   br.OutboundSelector,\n\t\t\tstrategy:    &LeastPingStrategy{},',
        '\tcase "leastping":\n'
        '\t\tvar strategy BalancingStrategy = &LeastPingStrategy{}\n'
        '\t\t// Nimbo-generated Auto only; ordinary/provider leastPing is unchanged.\n'
        '\t\tif br.Tag == "nimbo-auto-sticky-v1" && len(br.OutboundSelector) == 1 && br.OutboundSelector[0] == "proxy/" && br.FallbackTag == "proxy/0" {\n'
        '\t\t\tstrategy = &nimboStickyStrategy{}\n'
        '\t\t}\n'
        '\t\treturn &Balancer{\n\t\t\tselectors:   br.OutboundSelector,\n\t\t\tstrategy:    strategy,')


def patch_observer(source):
    return replace_once(source,
        'func (o *Observer) GetObservation(ctx context.Context) (proto.Message, error) {\n\treturn &ObservationResult{Status: o.status}, nil\n}',
        'func (o *Observer) GetObservation(ctx context.Context) (proto.Message, error) {\n'
        '\t// Readers must not race with probe completion or retain mutable status.\n'
        '\to.statusLock.Lock()\n\tdefer o.statusLock.Unlock()\n'
        '\treturn proto.Clone(&ObservationResult{Status: o.status}), nil\n}')


def apply_sticky_patch(destination):
    destination = Path(destination)
    if destination.is_symlink():
        raise ValueError('Expected a private staged Xray source directory')
    destination = destination.resolve()
    if not destination.is_dir():
        raise ValueError('Expected a private staged Xray source directory')
    # Explicitly refuse the usual immutable cache path as defense in depth.
    if '/pkg/mod/' in destination.as_posix().lower():
        raise ValueError('Never patch a Go module cache')
    sources = ROOT / 'tools/native/xray-apple-sticky'
    prepared = {}
    for relative, expected in PINNED_HASHES.items():
        path = destination / relative
        if path.is_symlink() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError('Unexpected pinned Xray source: ' + relative)
        source = path.read_text(encoding='utf-8')
        prepared[path] = (patch_router if relative.endswith('config.go') else patch_observer)(source)
    for source, relative in COPY_TARGETS.items():
        if not (sources / source).is_file() or (destination / relative).exists():
            raise ValueError('Missing policy source or existing patch target: ' + relative)
    # Validate every input before the first write. No global cache/file mutation.
    for path, value in prepared.items():
        path.write_text(value, encoding='utf-8', newline='\n')
    for source, relative in COPY_TARGETS.items():
        shutil.copyfile(sources / source, destination / relative)

