#!/usr/bin/env python3
"""Stage verified Xray with upstream Darwin TUN ownership and Nimbo Auto."""
import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
from prepare_xray_sticky import apply_sticky_patch

PIN = dict(module="github.com/xtls/xray-core", version="v1.260327.1-0.20261010092107-701af60772cd",
           sum="h1:Am/9Pzw5PZr7+hMN1Vs5faeSXRoutnrwMAgtUDnh/J8=")
HASH = "54b4d648182354d8af4a5ec44c530989765422b89a7185a3bb61d1721a0aa29d"


def validate_upstream_ownership(source):
    # Xray 26.10.10 includes the descriptor ownership fix. Do not duplicate
    # again or wrap the borrowed NE descriptor. Exact file hash is checked
    # by main before copying; these semantic guards explain the requirement.
    required = (
        'unix.FcntlInt(uintptr(providedFd), unix.F_DUPFD_CLOEXEC, 0)',
        'if err = unix.SetNonblock(fd, true); err != nil {\n\t\t\t_ = unix.Close(fd)',
        'tunFile: os.NewFile(uintptr(fd), "utun")',
        'return xerrors.Combine(routeErr, t.tunFile.Close())',
    )
    if any(source.count(fragment) != 1 for fragment in required):
        raise ValueError('Pinned upstream Darwin TUN ownership contract changed')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--dependency-dir', required=True)
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location('verified', Path(__file__).with_name('prepare-mihomo-merged.py'))
    verified = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verified)
    metadata = json.loads(subprocess.check_output(['go', 'mod', 'download', '-json', PIN['module'] + '@' + PIN['version']]))
    verified.verify_download(metadata, PIN)
    original = Path(metadata['Dir'])
    if verified.digest(original / 'proxy/tun/tun_darwin.go') != HASH:
        raise ValueError('Unexpected pinned Darwin TUN source hash')
    validate_upstream_ownership((original / 'proxy/tun/tun_darwin.go').read_text(encoding='utf-8'))
    destination = Path(args.dependency_dir).resolve()
    # Never overwrite a cache or an existing staging tree.
    if destination.exists():
        raise ValueError('Refusing to overwrite an existing staging tree')
    shutil.copytree(original, destination)
    verified.make_staged_tree_writable(destination)
    test = Path(__file__).resolve().parents[2] / 'tools/native/xray-apple-tun/descriptor_darwin_test.go'
    shutil.copyfile(test, destination / 'proxy/tun/nimbo_descriptor_darwin_test.go')
    apply_sticky_patch(destination)
    subprocess.run(['go', 'mod', 'edit', '-replace=' + PIN['module'] + '=' + str(destination)], check=True)
    print('Verified pinned Xray: upstream Apple TUN ownership retained; opt-in sticky Auto and immutable observations patched')


if __name__ == '__main__':
    main()
