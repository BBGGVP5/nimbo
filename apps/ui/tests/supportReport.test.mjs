import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFileSync} from 'node:fs';
import ts from 'typescript';
const code=ts.transpileModule(readFileSync(new URL('../src/lib/supportReport.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
const {buildSupportReport,readSupportSource}=await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);
const secret='SECRET-private-subscription-key';
test('report projects known fields, never whole objects or raw logs',()=>{
 const report=buildSupportReport({version:'1.3.0-beta.1',status:{state:'connected',connection_mode:'tun',server_count:74,subscription_count:1,socks_password:secret,active_subscription_url:secret,active_server_id:secret},device:{os:'Windows',os_version:'10.0.26100',hostname:secret,hwid:secret,user_agent:secret},profiles:{preferred_core:'auto',profiles:[{name:secret}]},logs:[{level:'error',message:secret,source:secret},{level:'info',message:secret},{level:secret}],issue:'connection'},'2026-10-15T12:00:00.000Z');
 assert.equal(report.includes(secret),false);
 const r=JSON.parse(report);assert.equal(r.connection.state,'connected');assert.equal(r.connection.mode,'tun');assert.equal(r.corePreference,'auto');assert.equal(r.issue,'connection');assert.deepEqual(r.logEventCounts,{debug:0,info:1,warn:0,error:1});assert.equal(r.buildRevision,null);
});
test('untrusted enum values, invalid counts and free text are omitted',()=>{
 const r=JSON.parse(buildSupportReport({version:secret,status:{state:secret,connection_mode:secret,server_count:-1,subscription_count:Infinity},device:{os:secret,os_version:secret},profiles:{preferred_core:secret},logs:[],issue:secret},'2026-10-15T12:00:00.000Z'));
 assert.equal(JSON.stringify(r).includes(secret),false);assert.equal(r.appVersion,null);assert.equal(r.connection.state,null);assert.equal(r.serverCount,null);assert.equal(r.issue,'other');
});
test('missing sources are explicit, not zero measurements',()=>{
 const r=JSON.parse(buildSupportReport({issue:'other'},'2026-10-15T12:00:00.000Z'));
 assert.equal(r.logEventCounts,null);assert.equal(r.subscriptionCount,null);assert.equal(r.sources.status,false);
});
test('read failure and deadline never return free text errors',async()=>{
 assert.equal(await readSupportSource(Promise.reject(Error(secret)),10),undefined);
 assert.equal(await readSupportSource(new Promise(()=>{}),10),undefined);
 assert.equal(await readSupportSource(Promise.resolve(5),10),5);
});
