import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';
const exports = {};
vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/tray-menu/quickServers.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText, { exports });
const servers = Array.from({length: 8}, (_, i) => ({ id: String(i) }));
const result = raw => Array.from(exports.favoriteServers(servers, raw), s => s.id);
test('invalid and obsolete favorites do not break tray', () => {
  for (const raw of [null, 'oops', '{}', 'null', '[false, 3, "gone"]']) assert.deepEqual(result(raw), []);
});
test('favorites retain order, remove duplicates and cap list', () => {
  assert.deepEqual(result('["7","1","7","2","3","4","5"]'), ['7','1','2','3','4']);
});
test('without favorites the tray offers the active server, then the fastest', () => {
  const pinged = [{ id: 'a', latencyMs: 300 }, { id: 'b', latencyMs: null }, { id: 'c', latencyMs: 40 }, { id: 'd', latencyMs: 90 }, { id: 'e' }, { id: 'f' }, { id: 'g', latencyMs: 10 }];
  const pick = (raw, active) => { const r = exports.quickServers(pinged, raw, active); return [Array.from(r.servers, s => s.id), r.fallback]; };
  assert.deepEqual(pick('["d"]', 'a'), [['d'], false]);
  assert.deepEqual(pick(null, 'b'), [['b', 'g', 'c', 'd', 'a'], true]);
  assert.deepEqual(pick('[]', null), [['g', 'c', 'd', 'a', 'b'], true]);
  assert.deepEqual(Array.from(exports.quickServers([], null, null).servers), []);
});
