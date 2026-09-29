import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
import { TARGETS } from '../scripts/build-awg.mjs';

const source = readFileSync(new URL('../src/lib/awg.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { parseAwgInput, isAwgInput } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const key = Buffer.alloc(32, 1).toString('base64');
const ini = `[Interface]\nPrivateKey=${key}\nAddress=10.0.0.2/32\nS4=20\nI5=<b 0x1234>\n[Peer]\nPublicKey=${key}\nEndpoint=vpn.example:51820\nAllowedIPs=0.0.0.0/0\n`;

test('raw and base64 imports retain AWG 3.1 data', () => {
  for (const input of [ini, `awg://${Buffer.from(ini).toString('base64url')}#Office%20VPN`]) {
    assert.equal(isAwgInput(input), true);
    const result = parseAwgInput(input);
    assert.equal(result.config.port, 51820);
    assert.equal(result.config.address, 'vpn.example');
    assert.ok(result.config.config.includes('I5=<b 0x1234>'));
  }
});
test('query keys and IPv6 are accepted without form-decoding base64 plus signs', () => {
  const result = parseAwgInput(`amneziawg://[2001:db8::1]:1234?private_key=${key}&public_key=${key}&address=10.0.0.2/32&S3=9`);
  assert.equal(result.config.address, '2001:db8::1');
  assert.ok(result.config.config.includes('S3=9'));
});
test('malformed profiles never expose keys in errors or become fake VLESS servers', () => {
  for (const input of [ini + '\n[Peer]', ini.replace(key, 'SECRET'), `awg://vpn.example:1?privatekey=${key}%0AAddress=x`]) {
    assert.throws(() => parseAwgInput(input), error => !error.message.includes('SECRET') && !error.message.includes(key));
  }
});
test('packaging maps all five native architectures without host fallback', () => {
  assert.equal(Object.keys(TARGETS).length, 5);
  assert.deepEqual(TARGETS['i686-pc-windows-msvc'], ['windows-x86', 'windows', '386']);
  assert.deepEqual(TARGETS['aarch64-unknown-linux-gnu'], ['linux-arm64', 'linux', 'arm64']);
});
