import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import { createStore } from 'zustand/vanilla';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement, useEffect, useState } from 'react';
import * as jsxRuntime from 'react/jsx-runtime';
import ts from 'typescript';

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8');
async function moduleWithMocks(path, mocks) {
  // Execute production modules, replacing only their imported dependencies.
  const output = ts.transpileModule(read(path), { compilerOptions: {
    module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText.replace(/^import .*?;\r?\n/gm, '');
  const key = `__coreSelection${Math.random().toString(36).slice(2)}`;
  globalThis[key] = mocks;
  const code = `const {${Object.keys(mocks).join(',')}} = globalThis[${JSON.stringify(key)}];\n${output}`;
  try { return await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`); }
  finally { delete globalThis[key]; }
}

const helpers = await moduleWithMocks('../src/lib/coreProfiles.ts', {});

test('Mihomo workspace breadcrumb does not fall back to Home', async () => {
  const Link = ({ to, children }) => createElement('a', { href: to }, children);
  const { WorkspaceBar } = await moduleWithMocks('../src/components/WorkspaceBar.tsx', {
    useState, Link, NavLink: Link, useLocation: () => ({ pathname: '/mihomo' }),
    _jsx: jsxRuntime.jsx, _jsxs: jsxRuntime.jsxs, _Fragment: jsxRuntime.Fragment,
    useMessages: () => ({ common: { locale: 'en' }, app: { home: 'Home', notifications: 'Notifications' } }),
    useAppStore: selector => selector({ subscriptions: [] }), HomeMetaIcon: () => null, notifyError: () => {},
  });
  const html = renderToStaticMarkup(createElement(WorkspaceBar));
  assert.match(html, /<strong>Mihomo profiles<\/strong>/);
  assert.doesNotMatch(html, /<strong>Home<\/strong>/);
});

test('full-profile URL validation and errors never disclose a subscription token', () => {
  assert.equal(helpers.validateCoreSourceUrl(' https://example.test/profile?token=private '), 'https://example.test/profile?token=private');
  for (const value of ['file:///secret', 'ftp://example.test/profile', 'https://user:private@example.test/',
    'https://example.test/#private', 'https://example.test/a b', 'https://example.test/\nprivate', 'https://example.test/' + 'x'.repeat(8192)]) {
    assert.throws(() => helpers.validateCoreSourceUrl(value), /INVALID_SOURCE_URL/);
  }
  for (const code of ['SOURCE_FETCH_FAILED', 'SOURCE_FETCH_TIMEOUT', 'SOURCE_HTTP_ERROR', 'SOURCE_REDIRECT_BLOCKED', 'SOURCE_INVALID_UTF8', 'SOURCE_NOT_PROFILE']) {
    const result = helpers.mihomoErrorMessage(`${code}: https://example.test/?token=private`, true);
    assert.ok(!result.includes('private') && !result.includes('example.test'));
    assert.ok(!result.includes('CORE_OPERATION_FAILED'), 'known download failure has a dedicated message');
  }
});

test('URL import invokes only the native bounded importer and does not change network mode', async () => {
  const calls = [];
  const { coreApi } = await moduleWithMocks('../src/lib/coreApi.ts', {
    isTauriRuntime: () => true,
    tauriInvoke: async (...args) => { calls.push(args); return { inspection_error: null }; },
  });
  await coreApi.importUrl('My profile', 'https://example.test/profile');
  assert.deepEqual(calls, [['import_mihomo_profile_url', { name: 'My profile', url: 'https://example.test/profile' }]]);
});

test('Mihomo ad-blocking mode rejection explains rule mode without exposing native error details', () => {
  const code = 'AD_BLOCKING_REQUIRES_RULE_MODE';
  const error = `${code}: https://example.test/profile?token=private`;
  assert.equal(helpers.coreErrorCode(error), code);
  for (const ru of [false, true]) {
    const message = helpers.mihomoErrorMessage(error, ru);
    assert.match(message, /Mihomo/);
    assert.match(message, /rule/);
    assert.match(message, ru ? /выключите блокировку рекламы/ : /turn off ad blocking/);
    assert.ok(!message.includes('private') && !message.includes('example.test'));
    assert.ok(!message.includes('CORE_OPERATION_FAILED'));
  }
});

test('saving a preference while connected preserves the active session and never disconnects', async () => {
  let selected = 'auto';
  let fail = false;
  let disconnects = 0;
  const runtime = { running: true, profile_id: 'existing', session_id: 'session', native_generation: 9 };
  const { useCoreStore: store } = await moduleWithMocks('../src/coreStore.ts', {
    create: createStore, ...helpers,
    coreApi: {
      preference: async core => { if (fail) throw Error('STATE_WRITE_FAILED'); selected = core; },
      profiles: async () => ({ preferred_core: selected, active_profile_id: 'existing', profiles: [] }),
      availability: async () => [], runtime: async () => runtime,
    },
    useAppStore: { getState: () => ({ disconnectServer: async () => { disconnects++; } }) },
  });
  const snapshot = { groups: { Main: { now: 'node' } } };
  const delay = { name: 'node', ms: 9 };
  store.setState({ runtime, snapshot, delay });
  for (const core of ['awg', 'xray', 'auto', 'mihomo']) {
    await store.getState().preference(core);
    assert.equal(store.getState().data.preferred_core, core);
    assert.equal(store.getState().runtime, runtime);
    assert.equal(store.getState().snapshot, snapshot);
    assert.equal(store.getState().delay, delay);
    assert.equal(disconnects, 0);
    assert.equal(store.getState().busy, null);
  }
  fail = true;
  await store.getState().preference('xray');
  assert.equal(store.getState().data.preferred_core, 'mihomo');
  assert.ok(store.getState().error);
  assert.equal(store.getState().runtime, runtime);
  assert.equal(disconnects, 0);
});

test('settings show authoritative unavailable states and next manual connection semantics', async () => {
  let awg = false;
  let mihomo = false;
  const { CorePreferenceSetting } = await moduleWithMocks('../src/components/CorePreferenceSetting.tsx', {
    useEffect, NimboSelect: ({children,...props}) => createElement("select",props,children), ...helpers, _jsx: jsxRuntime.jsx, _jsxs: jsxRuntime.jsxs,
    useMessages: () => ({ common: { locale: 'en' } }), isTauriRuntime: () => true,
    useCoreStore: () => ({ data: { preferred_core: 'auto' }, availability: [
      { core: 'xray', selector_available: true }, { core: 'awg', selector_available: awg },
      { core: 'mihomo', selector_available: mihomo, system_proxy_available: mihomo },
    ], busy: null, error: null, refresh: async () => {}, preference: async () => {} }),
  });
  let html = renderToStaticMarkup(createElement(CorePreferenceSetting));
  assert.match(html, /From the next connection/);
  assert.match(html, /value="auto" selected=""/);
  assert.match(html, /value="awg" disabled=""/);
  assert.match(html, /value="mihomo" disabled=""/);
  awg = true;
  html = renderToStaticMarkup(createElement(CorePreferenceSetting));
  assert.match(html, /<option value="awg">AWG<\/option>/);
  assert.match(html, /value="mihomo" disabled=""/);
  assert.doesNotMatch(html, /href="#\/mihomo"/, 'no redundant Advanced YAML link in settings');
  mihomo = true;
  html = renderToStaticMarkup(createElement(CorePreferenceSetting));
  assert.match(html, /<option value="mihomo">Mihomo<\/option>/);
});

test('full-profile page uses native categories and blocks connect for TUN/Both/KS', async () => {
  let mode = 'system_proxy';
  let ks = false;
  const state = {
    data: { preferred_core: 'mihomo', profiles: [{ id:'p',name:'Complete YAML',kind:'mihomo_yaml',inspection:null }] },
    availability:[{core:'mihomo',binary_verified:true,system_proxy_available:true,inspect_available:true}],
    runtime:{running:true,profile_id:'other'}, busy:null, error:null,
    snapshot: {
      groups:{'Manual / 日本':{type:'Selector',now:'DIRECT',all:['DIRECT','REJECT']},'Automatic':{type:'URLTest',now:'node'}},
      providers:{'Subscription Feed':{vehicleType:'HTTP',proxies:[{name:'node'}]}},
      ruleProviders:{'Rules':{behavior:'Domain',ruleCount:7}},
    },
  };
  const {MihomoProfiles} = await moduleWithMocks('../src/pages/MihomoProfiles.tsx', {
    useEffect,useState,_jsx:jsxRuntime.jsx,_jsxs:jsxRuntime.jsxs,_Fragment:jsxRuntime.Fragment,
    NimboSelect: ({children,...props}) => createElement("select",props,children), ...helpers, coreApi:{}, api:{}, isTauriRuntime:()=>true,
    useMessages:()=>({common:{locale:'en'}}), useCoreStore:()=>state,
    useAppStore:selector=>selector({status:{connection_mode:mode},preferences:{connection_kill_switch:ks}}),
  });
  let html=renderToStaticMarkup(createElement(MihomoProfiles));
  assert.match(html, /<button>Connect<\/button>/);
  assert.match(html, /type="file"/);
  assert.match(html, /Complete original YAML/);
  assert.match(html, /Complete YAML URL/);
  assert.match(html, /One-time download, up to 20 seconds/);
  assert.match(html, /autocomplete="off"/i);
  assert.match(html, /Manual \/ 日本/);
  assert.match(html, /URLTest/);
  assert.match(html, /Subscription Feed/);
  assert.match(html, /Refresh provider/);
  assert.match(html, /Refresh rules/);
  assert.equal((html.match(/<select/g)||[]).length,1,'only a native Selector permits manual selection');
  for(const value of ['tun','both']) {
    mode=value;
    html=renderToStaticMarkup(createElement(MihomoProfiles));
    assert.match(html, /<button disabled=""[^>]*>Connect<\/button>/);
    assert.match(html, /This Mihomo network mode is unavailable/);
  }
  mode='system_proxy';ks=true;
  html=renderToStaticMarkup(createElement(MihomoProfiles));
  assert.match(html, /<button disabled=""[^>]*>Connect<\/button>/);
  assert.match(html, /requires Windows, the prepared helper and TUN or Both/);
  state.runtime.profile_id = 'p';
  html = renderToStaticMarkup(createElement(MihomoProfiles));
  assert.match(html, /data-active="true"/);
  assert.match(html, /Disconnect/);
});

test('full-profile page polls sequentially and does not start a live request after disposal', async () => {
  let task, cleanup, stopped = 0, finishRefresh, snapshots = 0;
  const state = {
    data: { profiles: [] }, availability: [], runtime: { running: true }, busy: null, error: null,
    refresh: () => new Promise(resolve => { finishRefresh = resolve; }),
    live: async () => { snapshots++; },
  };
  const store = () => state;
  store.getState = () => state;
  const { MihomoProfiles } = await moduleWithMocks('../src/pages/MihomoProfiles.tsx', {
    useEffect: effect => { cleanup = effect(); }, useState,
    _jsx: jsxRuntime.jsx, _jsxs: jsxRuntime.jsxs, _Fragment: jsxRuntime.Fragment,
    ...helpers, coreApi: {}, api: {}, isTauriRuntime: () => true,
    useMessages: () => ({ common: { locale: 'en' } }), useCoreStore: store,
    useAppStore: selector => selector({ status: { connection_mode: 'system_proxy' }, preferences: { connection_kill_switch: false } }),
    startVisiblePolling: (callback, interval) => { task = callback; assert.equal(interval, 10000); return () => { stopped++; }; },
    document: { visibilityState: 'visible' },
  });
  renderToStaticMarkup(createElement(MihomoProfiles));
  const pending = task();
  cleanup(); finishRefresh(); await pending;
  assert.equal(stopped, 1);
  assert.equal(snapshots, 0);
});

test('native selection is acknowledged and a stale session response is discarded', async () => {
  let runtime={running:true,profile_id:'p',session_id:'one'};
  let confirmed='DIRECT';
  const {useCoreStore:store}=await moduleWithMocks('../src/coreStore.ts', {
    create:createStore,...helpers,useAppStore:{},coreApi:{
      select:async()=>({groups:{Choice:{type:'Selector',now:confirmed,all:['DIRECT','REJECT']}}}),
      runtime:async()=>runtime,
    },
  });
  store.setState({runtime,snapshot:{groups:{Choice:{now:'DIRECT'}}}});
  await store.getState().live('select','Choice','REJECT');
  assert.equal(store.getState().error,'READBACK_MISMATCH');
  assert.equal(store.getState().snapshot.groups.Choice.now,'DIRECT');
  confirmed='REJECT';
  await store.getState().live('select','Choice','REJECT');
  assert.equal(store.getState().snapshot.groups.Choice.now,'REJECT');
  runtime={...runtime,session_id:'two'};
  await store.getState().live('select','Choice','DIRECT');
  assert.equal(store.getState().error,'STALE_GENERATION');
  assert.equal(store.getState().snapshot,null);
});

test('Auto preserves the existing full-profile proxy contract and original source bytes', () => {
  const availability = { binary_verified: true, system_proxy_available: true, tun_available: false };
  assert.equal(helpers.mihomoBlockReason(availability, 'auto', 'system_proxy', false), null);
  assert.equal(helpers.mihomoBlockReason(availability, 'xray', 'system_proxy', false), 'UNSUPPORTED_CORE');
  assert.equal(helpers.mihomoBlockReason(availability, 'auto', 'tun', false), 'MIHOMO_TUN_UNAVAILABLE');
  const source = '\uFEFF# preserve\r\nproxy-providers: {}\r\n';
  assert.equal(helpers.validateCoreSource(source), source);
  assert.equal(helpers.decodeCoreSource(new TextEncoder().encode(source).buffer), source);
});

test('manual server switching lets Rust reject a mismatch without frontend teardown', async () => {
  const source = read('../src/store.ts');
  const parsed = ts.createSourceFile('store.ts', source, ts.ScriptTarget.Latest, true);
  let action;
  function visit(node) {
    if (ts.isPropertyAssignment(node) && node.name.getText(parsed) === 'setActiveServer') action = node.initializer.getText(parsed);
    ts.forEachChild(node, visit);
  }
  visit(parsed);
  assert.ok(action);
  const output = ts.transpileModule(`export default ${action}`, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText.replace('export default ', 'return ');
  let disconnects = 0;
  let resets = 0;
  let reject = true;
  const state = {
    status: { state: 'connected' }, activeServerId: 'original',
    connectServer: async () => { if (reject) throw Error('CORE_MISMATCH'); state.activeServerId = 'new'; },
    resetTrafficSession: () => { resets++; },
  };
  const switchServer = new Function('get', 'set', 'api', output)(
    () => state, patch => Object.assign(state, patch),
    { disconnectServer: async () => { disconnects++; } },
  );
  await assert.rejects(switchServer('new'), /CORE_MISMATCH/);
  assert.equal(disconnects, 0);
  assert.equal(resets, 0);
  assert.equal(state.activeServerId, 'original');
  reject = false;
  await switchServer('new');
  assert.equal(disconnects, 0);
  assert.equal(resets, 1);
});


test('on-demand validation preserves exact network names and rejects invalid input', async () => {
  const helpers = await moduleWithMocks('../src/lib/onDemand.ts', { invoke: async () => {}, isTauriRuntime: () => true });
  const enabled = { ...helpers.defaultOnDemand, enabled: true };
  assert.deepEqual(helpers.validatedOnDemand(enabled, ' Home \nHome\nhome\nCafe:Guest').trusted_ssids, ['Home', 'home', 'Cafe:Guest']);
  assert.throws(() => helpers.validatedOnDemand(enabled, 'я'.repeat(17)), /INVALID_SSID/);
  assert.throws(() => helpers.validatedOnDemand(enabled, 'bad\u0000name'), /INVALID_SSID/);
  assert.throws(() => helpers.validatedOnDemand(enabled, Array.from({ length: 33 }, (_, i) => `WiFi${i}`).join('\n')), /TOO_MANY_SSIDS/);
  assert.throws(() => helpers.validatedOnDemand({ ...enabled, wifi: false, cellular: false }, ''), /NO_TRANSPORT/);
  assert.equal(helpers.validatedOnDemand({ ...enabled, wifi: false, cellular: false, ethernet: true }, '').ethernet, true);
  assert.doesNotMatch(helpers.onDemandError('unexpected SSID=private-home', true), /private-home/);
});

test('on-demand uses explicit native saves and has no browser persistence fallback', async () => {
  const calls = [];
  const mocks = { invoke: async (...args) => { calls.push(args); return {}; }, isTauriRuntime: () => true };
  const { onDemandApi, defaultOnDemand } = await moduleWithMocks('../src/lib/onDemand.ts', mocks);
  await onDemandApi.get();
  await onDemandApi.save(defaultOnDemand);
  assert.deepEqual(calls, [['get_on_demand'], ['set_on_demand', { settings: defaultOnDemand }]]);
  const browser = await moduleWithMocks('../src/lib/onDemand.ts', { ...mocks, isTauriRuntime: () => false });
  await assert.rejects(browser.onDemandApi.save(defaultOnDemand), /NATIVE_UNAVAILABLE/);
  assert.equal(calls.length, 2);
});


test('a pending live server switch cannot launch a second row action', async () => {
  const parsed = ts.createSourceFile('store.ts', read('../src/store.ts'), ts.ScriptTarget.Latest, true);
  let action;
  function visit(node) {
    if (ts.isPropertyAssignment(node) && node.name.getText(parsed) === 'setActiveServer') action = node.initializer.getText(parsed);
    ts.forEachChild(node, visit);
  }
  visit(parsed);
  const output = ts.transpileModule(`export default ${action}`, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText.replace('export default ', 'return ');
  let release; let starts = 0;
  const state = { status: {state:'connected'}, activeServerId:'old', switchingServerId:null,
    connectServer: async () => { starts++; await new Promise(resolve => { release=resolve; }); },
    resetTrafficSession: () => {},
  };
  const switchServer = new Function('get','set','api',output)(()=>state,patch=>Object.assign(state,patch),{});
  const first = switchServer('new');
  assert.equal(state.switchingServerId,'new');
  await assert.rejects(switchServer('third'), /Дождитесь/);
  assert.equal(starts,1);
  release(); await first;
  assert.equal(state.switchingServerId,null);
});

 test('native Linux TUN admission requires verified helper, never proxy fallback',()=>{
   const cap={core:'mihomo',binary_verified:true,system_proxy_available:false,tun_available:true,selector_available:true};
   assert.equal(helpers.mihomoBlockReason(cap,'mihomo','tun',false),null);
   assert.equal(helpers.mihomoBlockReason(cap,'auto','tun',true),'MIHOMO_KILL_SWITCH_UNAVAILABLE');
   assert.equal(helpers.mihomoBlockReason(cap,'xray','tun',false),'UNSUPPORTED_CORE');
   assert.equal(helpers.mihomoBlockReason(cap,'mihomo','both',false),'MIHOMO_TUN_UNAVAILABLE');
   assert.equal(helpers.mihomoBlockReason({...cap,tun_available:false,reason:'MIHOMO_HELPER_REQUIRED'},'mihomo','tun',false),'MIHOMO_HELPER_REQUIRED');
   assert.equal(helpers.mihomoBlockReason(cap,'mihomo','system_proxy',false),'SYSTEM_PROXY_PLATFORM_UNAVAILABLE');
 });


test('Linux helper setup is explicit and disabled during an active connection', async () => {
  const state={data:{profiles:[]},availability:[{core:'mihomo',binary_verified:true,tun_available:false,reason:'MIHOMO_HELPER_REQUIRED'}],runtime:{running:false},busy:null,error:null};
  const {MihomoProfiles}=await moduleWithMocks('../src/pages/MihomoProfiles.tsx',{
    useEffect,useState,_jsx:jsxRuntime.jsx,_jsxs:jsxRuntime.jsxs,_Fragment:jsxRuntime.Fragment,
    ...helpers,coreApi:{},api:{},isTauriRuntime:()=>true,
    useMessages:()=>({common:{locale:'en'}}),useCoreStore:()=>state,
    useAppStore:selector=>selector({status:{connection_mode:'tun'},preferences:{connection_kill_switch:false}}),
  });
  let html=renderToStaticMarkup(createElement(MihomoProfiles));
  assert.match(html,/<button>Prepare Mihomo TUN<\/button>/);
  assert.match(html,/System authorization is required/);
  state.runtime.running=true;
  html=renderToStaticMarkup(createElement(MihomoProfiles));
  assert.match(html,/<button disabled="">Prepare Mihomo TUN<\/button>/);
  state.runtime.running=false;state.busy='prepare-tun';
  html=renderToStaticMarkup(createElement(MihomoProfiles));
  assert.match(html,/<button disabled="">Preparing…<\/button>/);
  state.availability[0].tun_available=true;state.availability[0].reason=null;
  html=renderToStaticMarkup(createElement(MihomoProfiles));
  assert.doesNotMatch(html,/Prepare Mihomo TUN/);
});

test('Windows Both and KS require explicit authenticated helper capabilities',()=>{
 const cap={binary_verified:true,tun_available:true,system_proxy_available:true,both_available:true,kill_switch_available:true};
 assert.equal(helpers.mihomoBlockReason(cap,'mihomo','both',true),null);
 assert.equal(helpers.mihomoBlockReason(cap,'mihomo','tun',true),null);
 assert.equal(helpers.mihomoBlockReason({...cap,both_available:false},'mihomo','both',false),'MIHOMO_TUN_UNAVAILABLE');
 assert.equal(helpers.mihomoBlockReason({...cap,kill_switch_available:false},'mihomo','both',true),'MIHOMO_KILL_SWITCH_UNAVAILABLE');
 assert.equal(helpers.mihomoBlockReason(cap,'mihomo','system_proxy',true),'MIHOMO_KILL_SWITCH_UNAVAILABLE');
});
