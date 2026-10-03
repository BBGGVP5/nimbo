#!/usr/bin/env python3
"""Source-verified portable Mihomo desktop build (no prebuilt core download)."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / 'tools/native/mihomo-core'
TAGS = 'no_tailscale,no_zerotier,no_easytier'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--go', default='go')
    parser.add_argument('--target', required=True, choices=['linux/amd64', 'linux/arm64', 'windows/amd64'])
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve(); output.mkdir(parents=True, exist_ok=True)
    pins = json.loads((NATIVE/'pins.json').read_text(encoding='utf-8-sig'))
    environment = os.environ.copy()
    # Never inherit a cross-build target for executable host tests.
    for key in ('GOOS', 'GOARCH'):
        environment.pop(key, None)
    environment.update(GOTOOLCHAIN='local', GOWORK='off', GOENV='off', CGO_ENABLED='0',
                       GOPROXY='https://proxy.golang.org', GOSUMDB='sum.golang.org',
                       GOPRIVATE='', GONOPROXY='', GONOSUMDB='', GOINSECURE='')
    def go(*arguments, cwd=NATIVE, env=environment, capture=True):
        if capture:
            return subprocess.check_output([args.go, *arguments], cwd=cwd, env=env, text=True)
        subprocess.check_call([args.go, *arguments], cwd=cwd, env=env)
    version = go('version').strip()
    if go('env','GOVERSION').strip() != pins['toolchain']:
        raise RuntimeError('pinned toolchain required')
    spec = importlib.util.spec_from_file_location('mihomo_stage',Path(__file__).with_name('prepare-mihomo-merged.py'))
    stage = importlib.util.module_from_spec(spec); spec.loader.exec_module(stage)
    source_paths = sorted([*NATIVE.glob('*.go'), *NATIVE.glob('cmd/**/*.go'),
                           *(NATIVE/name for name in ('API.md','README.md','VERIFICATION.md','go.mod','go.sum','pins.json','protobuf-directive.patch','mihomo-session-lifecycle.patch','mihomo-reality-client-version.patch','mihomo-rule-journal.patch','sing-tun-rule-journal.patch','netlink-rule-identity.patch'))])
    frozen = [{'path': p.relative_to(NATIVE).as_posix(), 'sha256': digest(p)} for p in source_paths]
    with tempfile.TemporaryDirectory(prefix='nimbo-desktop-source-') as temporary:
        temp = Path(temporary)
        metadata = []
        for pin in (pins, pins['protobuf'], pins['singTun'], pins['netlink']):
            value = json.loads(go('mod','download','-json',pin['module']+'@'+pin['version']))
            if value.get('Sum') != pin['sum'] or value.get('GoModSum') != pin['goModSum']:
                raise RuntimeError('source checksum mismatch')
            if value.get('Origin') and value['Origin'].get('Hash') != pin['commit']:
                raise RuntimeError('source commit mismatch')
            stage.verify_download(value, pin)
            if pin.get('sourceZipSHA256') and digest(Path(value['Zip'])) != pin['sourceZipSHA256']:
                raise RuntimeError('source ZIP digest mismatch')
            metadata.append(value)
        stage.stage_protobuf(Path(metadata[1]['Dir']),temp/'protobuf',pins['protobuf'])
        patches = [NATIVE/name for name in ('mihomo-session-lifecycle.patch','mihomo-reality-client-version.patch','mihomo-rule-journal.patch')]
        stage.stage_mihomo(Path(metadata[0]['Dir']),temp/'mihomo',patches)
        stage.stage_singtun(Path(metadata[2]['Dir']),temp/'sing-tun',NATIVE/'sing-tun-rule-journal.patch')
        stage.stage_netlink(Path(metadata[3]['Dir']),temp/'netlink',NATIVE/'netlink-rule-identity.patch')
        modfile = temp/'desktop.mod'
        shutil.copyfile(NATIVE/'go.mod',modfile); shutil.copyfile(NATIVE/'go.sum',temp/'desktop.sum')
        go('mod','edit','-modfile='+str(modfile),
           '-replace=github.com/metacubex/mihomo='+str(temp/'mihomo'),
           '-replace=google.golang.org/protobuf='+str(temp/'protobuf'),
           '-replace=github.com/metacubex/sing-tun='+str(temp/'sing-tun'),
           '-replace=github.com/sagernet/netlink='+str(temp/'netlink'))
        flags = ['-tags='+TAGS, '-modfile='+str(modfile), '-mod=readonly']
        go('test',*flags,'-count=1','-timeout=180s','./...',capture=False)
        go('vet',*flags,'./...',capture=False)
        target_env = environment.copy()
        target_env['GOOS'], target_env['GOARCH'] = args.target.split('/')
        name = 'nimbo-mihomo.exe' if args.target.startswith('windows/') else 'nimbo-mihomo'
        binary = output/name
        go('build',*flags,'-trimpath','-buildvcs=false','-o',str(binary),'./cmd/nimbo-mihomo',env=target_env,capture=False)
        # Corresponding sources and all upstream notices travel with the binary.
        source = output/'adapter-source'
        for entry in frozen:
            original = NATIVE/entry['path']
            if digest(original) != entry['sha256']:
                raise RuntimeError('adapter changed during build; manifest not published')
            destination = source/entry['path']; destination.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(original,destination)
        reached=set(go('list',*flags,'-deps','-f','{{if .Module}}{{.Module.Path}}{{end}}','./cmd/nimbo-mihomo',env=target_env).splitlines())
        rows=go('list','-modfile='+str(modfile),'-mod=readonly','-m','-f','{{if not .Main}}{{.Path}}|{{.Version}}|{{if .Replace}}{{.Replace.Path}}|{{.Replace.Version}}|{{.Replace.Dir}}{{else}}||{{.Dir}}{{end}}{{end}}','all')
        modules=[]
        for row in rows.splitlines():
            if not row: continue
            path,revision,replacement,replacement_revision,directory=row.split('|')
            key=re.sub(r'[^a-zA-Z0-9._@-]','_',path+'@'+revision)
            notices=[]
            for original in sorted(Path(directory).rglob('*')) if directory else []:
                if original.is_file() and re.match(r'^(LICENSE|LICENCE|COPYING|NOTICE|PATENTS|AUTHORS)(\..*|[-_].*)?$',original.name,re.I):
                    relative=Path('modules')/key/original.relative_to(directory)
                    destination=output/'notices'/relative; destination.parent.mkdir(parents=True,exist_ok=True)
                    shutil.copyfile(original,destination)
                    notices.append({'path': 'notices/'+relative.as_posix(), 'sha256':digest(destination)})
            modules.append(dict(module=path,version=revision,replacement=replacement,replacementVersion=replacement_revision,reachedByCLI=path in reached,notices=notices))
        missing = [row['module'] for row in modules if row['reachedByCLI'] and not row['notices']]
        if missing:
            raise RuntimeError('reached modules missing corresponding license notices: '+', '.join(missing))
        for module in modules:
            for notice in module['notices']:
                if digest(output/notice['path']) != notice['sha256']:
                    raise RuntimeError('notice manifest path/digest mismatch')
        inventory=dict(schemaVersion=1,apiVersion=1,goModSHA256=digest(NATIVE/'go.mod'),goSumSHA256=digest(NATIVE/'go.sum'),pinsSHA256=digest(NATIVE/'pins.json'),modules=modules)
        notice_manifest=output/'notices/source-license-manifest.json'; notice_manifest.parent.mkdir(exist_ok=True)
        notice_manifest.write_text(json.dumps(inventory,indent=2)+'\n',encoding='utf-8')
        manifest=dict(builderSHA256=digest(Path(__file__)),acceptanceSHA256=digest(Path(__file__).with_name('test-mihomo-desktop-netns.py')),apiVersion=1,coreVersion=pins['version'],coreCommit=pins['commit'],toolchain=version,target=args.target,sha256=digest(binary),
                      goModSHA256=digest(NATIVE/'go.mod'),goSumSHA256=digest(NATIVE/'go.sum'),pinsSHA256=digest(NATIVE/'pins.json'),effectiveModSHA256=digest(modfile),
                      lifecyclePatchSHA256=digest(patches[0]),realityPatchSHA256=digest(patches[1]),ruleJournalPatchSHA256=digest(patches[2]),singTunJournalPatchSHA256=digest(NATIVE/'sing-tun-rule-journal.patch'),netlinkIdentityPatchSHA256=digest(NATIVE/'netlink-rule-identity.patch'),sourceLicenseManifestSHA256=digest(notice_manifest),sourceFiles=frozen,
                      windowsTunOwnership='exclusive-adapter-rollback-v1' if args.target=='windows/amd64' else None,nativeTunCompiled=True,desktopAdmission='privileged native entry only; service/UI acceptance required')
        (output/'build-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
        print('Verified source-built '+args.target+' Mihomo: '+manifest['sha256'])

if __name__=='__main__':
    main()
