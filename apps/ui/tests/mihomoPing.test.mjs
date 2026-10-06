import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
const js=ts.transpileModule(readFileSync(new URL('../src/lib/mihomoPing.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
const {MihomoPingQueue}=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'));
const deferred=()=>{let resolve;const promise=new Promise(r=>{resolve=r;});return {promise,resolve};};
const setup=()=>{const states=[];const queue=new MihomoPingQueue(s=>states.push(s));queue.setContext('one');return {queue,states,last:()=>states.at(-1)};};

test('category ping is sequential, deduplicated and accepts real zero latency',async()=>{
 const {queue,last}=setup(),first=deferred(),names=[];
 const work=queue.run(['FI','DIRECT','FI'],async name=>{names.push(name);return name==='FI'?first.promise:{delayMs:0};},()=>true);
 assert.deepEqual(names,['FI']);assert.equal(last().running,true);assert.equal(last().results.get('DIRECT').state,'pending');
 await queue.run(['DE'],async()=>{throw Error('must not dispatch twice');},()=>true);
 first.resolve({delayMs:42});await work;
 assert.deepEqual(names,['FI','DIRECT']);assert.deepEqual(last().results.get('FI'),{state:'success',ms:42});assert.deepEqual(last().results.get('DIRECT'),{state:'success',ms:0});assert.equal(last().running,false);
});
test('invalid native replies and remote errors never become latency or raw UI text',async()=>{
 for(const delayMs of [-1,NaN,Infinity,'42',1.5,65536]){
  const {queue,last}=setup();await queue.run(['FI'],async()=>({delayMs}),()=>true);assert.deepEqual(last().results.get('FI'),{state:'error',reason:'unavailable'});
 }
 const {queue,last}=setup();await queue.run(['Timeout','Secret'],async name=>{throw name==='Timeout'?Error('PROBE_TIMEOUT'):Error('DELAY_FAILED https://secret.invalid credential');},()=>true);
 assert.deepEqual(last().results.get('Timeout'),{state:'error',reason:'timeout'});assert.deepEqual(last().results.get('Secret'),{state:'error',reason:'unavailable'});
 assert(!JSON.stringify([...last().results]).includes('secret'));
});
test('stop discards pending replies and prevents remaining batch requests',async()=>{
 const {queue,last}=setup(),pending=deferred(),names=[];
 await queue.run(['done'],async()=>({delayMs:12}),()=>true);
 const work=queue.run(['FI','DE'],name=>{names.push(name);return pending.promise;},()=>true);
 queue.cancel();assert.equal(last().running,false);assert(!last().results.has('FI'));assert(!last().results.has('DE'));assert.equal(last().results.get('done').ms,12);
 assert.equal(last().waiting,true);await queue.run(['new'],async()=>{throw Error('replacement flooded native lock');},()=>true);
 pending.resolve({delayMs:100});await work;assert.deepEqual(names,['FI']);assert(!last().results.has('FI'));assert.equal(last().waiting,false);
});
test('context replacement hides old results and ignores an old completion during a new run',async()=>{
 const {queue,last}=setup(),old=deferred(),next=deferred();
 const work=queue.run(['FI'],()=>old.promise,()=>true);queue.setContext('two');assert.equal(last().results.size,0);
 old.resolve({delayMs:2});await work;assert.equal(last().results.size,0);assert.equal(last().waiting,false);
 const newWork=queue.run(['FI'],()=>next.promise,()=>true);assert.equal(last().running,true);assert.equal(last().results.get('FI').state,'pending');
 next.resolve({delayMs:7});await newWork;assert.equal(last().results.get('FI').ms,7);queue.setContext(null);assert.equal(last().results.size,0);
 await queue.run(['FI'],()=>{throw Error('offline dispatched');},()=>true);assert.equal(last().results.size,0);
});
test('changed native identity clears results, aborts queue and never publishes stale success',async()=>{
 const {queue,last}=setup(),pending=deferred(),names=[];let current=true;
 const work=queue.run(['FI','DE'],name=>{names.push(name);return pending.promise;},()=>current);
 current=false;pending.resolve({delayMs:5});await work;assert.deepEqual(names,['FI']);assert.equal(last().running,false);assert.equal(last().results.size,0);
 current=true;await queue.run(['FI'],async()=>{throw Error('STALE_GENERATION');},()=>current);assert.equal(last().results.size,0);
});
test('retry replaces only requested names and special member names are safe map keys',async()=>{
 const {queue,last}=setup();await queue.run(['FI','__proto__','constructor'],async()=>({delayMs:20}),()=>true);
 await queue.run(['FI'],async()=>({delayMs:8}),()=>true);assert.equal(last().results.get('FI').ms,8);assert.equal(last().results.get('__proto__').ms,20);assert.equal(last().results.get('constructor').ms,20);
});
test('offline source replacement or child cancellation discards the batch',async()=>{
 for(const code of ['STALE_REVISION','SOURCE_DIGEST_MISMATCH','PROBE_CANCELLED']){
  const {queue,last}=setup(),names=[];await queue.run(['FI','DE'],async name=>{names.push(name);throw Error(code);},()=>true);
  assert.deepEqual(names,['FI']);assert.equal(last().results.size,0);assert.equal(last().waiting,false);
 }
});
