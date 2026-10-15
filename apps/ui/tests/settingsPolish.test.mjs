import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import { renderToStaticMarkup } from 'react-dom/server';
import * as jsx from 'react/jsx-runtime';
import ts from 'typescript';

const source=readFileSync(new URL('../src/pages/Settings.tsx',import.meta.url),'utf8');
const file=ts.createSourceFile('Settings.tsx',source,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
function renderFunction(name,args,globals) {
  const node=file.statements.find(n=>ts.isFunctionDeclaration(n)&&n.name?.text===name);
  const js=ts.transpileModule(node.getText(file),{compilerOptions:{jsx:ts.JsxEmit.ReactJSX,module:ts.ModuleKind.ESNext}}).outputText.replace(/^import .*?;\r?\n/gm,'');
  const component=new Function(...Object.keys(globals),'_jsx','_jsxs','_Fragment',js+`;return ${name};`)(...Object.values(globals),jsx.jsx,jsx.jsxs,jsx.Fragment);
  return renderToStaticMarkup(jsx.jsx(component,args));
}
test('overview updates directly precedes about, without duplicate destinations',()=>{
  const ids=['general','appearance','connection','tunnel','lan','subscriptions','servers','latency','backup','updates','about'];
  const html=renderFunction('SettingsOverview',{preferences:{latency_protocol:'nimbo'},version:'fixture',onSelect:()=>{}},{
    useMessages:()=>({common:{locale:'en'},settings:Object.fromEntries(ids.map(id=>[id,id])),app:{routing:'Routing',apps:'Applications',sync:'Sync',notifications:'Notifications',connections:'Connections',statistics:'Statistics',tunnelLogs:'Logs'}}),
    sectionItems:ids.map(id=>({id,labelKey:id,icon:null})),
    Link:({to,children,...props})=>jsx.jsx('a',{...props,href:to,children}),
    useCoreStore:selector=>selector({data:{preferred_core:'auto'}}),
    ListIcon:()=>null,RouteIcon:()=>null,RefreshIcon:()=>null,ConnectionsIcon:()=>null,InfoIcon:()=>null,StatsBarsIcon:()=>null,LogsIcon:()=>null,
  });
  const application=html.match(/<h2>Application<\/h2>(.*?)<\/section>/)?.[1];
  assert(application,'application group absent');
  assert.equal([...html.matchAll(/<h2>(.*?)<\/h2>/g)].at(-1)[1],'Application');
  const titles=[...application.matchAll(/<strong>(.*?)<\/strong>/g)].map(match=>match[1]);
  assert.deepEqual(titles.slice(-2),['updates','about']);
  assert.equal((html.match(/<strong>updates<\/strong>/g)||[]).length,1);
  assert.equal((html.match(/<strong>about<\/strong>/g)||[]).length,1);
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
