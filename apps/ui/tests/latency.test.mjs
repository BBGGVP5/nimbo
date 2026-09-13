import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import ts from 'typescript';
import React from 'react';
import * as jsx from 'react/jsx-runtime';
import { renderToStaticMarkup } from 'react-dom/server';

const source = path => readFileSync(new URL(path, import.meta.url), 'utf8');
function evaluate(code, deps = {}, globals = {}) {
  const exports = {};
  vm.runInNewContext(ts.transpileModule(code, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText, { exports, URL, require: name => { assert.ok(name in deps, name); return deps[name]; }, ...globals });
  return exports;
}
const latency = evaluate(source('../src/lib/latency.ts'));
const store = { preferences: { latency_display_format: 'numeric' } };
const display = evaluate(source('../src/components/LatencyDisplay.tsx'), {
  '../lib/latency': latency, '../store': { useAppStore: fn => fn(store) }, 'react/jsx-runtime': jsx,
}).LatencyDisplay;

test('display contract boundaries and zero; missing/failure/running never succeed', () => {
  for (const [value, bars] of [[0,4],[99,4],[100,3],[199,3],[200,2],[399,2],[400,1],[9000,1],[-1,0],[null,0],[undefined,0],[NaN,0],[Infinity,0]]) {
    assert.equal(latency.latencyBars(value), bars, String(value));
    assert.equal(latency.latencyBars(value, true), 0);
  }
});

test('all saved IDs survive normalization, unset defaults to Nimbo, malformed URL/timeout rejected', () => {
  for (const id of ['tcp_connect','icmp','http_head','http_get','nimbo']) assert.equal(latency.normalizeLatencyProtocol(id), id);
  for (const id of ['numeric','bars','both','dots','ms','badge']) assert.equal(latency.normalizeLatencyDisplay(id), id);
  for (const value of [undefined,null,'']) assert.equal(latency.normalizeLatencyProtocol(value), 'nimbo');
  assert.equal(apiFixture(false).defaultAppPreferences.latency_protocol, 'nimbo');
  assert.equal(latency.normalizeLatencyProtocol('unknown'), 'tcp_connect');
  assert.equal(latency.normalizeLatencyDisplay('unknown'), 'ms');
  for (const url of ['http://','ftp://example.com','https://user:pass@example.com','javascript:alert(1)',null]) {
    assert.equal(latency.normalizeLatencyUrl(url), latency.DEFAULT_TEST_URL);
  }
  assert.equal(latency.normalizeLatencyUrl(' HTTPS://example.com/check '), 'HTTPS://example.com/check');
  for (const [value, expected] of [[0,500],[Infinity,5000],[NaN,5000],[100000,60000],[500.6,501],[undefined,5000]]) assert.equal(latency.normalizeLatencyTimeout(value), expected);
});

test('actual component renders selected numeric/bars/both/dots and legacy aliases accessibly', () => {
  for (const format of ['numeric','bars','both','dots','ms','badge']) {
    store.preferences.latency_display_format = format;
    const html = renderToStaticMarkup(React.createElement(display, { value: 0 }));
    assert.ok(html.includes('aria-label="0 ms"'));
    assert.equal(html.includes('<svg'), ['bars','both','dots','badge'].includes(format));
    assert.equal(html.includes('>0 ms</span>'), ['numeric','both','ms'].includes(format));
    assert.equal(html.includes('<circle'), ['dots','badge'].includes(format));
  }
  for (const props of [{value:null}, {value:-1}, {value:0,loading:true}, {value:0,error:'timeout'}]) {
    const html = renderToStaticMarkup(React.createElement(display, props));
    assert.ok(!html.includes('<svg'));
    assert.ok(!html.includes('0 ms'));
  }
});

test('progressive requests retain IDs, deduplicate and reject mismatched/failing replies', async () => {
  const calls = [], results = [];
  const { pingServersProgressively } = evaluate(source('../src/lib/ping.ts'), { './api': { api: {
    pingServer: async id => {
      calls.push(id);
      if (id === 'wrong') return {server_id:'active',latency_ms:10};
      if (id === 'error') return {server_id:id,latency_ms:10,error:'failed'};
      if (id === 'throws') throw Error('offline');
      return {server_id:id,latency_ms:0};
    },
  } } });
  await pingServersProgressively(['active','wrong','error','throws','active'], result => results.push(result), NaN);
  assert.equal(calls.length,4);
  assert.equal(results.find(r=>r.server_id==='active').latency_ms,0);
  for (const id of ['wrong','error','throws']) assert.equal(results.find(r=>r.server_id===id).latency_ms,null);
});

test('92 subscription nodes each get their own request with at most three in flight', async () => {
  let active=0,peak=0; const calls=[],results=[];
  const {pingServersProgressively}=evaluate(source('../src/lib/ping.ts'), {'./api':{api:{
    pingServer:async id=>{
      active++;peak=Math.max(peak,active);calls.push(id);
      await new Promise(resolve=>setTimeout(resolve,1));active--;
      return {server_id:id,latency_ms:Number(id)};
    },
  }}});
  await pingServersProgressively(Array.from({length:92},(_,i)=>String(i)),result=>results.push(result));
  assert.equal(new Set(calls).size,92);assert.equal(results.length,92);assert.equal(peak,3);
  for (const result of results) assert.equal(result.latency_ms,Number(result.server_id));
});

test('cancel stops progressive queue, cancels owned backend probes and suppresses late samples', async () => {
  let finish,cancelled=0; const calls=[],results=[];const controller=new AbortController();
  const {pingServersProgressively}=evaluate(source('../src/lib/ping.ts'), {'./api':{api:{
    pingServer:id=>{calls.push(id);return new Promise(resolve=>finish=resolve);},
    cancelPings:async()=>{cancelled++;},
  }}});
  const pending=pingServersProgressively(['a','b','c'],result=>results.push(result),1,controller.signal);
  controller.abort();finish({server_id:'a',latency_ms:0});await pending;
  assert.equal(cancelled,1);assert.deepEqual(calls,['a']);assert.deepEqual(results,[]);
});

function apiFixture(tauri, invoke = async () => {}) {
  const storage = new Map();
  const result = evaluate(source('../src/lib/api.ts'), {
    './latency': latency, './awg': {}, '@tauri-apps/api/core': {invoke},
    '@tauri-apps/api/app': {getVersion:async ()=>'1.2.0'}, '../../package.json': {default:{version:'1.2.0'}},
  }, { window: tauri ? {__TAURI_INTERNALS__:{invoke}} : {}, localStorage: {
    getItem:key=>storage.get(key)??null, setItem:(key,value)=>storage.set(key,value), removeItem:key=>storage.delete(key),
  } });
  return result;
}

test('browser preview never fabricates ping measurements', async () => {
  const {api} = apiFixture(false);
  assert.equal((await api.pingServer('first')).latency_ms,null);
  const batch = await api.pingServers(['a','b','a']);
  assert.equal(batch.length,2);
  assert.ok(batch.every(r=>r.latency_ms===null && r.error));
});

test('independent Nimbo survives disconnect; legacy HTTP, settings and cancel reject stale zero', async () => {
  for (const action of ['disconnect','legacy','settings','cancel']) {
    let finish;
    const {api,defaultAppPreferences} = apiFixture(true, async (command,args) => {
      if (command === 'ping_server') return new Promise(resolve => finish = resolve);
      if (command === 'set_preferences') return args.preferences;
      return {};
    });
    if (action === 'legacy') await api.setPreferences({...defaultAppPreferences,latency_protocol:'http_head'});
    const pending = api.pingServer('active');
    if (action === 'disconnect' || action === 'legacy') await api.disconnectServer();
    else if (action === 'cancel') await api.cancelPings();
    else await api.setPreferences({...defaultAppPreferences,latency_protocol:'tcp_connect'});
    finish({server_id:'active',latency_ms:0});
    assert.equal((await pending).latency_ms,action === 'disconnect' ? 0 : null);
  }
});

test('fastest-server selection accepts measured zero and rejects missing/negative', () => {
  const {fastestServerId} = evaluate(source('../src/lib/fastest.ts'), {'./ping':{}});
  assert.equal(fastestServerId([{id:'missing'},{id:'bad'},{id:'slow'},{id:'zero'}],{bad:-1,slow:40,zero:0}), 'zero');
});

test('real settings section exposes methods, active-route limitation, presets, custom URL and display choices', async () => {
  const settingsSource = source('../src/pages/Settings.tsx');
  const code = settingsSource.slice(settingsSource.indexOf('function LatencySection('),settingsSource.indexOf('function BackupSection(')) + '\nexports.LatencySection = LatencySection;';
  const changes = [], hooks = []; let cursor = 0;
  const preferences = {...apiFixture(false).defaultAppPreferences,latency_protocol:'nimbo'};
  const m = {settings:new Proxy({}, {get:(_,key)=>String(key)})};
  const {LatencySection} = evaluate(code, {'react/jsx-runtime':{jsx:(type,props)=>({type,props}),jsxs:(type,props)=>({type,props})}}, {
    ...latency, useMessages:()=>m, useEffect:()=>{}, useState:initial=>{
      const index=cursor++; if (!(index in hooks)) hooks[index]=initial;
      return [hooks[index],value=>hooks[index]=value];
    }, Section:'Section',SettingsCard:'Card',SettingsChoiceRow:'Choice',SettingsInputRow:'Input',
    GlobeIcon:'Globe',SignalIcon:'Signal',SlidersIcon:'Sliders',InfoIcon:'Info',
  });
  const render=()=>{cursor=0; return LatencySection({preferences,onChange:async patch=>changes.push(patch)});};
  const nodes=tree=>[tree,...(Array.isArray(tree?.props?.children)?tree.props.children:[tree?.props?.children]).filter(Boolean).flatMap(nodes)];
  let rows=nodes(render());
  assert.deepEqual(Array.from(rows.find(r=>r.props?.label==='protocol').props.options,o=>o.value), ['nimbo','tcp_connect','icmp','http_get','http_head']);
  const preset=rows.find(r=>r.props?.description==='latencyActiveRouteOnly');
  assert.ok(preset);
  await preset.props.onChange(latency.LATENCY_URL_PRESETS[1].value);
  assert.equal(changes.at(-1).latency_test_url,latency.LATENCY_URL_PRESETS[1].value);
  await preset.props.onChange('custom'); rows=nodes(render());
  const custom=rows.find(r=>r.type==='Input' && r.props.inputMode==='url');
  custom.props.onChange('https://example.com/custom'); rows=nodes(render());
  rows.find(r=>r.type==='Input' && r.props.inputMode==='url').props.onCommit();
  assert.equal(changes.at(-1).latency_test_url,'https://example.com/custom');
  const displayRow=rows.find(r=>r.props?.label==='displayFormat');
  assert.deepEqual(Array.from(displayRow.props.options,o=>o.value), ['numeric','bars','both','dots']);
  await displayRow.props.onChange('dots'); assert.equal(changes.at(-1).latency_display_format,'dots');
});
