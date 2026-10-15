import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';

const exports = {};
vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/pages/home/pullRefresh.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText, { exports, Date, Set, Map, Promise });
const { pullRefreshGesture, subscriptionRefreshCoordinator } = exports;
const start = (overrides = {}) => ({ id: 1, x: 20, y: 20, atTop: true, blocked: false, primary: true, button: 0, ...overrides });

test('only downward release at scroll top refreshes once; latest position determines release', () => {
  const gesture = pullRefreshGesture();
  gesture.down(start());
  assert.equal(gesture.move(1, 24, 105), 1);
  assert.equal(gesture.up(1, 24, 105), true);
  assert.equal(gesture.up(1, 24, 105), false);
  gesture.down(start()); gesture.move(1, 20, 120);
  assert.equal(gesture.up(1, 20, 45), false, 'retreat below threshold must not refresh');
});

test('away from top, horizontal, upward, cancelled, blocked and secondary drags never refresh', () => {
  for (const overrides of [{ atTop: false }, { blocked: true }, { primary: false }, { button: 2 }]) {
    const g = pullRefreshGesture(); g.down(start(overrides)); g.move(1, 20, 150);
    assert.equal(g.up(1, 20, 150), false);
  }
  for (const [x, y] of [[100, 25], [20, 0]]) {
    const g = pullRefreshGesture(); g.down(start()); g.move(1, x, y); g.move(1, 20, 150);
    assert.equal(g.up(1, 20, 150), false, 'direction lock must not rearm');
  }
  const g = pullRefreshGesture(); g.down(start()); g.move(1, 20, 150); g.cancel();
  assert.equal(g.up(1, 20, 150), false);
  g.down(start()); assert.equal(g.up(2, 20, 150), false); g.cancel();
});

test('concurrent Home/profile requests coalesce per URL, distinct URLs refresh, cooldown blocks spam', async () => {
  let now = 0;
  const c = subscriptionRefreshCoordinator(() => now);
  const calls = [], complete = [];
  const refresh = url => { calls.push(url); return new Promise(resolve => complete.push(resolve)); };
  let notifications = 0; const stop = c.subscribe(() => notifications++);
  const first = c.refresh(['a', 'a'], refresh, assert.fail);
  const second = c.refresh(['a', 'b'], refresh, assert.fail);
  await Promise.resolve();
  assert.deepEqual(calls, ['a', 'b']);
  assert.deepEqual([...c.pending()].sort(), ['a', 'b']);
  const g = pullRefreshGesture(); g.down(start({ blocked: c.pending().size > 0 }));
  assert.equal(g.up(1, 20, 150), false);
  complete.forEach(resolve => resolve()); await Promise.all([first, second]);
  assert.equal(c.pending().size, 0); assert(notifications >= 3);
  await c.refresh(['a'], refresh, assert.fail); assert.equal(calls.length, 2);
  now = 1600; const third = c.refresh(['a'], refresh, assert.fail);
  await Promise.resolve(); assert.deepEqual(calls, ['a', 'b', 'a']);
  complete.at(-1)(); await third; stop();
});

test('real provider error reports once and other subscriptions finish; retry becomes available', async () => {
  let now = 0; const c = subscriptionRefreshCoordinator(() => now);
  const error = new Error('Provider rejected update: HTTP 503'); const errors = [], calls = [];
  const refresh = async url => { calls.push(url); if (url === 'bad') throw error; };
  await Promise.all([c.refresh(['bad', 'good'], refresh, e => errors.push(e)), c.refresh(['bad'], refresh, e => errors.push(e))]);
  assert.deepEqual(errors, [error]); assert.deepEqual(calls, ['bad', 'good']); assert.equal(c.pending().size, 0);
  now = 1600; await c.refresh(['bad'], async () => {}, assert.fail); assert.equal(c.pending().size, 0);
});
