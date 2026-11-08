import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';

function load(path, deps = {}) {
  const exports = {};
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, { exports, AbortController, setTimeout, clearTimeout, require: name => deps[name] });
  return exports;
}
const { serverHold } = load('../src/lib/serverHold.ts');
const { pingActions } = load('../src/lib/pingActions.ts');
function gesture() {
  let timer, opened = 0;
  const hold = serverHold(() => opened++, callback => (timer = callback), () => { timer = undefined; });
  return { hold, tick: () => timer?.(), get opened() { return opened; }, get scheduled() { return !!timer; } };
}
test('short click selects normally, hold opens once and suppresses only its subsequent click', () => {
  const f = gesture(); f.hold.down(0, 0); f.hold.up(); f.tick(); assert.equal(f.opened, 0); assert.equal(f.hold.consumeClick(), false);
  f.hold.down(0, 0); f.tick(); f.hold.up(); assert.equal(f.opened, 1); assert.equal(f.hold.consumeClick(), true); assert.equal(f.hold.consumeClick(), false);
  f.hold.down(0, 0); f.hold.up(); assert.equal(f.hold.consumeClick(), false);
});
test('movement, pointer cancellation and unmount cancel hold timers', () => {
  for (const cancel of [h => h.move(9, 0), h => h.cancel(), h => h.up()]) {
    const f = gesture(); f.hold.down(0, 0); cancel(f.hold); f.tick(); assert.equal(f.opened, 0); assert.equal(f.scheduled, false);
  }
  const f = gesture(); f.hold.down(0, 0); f.hold.move(4, 4); f.tick(); assert.equal(f.opened, 1);
});
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
function fixture() {
  const probes = [], pending = [], values = new Map([['one', 151]]); let stops = 0;
  const actions = pingActions(async (ids, publish, signal) => {
    const completion = deferred(); probes.push({ ids, publish, signal, completion }); await completion.promise;
  }, async () => { stops++; }, ids => pending.push([...ids]));
  return { actions, probes, pending, values, publish: r => values.set(r.server_id, r.latency_ms), get stops() { return stops; } };
}
test('single-server action measures one ID; repeat cancels and late reply never erases stored ping', async () => {
  const f = fixture(); const run = f.actions.toggle(['one'], f.publish); await flush();
  assert.equal(f.probes[0].ids.join(','), 'one'); assert.equal(f.values.get('one'), 151);
  await f.actions.toggle(['one'], f.publish); await flush(); assert.equal(f.stops, 1); assert.equal(f.probes[0].signal.aborted, true);
  f.probes[0].publish({ server_id: 'one', latency_ms: null }); f.probes[0].completion.resolve(); await run;
  assert.equal(f.values.get('one'), 151); assert.equal(f.pending.at(-1).length, 0);
});
test('replacement waits for backend cancel; old cleanup cannot clear a new operation', async () => {
  const f = fixture(); const old = f.actions.toggle(['one'], f.publish); await flush();
  const current = f.actions.toggle(['two'], f.publish); await flush(); assert.equal(f.stops, 1); assert.equal(f.probes.length, 2);
  f.probes[0].completion.resolve(); await old; assert.equal(f.pending.at(-1).join(','), 'two');
  f.probes[1].publish({ server_id: 'two', latency_ms: 42 }); f.probes[1].completion.resolve(); await current;
  assert.equal(f.values.get('two'), 42); assert.equal(f.pending.at(-1).length, 0);
});
test('cancel during retirement launches no replacement and duplicate IDs stay bounded', async () => {
  const barrier = deferred(); let calls = 0; const probes = [];
  const actions = pingActions(async (ids, publish, signal) => { calls++; probes.push({ ids, signal }); await new Promise(r => signal.addEventListener('abort', r, { once: true })); }, () => barrier.promise, () => {});
  const first = actions.toggle(['one', 'one'], () => {}); await flush(); assert.equal(probes[0].ids.length, 1);
  const second = actions.toggle(['two'], () => {}); actions.cancel(); barrier.resolve(); await Promise.all([first, second]); assert.equal(calls, 1);
});
test('progressive cancellation stops queued nodes and ignores all cancelled replies', async () => {
  const replies = [], published = [], controller = new AbortController(); let stops = 0;
  const api = { pingServer: id => { const result = deferred(); replies.push({ id, result }); return result.promise; }, cancelPings: async () => { stops++; } };
  const { pingServersProgressively } = load('../src/lib/ping.ts', { './api': { api } });
  const operation = pingServersProgressively(['one', 'two', 'three', 'four'], r => published.push(r), 2, controller.signal);
  assert.equal(replies.length, 2); controller.abort();
  replies.forEach(({ id, result }) => result.resolve({ server_id: id, latency_ms: 123 })); await operation;
  assert.equal(stops, 1); assert.equal(replies.length, 2); assert.equal(published.length, 0);
});
