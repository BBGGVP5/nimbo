#!/usr/bin/env python3
"""Patch only the verified pinned Darwin TUN in a private Apple build tree."""
import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess

PIN = dict(module="github.com/xtls/xray-core", version="v1.260327.1-0.20260930074004-b26a91de4f32",
           sum="h1:Bxo6+07IvdWlqxugsn2/sQrFbR7hjrxRRDMjbOInRho=")
HASH = "89553b16685f64ff56f2f3e14ad9de330c5ceef38aa92eeeee3dae0459ef3763"


def patch(source):
    old = """		if err = unix.SetNonblock(fd, true); err != nil {
			return nil, err
		}
"""
    new = """		// Nimbo: os.File owns its descriptor (including its finalizer).
		// Never wrap NE's borrowed descriptor directly. Closing the duplicate
		// also wakes a Go-poller read before a new provider session starts.
		fd, err = unix.Dup(fd)
		if err != nil {
			return nil, err
		}
		unix.CloseOnExec(fd)
		if err = unix.SetNonblock(fd, true); err != nil {
			_ = unix.Close(fd)
			return nil, err
		}
"""
    assert source.count(old) == 1
    source = source.replace(old, new)
    old = """	if t.ownsFd {
		return xerrors.Combine(routeErr, t.tunFile.Close())
	}
	// iOS: don't close the fd, it's owned by NetworkExtension
	return routeErr"""
    assert source.count(old) == 1
    source = source.replace(old, """	// ownsFd controls system routes, not os.File ownership. In iOS mode
	// tunFile owns only our duplicate; the original remains with NE.
	return xerrors.Combine(routeErr, t.tunFile.Close())""")
    return source


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
    assert verified.digest(original / 'proxy/tun/tun_darwin.go') == HASH
    destination = Path(args.dependency_dir).resolve()
    # Never overwrite a cache or an existing staging tree.
    assert not destination.exists()
    shutil.copytree(original, destination)
    verified.make_staged_tree_writable(destination)
    target = destination / 'proxy/tun/tun_darwin.go'
    target.write_text(patch(target.read_text(encoding='utf-8')), encoding='utf-8', newline='\n')
    test = Path(__file__).resolve().parents[2] / 'tools/native/xray-apple-tun/descriptor_darwin_test.go'
    shutil.copyfile(test, destination / 'proxy/tun/nimbo_descriptor_darwin_test.go')
    subprocess.run(['go', 'mod', 'edit', '-replace=' + PIN['module'] + '=' + str(destination)], check=True)
    print('Verified pinned Xray: Apple TUN duplicate ownership and close cancellation patched')


if __name__ == '__main__':
    main()
