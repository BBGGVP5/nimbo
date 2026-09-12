import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import test from 'node:test';
import { resolveAwgSource } from '../scripts/build-awg.mjs';

const root = resolve('test-repository');
const home = resolve('test-home');
const local = resolve(root, 'tools/native/awg-core');
const fallback = resolve(home, 'AndroidStudioProjects/Nimbo/tools/native/awg-core');

test('AWG source uses the repository module before the separate-tree fallback', () => {
  const inspected = [];
  assert.equal(resolveAwgSource({ root, home, override: '', exists: path => {
    inspected.push(path); return path === resolve(local, 'go.mod');
  } }), local);
  assert.deepEqual(inspected, [resolve(local, 'go.mod')]);
});

test('AWG environment override wins even if absent, so misconfiguration fails visibly', () => {
  const override = resolve('explicit-module');
  assert.equal(resolveAwgSource({ root, home, override, exists: () => {
    assert.fail('An explicit override must not fall back to another module');
  } }), override);
});

test('AWG source retains separate-tree fallback for desktop-only archives', () => {
  assert.equal(resolveAwgSource({ root, home, override: '', exists: () => false }), fallback);
});
