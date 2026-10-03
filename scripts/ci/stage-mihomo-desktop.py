#!/usr/bin/env python3
"""Stage only a hash/source-verified portable desktop native build."""
import argparse,hashlib,json,shutil,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',type=Path,required=True);args=parser.parse_args();source=args.source.resolve()
    m=json.loads((source/'build-manifest.json').read_text())
    platform={'linux/amd64':'linux-x64','linux/arm64':'linux-arm64'}.get(m['target'])
    assert platform and m['apiVersion']==1 and m['coreCommit']=='ab405bad5beeeac8b003bb01f60f134f6df54471'
    assert sha(source/'nimbo-mihomo')==m['sha256']
    for entry in m['sourceFiles']:
        path=(source/'adapter-source'/entry['path']).resolve();assert path.is_relative_to(source/'adapter-source') and sha(path)==entry['sha256']
        assert sha(ROOT/'tools/native/mihomo-core'/entry['path'])==entry['sha256'], 'edited source after build'
    inventory=source/'notices/source-license-manifest.json';assert sha(inventory)==m['sourceLicenseManifestSHA256']
    for module in json.loads(inventory.read_text())['modules']:
        if module['reachedByCLI']:assert module['notices']
        for notice in module['notices']:
            path=(source/notice['path']).resolve();assert path.is_relative_to(source/'notices') and sha(path)==notice['sha256']
    dest=ROOT/'apps/ui/src-tauri/resources/mihomo';target=dest/platform;target.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(source/'nimbo-mihomo',target/'nimbo-mihomo');(target/'nimbo-mihomo').chmod(0o755)
    # Prevent linuxdeploy from rewriting the trusted executable in AppImage.
    with zipfile.ZipFile(target/'nimbo-mihomo.zip','w',compression=zipfile.ZIP_STORED) as archive:archive.write(source/'nimbo-mihomo','nimbo-mihomo')
    m['archiveSHA256']=sha(target/'nimbo-mihomo.zip')
    for name in ['go.mod','go.sum','pins.json']:shutil.copyfile(source/'adapter-source'/name,target/name)
    shutil.copytree(source/'adapter-source',target/'adapter-source',dirs_exist_ok=True);shutil.copytree(source/'notices',target/'notices',dirs_exist_ok=True)
    (target/'build-manifest.json').write_text(json.dumps(m,indent=2)+'\n',encoding='utf-8')
    print('Staged verified '+platform+' native/helper anchor: '+m['sha256'])
if __name__=='__main__':main()
