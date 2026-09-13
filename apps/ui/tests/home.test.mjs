import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';
import React from 'react';
import * as jsxRuntime from 'react/jsx-runtime';
import { renderToStaticMarkup } from 'react-dom/server';

const compile = path => ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.ReactJSX },
}).outputText;
const homeCode = compile('../src/pages/Home.tsx');
const pollingCode = compile('../src/lib/visiblePolling.ts');
const i18nCode = compile('../src/lib/i18n.ts');
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
const server = { id: 'first', name: 'First server', protocol: { kind: 'vless' } };
const sub = { url: 'https://example.test/sub', name: 'Example', servers: [server] };

// Execute the real component and effects; stub only the React scheduler, child
// components and external APIs. Dependency comparison and effect cleanup mirror
// React so late API completions and session changes can be tested without a DOM.
function fixture({ style = 'classic', state = 'connected', stored = {}, memory } = {}) {
  const hooks = [], timers = new Map(), listeners = new Set(), storage = new Map(Object.entries(stored));
  let cursor = 0, pending = [], dirty = false, tree, nextTimer = 0, now = 100_000, calls = 0;
  const connectedIds = [], errors = [];
  let disconnected = 0;
  const store = {
    subscriptions: [sub], activeServerId: 'first', activeSubscriptionUrl: sub.url,
    status: { state, connection_mode: 'tun' }, serverPings: {},
    connectingServerId: null, switchingServerId: null, disconnecting: false,
    preferences: { ui_style: style, servers_sorting: 'default', show_memory_usage: true },
    trafficStats: null, trafficHistory: [], trafficMonitoringAvailable: false,
    sessionStartedAt: state === 'connected' ? 10_000 : null,
    connectServer: async id => connectedIds.push(id),
    disconnectServer: async () => { disconnected++; },
  };
  const changed = (a, b) => !a || !b || a.length !== b.length || a.some((value, i) => !Object.is(value, b[i]));
  const react = {
    useState(initial) {
      const index = cursor++;
      if (!hooks[index]) hooks[index] = { value: typeof initial === 'function' ? initial() : initial };
      return [hooks[index].value, value => {
        const next = typeof value === 'function' ? value(hooks[index].value) : value;
        if (!Object.is(next, hooks[index].value)) { hooks[index].value = next; dirty = true; }
      }];
    },
    useRef(initial) { const index = cursor++; return (hooks[index] ??= { current: initial }); },
    useMemo(fn, deps) {
      const index = cursor++;
      if (!hooks[index] || changed(hooks[index].deps, deps)) hooks[index] = { deps, value: fn() };
      return hooks[index].value;
    },
    useCallback(fn, deps) { return react.useMemo(() => fn, deps); },
    useEffect(fn, deps) {
      const index = cursor++;
      if (!hooks[index] || changed(hooks[index].deps, deps)) pending.push(() => {
        hooks[index]?.cleanup?.(); hooks[index] = { deps, cleanup: fn() };
      });
    },
  };
  const document = { visibilityState: 'visible',
    addEventListener: (_, fn) => listeners.add(fn), removeEventListener: (_, fn) => listeners.delete(fn),
  };
  const window = { localStorage: { getItem: key => storage.get(key) ?? null },
    setTimeout: (fn, delay) => { timers.set(++nextTimer, { fn, delay }); return nextTimer; },
    clearTimeout: id => timers.delete(id),
  };
  const polling = {};
  vm.runInNewContext(pollingCode, { exports: polling, document, window });
  const translations = {};
  vm.runInNewContext(i18nCode, { exports: translations, require: () => ({ useAppStore: selector => selector(store) }) });
  const labels = translations.messages.en;
  const overrides = { overrides: {}, hiddenCount: 0 };
  const api = {
    listAppProxyRules: async () => [],
    getMemoryUsage: () => { calls++; return memory ? memory() : Promise.resolve({ bytes: calls * 1024 }); },
  };
  const stub = name => ({ [name]: name });
  const latency = {};
  vm.runInNewContext(compile("../src/lib/latency.ts"), { exports: latency, URL });
  const modules = {
    "../lib/latency": latency,
    react,
    'react/jsx-runtime': { jsx: (type, props) => ({ type, props }), jsxs: (type, props) => ({ type, props }), Fragment: 'Fragment' },
    'react-router-dom': { Link: 'Link', useNavigate: () => () => {} },
    '../store': { useAppStore: selector => selector(store) },
    '../lib/i18n': { ...translations, useMessages: () => labels },
    '../lib/api': { api, subscriptionVisibleOnHome: () => true, formatBytes: bytes => `${bytes} B`,
      protocolLabel: p => p.kind, transportLabel: () => '', serverListDescription: () => '' },
    '../lib/visiblePolling': polling,
    '../lib/notify': { notifyError: error => errors.push(error) },
    '../lib/serverUiOverrides': { useServerUiOverrides: () => overrides, serverDisplayLabel: s => s.name },
    '../lib/ping': {}, '../lib/subscriptionLogo': {},
    '../components/CountryFlag': stub('CountryFlag'),
    '../components/LatencyDisplay': stub('LatencyDisplay'),
    '../../components/LatencyDisplay': stub('LatencyDisplay'),
    './home/SignalHome': stub('SignalHome'), './home/SignalServerRail': stub('SignalServerRail'),
    './home/SignalSpeedChart': stub('SignalSpeedChart'),
  };
  const exports = {};
  vm.runInNewContext(homeCode, { exports, require: name => {
    assert.ok(modules[name], `Unexpected dependency: ${name}`); return modules[name];
  }, document, window, Date: { now: () => now }, localStorage: {
    getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key),
  } });
  const render = () => {
    for (let i = 0; i < 20; i++) {
      cursor = 0; pending = []; dirty = false; tree = exports.Home();
      pending.forEach(effect => effect());
      if (!dirty) return tree;
    }
    throw new Error('Render did not settle');
  };
  const find = (name, node = tree) => {
    if (!node || typeof node !== 'object') return null;
    if (node.type === name || node.type?.name === name || node.props?.className === name) return node;
    for (const child of [node.props?.children].flat(Infinity)) { if (!child) continue; const found = find(name, child); if (found) return found; }
    return null;
  };
  return { store, render, find, labels, timers, errors, connectedIds, get disconnected() { return disconnected; },
    get calls() { return calls; },
    async settle() { await flush(); render(); },
    async tick(delay) {
      const entry = [...timers].find(([, timer]) => timer.delay === delay);
      assert.ok(entry, `Expected ${delay}ms timer`);
      timers.delete(entry[0]); now += delay; entry[1].fn(); await flush(); render();
    },
    visibility(value) { document.visibilityState = value; listeners.forEach(fn => fn()); },
    stop() { hooks.forEach(hook => hook?.cleanup?.()); },
  };
}

