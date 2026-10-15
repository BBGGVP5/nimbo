import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtempSync, readFileSync, writeFileSync, existsSync, rmSync, chmodSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import test from 'node:test';
import { stageLinuxHelper } from '../scripts/build-linux.mjs';

function elf(machine) {
  const bytes = Buffer.alloc(32);
  bytes.set([0x7f, 0x45, 0x4c, 0x46, 2, 1]); bytes.writeUInt16LE(machine, 18);
  return bytes;
}
for (const [target, machine] of [['x86_64-unknown-linux-gnu', 62], ['aarch64-unknown-linux-gnu', 183]]) {
  test(`stages exact executable helper and target manifest: ${target}`, () => {
    const root = mkdtempSync(join(tmpdir(), 'nimbo-helper-stage-'));
    try {
      const source = join(root, 'source'); const dest = join(root, 'resources/nimbo-svc'); const bytes = elf(machine);
      writeFileSync(source, bytes, { mode: 0o755 }); chmodSync(source, 0o755);
      const metadata = stageLinuxHelper(source, dest, target, '1.3.0-beta.1');
      assert.deepEqual(readFileSync(dest), bytes);
      const archive = readFileSync(dest + '.zip');
      assert.equal(archive.readUInt32LE(0), 0x04034b50);
      assert.deepEqual(archive.subarray(39, 39 + bytes.length), bytes);
      assert.deepEqual(JSON.parse(readFileSync(dest + '.manifest.json', 'utf8')), metadata);
      assert.equal(metadata.target, target);
      assert.equal(metadata.sha256, createHash('sha256').update(bytes).digest('hex'));
      if (process.platform !== 'win32') assert.equal(statSync(dest).mode & 0o777, 0o755);
      assert.equal(existsSync(dest + '.partial'), false);
      assert.equal(existsSync(dest + '.zip.partial'), false);
    } finally { rmSync(root, { recursive: true, force: true }); }
  });
}
test('wrong architecture/corrupt helper cannot replace previously staged output', () => {
  const root = mkdtempSync(join(tmpdir(), 'nimbo-helper-stage-'));
  try {
    const source = join(root, 'source'); const dest = join(root, 'nimbo-svc');
    writeFileSync(source, elf(62), { mode: 0o755 }); chmodSync(source, 0o755);
    stageLinuxHelper(source, dest, 'x86_64-unknown-linux-gnu', '1.3.0-beta.1');
    const old = readFileSync(dest); const manifest = readFileSync(dest + '.manifest.json');
    for (const bytes of [elf(183), Buffer.from('not ELF')]) {
      writeFileSync(source, bytes); assert.throws(() => stageLinuxHelper(source, dest, 'x86_64-unknown-linux-gnu', '1.3.0-beta.1'));
      assert.deepEqual(readFileSync(dest), old); assert.deepEqual(readFileSync(dest + '.manifest.json'), manifest);
    }
    assert.throws(() => stageLinuxHelper(source, dest, 'i686-pc-windows-msvc', '1.3.0-beta.1'));
  } finally { rmSync(root, { recursive: true, force: true }); }
});
