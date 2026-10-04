import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
const js=ts.transpileModule(readFileSync(new URL('../src/components/core-subscription-groups.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
const { subscriptionGroups }=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'));
const profile={source_digest:'sha',selections:{VPN:'FI'},inspection:{api:1,sourceDigest:'sha',nativeValidated:false,issues:[],graph:{groups:[{name:'VPN',type:'select',proxies:['FI','Auto']},{name:'Auto',type:'url-test',proxies:['FI','DE']},{name:'Secret',hidden:true,type:'select',proxies:['FI']}]}}};
test('disconnected preview uses digest-matched inspection without claiming runtime validation',()=>{
 const groups=subscriptionGroups(profile,null,false);
 assert.deepEqual(groups.map(([name])=>name),['VPN','Auto']);
 assert.deepEqual(groups[0][1],{type:'Selector',all:['FI','Auto'],now:'FI'});
 assert.equal(groups[1][1].now,undefined,'never invent the automatically selected node');
});
test('stale, unsupported, invalid and absent inspections never invent groups',()=>{
 for(const inspection of [null,{...profile.inspection,sourceDigest:'old'},{...profile.inspection,api:2},{...profile.inspection,issues:[{code:'INVALID_GRAPH'}]},{...profile.inspection,graph:{groups:[null,{name:'broken',proxies:'FI'}]}}])assert.deepEqual(subscriptionGroups({...profile,inspection},null,false),[]);
});
test('matching running session uses actual controller members only and hides hidden groups',()=>{
 const groups=subscriptionGroups(profile,{groups:{VPN:{type:'Selector',now:'DE',all:['FI','DE']},Hidden:{hidden:true}}},true);
 assert.equal(groups[0][1].now,'DE');assert.deepEqual(groups[0][1].all,['FI','DE']);assert.equal(groups.length,1);
 assert.deepEqual(subscriptionGroups(profile,null,true),[],'never substitute offline preview for live snapshot');
});