test('disconnected and unavailable service never show or poll memory', async () => {
  for (const state of ['disconnected', 'service_unavailable']) {
    const f = fixture({ state }); f.render(); await f.settle();
    assert.equal(f.calls, 0); assert.equal(f.find('MemoryUsageCard'), null); assert.equal(f.timers.size, 0); f.stop();
  }
});

test('collapsed widgets and Signal never poll the unrendered memory widget', async () => {
  for (const options of [{ stored: { 'nimbo.homeWidgetsCollapsed': '1' } }, { style: 'signal' }]) {
    const f = fixture(options); f.render(); await f.settle();
    assert.equal(f.find('MemoryUsageCard'), null); assert.equal(f.calls, 0); f.stop();
  }
});

test('disconnect request immediately hides memory and rejects a late sample', async () => {
  let finish;
  const f = fixture({ memory: () => new Promise(resolve => { finish = resolve; }) });
  f.render(); f.store.disconnecting = true; f.render();
  assert.equal(f.find('MemoryUsageCard'), null);
  finish({ bytes: 999 }); await f.settle();
  assert.equal(f.find('MemoryUsageCard'), null);
  assert.equal([...f.timers.values()].filter(timer => timer.delay === 2000).length, 0); f.stop();
});

test('replacing a connected session clears history and ignores the old request', async () => {
  const completions = [];
  const f = fixture({ memory: () => new Promise(resolve => completions.push(resolve)) });
  f.render(); completions.shift()({ bytes: 100 }); await f.settle();
  await f.tick(2000);
  f.store.sessionStartedAt = 99_000; f.render();
  completions.shift()({ bytes: 999 }); await f.settle();
  assert.equal(f.find('MemoryUsageCard').props.bytes, 0);
  assert.equal(f.find('MemoryUsageCard').props.samples.length, 0);
  await f.tick(2000); completions.shift()({ bytes: 200 }); await f.settle();
  assert.deepEqual(Array.from(f.find('MemoryUsageCard').props.samples), [200]); f.stop();
});

