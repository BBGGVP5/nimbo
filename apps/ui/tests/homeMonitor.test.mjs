import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';
const lib = {};
vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/lib/homeMonitor.ts', import.meta.url), 'utf8'), {compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText, {exports:lib});
test('memory never draws fabricated empty or single-point history', () => {
 for (const samples of [[], [{bytes:0,at:10}], [{bytes:NaN,at:10}], [{bytes:-1,at:10}]]) {
  const p=lib.memoryPaths(samples); assert.equal(p.line,''); assert.equal(p.area,'');
 }
 assert.equal(lib.memoryPaths([]).peak,null);
 assert.equal(lib.memoryPaths([{bytes:0,at:10}]).peak,0);
});
test('memory step path uses measured spacing and measured peak, not interpolated triangles', () => {
 const p=lib.memoryPaths([{bytes:20,at:1000},{bytes:40,at:2000},{bytes:30,at:5000}],100,100,0);
 assert.equal(p.line,'M 0.00 100.00 H 25.00 V 0.00 H 100.00 V 50.00');
 assert.equal(p.peak,40); assert(p.area.endsWith(' L 100 100 L 0 100 Z'));
});
test('observation axis is based on real timestamps, not an assumed sixty-second history', () => {
 const times=[{at:1000},{at:4000},{at:21000}];
 assert.equal(lib.sampleX(4000,1000,21000,600),90);
 const labels=lib.measuredTimeLabels(times,'en-GB');
 assert.equal(JSON.stringify(labels.map(t=>t.at)),JSON.stringify([1000,4000,21000]));
 assert.equal(labels[0].label,new Date(1000).toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit',second:'2-digit'}));
 assert.equal(lib.measuredTimeLabels([],'en-GB').length,0);
});
