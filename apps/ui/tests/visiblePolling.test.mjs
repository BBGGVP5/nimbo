import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';

const source = readFileSync(new URL('../src/lib/visiblePolling.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;

const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };
function fixture(visibility = 'visible') {
  const timers = new Map();
  const listeners = new Set();
  let nextId = 0;
  const document = {
    visibilityState: visibility,
    addEventListener: (_, listener) => listeners.add(listener),
    removeEventListener: (_, listener) => listeners.delete(listener),
  };
  const exports = {};
  vm.runInNewContext(compiled, { exports, document, window: {
    setTimeout: (callback, delay) => { timers.set(++nextId, { callback, delay }); return nextId; },
    clearTimeout: (id) => timers.delete(id),
  } });
  return {
    start: exports.startVisiblePolling, timers, listeners,
    visibility(value) {
      document.visibilityState = value;
      for (const listener of listeners) listener();
    },
    fire() {
      assert.equal(timers.size, 1);
      const [id, timer] = timers.entries().next().value;
      timers.delete(id);
      timer.callback();
    },
  };
}

test('hidden launch performs no work; showing refreshes immediately', async () => {
  const f = fixture('hidden'); let calls = 0;
  const stop = f.start(() => { calls++; }, 2000);
  await flush();
  assert.equal(calls, 0); assert.equal(f.timers.size, 0);
  f.visibility('visible'); await flush();
  assert.equal(calls, 1); assert.equal(f.timers.size, 1);
  assert.equal([...f.timers.values()][0].delay, 2000);
  stop(); assert.equal(f.listeners.size, 0); assert.equal(f.timers.size, 0);
});

test('slow requests never overlap, including hide/show transitions', async () => {
  const f = fixture(); let calls = 0; let finish;
  const stop = f.start(() => { calls++; return new Promise(resolve => { finish = resolve; }); }, 1000);
  await flush();
  f.visibility('hidden'); f.visibility('visible'); f.visibility('visible'); await flush();
  assert.equal(calls, 1); assert.equal(f.timers.size, 0);
  finish(); await flush();
  assert.equal(f.timers.size, 1);
  f.fire(); await flush(); assert.equal(calls, 2);
  stop(); finish(); await flush(); assert.equal(f.timers.size, 0);
});

test('hiding cancels the scheduled tick; showing resumes immediately', async () => {
  const f = fixture(); let calls = 0;
  const stop = f.start(() => { calls++; }, 1000);
  await flush(); f.visibility('hidden');
  assert.equal(f.timers.size, 0);
  f.visibility('visible'); await flush(); assert.equal(calls, 2);
  stop(); f.visibility('visible'); await flush(); assert.equal(calls, 2);
});

test('request completing while hidden does not schedule another', async () => {
  const f = fixture(); let finish;
  const stop = f.start(() => new Promise(resolve => { finish = resolve; }), 1000);
  await flush(); f.visibility('hidden'); finish(); await flush();
  assert.equal(f.timers.size, 0); stop();
});

test('errors do not leave polling locked or cause unhandled rejections', async () => {
  const f = fixture(); let calls = 0;
  const stop = f.start(async () => { calls++; throw new Error('offline'); }, 1000);
  await flush(); assert.equal(f.timers.size, 1);
  f.fire(); await flush(); assert.equal(calls, 2); assert.equal(f.timers.size, 1); stop();
});

test('disposing an in-flight request never schedules more work', async () => {
  const f = fixture(); let finish;
  const stop = f.start(() => new Promise(resolve => { finish = resolve; }), 1000);
  await flush(); stop(); stop(); finish(); await flush();
  assert.equal(f.timers.size, 0); assert.equal(f.listeners.size, 0);
});