test('memory history is bounded and visibility/unmount cancel background timers', async () => {
  const f = fixture(); f.render(); await f.settle();
  for (let i = 0; i < 65; i++) await f.tick(2000);
  assert.equal(f.find('MemoryUsageCard').props.samples.length, 60);
  f.visibility('hidden'); assert.equal(f.timers.size, 0);
  f.visibility('visible'); await f.settle(); assert.equal(f.timers.size, 2);
  f.stop(); assert.equal(f.timers.size, 0);
});

test('session timer resets when the start timestamp disappears', async () => {
  const f = fixture({ style: 'signal' }); f.render(); await f.settle();
  assert.equal(f.find('SignalHome').props.sessionLabel, '00:01:30');
  f.store.sessionStartedAt = null; f.render();
  assert.equal(f.find('SignalHome').props.sessionLabel, '00:00:00'); f.stop();
});

test('Signal shows and connects the first available server without explicit selection', async () => {
  const f = fixture({ style: 'signal', state: 'disconnected' }); f.store.activeServerId = null; f.render();
  const signal = f.find('SignalHome');
  assert.equal(signal.props.serverName, 'First server');
  const button = f.find('button', signal.props.actions);
  assert.equal(button.props.disabled, false); button.props.onClick(); await f.settle();
  assert.deepEqual(f.connectedIds, ['first']); f.stop();
});

test('Signal still allows disconnect when the active profile disappeared', async () => {
  const f = fixture({ style: 'signal' }); f.store.subscriptions = []; f.render();
  const button = f.find('button', f.find('SignalHome').props.actions);
  assert.equal(button.props.disabled, false); button.props.onClick(); await f.settle();
  assert.equal(f.disconnected, 1); f.stop();
});

test('backend connecting status is busy even without a local request', () => {
  const f = fixture({ style: 'signal', state: 'connecting' }); f.render();
  const signal = f.find('SignalHome'); assert.equal(signal.props.state, 'connecting');
  assert.equal(f.find('button', signal.props.actions).props.disabled, true); f.stop();
});

test('malformed persisted server orders never crash Home', () => {
  for (const raw of ['"broken"', '42', 'null', '{}', '[null,4,"first","first"]']) {
    const f = fixture({ stored: { 'nimbo.order.https___example_test_sub': raw } });
    assert.doesNotThrow(() => f.render()); f.stop();
  }
});

test('changing profiles clears a protocol filter unavailable in the new profile', () => {
  const f = fixture({ style: 'signal' }); f.render();
  f.find('SignalHome').props.serverRail.props.onProtocolFilter('vless'); f.render();
  f.store.subscriptions = [{ ...sub, servers: [{ ...server, protocol: { kind: 'awg' } }] }]; f.render();
  const rail = f.find('SignalHome').props.serverRail;
  assert.equal(rail.props.protocolFilter, null); assert.equal(rail.props.entries.length, 1); f.stop();
});

test('Signal empty states distinguish search, favorites, and an empty profile list', () => {
  const f = fixture({ style: 'signal' }); f.render();
  const baseProps = f.find('SignalHome').props.serverRail.props;
  const code = compile('../src/pages/home/SignalServerRail.tsx');
  for (const { query, props, expected, link } of [
    { query: 'missing', props: baseProps, expected: f.labels.home.noMatchingServers },
    { query: '', props: { ...baseProps, entries: [], showFavOnly: true }, expected: f.labels.home.noFavorites },
    { query: '', props: { ...baseProps, entries: [], protocolFilter: 'awg' }, expected: f.labels.home.noMatchingServers },
    { query: '', props: { ...baseProps, entries: [], subs: [], currentSub: null }, expected: f.labels.profiles.emptyTitle, link: true },
  ]) {
    const exports = {};
    const deps = {
      react: { ...React, useState: initial => [typeof initial === 'string' ? query : initial, () => {}] },
      'react/jsx-runtime': jsxRuntime,
      'react-router-dom': { Link: ({ to, children, ...rest }) => React.createElement('a', { href: to, ...rest }, children) },
      '../../lib/api': { protocolLabel: p => p.kind },
      '../../lib/i18n': { fillTemplate: value => value },
      '../../lib/serverUiOverrides': { serverDisplayLabel: s => s.name },
      '../../components/CountryFlag': { CountryFlag: () => null },
      '../../components/LatencyDisplay': { LatencyDisplay: () => null },
    };
    vm.runInNewContext(code, { exports, require: name => deps[name] });
    const html = renderToStaticMarkup(React.createElement(exports.SignalServerRail, props));
    assert.ok(html.includes(expected), expected);
    if (link) assert.ok(html.includes('href="/subscriptions"'));
  }
  f.stop();
});
