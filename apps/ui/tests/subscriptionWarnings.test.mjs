import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const source = readFileSync(new URL('../src/lib/subscriptionWarnings.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { dueSubscriptionWarnings: due, subscriptionWarningText: text } =
  await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);

const DAY = 86_400_000;
const NOW = Date.UTC(2026, 10, 4, 12);
const sub = (info, url = 'https://provider.invalid/sub', name = 'Провайдер') => ({ url, name, servers: [], info, fetched_at: 1 });
const at = days => Math.floor((NOW + days * DAY) / 1000);

test('far expiry, unlimited plans and expire=0 stay quiet', () => {
  assert.deepEqual(due([sub({ expire: at(10) }), sub({ expire: 0 }), sub(null)], {}, NOW).due, []);
});

test('each expiry stage is shown once and a jump skips earlier stages', () => {
  let warned = {};
  let r = due([sub({ expire: at(2.5) })], warned, NOW);
  assert.deepEqual(r.due.map(w => [w.stage, w.value]), [['expire-3d', 2]]);
  warned = r.next;
  assert.deepEqual(due([sub({ expire: at(2.5) })], warned, NOW).due, []);
  r = due([sub({ expire: at(0.5) })], {}, NOW);
  assert.deepEqual(r.due.map(w => w.stage), ['expire-1d']);
  assert.deepEqual(r.next['https://provider.invalid/sub'].stages, ['expire-3d', 'expire-1d']);
  assert.deepEqual(due([sub({ expire: at(-1) })], r.next, NOW).due.map(w => w.stage), ['expired']);
});

test('renewal re-arms reminders', () => {
  const first = due([sub({ expire: at(0.5) })], {}, NOW).next;
  const renewed = due([sub({ expire: at(2.5) })], first, NOW);
  assert.deepEqual(renewed.due.map(w => w.stage), ['expire-3d']);
});

test('traffic reminders at 90% and 100%, independent of expiry', () => {
  const r = due([sub({ upload: 5, download: 86, total: 100, expire: at(2.5) })], {}, NOW);
  assert.deepEqual(r.due.map(w => [w.stage, w.value]), [['expire-3d', 2], ['traffic-90', 91]]);
  assert.deepEqual(due([sub({ upload: 0, download: 100, total: 100, expire: at(2.5) })], r.next, NOW).due.map(w => w.stage), ['traffic-100']);
});

test('messages name the subscription in both languages', () => {
  const [w] = due([sub({ expire: at(-1) })], {}, NOW).due;
  assert.match(text(w, true), /«Провайдер» закончилась/);
  assert.match(text(w, false), /“Провайдер” has expired/);
  const [nameless] = due([sub({ expire: at(-1) }, 'https://vpn.example.invalid/x', '  ')], {}, NOW).due;
  assert.equal(nameless.name, 'vpn.example.invalid');
});
