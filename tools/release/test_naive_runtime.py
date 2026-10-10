"""Pinned official NaiveProxy packaging checks; no proxy startup or network traffic."""
from pathlib import Path
import hashlib
import json
import struct
import unittest
ROOT=Path(__file__).resolve().parents[2]

class NaiveRuntimeTests(unittest.TestCase):
    def test_android_payload_hash_architecture_and_page_alignment(self):
        manifest=json.loads((ROOT/'app/src/main/assets/third_party/naiveproxy.json').read_text())
        self.assertEqual(manifest['version'],'v154.0.8037.49-4')
        for abi,machine in [('arm64-v8a',183),('armeabi-v7a',40)]:
            b=(ROOT/f'app/src/main/jniLibs/{abi}/libnaive.so').read_bytes()
            self.assertEqual(hashlib.sha256(b).hexdigest(),manifest['bundled'][abi]['sha256'])
            self.assertEqual(b[:4],b'\x7fELF');self.assertEqual(struct.unpack_from('<H',b,18)[0],machine)
            bits=b[4];offset=struct.unpack_from('<Q' if bits==2 else '<I',b,32 if bits==2 else 28)[0]
            size,count=struct.unpack_from('<HH',b,54 if bits==2 else 42)
            for i in range(count):
                ph=struct.unpack_from('<IIQQQQQQ' if bits==2 else '<IIIIIIII',b,offset+i*size)
                if ph[0]==1:self.assertGreaterEqual(ph[7],16384 if bits==2 else 4096)

    def test_desktop_checksum_pins_match_real_payloads(self):
        commands=(ROOT/'apps/ui/src-tauri/src/commands.rs').read_text(encoding='utf-8-sig')
        for platform,file in [('windows-x64','naive.exe'),('linux-x64','naive')]:
            b=(ROOT/f'apps/ui/src-tauri/resources/naive/{platform}/{file}').read_bytes();sha=hashlib.sha256(b).hexdigest()
            self.assertIn(sha,commands)
            self.assertIn(sha,(ROOT/'apps/ui/src-tauri/resources/naive/README.txt').read_text())
        b=(ROOT/'apps/ui/src-tauri/resources/naive/windows-x64/naive.exe').read_bytes();pe=struct.unpack_from('<I',b,0x3c)[0]
        self.assertEqual(struct.unpack_from('<H',b,pe+4)[0],0x8664)

if __name__=='__main__':unittest.main()
