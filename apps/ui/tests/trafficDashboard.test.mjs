import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';
import { createStore } from 'zustand/vanilla';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement, useId, useRef, useState } from 'react';
import * as jsx from 'react/jsx-runtime';

const data = {};
vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/lib/statisticsPresentation.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText, { exports: data });
const route = { proxy_upload: 60, proxy_download: 40, direct_upload: 0, direct_download: 0 };
const stats = { session_upload: 60, session_download: 40, session_available: true, speed_available: true,
  upload_speed: 12, download_speed: 24, route_traffic: route, tcp_connections: 4, udp_connections: 0,
  monthly_upload: 600, monthly_download: 400, all_time_upload: 6000, all_time_download: 4000 };

test('route share comes only from valid cumulative route counters', () => {
  assert.equal(data.routeShare(null), null);
  assert.equal(data.routeShare(route), 1);
  assert.equal(data.routeShare({ ...route, direct_download: 100 }), 0.5);
  assert.equal(data.routeShare({ proxy_upload: 0, proxy_download: 0, direct_upload: 0, direct_download: 0 }), null);
  for (const value of [-1, NaN, Infinity, undefined, '10']) {
    assert.equal(data.routeShare({ ...route, direct_upload: value }), null);
  }
});
test('explicit unavailable sessions cannot render zero totals, rates, routes or protocols as measured', () => {
  const view = data.trafficDashboardValues({ ...stats, session_available: false }, 'session', true, true);
  for (const key of ['upload', 'download', 'uploadSpeed', 'downloadSpeed', 'route', 'tcp', 'udp']) assert.equal(view[key], null, key);
});
test('month and all-time retain totals but never inherit current-session route bytes', () => {
  for (const [range, upload, download] of [['month', 600, 400], ['all', 6000, 4000]]) {
    const view = data.trafficDashboardValues(stats, range, true, true);
    assert.equal(view.upload, upload); assert.equal(view.download, download);
    assert.equal(view.route, null); assert.equal(view.tcp, 4); assert.equal(view.udp, 0);
  }
});
test('old binaries can show reported totals without fabricating missing telemetry', () => {
  const view = data.trafficDashboardValues({ ...stats, session_available: undefined, route_traffic: undefined, tcp_connections: undefined, udp_connections: undefined }, 'session', true, true);
  assert.equal(view.upload, 60); assert.equal(view.download, 40);
  assert.equal(view.route, null); assert.equal(view.tcp, null); assert.equal(view.udp, null);
});
test('disconnects hide live data and zero supported counters stay measured', () => {
  const offline = data.trafficDashboardValues(stats, 'session', false, true);
  assert.equal(offline.upload, null); assert.equal(offline.route, null); assert.equal(offline.tcp, null);
  const idle = data.trafficDashboardValues({ ...stats, session_upload: 0, session_download: 0, tcp_connections: 0 }, 'session', true, true);
  assert.equal(idle.upload, 0); assert.equal(idle.tcp, 0); assert.equal(idle.udp, 0);
  const noStats = data.trafficDashboardValues(null, 'all', true, true);
  assert.equal(noStats.upload, null);
});
test('invalid protocol counts and unavailable speeds are not coerced to zero', () => {
  for (const count of [-1, 0.5, NaN, undefined]) {
    const view = data.trafficDashboardValues({ ...stats, tcp_connections: count, udp_connections: count }, 'session', true, false);
    assert.equal(view.tcp, null); assert.equal(view.udp, null); assert.equal(view.uploadSpeed, null);
  }
});

