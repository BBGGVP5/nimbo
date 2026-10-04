import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import ts from 'typescript';
const js=ts.transpileModule(readFileSync(new URL('../src/components/core-subscription-groups.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
const { subscriptionGroups, subscriptionMemberDetails }=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'));
const profile={source_digest:'sha',selections:{VPN:'FI'},inspection:{api:1,sourceDigest:'sha',nativeValidated:false,issues:[],graph:{groups:[{name:'VPN',type:'select',proxies:['FI','Auto']},{name:'Auto',type:'url-test',proxies:['FI','DE']},{name:'Secret',hidden:true,type:'select',proxies:['FI']}]}}};
test('disconnected preview uses digest-matched inspection without claiming runtime validation',()=>{
 const groups=subscriptionGroups(profile,null,false);
 assert.deepEqual(groups.map(([name])=>name),['VPN','Auto']);
 assert.deepEqual(groups[0][1],{type:'Selector',all:['FI','Auto'],now:'FI'});
 assert.equal(groups[1][1].now,undefined,'never invent the automatically selected node');
});
test('stale, unsupported, malformed and absent inspections never invent groups',()=>{
 for(const inspection of [null,{...profile.inspection,sourceDigest:'old'},{...profile.inspection,api:2},{...profile.inspection,issues:null},{...profile.inspection,graph:{groups:null}},{...profile.inspection,graph:{groups:[null,{name:'broken',proxies:'FI'}]}}])assert.deepEqual(subscriptionGroups({...profile,inspection},null,false),[]);
});
test('actual native inspection issues never erase declared read-only categories',()=>{
 const fixture=JSON.parse(readFileSync(new URL('./fixtures/mihomo-warning-inspection.json',import.meta.url),'utf8'));
 const declared={source_digest:fixture.inspection.sourceDigest,selections:{},inspection:fixture.inspection};
 assert.equal(createHash('sha256').update(fixture.source).digest('hex'),declared.source_digest);
 assert.equal(declared.inspection.issues.length,2);
 assert.deepEqual(subscriptionGroups(declared,null,false).map(([name])=>name),['VPN','Streaming','Auto']);
 assert.equal(declared.inspection.nativeValidated,false);
 assert(subscriptionGroups(declared,null,false).every(([,group])=>group.now===undefined),'inspection is not an active selection');
 assert.deepEqual(subscriptionGroups({...declared,source_digest:'changed-source'},null,false),[]);
});
test('matching running session uses actual controller members only and hides hidden groups',()=>{
 const groups=subscriptionGroups(profile,{groups:{VPN:{type:'Selector',now:'DE',all:['FI','DE']},Hidden:{hidden:true}}},true);
 assert.equal(groups[0][1].now,'DE');assert.deepEqual(groups[0][1].all,['FI','DE']);assert.equal(groups.length,1);
 assert.deepEqual(subscriptionGroups(profile,null,true),[],'never substitute offline preview for live snapshot');
});
test('proxy cards use only current sanitized member metadata and real nested selections',()=>{
 const described={...profile,inspection:{...profile.inspection,graph:{...profile.inspection.graph,proxies:[{name:'FI',type:'vless',password:'not-display-metadata'}]}}};
 assert.deepEqual(subscriptionMemberDetails(described,null,false,'FI'),{type:'vless'});
 assert.deepEqual(subscriptionMemberDetails(described,null,false,'Auto'),{type:'url-test'});
 assert.deepEqual(subscriptionMemberDetails({...described,source_digest:'changed'},null,false,'FI'),{});
 assert.deepEqual(subscriptionMemberDetails(described,null,false,'DIRECT'),{type:'Direct'});
 assert.deepEqual(subscriptionMemberDetails(described,null,false,'REJECT'),{type:'Reject'});
 assert.deepEqual(subscriptionMemberDetails(described,null,false,'Unknown'),{});
 const snapshot={groups:{Auto:{type:'URLTest',now:'FI'}},providers:{Nodes:{proxies:[{name:'FI',type:'Trojan'}]}},ruleProviders:{}};
 assert.deepEqual(subscriptionMemberDetails(described,snapshot,true,'Auto'),{type:'URLTest',now:'FI'});
 assert.deepEqual(subscriptionMemberDetails(described,snapshot,true,'FI'),{type:'Trojan'});
 assert.deepEqual(subscriptionMemberDetails(described,snapshot,false,'FI'),{type:'vless'},'ignore an unrelated runtime while previewing this profile');
});
