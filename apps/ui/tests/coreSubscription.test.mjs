import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import { createStore } from 'zustand/vanilla';
import ts from 'typescript';

async function moduleWithMocks(path, mocks) {
  const output = ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  }).outputText.replace(/^import .*?;\r?\n/gm, '');
  const key = `__coreSubscription${Math.random().toString(36).slice(2)}`;
  globalThis[key] = mocks;
  try {
    return await import(`data:text/javascript;base64,${Buffer.from(`const {${Object.keys(mocks).join(',')}} = globalThis[${JSON.stringify(key)}];\n${output}`).toString('base64')}`);
  } finally { delete globalThis[key]; }
}
const helpers = await moduleWithMocks('../src/lib/coreProfiles.ts', {});

test('Mihomo then Xray reconciles subscribed profiles and preserves the active session', async () => {
  let preferred = 'auto', hydrates = 0;
  const runtime = { running: true, profile_id: 'active', session_id: 'session' };
  const snapshot = { groups: { Choice: { now: 'node' } } }, delay = { name: 'node', ms: 4 };
  const profile = { id: 'sub-profile', kind: 'mihomo_yaml' };
  const { useCoreStore: store } = await moduleWithMocks('../src/coreStore.ts', {
    create: createStore, ...helpers,
    coreApi: {
      preference: async value => { preferred = value; },
      profiles: async () => ({ preferred_core: preferred, profiles: [profile] }),
      availability: async () => [], runtime: async () => runtime,
    },
    useAppStore: { getState: () => ({ hydrate: async () => { hydrates++; } }) },
  });
  store.setState({ runtime, snapshot, delay });
  for (const value of ['mihomo', 'xray']) {
    await store.getState().preference(value);
    assert.equal(store.getState().data.preferred_core, value);
    assert.equal(store.getState().data.profiles[0], profile);
    assert.equal(store.getState().runtime, runtime);
    assert.equal(store.getState().snapshot, snapshot);
    assert.equal(store.getState().delay, delay);
  }
  assert.equal(hydrates, 2, 'subscription metadata is read back after representation refresh');
});

test('failed refresh reconciles the saved preference but retains valid live data and reports the error', async () => {
  let preferred = 'xray';
  const runtime = { running: true, profile_id: 'active', session_id: 'same' };
  const snapshot = { groups: { Choice: { now: 'node' } } };
  const profile = { id: 'valid-cached-profile', source_digest: 'old' };
  const { useCoreStore: store } = await moduleWithMocks('../src/coreStore.ts', {
    create: createStore, ...helpers,
    coreApi: {
      preference: async value => { preferred = value; throw Error('SOURCE_HTTP_ERROR'); },
      profiles: async () => ({ preferred_core: preferred, profiles: [profile] }),
      availability: async () => [], runtime: async () => runtime,
    },
    useAppStore: { getState: () => ({ hydrate: async () => {} }) },
  });
  store.setState({ runtime, snapshot });
  await store.getState().preference('mihomo');
  assert.equal(store.getState().data.preferred_core, 'mihomo');
  assert.equal(store.getState().data.profiles[0], profile);
  assert.equal(store.getState().error, 'SOURCE_HTTP_ERROR');
  assert.equal(store.getState().runtime, runtime);
  assert.equal(store.getState().snapshot, snapshot);
});

test('failed readback never clears the last valid runtime, profile list or controller snapshot', async () => {
  const runtime = { running: true, profile_id: 'active', session_id: 'same' };
  const data = { preferred_core: 'xray', profiles: [{ id: 'cached' }] };
  const snapshot = { groups: {} };
  const { useCoreStore: store } = await moduleWithMocks('../src/coreStore.ts', {
    create: createStore, ...helpers, useAppStore: {},
    coreApi: { profiles: async () => { throw Error('READBACK_MISMATCH'); }, availability: async () => [], runtime: async () => runtime },
  });
  store.setState({ runtime, data, snapshot });
  await store.getState().refresh();
  assert.equal(store.getState().runtime, runtime);
  assert.equal(store.getState().snapshot, snapshot);
  assert.equal(store.getState().data, data);
  assert.equal(store.getState().error, 'READBACK_MISMATCH');
});
