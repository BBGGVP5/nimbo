import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import { useState } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import * as jsx from 'react/jsx-runtime';
import ts from 'typescript';

const source=readFileSync(new URL('../src/pages/Settings.tsx',import.meta.url),'utf8');
const file=ts.createSourceFile('Settings.tsx',source,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
const discoveryCode=ts.transpileModule(readFileSync(new URL('../src/lib/settingsDiscovery.ts',import.meta.url),'utf8'),{
  compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022},
}).outputText;
const {SETTINGS_ADVANCED_KEY,readSettingsAdvanced,writeSettingsAdvanced,matchesSettingsEntry}=
  await import(`data:text/javascript;base64,${Buffer.from(discoveryCode).toString('base64')}`);
function renderFunction(name,args,globals) {
  const node=file.statements.find(n=>ts.isFunctionDeclaration(n)&&n.name?.text===name);
  const js=ts.transpileModule(node.getText(file),{compilerOptions:{jsx:ts.JsxEmit.ReactJSX,module:ts.ModuleKind.ESNext}}).outputText.replace(/^import .*?;\r?\n/gm,'');
  const component=new Function('useState',...Object.keys(globals),'_jsx','_jsxs','_Fragment',js+`;return ${name};`)(useState,...Object.values(globals),jsx.jsx,jsx.jsxs,jsx.Fragment);
  return renderToStaticMarkup(jsx.jsx(component,args));
}
function renderOverview() {
  const ids=['general','appearance','connection','tunnel','lan','subscriptions','servers','latency','backup','updates','about'];
  return renderFunction('SettingsOverview',{preferences:{latency_protocol:'nimbo'},version:'fixture',onSelect:()=>{}},{
    readSettingsAdvanced,writeSettingsAdvanced,matchesSettingsEntry,
    useMessages:()=>({common:{locale:'en'},settings:Object.fromEntries(ids.map(id=>[id,id])),app:{routing:'Routing',modules:'Modules',apps:'Applications',sync:'Sync',notifications:'Notifications',connections:'Connections',statistics:'Statistics',tunnelLogs:'Logs'}}),
    sectionItems:ids.map(id=>({id,labelKey:id,icon:null})),
    Link:({to,children,...props})=>jsx.jsx('a',{...props,href:to,children}),
    useCoreStore:selector=>selector({data:{preferred_core:'auto'}}),
    ListIcon:()=>null,RouteIcon:()=>null,RefreshIcon:()=>null,ConnectionsIcon:()=>null,InfoIcon:()=>null,StatsBarsIcon:()=>null,LogsIcon:()=>null,
  });
}

function withSettingsStorage(storage,run) {
  const previous=Object.getOwnPropertyDescriptor(globalThis,'localStorage');
  Object.defineProperty(globalThis,'localStorage',{configurable:true,value:storage});
  try { return run(); }
  finally {
    if (previous) Object.defineProperty(globalThis,'localStorage',previous);
    else delete globalThis.localStorage;
  }
}

test('overview updates directly precedes about, without duplicate destinations',()=>{
  for (const advanced of [false,true]) withSettingsStorage({getItem:key=>{
    assert.equal(key,SETTINGS_ADVANCED_KEY);
    return String(advanced);
  }},()=>{
    const html=renderOverview();
    const application=html.match(/<h2>Application<\/h2>(.*?)<\/section>/)?.[1];
    assert(application,'application group absent');
    assert.equal([...html.matchAll(/<h2>(.*?)<\/h2>/g)].at(-1)[1],'Application');
    const titles=[...application.matchAll(/<strong>(.*?)<\/strong>/g)].map(match=>match[1]);
    assert.deepEqual(titles.slice(-2),['updates','about']);
    assert.equal((html.match(/<strong>updates<\/strong>/g)||[]).length,1);
    assert.equal((html.match(/<strong>about<\/strong>/g)||[]).length,1);
  });
});

test('overview defaults to simple mode with absent or unavailable storage',()=>{
  for (const storage of [undefined,{getItem:()=>null},{getItem:()=>{throw Error('blocked');}}]) {
    withSettingsStorage(storage,()=>{
      const html=renderOverview();
      assert.ok(html.includes('aria-pressed="true">Simple</button>'));
      assert.ok(html.includes('aria-pressed="false">Advanced</button>'));
      for (const title of ['connection','subscriptions','backup','latency','Logs','updates','about']) {
        assert.ok(html.includes(`<strong>${title}</strong>`),`Missing simple destination: ${title}`);
      }
      for (const title of ['tunnel','lan','servers','Modules','Connections']) {
        assert.ok(!html.includes(`<strong>${title}</strong>`),`Advanced destination shown in simple mode: ${title}`);
      }
      assert.ok(html.includes('type="search"'));
      assert.ok(html.includes('value=""'));
      assert.doesNotMatch(html,/No results/);
    });
  }
});

test('overview restores advanced destinations without changing stored preferences',()=>{
  const values=new Map([[SETTINGS_ADVANCED_KEY,'true'],['vpn.dns','unchanged']]);
  const before=[...values];
  let writes=0;
  withSettingsStorage({getItem:key=>values.get(key)??null,setItem:(key,value)=>{
    writes++;
    values.set(key,value);
  }},()=>{
    const html=renderOverview();
    assert.ok(html.includes('aria-pressed="false">Simple</button>'));
    assert.ok(html.includes('aria-pressed="true">Advanced</button>'));
    for (const title of ['tunnel','lan','servers','Modules','Connections','updates','about']) {
      assert.ok(html.includes(`<strong>${title}</strong>`),`Missing advanced destination: ${title}`);
    }
    assert.equal(writes,0);
    assert.deepEqual([...values],before);
  });
});

test('latency URL description is separate from its full-width choice row',()=>{
  const html=renderFunction('SettingsChoiceRow',{label:'Test URL',description:'Small explanation',value:'a',options:[{value:'a',label:'A'},{value:'b',label:'B'}],onChange:async()=>{}},{});
  assert.match(html,/class="settings-row settings-choice-row"/);
  assert.match(html,/aria-pressed="true"/);
  const latency=file.statements.find(n=>ts.isFunctionDeclaration(n)&&n.name?.text==='LatencySection').getText(file);
  assert.match(latency,/<details className="latency-routing-help">/);
  assert.doesNotMatch(latency,/description={m.settings.latencyActiveRouteOnly}/);
  assert.doesNotMatch(latency,/<CorePreferenceSetting/);
});
