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