async function moduleWithMocks(path, mocks) {
  const output = ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), { compilerOptions: {
    module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText.replace(/^import .*?;\r?\n/gm, '');
  const key = `__trafficDashboard${Math.random().toString(36).slice(2)}`;
  globalThis[key] = mocks;
  try {
    return await import(`data:text/javascript;base64,${Buffer.from(`const {${Object.keys(mocks).join(',')}}=globalThis[${JSON.stringify(key)}];\n${output}`).toString('base64')}`);
  } finally { delete globalThis[key]; }
}
const latency = await moduleWithMocks('../src/lib/latency.ts', {});
const awg = await moduleWithMocks('../src/lib/awg.ts', {});
function memoryStorage() {
  const values = new Map();
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
}
async function testApi(native = false, invoke = async () => { throw Error('Unexpected IPC'); }) {
  return moduleWithMocks('../src/lib/api.ts', { ...latency, ...awg,
    uiPackage: { version: 'fixture' }, getVersion: async () => 'fixture', tauriInvoke: invoke,
    window: native ? { __TAURI_INTERNALS__: { invoke } } : {}, localStorage: memoryStorage(),
  });
}
test('ad preference defaults off for missing and malformed old settings, and round-trips on/off', async () => {
  const { api, defaultAppPreferences } = await testApi();
  assert.equal(defaultAppPreferences.ad_blocking_enabled, false);
  assert.equal((await api.getPreferences()).ad_blocking_enabled, false);
  for (const malformed of [undefined, null, 'true', 'false', 1]) {
    assert.equal((await api.setPreferences({ ...defaultAppPreferences, ad_blocking_enabled: malformed })).ad_blocking_enabled, false);
  }
  for (const enabled of [true, false]) {
    await api.setPreferences({ ...defaultAppPreferences, ad_blocking_enabled: enabled, tunnel_dns: 'fixture-preserved' });
    const readback = await api.getPreferences();
    assert.equal(readback.ad_blocking_enabled, enabled); assert.equal(readback.tunnel_dns, 'fixture-preserved');
  }
});
test('native ad preference save sends one persisted flag, with no runtime reapply or reconnect', async () => {
  const calls = [];
  const { api, defaultAppPreferences } = await testApi(true, async (command, args) => {
    calls.push(command); assert.equal(command, 'set_preferences'); return args.preferences;
  });
  const saved = await api.setPreferences({ ...defaultAppPreferences, ad_blocking_enabled: true });
  assert.equal(saved.ad_blocking_enabled, true); assert.deepEqual(calls, ['set_preferences']);
});
test('browser-only runtime declares unavailable session/route/protocol measurements', async () => {
  const { api } = await testApi();
  const stats = await api.getTrafficStats();
  assert.equal(stats.session_available, false); assert.equal(stats.route_traffic, null);
  assert.equal(stats.tcp_connections, null); assert.equal(stats.udp_connections, null);
});
test('unavailable session samples do not enter speed history, app estimates, or saved session history', async () => {
  const { defaultAppPreferences } = await testApi();
  const { useAppStore: store } = await moduleWithMocks('../src/store.ts', {
    create: createStore, api: {}, defaultAppPreferences, latencySettingsKey: latency.latencySettingsKey,
    readBootAppearance: () => null, measureFastestServer: () => {}, localStorage: memoryStorage(),
  });
  store.setState({ status: { state: 'connected' }, sessionStartedAt: 1 });
  store.getState().recordTrafficStats({ ...stats, session_available: false }, 10);
  assert.equal(store.getState().trafficHistory.length, 0);
  assert.equal(store.getState().trafficMonitoringAvailable, false);
  store.getState().recordAppTraffic([{ route: 'proxy', process: 'fixture' }]);
  assert.deepEqual(store.getState().appTraffic, {});
  store.getState().resetTrafficSession();
  assert.equal(store.getState().sessionHistory.length, 0);
  store.setState({ status: { state: 'connected' } });
  store.getState().recordTrafficStats(stats, 20);
  assert.equal(store.getState().trafficHistory.length, 1);
});
test('dashboard renders measured/empty/unavailable rings and nullable protocol counts truthfully', async () => {
  const { formatBytes } = await testApi();
  const { TrafficDashboard } = await moduleWithMocks('../src/pages/stats/TrafficDashboard.tsx', {
    _jsx: jsx.jsx, _jsxs: jsx.jsxs, _Fragment: jsx.Fragment,
    useMessages: () => ({ common: { locale: 'en' }, statistics: { uploaded: 'Uploaded', received: 'Downloaded' } }),
    trafficDashboardValues: data.trafficDashboardValues, formatBytes, AdBlockingControl: () => null,
  });
  const render = (stats, range = 'session') => renderToStaticMarkup(createElement(TrafficDashboard, { stats, range, connected: true, speedAvailable: true }));
  assert.match(render(stats), /data-state="measured"/);
  assert.match(render(stats), /Proxied 100%/);
  assert.match(render(stats), /data-testid="udp-count">0</);
  const unavailable = render({ ...stats, session_available: false });
  assert.match(unavailable, /data-state="unavailable"/);
  assert.match(unavailable, /data-testid="upload-total">—</);
  assert.match(unavailable, /data-testid="tcp-count">—</);
  assert.doesNotMatch(unavailable, /traffic-ring-proxy/);
  assert.match(render({ ...stats, route_traffic: { proxy_upload: 0, proxy_download: 0, direct_upload: 0, direct_download: 0 } }), /data-state="empty"/);
  assert.match(render(stats, 'month'), /current session only/);
});
test('ad control is an accessible persisted switch with next-connection and domain-filter caveats', async () => {
  let locale = 'en';
  const { AdBlockingControl } = await moduleWithMocks('../src/components/AdBlockingControl.tsx', {
    _jsx: jsx.jsx, _jsxs: jsx.jsxs, _Fragment: jsx.Fragment, useId, useRef, useState,
    useMessages: () => ({ common: { locale } }),
    useAppStore: selector => selector({ preferences: { ad_blocking_enabled: false } }),
  });
  const html = renderToStaticMarkup(createElement(AdBlockingControl));
  assert.match(html, /role="switch" aria-checked="false"/);
  for (const text of ['next connection', 'Provider and custom rules are preserved', 'does not remove all ads', 'encrypted DNS']) assert(html.includes(text));
  assert.doesNotMatch(html, /requests blocked|ads blocked/i);
  assert.match(html, /<details>.*Mihomo.*rule.*global.*direct.*<\/details>/);
  locale = 'ru';
  const russian = renderToStaticMarkup(createElement(AdBlockingControl));
  assert.match(russian, /<details>.*Mihomo.*rule.*global.*direct.*<\/details>/);
});
