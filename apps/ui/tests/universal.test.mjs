import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';
import React from 'react';
import * as jsx from 'react/jsx-runtime';
import { renderToStaticMarkup } from 'react-dom/server';
const compile=p=>ts.transpileModule(readFileSync(new URL(p,import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,target:ts.ScriptTarget.ES2020}}).outputText;
const icon={};vm.runInNewContext(compile('../src/components/ConnectionStateIcon.tsx'),{exports:icon,require:()=>jsx});
test('approved cloud is used only for confirmed connected, never transition',()=>{
 for(const connected of [false,true])for(const busy of [false,true]){
  const html=renderToStaticMarkup(React.createElement(icon.ConnectionStateIcon,{connected,busy}));
  assert(html.includes(`data-connection-icon="${busy?'loading':connected?'cloud':'power'}"`));
  if(connected&&!busy){const template=readFileSync(new URL('../../../assets/branding/1.3.0-beta.1/cloud-template.svg',import.meta.url),'utf8');assert(html.includes(template.match(/ d="([^"]+)"/)[1]));}
 }
});
function phraseFixture(reduced=false){let index=0,effect,cleanup,hidden=false,match=reduced,timer,intervalMs;const events=new Map(),motionEvents=new Map();const motion={get matches(){return match},addEventListener:(n,f)=>motionEvents.set(n,f),removeEventListener:n=>motionEvents.delete(n)};const window={matchMedia:()=>motion,setInterval:(f,ms)=>{timer=f;intervalMs=ms;return 1},clearInterval:()=>timer=undefined};const document={get hidden(){return hidden},addEventListener:(n,f)=>events.set(n,f),removeEventListener:n=>events.delete(n)};const exports={};vm.runInNewContext(compile('../src/components/OperationPhrase.tsx'),{exports,window,document,require:n=>n==='react'?{useState:()=>[index,v=>index=typeof v==='function'?v(index):v],useEffect:f=>effect=f}:jsx});
 return {render(active=true){return exports.OperationPhrase({active,locale:'ru'})},start(){cleanup=effect()},stop(){cleanup?.()},tick(){timer?.()},setReduced(v){match=v;motionEvents.get('change')?.()},setHidden(v){hidden=v;events.get('visibilitychange')?.()},get timer(){return timer},get ms(){return intervalMs},get listeners(){return events.size+motionEvents.size}};
}
test('secondary phrase rotates every 4.5s without accessible announcements and cleans up',()=>{const f=phraseFixture();f.render();f.start();assert.equal(f.ms,4500);assert.equal(f.render().props.children,'Прокладываем путь…');f.tick();const next=f.render();assert.equal(next.props.children,'Готовим облако…');assert.equal(next.props['aria-hidden'],'true');assert.equal(next.props['aria-live'],'off');f.stop();assert.equal(f.timer,undefined);assert.equal(f.listeners,0);assert.equal(f.render(false),null);f.start();assert.equal(f.timer,undefined);});
test('reduced motion stays static; hidden pages pause and restore phrase timer',()=>{const f=phraseFixture(true);f.render();f.start();assert.equal(f.timer,undefined);f.setReduced(false);assert(f.timer);f.setHidden(true);assert.equal(f.timer,undefined);f.setHidden(false);assert(f.timer);f.setReduced(true);assert.equal(f.timer,undefined);f.stop();assert.equal(f.listeners,0);});

test('context menu placement uses measured wrapped-content height and clamps all viewport edges',()=>{
 const exports={};vm.runInNewContext(compile('../src/components/Universal.tsx'),{exports,require:n=>n==='react'?React:n==='react-dom'?{createPortal:v=>v}:jsx});
 for(const [width,height] of [[360,480],[1440,420]])for(const right of [0,20,width-10,width+100])for(const bottom of [0,height-10,height+100]){
  const panel={width:220,height:Math.min(350,height-24)};const pos=exports.menuPosition({right,bottom},panel,{width,height});
  assert(pos.left>=12&&pos.top>=12);assert(pos.left+panel.width<=width-12);assert(pos.top+panel.height<=height-12);
 }
 const short=exports.menuPosition({right:350,bottom:460},{width:220,height:130},{width:360,height:480});
 const wrapped=exports.menuPosition({right:350,bottom:460},{width:220,height:230},{width:360,height:480});
 assert.equal(short.top-wrapped.top,100);
});
