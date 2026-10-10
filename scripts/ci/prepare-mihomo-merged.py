#!/usr/bin/env python3
"""Stage verified protobuf sources and ROOT replacements for one merged Go build.

No binary downloads, cache patching, conflict-policy overrides or dependency
resolution. The existing native pins are authoritative. Run in a scratch root.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import stat

ROOT = Path(__file__).resolve().parents[2]


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def source_files(directory):
    return {p.relative_to(directory).as_posix(): p for p in directory.rglob('*') if p.is_file()}


def make_staged_tree_writable(directory):
    """Go module cache is read-only; copied staging trees must be patchable."""
    for path in (directory, *directory.rglob('*')):
        if path.is_dir():
            path.chmod(path.stat().st_mode | stat.S_IWUSR | stat.S_IXUSR)
        elif path.is_file():
            path.chmod(path.stat().st_mode | stat.S_IWUSR)


def verify_download(metadata, pin):
    """Rehash immutable ZIP (Go dirhash.Hash1) and every extracted cache file."""
    prefix = pin['module'] + '@' + pin['version'] + '/'
    summary = hashlib.sha256()
    hashes = {}
    with zipfile.ZipFile(metadata['Zip']) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise RuntimeError('Duplicate module ZIP entries')
        for name in sorted(names):
            if '\n' in name or not name.startswith(prefix) or name.endswith('/'):
                raise RuntimeError('Unexpected module ZIP entry')
            file_hash = hashlib.sha256(archive.read(name)).hexdigest()
            summary.update((file_hash + '  ' + name + '\n').encode())
            hashes[name[len(prefix):]] = file_hash
    if 'h1:' + base64.b64encode(summary.digest()).decode() != pin['sum']:
        raise RuntimeError('Module ZIP content hash mismatch')
    extracted = source_files(Path(metadata['Dir']))
    if extracted.keys() != hashes.keys() or any(digest(extracted[name]) != expected for name, expected in hashes.items()):
        raise RuntimeError('Module cache differs from pinned immutable ZIP')


def stage_protobuf(original, destination, pin):
    original_files = source_files(original)
    if digest(original / 'go.mod') != pin['originalGoModSHA256']:
        raise RuntimeError('Unexpected original protobuf directive')
    if not destination.exists():
        shutil.copytree(original, destination)
        make_staged_tree_writable(destination)
        module = destination / 'go.mod'
        module.write_bytes(module.read_bytes().replace(b'go 1.20', b'go 1.22'))
    staged = source_files(destination)
    if staged.keys() != original_files.keys():
        raise RuntimeError('Protobuf source file set changed')
    for name, path in original_files.items():
        expected = pin['patchedGoModSHA256'] if name == 'go.mod' else digest(path)
        if digest(staged[name]) != expected:
            raise RuntimeError('Unexpected protobuf source modification: ' + name)


def apply_pinned_patch(destination, patch_file):
    git = shutil.which('git')
    if not git:
        raise RuntimeError('Git is required to apply pinned Mihomo source patches')
    # The scratch tree lives below the checkout. Without a ceiling, git apply
    # silently skips paths that are untracked by the parent repository.
    environment = os.environ.copy()
    environment.pop('GIT_DIR', None)
    environment.pop('GIT_WORK_TREE', None)
    environment['GIT_CEILING_DIRECTORIES'] = str(destination.resolve().parent)
    for arguments in (['apply', '--check', str(patch_file.resolve())], ['apply', str(patch_file.resolve())]):
        result = subprocess.run([git, *arguments], cwd=destination, env=environment,
                                capture_output=True, text=True)
        if result.returncode:
            detail = (result.stderr or result.stdout).strip()
            action = 'validate' if '--check' in arguments else 'apply'
            raise RuntimeError('Could not ' + action + ' pinned Mihomo patch ' + patch_file.name + ': ' + detail)


def stage_mihomo(original, destination, patch_files):
    """Copy the verified pin and apply only the reviewed source patches."""
    if destination.exists():
        raise RuntimeError('Mihomo staging directory must be fresh')
    original_files = source_files(original)
    shutil.copytree(original, destination)
    make_staged_tree_writable(destination)
    staged = source_files(destination)
    if staged.keys() != original_files.keys():
        raise RuntimeError('Staged Mihomo source file set differs from verified pin')
    for name, source in original_files.items():
        target = staged[name]
        if digest(source) != digest(target):
            raise RuntimeError('Staged Mihomo source differs from verified pin: ' + name)
    for patch_file in patch_files:
        apply_pinned_patch(destination, patch_file)
    patched = source_files(destination)
    if patched.keys() != original_files.keys():
        raise RuntimeError('Pinned patches changed the Mihomo source file set')
    changed = {name for name in original_files if digest(original_files[name]) != digest(patched[name])}
    allowed = {'adapter/provider/healthcheck.go', 'adapter/provider/provider.go',
               'adapter/outboundgroup/groupbase.go', 'listener/sing_tun/server.go', 'tunnel/tunnel.go',
               'listener/sing_tun/server_android.go', 'component/tls/reality.go', 'listener/config/tun.go',
               'component/dialer/dialer.go', 'component/dialer/socket_hook.go',
               'tunnel/statistic/manager.go', 'tunnel/statistic/tracker.go'}
    if changed != allowed:
        raise RuntimeError('Unexpected pinned Mihomo patch scope: ' + ', '.join(sorted(changed)))
    reality = (destination / 'component/tls/reality.go').read_text(encoding='utf-8')
    if 'binary.BigEndian.PutUint32(hello.SessionId[4:], uint32(ntp.Now().Unix()))' not in reality or 'hello.SessionId[0] = 26' not in reality:
        raise RuntimeError('Pinned Mihomo REALITY client-version patch is missing')
    return {name: digest(patched[name]) for name in sorted(changed)}


def stage_rule_dependency(original, destination, patch_file, allowed):
    if destination.exists():
        raise RuntimeError('Rule dependency staging directory must be fresh')
    before = source_files(original)
    shutil.copytree(original, destination)
    make_staged_tree_writable(destination)
    apply_pinned_patch(destination, patch_file)
    after = source_files(destination)
    if before.keys() != after.keys():
        raise RuntimeError('Rule dependency patch changed the source file set')
    changed = {name for name in before if digest(before[name]) != digest(after[name])}
    if changed != allowed:
        raise RuntimeError('Unexpected rule dependency patch scope')
    return {name: digest(after[name]) for name in sorted(changed)}


def stage_singtun(original, destination, patch_file):
    return stage_rule_dependency(original, destination, patch_file, {'tun.go', 'tun_linux.go', 'tun_windows.go'})


def stage_netlink(original, destination, patch_file):
    return stage_rule_dependency(original, destination, patch_file, {'rule_linux.go'})


def download_pin(go, pin):
    value = json.loads(go('mod', 'download', '-json', pin['module']+'@'+pin['version']))
    if value.get('Sum') != pin['sum'] or value.get('GoModSum') != pin['goModSum']:
        raise RuntimeError('Module checksum pin mismatch: '+pin['module'])
    if value.get('Origin', {}).get('Hash') and value['Origin']['Hash'] != pin['commit']:
        raise RuntimeError('Module origin mismatch')
    if pin.get('sourceZipSHA256') and digest(value['Zip']) != pin['sourceZipSHA256']:
        raise RuntimeError('Module ZIP digest mismatch')
    verify_download(value, pin)
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir', type=Path)
    parser.add_argument('--dependency-dir', type=Path)
    parser.add_argument('--native-dir', type=Path, default=ROOT / 'tools/native/mihomo-core')
    parser.add_argument('--go', default='go')
    parser.add_argument('--stage-only', action='store_true',
                        help='stage and patch an already checksum-verified Mihomo source directory')
    args = parser.parse_args()
    if args.stage_only:
        if args.source_dir is None or args.dependency_dir is None:
            parser.error('--stage-only needs --source-dir and --dependency-dir')
        original, dependencies, native = (p.resolve() for p in (args.source_dir, args.dependency_dir, args.native_dir))
        patches = [native / name for name in ('mihomo-session-lifecycle.patch', 'mihomo-reality-client-version.patch', 'mihomo-rule-journal.patch', 'mihomo-traffic-counters.patch')]
        if any(not patch.is_file() for patch in patches):
            raise RuntimeError('Pinned Mihomo source patch missing')
        dependencies.mkdir(parents=True, exist_ok=True)
        staged = dependencies / 'mihomo'
        changed = stage_mihomo(original, staged, patches)
        pins = json.loads((native/'pins.json').read_text(encoding='utf-8-sig'))
        environment = os.environ.copy()
        environment.update(GOTOOLCHAIN='local', GOWORK='off', GOENV='off', GOPROXY='https://proxy.golang.org', GOSUMDB='sum.golang.org', GOPRIVATE='', GONOPROXY='', GONOSUMDB='', GOINSECURE='')
        def pinned_go(*argv):
            return subprocess.check_output([args.go, *argv], cwd=native, env=environment, text=True)
        if pinned_go('env','GOVERSION').strip() != pins['toolchain']:
            raise RuntimeError('Pinned Go toolchain required')
        meta = download_pin(pinned_go, pins['singTun'])
        stage_singtun(Path(meta['Dir']), dependencies/'sing-tun', native/'sing-tun-rule-journal.patch')
        meta = download_pin(pinned_go, pins['netlink'])
        stage_netlink(Path(meta['Dir']), dependencies/'netlink', native/'netlink-rule-identity.patch')
        print(json.dumps({'staged': str(staged), 'patchSHA256': {patch.name: digest(patch) for patch in [*patches, native/'sing-tun-rule-journal.patch', native/'netlink-rule-identity.patch']}, 'changedSources': changed}, indent=2))
        return
    if args.source_dir is None or args.dependency_dir is None:
        parser.error('--source-dir and --dependency-dir are required')
    source, dependencies, native = (p.resolve() for p in (args.source_dir, args.dependency_dir, args.native_dir))
    if source == ROOT / 'iosApp/GoBridge' or not (source / 'cgo_bridge/main.go').is_file():
        raise RuntimeError('Use an extracted real LibXray source root, never the production bridge directory')
    pins = json.loads((native / 'pins.json').read_text(encoding='utf-8-sig'))
    environment = os.environ.copy()
    environment.update(GOTOOLCHAIN='local', GOWORK='off', GOENV='off',
                       GOPROXY='https://proxy.golang.org', GOSUMDB='sum.golang.org',
                       GOPRIVATE='', GONOPROXY='', GONOSUMDB='', GOINSECURE='')
    def go(*arguments, cwd=source):
        return subprocess.check_output([args.go, *arguments], cwd=cwd, env=environment, text=True)
    if go('env', 'GOVERSION').strip() != pins['toolchain']:
        raise RuntimeError('Existing pinned Go toolchain required')
    dependencies.mkdir(parents=True, exist_ok=True)
    pin_root = dependencies / 'pin-download'
    pin_root.mkdir(exist_ok=True)
    (pin_root / 'go.mod').write_text('module nimbo/sourcepins\n\ngo 1.27.1\n', encoding='utf-8')
    verified = []
    for pin in (pins, pins['protobuf'], pins['singTun'], pins['netlink']):
        metadata = json.loads(go('mod', 'download', '-json', pin['module'] + '@' + pin['version'], cwd=pin_root))
        if metadata.get('Sum') != pin['sum'] or metadata.get('GoModSum') != pin['goModSum']:
            raise RuntimeError('Module checksum pin mismatch: ' + pin['module'])
        origin = metadata.get('Origin', {})
        if origin.get('Hash') and origin['Hash'] != pin['commit']:
            raise RuntimeError('Module origin mismatch')
        if pin.get('sourceZipSHA256') and digest(metadata['Zip']) != pin['sourceZipSHA256']:
            raise RuntimeError('Source archive checksum mismatch')
        verify_download(metadata, pin)
        verified.append(metadata)
    protobuf = dependencies / 'protobuf'
    stage_protobuf(Path(verified[1]['Dir']), protobuf, pins['protobuf'])
    mihomo_patches = [native / name for name in ('mihomo-session-lifecycle.patch', 'mihomo-reality-client-version.patch', 'mihomo-rule-journal.patch', 'mihomo-traffic-counters.patch')]
    if any(not patch.is_file() for patch in mihomo_patches):
        raise RuntimeError('Pinned Mihomo source patch missing')
    mihomo_source = dependencies / 'mihomo'
    patched_sources = stage_mihomo(Path(verified[0]['Dir']), mihomo_source, mihomo_patches)
    sing_tun = dependencies / 'sing-tun'
    sing_tun_sources = stage_singtun(Path(verified[2]['Dir']), sing_tun, native / 'sing-tun-rule-journal.patch')
    netlink = dependencies / 'netlink'
    netlink_sources = stage_netlink(Path(verified[3]['Dir']), netlink, native / 'netlink-rule-identity.patch')
    go('mod', 'edit', '-replace=github.com/sagernet/netlink=' + netlink.as_posix(), '-replace=github.com/metacubex/sing-tun=' + sing_tun.as_posix(), '-replace=nimbo/mihomocore=' + native.as_posix(),
       '-replace=google.golang.org/protobuf=' + protobuf.as_posix(),
       '-replace=github.com/metacubex/mihomo=' + mihomo_source.as_posix())
    # The child module's replace is ignored by Go. Prove the effective root graph.
    graph = json.loads(go('mod', 'edit', '-json'))
    replacements = {r['Old']['Path']: r['New']['Path'] for r in graph.get('Replace', [])}
    if replacements.get('nimbo/mihomocore') != native.as_posix() or replacements.get('google.golang.org/protobuf') != protobuf.as_posix():
        raise RuntimeError('Root module replacement missing')
    if replacements.get('github.com/metacubex/mihomo') != mihomo_source.as_posix():
        raise RuntimeError('Patched Mihomo source replacement missing from effective root graph')
    if replacements.get('github.com/metacubex/sing-tun') != sing_tun.as_posix():
        raise RuntimeError('Owned sing-tun replacement missing from root graph')
    if replacements.get('github.com/sagernet/netlink') != netlink.as_posix():
        raise RuntimeError('Exact netlink rule identity replacement missing')
    requirements = {r['Path']: r['Version'] for r in graph['Require']}
    for module, version in {
        'github.com/xtls/xray-core': 'v1.260327.1-0.20260930074004-b26a91de4f32',
        'github.com/amnezia-vpn/amneziawg-go/v3': 'v3.1.20260828',
        'gvisor.dev/gvisor': 'v0.0.0-20260122175437-89a5d21be8f0',
    }.items():
        if requirements.get(module) != version:
            raise RuntimeError('Existing engine pin changed: ' + module)
    manifest = {'pins': pins, 'rootReplacements': replacements,
                'protobufGoModSHA256': digest(protobuf / 'go.mod'),
                'protobufFilesVerified': len(source_files(protobuf)),
                'mihomoLifecyclePatchSHA256': digest(mihomo_patches[0]),
                'mihomoPatchSHA256': {patch.name: digest(patch) for patch in mihomo_patches},
                'mihomoPatchedSources': patched_sources,
                'singTunPatchSHA256': digest(native / 'sing-tun-rule-journal.patch'),
                'singTunPatchedSources': sing_tun_sources,
                'netlinkPatchSHA256': digest(native / 'netlink-rule-identity.patch'),
                'netlinkPatchedSources': netlink_sources,
                'nativeSources': {p.name: digest(p) for p in sorted(list(native.glob('*.go')) +
                                  [native / name for name in ['go.mod', 'go.sum', 'pins.json', 'protobuf-directive.patch', 'mihomo-session-lifecycle.patch', 'mihomo-reality-client-version.patch', 'mihomo-rule-journal.patch', 'mihomo-traffic-counters.patch', 'sing-tun-rule-journal.patch', 'netlink-rule-identity.patch']])}}
    (dependencies / 'mihomo-source-verification.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print('Verified Mihomo source, protobuf directive-only patch and merged ROOT replacements')


if __name__ == '__main__':
    main()
