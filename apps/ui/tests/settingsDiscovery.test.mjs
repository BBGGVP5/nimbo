import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const code = ts.transpileModule(readFileSync(new URL('../src/lib/settingsDiscovery.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
}).outputText;
const { matchesSettingsEntry: matches, readSettingsAdvanced: read, writeSettingsAdvanced: write, SETTINGS_ADVANCED_KEY: key } =
  await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);

test('simple mode hides advanced entries only when search is empty', () => {
  assert.equal(matches(' \n ', 'DNS TLS', true, false), false);
  assert.equal(matches('', 'DNS TLS', true, true), true);
  assert.equal(matches('', 'Диагностика', false, false), true);
  assert.equal(matches(' dNs\tTLS ', 'DNS и транспорт TLS', true, false), true);
  assert.equal(matches('ЯДРО', 'Ядро VPN Xray Mihomo AWG', true, false), true);
  assert.equal(matches('dns ядро', 'DNS TLS', true, true), false);
});

test('presentation mode persists alone and unavailable storage is safe', () => {
  const values = new Map([['vpn.dns', 'unchanged']]);
  globalThis.localStorage = { getItem: k => values.get(k) ?? null, setItem: (k, v) => values.set(k, v) };
  try {
    assert.equal(read(), false);
    assert.equal(write(true), true);
    assert.equal(read(), true);
    assert.equal(write(false), true);
    assert.deepEqual([...values], [['vpn.dns', 'unchanged'], [key, 'false']]);
    globalThis.localStorage = { getItem() { throw Error('blocked'); }, setItem() { throw Error('blocked'); } };
    assert.equal(read(), false);
    assert.equal(write(true), false);
  } finally { delete globalThis.localStorage; }
});
