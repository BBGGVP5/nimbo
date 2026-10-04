import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { createServer } from 'vite';
import { chromium } from '../../../installer/node_modules/playwright/index.mjs';

const server=await createServer({server:{hmr:false,host:'127.0.0.1',port:5199,strictPort:true}});await server.listen();
const browser=await chromium.launch({headless:true,...(process.env.NIMBO_CHROMIUM_PATH?{executablePath:process.env.NIMBO_CHROMIUM_PATH}:{})});
const artifacts=process.env.NIMBO_LAYOUT_ARTIFACT_DIR;if(artifacts)await mkdir(artifacts,{recursive:true});
let cases=0;
async function pageFor(viewport,query='') {
 const page=await browser.newPage({viewport,reducedMotion:'reduce'});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  window.nativeCalls=[];window.unexpectedCalls=[];let running=false,now='DIRECT';
  window.__TAURI_INTERNALS__={invoke:async(command,args)=>{
   window.nativeCalls.push([command,args]);
   if(command==='get_core_profiles')return {preferred_core:new URLSearchParams(location.search).has('mihomo')?'mihomo':'auto',active_profile_id:running?'yaml-fixture':null,profiles:[{id:'yaml-fixture',name:'Провайдер · YAML',kind:'mihomo_yaml',revision:1,selections:{},source_digest:'fixture',inspection:null}]};
   if(command==='get_core_availability')return [{core:'mihomo',selector_available:true,binary_verified:true,system_proxy_available:true}];
   if(command==='get_mihomo_status'||command==='connect_mihomo_profile'){if(command==='connect_mihomo_profile')running=true;return {running,profile_id:running?'yaml-fixture':null,session_id:running?'fixture-session':null,native_generation:1,network_owner:running?'desktop-proxy':'none'};}
   if(command==='mihomo_snapshot'||command==='mihomo_select'){if(command==='mihomo_select')now=args.name;return {groups:{VPN:{type:'Selector',now,all:['DIRECT','REJECT']},Auto:{type:'URLTest',now:'node'}},providers:{},ruleProviders:{}};}
   window.unexpectedCalls.push(command);throw Error('Unexpected fixture IPC: '+command);
  }};
 });
 await page.goto('http://127.0.0.1:5199/tests/browser/cross-platform-polish.html?'+query);
 return {page,errors};
}
try {
 for(const width of [360,600,800,900,1280,1440])for(const theme of ['dark','light']) {
  const {page,errors}=await pageFor({width,height:850},'theme='+theme);await page.locator('.universal-connection').waitFor();
  const layout=await page.evaluate(()=>{const box=s=>{const r=document.querySelector(s).getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,bottom:r.bottom,height:r.height};};return {main:box('.app-main'),action:box('.nimbo-connect-action'),power:box('.universal-power'),connection:box('.universal-connection-section'),subs:box('.universal-subscription-section'),nav:box('.app-bottom-nav'),sidebar:box('.signal-rail'),overflow:document.documentElement.scrollWidth>innerWidth};});
  assert(layout.main.width>width*.65,'content stolen by sidebar');assert(layout.subs.y>=layout.connection.bottom,'subscriptions beside connection instead of below');assert(!layout.overflow);
  if(width<960){assert.equal(layout.sidebar.width,0);assert(Math.abs(layout.main.bottom-layout.nav.y)<2,'legacy fixed-bar margin leaves a gap above navigation');assert.equal(await page.locator('.app-bottom-nav-item:visible').count(),4);assert(layout.nav.y>=740&&layout.nav.bottom<=851,'bottom navigation not at window bottom');}
  if(width<=600)assert(layout.action.width>=layout.power.width-2&&layout.action.width>layout.main.width*.8,'mobile connect action is not full width');
  assert(await page.getByText('Описание подписки:',{exact:false}).isVisible());
  const heading=page.locator('.universal-page-heading h1');await heading.dispatchEvent('pointerdown',{pointerId:1,isPrimary:true,button:0,clientX:40,clientY:30,pointerType:'mouse'});await heading.dispatchEvent('pointermove',{pointerId:1,isPrimary:true,clientX:40,clientY:130,pointerType:'mouse'});await heading.dispatchEvent('pointerup',{pointerId:1,isPrimary:true,clientX:40,clientY:130,pointerType:'mouse'});
  await page.waitForFunction(()=>window.polishCalls.length===1);assert.deepEqual(await page.evaluate(()=>window.polishCalls),['refresh:https://fixture.invalid/sub']);
  await page.waitForFunction(()=>document.querySelector('.nimbo-pull-feedback').hidden);
  if(artifacts&&theme==='dark')await page.screenshot({path:resolve(artifacts,`home-${width}.png`)});
  assert.deepEqual(errors,[]);assert.deepEqual(await page.evaluate(()=>window.unexpectedCalls),[]);await page.close();cases++;
 }
 for(const width of [320,1100]) {
  const {page,errors}=await pageFor({width,height:760},'mode=select');const select=page.getByRole('combobox',{name:'Ядро'});await select.focus();
  await page.keyboard.press('ArrowDown');await page.keyboard.press('ArrowDown');await page.keyboard.press('Enter');assert.equal(await page.locator('[data-value]').innerText(),'xray','disabled choice not skipped');
  await select.click();const bounds=await page.getByRole('listbox').boundingBox();assert(bounds.x>=0&&bounds.x+bounds.width<=width&&bounds.y+bounds.height<=760);
  await page.keyboard.press('End');await page.keyboard.press('Escape');assert.equal(await select.getAttribute('aria-expanded'),'false');assert.equal(await page.locator('[data-value]').innerText(),'xray','Escape committed a value');
  await select.focus();await page.keyboard.press('Home');await page.keyboard.press('Enter');assert.equal(await page.locator('[data-value]').innerText(),'auto');
  await select.click();await page.getByRole('button',{name:'Снаружи'}).click();assert.equal(await select.getAttribute('aria-expanded'),'false');assert.equal(await page.locator('select').count(),0);
  assert.deepEqual(errors,[]);await page.close();cases++;
 }
 const {page:profile,errors:profileErrors}=await pageFor({width:800,height:850},'mode=profiles');await profile.locator('.parity-server-row.is-active').waitFor();
 const row=await profile.locator('.parity-server-row.is-active').evaluate(e=>({border:getComputedStyle(e).borderWidth,width:e.getBoundingClientRect().width,parent:e.parentElement.getBoundingClientRect().width}));assert.notEqual(row.border,'0px');assert(Math.abs(row.width-row.parent)<3,'selection does not cover full row');assert.deepEqual(profileErrors,[]);await profile.close();cases++;
 const {page:core,errors:coreErrors}=await pageFor({width:800,height:850},'mihomo=1');await core.getByRole('button',{name:/Подключ/}).first().click();
 await core.waitForFunction(()=>window.nativeCalls.some(([cmd])=>cmd==='connect_mihomo_profile'));
 assert.deepEqual(await core.evaluate(()=>window.nativeCalls.find(([cmd])=>cmd==='connect_mihomo_profile')[1]),{profileId:'yaml-fixture'});
 assert.deepEqual(await core.evaluate(()=>window.polishCalls.filter(call=>call.startsWith('legacy:'))),[]);
 await core.locator('.universal-subscription-toggle').click();const group=core.getByRole('combobox',{name:'VPN',exact:true});await group.waitFor();await group.scrollIntoViewIfNeeded();await core.waitForTimeout(150);await group.click();await core.getByRole('option',{name:'REJECT',exact:true}).click();
 await core.waitForFunction(()=>window.nativeCalls.some(([cmd])=>cmd==='mihomo_select'));assert.equal(await group.innerText(),'REJECT');assert.deepEqual(coreErrors,[]);assert.deepEqual(await core.evaluate(()=>window.unexpectedCalls),[]);await core.close();cases++;
 const {page:tray,errors:trayErrors}=await pageFor({width:360,height:760},'mode=tray');await tray.locator('.tray-card').waitFor();await tray.waitForTimeout(100);
 const trayBounds=await tray.locator('.tray-card').boundingBox();assert(trayBounds.width<=328&&trayBounds.height<590,'tray remains oversized');assert.equal(await tray.locator('.tray-utility-grid,.tray-maintenance').count(),0);
 if(artifacts)await tray.screenshot({path:resolve(artifacts,'tray.png')});assert.deepEqual(trayErrors,[]);await tray.close();cases++;
 console.log(`PASS: ${cases} shell/home/profile/dropdown/core/tray browser cases (isolated IPC)`);
} finally {await browser.close();await server.close();}
