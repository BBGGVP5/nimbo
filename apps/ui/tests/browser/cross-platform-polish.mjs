import assert from 'node:assert/strict';
import { mkdir, readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { createServer } from 'vite';
import { chromium } from '../../../installer/node_modules/playwright/index.mjs';

const server=await createServer({server:{hmr:false,host:'127.0.0.1',port:5199,strictPort:true}});await server.listen();
const browser=await chromium.launch({headless:true,...(process.env.NIMBO_CHROMIUM_PATH?{executablePath:process.env.NIMBO_CHROMIUM_PATH}:{})});
const artifacts=process.env.NIMBO_LAYOUT_ARTIFACT_DIR;if(artifacts)await mkdir(artifacts,{recursive:true});
let cases=0;
const warningInspection=JSON.parse(await readFile(new URL('../fixtures/mihomo-warning-inspection.json',import.meta.url),'utf8')).inspection;
const tabInspection=JSON.parse(await readFile(new URL('../fixtures/mihomo-tabbed-inspection.json',import.meta.url),'utf8'));
async function pageFor(viewport,query='') {
 const page=await browser.newPage({viewport,reducedMotion:'reduce'});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(({warningInspection,tabInspection})=>{
  window.nativeCalls=[];window.unexpectedCalls=[];let running=new URLSearchParams(location.search).has('running'),now='DIRECT',session='fixture-session',generation=1;const selectedMembers={};
  window.setNativeSession=id=>{session=id;generation++;};window.fixturePingActive=0;window.fixturePingMax=0;window.fixturePingWaiters=[];window.releaseFixturePing=()=>window.fixturePingWaiters.shift()?.();
  const params=new URLSearchParams(location.search);let inspected=!(params.has('inspect')||params.has('inspectFail')||params.has('stale')),failedInspection=false,updatedInspected=false;
  const profile=()=>window.fixtureCoreUpdated?{id:'yaml-fixture',name:'Провайдер · YAML',kind:'mihomo_yaml',revision:2,selections:{},source_digest:'updated-fixture',inspection:updatedInspected?{api:1,sourceDigest:'updated-fixture',nativeValidated:false,issues:[],graph:{groups:[{name:'Updated category',type:'select',proxies:['Updated node']}]}}:null}:({id:'yaml-fixture',name:'Провайдер · YAML',kind:'mihomo_yaml',revision:1,selections:{},source_digest:params.has('warning')?warningInspection.sourceDigest:'fixture',inspection:!inspected?null:params.has('warning')?warningInspection:{api:1,sourceDigest:'fixture',nativeValidated:false,issues:[],graph:{groups:params.has('empty')?[]:[{name:'VPN',type:'select',proxies:['DIRECT','REJECT']},{name:'Auto',type:'url-test',proxies:['node']}]}}});
  const currentProfile=()=>params.has('cards')?{...profile(),source_digest:tabInspection.sourceDigest,inspection:tabInspection}:profile();
  if(new URLSearchParams(location.search).get('mode')==='notifications') {
   const time=Date.now();localStorage.setItem('nimbo.notifications.lastSeen',String(time-3600000));
   localStorage.setItem('nimbo.notifications.history',JSON.stringify([{id:'one',tone:'info',message:'Настройки сохранены.',createdAt:time},{id:'two',tone:'error',message:'Длинное сообщение: '+('Проверьте подключение и повторите попытку. ').repeat(15),createdAt:time-86400000},{id:'three',tone:'success',message:'Подписка обновлена.',createdAt:time-10*86400000}]));
  }
  window.__TAURI_INTERNALS__={invoke:async(command,args)=>{
   window.nativeCalls.push([command,args]);
   if(command==='inspect_core_profile'){if(params.has('inspectFail')&&!failedInspection){failedInspection=true;throw Error('NATIVE_INSPECTION_FAILED');}inspected=true;if(window.fixtureCoreUpdated)updatedInspected=true;return profile();}
   if(command==='get_core_profiles')return {preferred_core:params.has('mihomo')?'mihomo':'auto',active_profile_id:running?'yaml-fixture':null,profiles:[currentProfile()]};
   if(command==='get_core_availability')return [{core:'mihomo',selector_available:true,binary_verified:true,system_proxy_available:true}];
   if(command==='get_mihomo_status'||command==='connect_mihomo_profile'){if(command==='connect_mihomo_profile'){if(params.has('warning'))throw Error('UNSUPPORTED_FIELD');running=true;}return {running,profile_id:running?'yaml-fixture':null,session_id:running?session:null,native_generation:generation,network_owner:running?'desktop-proxy':'none'};}
   if(command==='mihomo_delay'){
    window.fixturePingActive++;window.fixturePingMax=Math.max(window.fixturePingMax,window.fixturePingActive);
    try{
     if(params.has('deferPing'))await new Promise(resolve=>window.fixturePingWaiters.push(resolve));
     else await new Promise(resolve=>setTimeout(resolve,30));
     if(params.has('nativeChanged')){session='new-fixture-session';generation++;}
     if(params.has('pingErrors')&&args.name.includes('XHTTP'))throw Error('PROBE_TIMEOUT');
     if(params.has('pingErrors')&&args.name.includes('gRPC'))throw Error('DELAY_FAILED https://secret.invalid credential');
     return {delayMs:params.has('invalidPing')?-1:args.name==='DIRECT'?0:79};
    }finally{window.fixturePingActive--;}
   }
   if(params.has('cards')&&(command==='mihomo_snapshot'||command==='mihomo_select')){
    if(command==='mihomo_select')selectedMembers[args.group]=args.name;
    return {groups:Object.fromEntries(tabInspection.graph.groups.map(group=>[group.name,{type:group.type==='select'?'Selector':group.type==='url-test'?'URLTest':'Fallback',hidden:group.hidden,all:group.proxies,now:selectedMembers[group.name]??(group.proxies.includes('DIRECT')?'DIRECT':group.proxies[0])}])),providers:{},ruleProviders:{}};
   }
   if(command==='mihomo_snapshot'||command==='mihomo_select'){if(command==='mihomo_select'){if(new URLSearchParams(location.search).has('selectFail'))throw Error('READBACK_MISMATCH');now=args.name;}return {groups:{VPN:{type:'Selector',now,all:['DIRECT','REJECT']},Auto:{type:'URLTest',now:'node',all:['node']}},providers:{},ruleProviders:{}};}
   window.unexpectedCalls.push(command);throw Error('Unexpected fixture IPC: '+command);
  }};
 },{warningInspection,tabInspection});
 await page.goto('http://127.0.0.1:5199/tests/browser/cross-platform-polish.html?'+query);
 return {page,errors};
}
try {
 const {page:refreshProfile,errors:refreshErrors}=await pageFor({width:1440,height:850},'mode=profiles&mihomo=1');
 await refreshProfile.getByRole('tab',{name:'VPN',exact:true}).waitFor({timeout:5000});await refreshProfile.evaluate(()=>window.refreshFixtureSubscription());
 await refreshProfile.getByRole('group',{name:'Updated category',exact:true}).waitFor({timeout:5000});
 assert.equal(await refreshProfile.getByRole('tab').count(),1);assert(await refreshProfile.getByRole('button',{name:'Updated category: Updated node',exact:true}).isDisabled());
 assert(!(await refreshProfile.evaluate(()=>window.nativeCalls)).some(([cmd])=>cmd==='connect_mihomo_profile'||cmd==='mihomo_select'),'subscription refresh activated the native core');
 assert.deepEqual(refreshErrors,[]);assert.deepEqual(await refreshProfile.evaluate(()=>window.unexpectedCalls),[]);await refreshProfile.close();cases++;
 for(const width of [320,360,800,1440])for(const theme of ['dark','light']) {
  const {page,errors}=await pageFor({width,height:1100},`mode=profiles&mihomo=1&cards=1&announcement=1&theme=${theme}`);
  const first=page.getByRole('tab',{name:'🚫 Недоступные сайты',exact:true});await first.waitFor();
  assert.equal(await page.getByRole('tab').count(),5);assert.equal(await first.getAttribute('aria-selected'),'true');assert.equal(await page.getByRole('tabpanel').count(),1);
  assert.equal(await page.locator('.core-subscription-member').count(),8);
  assert.equal(await page.locator('.core-proxy-card-name .fi-fi').count(),6,'Windows country flags use missing emoji glyphs');
  assert.equal(await page.locator('.core-subscription-control details,.core-subscription-inspection').count(),0);
  assert.equal(await page.getByRole('button',{name:'Проверить профиль',exact:true}).count(),0);assert.equal(await page.getByText('Выбор сервера доступен после подключения.',{exact:true}).count(),0);
  const layout=await page.evaluate(()=>{const cards=[...document.querySelectorAll('.core-subscription-member')].map(e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height};});return {cards,overflow:document.documentElement.scrollWidth>innerWidth};});
  assert(!layout.overflow);assert(layout.cards.every(c=>Math.abs(c.h-layout.cards[0].h)<1&&c.h>=100&&c.h<=140&&Math.abs(c.w-layout.cards[0].w)<1));
  if(width<=360){assert(Math.abs(layout.cards[0].y-layout.cards[1].y)<1);assert(layout.cards[1].x>layout.cards[0].x);assert(layout.cards[2].y>layout.cards[0].y,'mobile cards are not two columns');}
  if(artifacts)await page.locator('.core-subscription-control').screenshot({path:resolve(artifacts,`mihomo-tabs-${width}-${theme}.png`)});
  await page.getByRole('tab',{name:'▶️ YouTube',exact:true}).click();assert.equal(await page.getByRole('tabpanel').getAttribute('aria-labelledby'),await page.getByRole('tab',{name:'▶️ YouTube',exact:true}).getAttribute('id'));assert.equal(await page.locator('.core-subscription-member').count(),2);
  await page.getByRole('tab',{name:'▶️ YouTube',exact:true}).focus();await page.keyboard.press('ArrowRight');assert.equal(await page.getByRole('tab',{name:'💬 Discord',exact:true}).getAttribute('aria-selected'),'true');
  await page.keyboard.press('Home');assert.equal(await first.getAttribute('aria-selected'),'true');await page.keyboard.press('End');assert.equal(await page.getByRole('tab').last().getAttribute('aria-selected'),'true');
  const menu=page.getByRole('combobox',{name:'Категория',exact:true});await menu.click();await page.getByRole('option',{name:'🤖 AI / Gemini',exact:true}).click();assert.equal(await page.getByRole('tab',{name:'🤖 AI / Gemini',exact:true}).getAttribute('aria-selected'),'true');
  assert.equal(await page.getByRole('tabpanel').count(),1);assert.equal(await page.locator('.core-subscription-member:not(:disabled)').count(),0);assert.equal(await page.locator('.core-subscription-member[aria-pressed="true"]').count(),0);
  assert(!(await page.evaluate(()=>window.nativeCalls)).some(([cmd])=>['connect_mihomo_profile','mihomo_select','inspect_core_profile'].includes(cmd)),'category navigation caused native actions');
  assert.deepEqual(errors,[]);assert.deepEqual(await page.evaluate(()=>window.unexpectedCalls),[]);await page.close();cases++;
 }
 for(const width of [360,1440])for(const theme of ['dark','light']) {
  const {page,errors}=await pageFor({width,height:1100},`mode=profiles&mihomo=1&cards=1&running=1&pingErrors=1&theme=${theme}`);
  const first=page.getByRole('tab',{name:'🚫 Недоступные сайты',exact:true});await first.waitFor();
  const selected=page.getByRole('button',{name:'🚫 Недоступные сайты: DIRECT',exact:true});await page.waitForFunction(()=>document.querySelector('[aria-label="🚫 Недоступные сайты: DIRECT"]')?.getAttribute('aria-pressed')==='true');
  assert.equal(await selected.locator('.core-proxy-card-selected').count(),1);assert.equal(await page.locator('.core-subscription-member[aria-pressed="true"]').count(),1);
  assert(await page.getByRole('button',{name:'🚫 Недоступные сайты: 🎲 Любой доступный сервер',exact:true}).locator('.core-proxy-card-route').isVisible());
  assert.equal(await page.locator('.core-subscription-head').count(),0);assert.equal(await page.locator('.core-subscription-control').getByRole('button',{name:/Подключить|Отключить/}).count(),0);
  assert.equal(await page.locator('.core-proxy-ping-one').count(),8);assert.equal(await page.locator('button button').count(),0,'nested buttons');
  const one=page.getByRole('button',{name:'Пинг: DIRECT',exact:true});await one.focus();await page.keyboard.press('Enter');
  await page.waitForFunction(()=>document.querySelector('[aria-label="🚫 Недоступные сайты: DIRECT"] .core-proxy-card-latency')?.textContent==='0 ms');
  assert.deepEqual(await page.evaluate(()=>window.nativeCalls.find(([cmd])=>cmd==='mihomo_delay')[1]),{profileId:'yaml-fixture',sessionId:'fixture-session',name:'DIRECT',url:'https://www.gstatic.com/generate_204',timeoutMs:5000,expectedStatus:null});
  await page.getByRole('button',{name:'Пинг категории: 🚫 Недоступные сайты',exact:true}).click();
  await page.waitForFunction(()=>document.querySelectorAll('.core-proxy-card-latency[data-state="success"],.core-proxy-card-latency[data-state="error"]').length===8&&!document.querySelector('.core-proxy-ping-all[aria-label="Остановить пинг"]'));
  assert.equal(await page.locator('.core-proxy-card-latency').filter({hasText:'Тайм-аут'}).count(),1);assert.equal(await page.locator('.core-proxy-card-latency').filter({hasText:'Недоступен'}).count(),1);
  const requests=await page.evaluate(()=>window.nativeCalls.filter(([cmd])=>cmd==='mihomo_delay').map(([,args])=>args.name));assert.deepEqual(requests,['DIRECT',...tabInspection.graph.groups[0].proxies]);
  assert.equal(await page.evaluate(()=>window.fixturePingMax),1);assert(!await page.locator('.core-subscription-control').innerText().then(text=>text.includes('secret.invalid')));
  assert.equal(await selected.getAttribute('aria-pressed'),'true','ping changed selection');assert(!(await page.evaluate(()=>window.nativeCalls)).some(([cmd])=>['connect_mihomo_profile','mihomo_select'].includes(cmd)));
  const pingBoxes=await page.locator('.core-proxy-ping-one').evaluateAll(elements=>elements.map(e=>{const r=e.getBoundingClientRect(),parent=e.parentElement.getBoundingClientRect();return {w:r.width,h:r.height,inside:r.x>=parent.x&&r.right<=parent.right&&r.y>=parent.y&&r.bottom<=parent.bottom};}));assert(pingBoxes.every(r=>r.w>=44&&r.h>=44&&r.inside));
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  if(artifacts)await page.locator('.core-subscription-control').screenshot({path:resolve(artifacts,`mihomo-ping-${width}-${theme}.png`)});
  if(artifacts)await page.locator('.core-subscription-control').screenshot({path:resolve(artifacts,`mihomo-tabs-selected-${width}-${theme}.png`)});
  const target=page.getByRole('button',{name:'🚫 Недоступные сайты: 🇫🇮 Финляндия · Shadowsocks ✈️',exact:true});await target.click();await page.waitForFunction(()=>document.querySelector('[aria-label="🚫 Недоступные сайты: 🇫🇮 Финляндия · Shadowsocks ✈️"]')?.getAttribute('aria-pressed')==='true');
  assert.equal(await selected.getAttribute('aria-pressed'),'false');assert.equal(await page.locator('.core-subscription-member[aria-pressed="true"]').count(),1);
  const readback=await target.evaluate(e=>({border:getComputedStyle(e).borderColor,plain:getComputedStyle(e.parentElement).borderColor,width:e.getBoundingClientRect().width,marker:!!e.querySelector('.core-proxy-card-selected')}));assert(readback.marker&&readback.border!==readback.plain,'selection does not highlight the whole card');
  assert.deepEqual(await page.evaluate(()=>window.nativeCalls.find(([cmd])=>cmd==='mihomo_select')[1]),{profileId:'yaml-fixture',sessionId:'fixture-session',group:'🚫 Недоступные сайты',name:'🇫🇮 Финляндия · Shadowsocks ✈️'});
  await page.getByRole('tab',{name:'🤖 AI / Gemini',exact:true}).click();assert.equal(await page.locator('.core-subscription-member:not(:disabled)').count(),0,'automatic group became manually selectable');assert.equal(await page.locator('.core-subscription-member[aria-pressed="true"]').count(),1);
  assert(await page.getByRole('button',{name:'Пинг: 🇫🇮 Финляндия',exact:true}).isEnabled(),'automatic category cannot be pinged');
  await first.click();assert.equal(await target.getAttribute('aria-pressed'),'true','category navigation lost the confirmed choice');
  assert.deepEqual(errors,[]);assert.deepEqual(await page.evaluate(()=>window.unexpectedCalls),[]);await page.close();cases++;
 }
 for(const change of ['cancel','settings','session','refresh','unmount']){
  const {page,errors}=await pageFor({width:360,height:1100},'mode=profiles&mihomo=1&cards=1&running=1&deferPing=1');
  await page.getByRole('button',{name:'Пинг категории: 🚫 Недоступные сайты',exact:true}).click();
  await page.waitForFunction(()=>window.fixturePingWaiters.length===1);
  assert.equal(await page.locator('.core-proxy-card-latency[data-state="pending"]').count(),8);
  if(change==='cancel')await page.getByRole('button',{name:'Остановить пинг',exact:true}).click();
  if(change==='settings')await page.evaluate(()=>window.changePingSettings());
  if(change==='session')await page.evaluate(()=>window.changeCoreSession());
  if(change==='refresh')await page.evaluate(()=>window.refreshFixtureSubscription());
  if(change==='unmount')await page.evaluate(()=>window.unmountMihomo());
  await page.waitForFunction(()=>!document.querySelector('.core-proxy-card-latency[data-state="pending"]'));
  if(change==='cancel')assert(await page.getByRole('button',{name:'Пинг категории: 🚫 Недоступные сайты',exact:true}).isDisabled(),'cancel allowed another queued native request before draining');
  await page.evaluate(()=>window.releaseFixturePing());await page.waitForFunction(()=>window.fixturePingActive===0);await page.waitForTimeout(50);
  assert.equal(await page.locator('.core-proxy-card-latency[data-state="success"]').count(),0);assert.equal((await page.evaluate(()=>window.nativeCalls)).filter(([cmd])=>cmd==='mihomo_delay').length,1,'cancelled queue kept dispatching');
  assert(!(await page.evaluate(()=>window.nativeCalls)).some(([cmd])=>['connect_mihomo_profile','mihomo_select'].includes(cmd)));
  if(change==='settings'){
   await page.getByRole('button',{name:'Пинг: DIRECT',exact:true}).click();await page.waitForFunction(()=>window.fixturePingWaiters.length===1);await page.evaluate(()=>window.releaseFixturePing());
   await page.waitForFunction(()=>document.querySelector('.core-proxy-card-latency[data-state="success"]'));
   const last=await page.evaluate(()=>window.nativeCalls.filter(([cmd])=>cmd==='mihomo_delay').at(-1)[1]);assert.equal(last.url,'https://changed.invalid/204');assert.equal(last.timeoutMs,30000);
  }
  assert.deepEqual(errors,[]);assert.deepEqual(await page.evaluate(()=>window.unexpectedCalls),[]);await page.close();cases++;
 }
 for(const query of ['invalidPing=1','nativeChanged=1']){
  const {page,errors}=await pageFor({width:800,height:900},'mode=profiles&mihomo=1&cards=1&running=1&'+query);
  await page.getByRole('button',{name:'Пинг: DIRECT',exact:true}).click();
  await page.waitForFunction(()=>window.nativeCalls.some(([cmd])=>cmd==='mihomo_delay')&&!document.querySelector('.core-proxy-card-latency[data-state="pending"]'));
  assert.equal(await page.locator('.core-proxy-card-latency[data-state="success"]').count(),0);
  assert.equal(await page.locator('.core-proxy-card-latency[data-state="error"]').count(),query==='invalidPing=1'?1:0);
  assert.deepEqual(errors,[]);assert.deepEqual(await page.evaluate(()=>window.unexpectedCalls),[]);await page.close();cases++;
 }
 for(const width of [360,600,800,900,960,1000,1280,1440])for(const theme of ['dark','light'])for(const variant of ['round','compact']) {
  const {page,errors}=await pageFor({width,height:850},'theme='+theme+'&button='+variant);await page.locator('.universal-connection').waitFor();
  const layout=await page.evaluate(()=>{const box=s=>{const r=document.querySelector(s).getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,bottom:r.bottom,height:r.height};};return {main:box('.app-main'),action:box('.nimbo-connect-action'),control:box('.universal-connect-control'),grid:box('.universal-home-grid'),connection:box('.universal-connection'),subs:box('.universal-subscription-section'),card:box('.universal-home-subscriptions .signal-profile'),nav:box('.app-bottom-nav'),sidebar:box('.signal-rail'),overflow:document.documentElement.scrollWidth>innerWidth};});
  assert(layout.main.width>width*.65,'content stolen by sidebar');assert(!layout.overflow);
  assert.equal(await page.locator('.universal-connection .nimbo-connect-action').count(),0,'action remains inside connection card');assert(layout.control.bottom<=layout.grid.y);
  if(width>=1000){assert(Math.abs(layout.connection.y-layout.card.y)<2,'card tops misaligned');assert(Math.abs(layout.connection.width-layout.card.width)<2,'card widths unequal');assert(Math.abs(layout.connection.height-layout.card.height)<2,'card heights unequal');assert(layout.card.x>=layout.connection.x+layout.connection.width);}
  else assert(layout.subs.y>=layout.connection.bottom,'narrow cards must stack');
  if(width>=1000)assert(layout.connection.height<=300,'ordinary home cards remain unnecessarily tall');
  if(variant==='round'){assert(Math.abs(layout.action.width-layout.action.height)<1&&layout.action.width>=(width<=600?144:176),'round action still too small');assert.equal(await page.locator('.nimbo-connect-action').innerText(),'');}
  else assert(layout.action.height<=52&&layout.action.width>layout.action.height&&layout.action.width<=240,'compact action stretched');
  assert.equal(await page.locator('.signal-rail-foot').getByText(/NIMBO/i).count(),0);assert.equal(await page.locator('.signal-core-chip > span').innerText(),'Соединение');
  if(width<960){assert.equal(layout.sidebar.width,0);assert(Math.abs(layout.main.bottom-layout.nav.y)<2,'legacy fixed-bar margin leaves a gap above navigation');assert.equal(await page.locator('.app-bottom-nav-item:visible').count(),4);assert(layout.nav.y>=740&&layout.nav.bottom<=851,'bottom navigation not at window bottom');}
  assert(await page.getByText('Описание подписки:',{exact:false}).isVisible());
  const heading=page.locator('.universal-page-heading h1');await heading.dispatchEvent('pointerdown',{pointerId:1,isPrimary:true,button:0,clientX:40,clientY:30,pointerType:'mouse'});await heading.dispatchEvent('pointermove',{pointerId:1,isPrimary:true,clientX:40,clientY:130,pointerType:'mouse'});await heading.dispatchEvent('pointerup',{pointerId:1,isPrimary:true,clientX:40,clientY:130,pointerType:'mouse'});
  await page.waitForFunction(()=>window.polishCalls.length===1);assert.deepEqual(await page.evaluate(()=>window.polishCalls),['refresh:https://fixture.invalid/sub']);
  await page.waitForFunction(()=>document.querySelector('.nimbo-pull-feedback').hidden);
  if(artifacts&&(theme==='dark'||width===1440))await page.screenshot({path:resolve(artifacts,`home-${width}-${variant}-${theme}.png`)});
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
 for(const width of [360,1440])for(const query of ['long=1','multiple=1','state=connecting','state=connected']) {
  const {page,errors}=await pageFor({width,height:850},query);await page.locator('.nimbo-connect-action').waitFor();
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  if(query==='long=1') {
   const announcement=page.locator('.nimbo-provider-announcement');const paragraph=announcement.locator('p');
   assert.equal(await paragraph.textContent(),await page.evaluate(()=>window.fixtureDescription),'full announcement lost source formatting');
   assert.equal(await announcement.locator('h3').count(),0,'redundant description heading');
   const full=await paragraph.evaluate(e=>({height:e.getBoundingClientRect().height,line:parseFloat(getComputedStyle(e).lineHeight),wrap:getComputedStyle(e).whiteSpace,clipped:e.scrollHeight>e.clientHeight+1}));assert.equal(full.wrap,'pre-wrap');assert(full.height>full.line*3&&!full.clipped,'description remains cropped');
   assert.equal(await announcement.locator('button').count(),0);assert.equal(await paragraph.locator('script').count(),0);
   if(artifacts)await page.locator('.signal-profile').screenshot({path:resolve(artifacts,`announcement-${width}-full.png`)});
   if(width>1000){const heights=await page.evaluate(()=>['.universal-connection','.signal-profile'].map(s=>document.querySelector(s).getBoundingClientRect().height));assert(Math.abs(heights[0]-heights[1])<2);}
  } else if(query==='multiple=1')assert.equal(await page.locator('.universal-home-subscriptions .signal-profile').count(),2);
  else if(query==='state=connecting'){assert(await page.locator('.nimbo-connect-action').isDisabled());assert.equal(await page.locator('.nimbo-connect-action [data-connection-icon="loading"]').count(),1);assert.deepEqual(await page.evaluate(()=>window.polishCalls),[]);}
  else {assert.equal(await page.locator('.nimbo-connect-action [data-connection-icon="cloud"]').count(),1);await page.locator('.nimbo-connect-action').click();await page.waitForFunction(()=>window.polishCalls.includes('disconnect'));}
  assert.deepEqual(errors,[]);assert.deepEqual(await page.evaluate(()=>window.unexpectedCalls),[]);await page.close();cases++;
 }
 for(const width of [360,1440])for(const theme of ['dark','light'])for(const mode of ['home','profiles']) {
  const {page,errors}=await pageFor({width,height:1100},`mode=${mode}&theme=${theme}&announcement=1`);
  const paragraph=page.locator('.nimbo-provider-announcement p');const disclosure=page.locator('.nimbo-profile-disclosure');
  await paragraph.waitFor();const before=await disclosure.getAttribute('aria-expanded');
  assert.equal(await paragraph.textContent(),await page.evaluate(()=>window.fixtureDescription));assert.equal(await page.locator('.nimbo-provider-announcement h3').count(),0);
  assert.equal(await page.locator('.nimbo-provider-announcement button').count(),0);
  const full=await paragraph.evaluate(e=>({height:e.getBoundingClientRect().height,line:parseFloat(getComputedStyle(e).lineHeight),clipped:e.scrollHeight>e.clientHeight+1}));assert(full.height>full.line*3&&!full.clipped);
  await paragraph.click();
  assert.equal(await disclosure.getAttribute('aria-expanded'),before,'announcement click toggled the server list');
  assert.equal(await paragraph.textContent(),await page.evaluate(()=>window.fixtureDescription));
  if(artifacts)await page.locator('.signal-profile').screenshot({path:resolve(artifacts,`provider-${mode}-${width}-${theme}-full.png`)});
  assert.deepEqual(errors,[]);assert.deepEqual(await page.evaluate(()=>window.unexpectedCalls),[]);await page.close();cases++;
 }
 const {page:resize,errors:resizeErrors}=await pageFor({width:1440,height:850},'mode=profiles');
 await resize.locator('.nimbo-provider-announcement p').waitFor();
 await resize.evaluate(()=>window.setFixtureDescription('A'.repeat(220)));
 await resize.waitForFunction(()=>document.querySelector('.nimbo-provider-announcement p').textContent==='A'.repeat(220));
 await resize.getByRole('button',{name:'Читать полностью',exact:true}).waitFor({state:'hidden'});
 await resize.setViewportSize({width:360,height:850});assert.equal(await resize.locator('.nimbo-provider-announcement button').count(),0);
 await resize.evaluate(()=>window.setFixtureDescription('New source\n'+('line\n').repeat(8)));
 await resize.waitForFunction(()=>document.querySelector('.nimbo-provider-announcement p').textContent===window.fixtureDescription);
 assert.equal(await resize.locator('.nimbo-provider-announcement button').count(),0);assert(await resize.locator('.nimbo-provider-announcement p').evaluate(e=>e.scrollHeight<=e.clientHeight+1),'updated description is cropped');
 assert.deepEqual(resizeErrors,[]);assert.deepEqual(await resize.evaluate(()=>window.unexpectedCalls),[]);await resize.close();cases++;
 for(const width of [360,1440])for(const theme of ['dark','light']) {
  const {page,errors}=await pageFor({width,height:850},'mode=notifications&theme='+theme);await page.locator('.notification-history-item').first().waitFor();
  const metrics=await page.locator('.notification-history-item').first().evaluate(e=>({inset:e.querySelector('.notification-history-icon').getBoundingClientRect().x-e.getBoundingClientRect().x,stripe:getComputedStyle(e,'::before').content,wrap:getComputedStyle(e.querySelector('.notification-history-message')).whiteSpace,overflow:document.documentElement.scrollWidth>innerWidth}));
  assert(metrics.inset>=12);assert.equal(metrics.stripe,'none');assert.equal(metrics.wrap,'normal');assert(!metrics.overflow);
  if(artifacts)await page.screenshot({path:resolve(artifacts,`notifications-${width}-${theme}.png`),fullPage:true});
  await page.locator('.notification-filter-chip[data-tone="error"]').click();assert.equal(await page.locator('.notification-history-item').count(),1);
  await page.locator('.notification-history-delete').click();assert.equal(await page.locator('.notification-history-item').count(),0);
  await page.locator('.notification-filter-chip[data-tone="all"]').click();assert.equal(await page.locator('.notification-history-item').count(),2);
  await page.getByRole('button',{name:'Очистить всё',exact:true}).click();const dialog=page.getByRole('dialog');await dialog.getByRole('button',{name:'Отмена',exact:true}).click();assert.equal(await page.locator('.notification-history-item').count(),2);
  await page.getByRole('button',{name:'Очистить всё',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'Очистить всё',exact:true}).click();assert.equal(await page.locator('.notification-history-item').count(),0);
  assert.deepEqual(errors,[]);assert.deepEqual(await page.evaluate(()=>window.unexpectedCalls),[]);await page.close();cases++;
 }
 const {page:offline,errors:offlineErrors}=await pageFor({width:1440,height:850},'mode=profiles&mihomo=1');await offline.getByRole('tab',{name:'VPN',exact:true}).waitFor();
 assert.equal(await offline.getByRole('tab').count(),2);assert.equal(await offline.getByRole('tabpanel').count(),1);assert(await offline.getByRole('button',{name:'VPN: REJECT',exact:true}).isDisabled());assert.equal(await offline.getByRole('link',{name:/Группы/}).count(),0);
 assert.equal(await offline.locator('.core-subscription-head').count(),0);assert.equal(await offline.locator('.core-proxy-ping-one').count(),2);assert(await offline.getByRole('button',{name:'Пинг: DIRECT',exact:true}).isDisabled());assert.equal(await offline.getByRole('button',{name:'Пинг: DIRECT',exact:true}).getAttribute('title'),'Пинг доступен при подключении');
 const header=await offline.locator('.signal-profile-head').evaluate(e=>[...e.querySelectorAll('.signal-icon-btn')].map(b=>({y:b.getBoundingClientRect().y,w:b.getBoundingClientRect().width,h:b.getBoundingClientRect().height})));assert(header.every(b=>b.w===44&&b.h===44&&Math.abs(b.y-header[0].y)<1),'header buttons misaligned');
 const cardWidth=await offline.locator('.signal-profile').evaluate(e=>({card:e.getBoundingClientRect().width,page:document.querySelector('.nimbo-home-profile').getBoundingClientRect().width}));assert(Math.abs(cardWidth.card-cardWidth.page)<2,'profile card needlessly narrower than content');
 const disclosure=offline.locator('.nimbo-profile-disclosure');await disclosure.click();assert.equal(await disclosure.getAttribute('aria-expanded'),'false');await disclosure.click();assert.equal(await disclosure.getAttribute('aria-expanded'),'true');
 if(artifacts)await offline.screenshot({path:resolve(artifacts,'mihomo-disconnected-groups.png'),fullPage:true});assert.deepEqual(offlineErrors,[]);assert.deepEqual(await offline.evaluate(()=>window.unexpectedCalls),[]);await offline.close();cases++;
 for(const query of ['inspect=1','stale=1','foreign=1']) {
  const {page,errors}=await pageFor({width:360,height:850},'mode=profiles&mihomo=1&'+query);await page.getByRole('tab',{name:'VPN',exact:true}).waitFor();
  assert(await page.getByRole('button',{name:'VPN: REJECT',exact:true}).isDisabled());assert.equal(await page.getByText('private-foreign-node',{exact:true}).count(),0);
  const calls=await page.evaluate(()=>window.nativeCalls);assert.equal(calls.filter(([cmd])=>cmd==='inspect_core_profile').length,query==='foreign=1'?0:1);assert(!calls.some(([cmd])=>cmd==='connect_mihomo_profile'||cmd==='mihomo_select'));
  assert.deepEqual(errors,[]);assert.deepEqual(await page.evaluate(()=>window.unexpectedCalls),[]);await page.close();cases++;
 }
 for(const width of [360,1440])for(const theme of ['dark','light']) {
  const {page,errors}=await pageFor({width,height:1100},`mode=profiles&mihomo=1&warning=1&announcement=1&theme=${theme}`);
  await page.getByRole('tab',{name:'VPN',exact:true}).waitFor();assert.equal(await page.getByRole('tab').count(),3,'native warning-bearing graph is hidden');
  for(const name of ['VPN','Streaming','Auto']){await page.getByRole('tab',{name,exact:true}).click();assert(await page.getByRole('group',{name,exact:true}).isVisible());assert.equal(await page.getByRole('tabpanel').count(),1);}
  assert.equal(await page.locator('.core-subscription-member:not(:disabled)').count(),0);assert.equal(await page.locator('.core-subscription-member[aria-pressed="true"]').count(),0);
  assert.equal(await page.locator('.core-subscription-inspection').count(),0);assert.equal(await page.getByText(/Замечания инспектора|UNSUPPORTED_CONFIG/).count(),0);assert.equal(await page.getByRole('button',{name:'Проверить профиль',exact:true}).count(),0);
  const calls=await page.evaluate(()=>window.nativeCalls);assert(!calls.some(([cmd])=>cmd==='connect_mihomo_profile'||cmd==='mihomo_select'||cmd==='inspect_core_profile'),'preview activated or re-inspected a current native graph');
  if(artifacts)await page.locator('.signal-profile').screenshot({path:resolve(artifacts,`mihomo-warning-categories-${width}-${theme}.png`)});
  await page.evaluate(()=>window.connectFixtureProfile());await page.locator('.core-subscription-control [role="alert"]').waitFor();
  assert.equal(await page.getByRole('tab').count(),3,'failed admission erased categories');assert.equal(await page.locator('.core-subscription-member:not(:disabled)').count(),0);
  assert.deepEqual(errors,[]);assert.deepEqual(await page.evaluate(()=>window.unexpectedCalls),[]);await page.close();cases++;
 }
 const {page:inspectFailure,errors:inspectErrors}=await pageFor({width:360,height:850},'mode=profiles&mihomo=1&inspectFail=1');
 await inspectFailure.locator('.core-subscription-control [role="alert"]').waitFor();assert.equal(await inspectFailure.getByRole('tab').count(),0);assert.equal(await inspectFailure.getByRole('button',{name:'Проверить профиль',exact:true}).count(),0);
 await inspectFailure.getByRole('button',{name:'Обновить подписку',exact:true}).click();await inspectFailure.getByRole('tab',{name:'VPN',exact:true}).waitFor();assert.equal(await inspectFailure.getByRole('tab').count(),2);
 assert.equal((await inspectFailure.evaluate(()=>window.nativeCalls)).filter(([cmd])=>cmd==='inspect_core_profile').length,2);assert.deepEqual(inspectErrors,[]);assert.deepEqual(await inspectFailure.evaluate(()=>window.unexpectedCalls),[]);await inspectFailure.close();cases++;
 const {page:empty,errors:emptyErrors}=await pageFor({width:1440,height:850},'mode=profiles&mihomo=1&empty=1');
 await empty.getByText('Нет категорий',{exact:true}).waitFor();assert.equal(await empty.getByRole('tab').count(),0);assert.equal(await empty.getByRole('button',{name:'Проверить профиль',exact:true}).count(),0);
 assert(!(await empty.evaluate(()=>window.nativeCalls)).some(([cmd])=>cmd==='inspect_core_profile'),'valid empty graph was pointlessly inspected again');
 assert.deepEqual(emptyErrors,[]);assert.deepEqual(await empty.evaluate(()=>window.unexpectedCalls),[]);await empty.close();cases++;
 const {page:failed,errors:failedErrors}=await pageFor({width:800,height:850},'mode=profiles&mihomo=1&selectFail=1&running=1');await failed.getByRole('button',{name:'VPN: REJECT',exact:true}).click();await failed.locator('.core-subscription-control [role="alert"]').waitFor();assert.equal(await failed.getByRole('button',{name:'VPN: REJECT',exact:true}).getAttribute('aria-pressed'),'false');assert.equal(await failed.getByRole('button',{name:'VPN: DIRECT',exact:true}).getAttribute('aria-pressed'),'true');assert.deepEqual(failedErrors,[]);assert.deepEqual(await failed.evaluate(()=>window.unexpectedCalls),[]);await failed.close();cases++;
 const {page:update}=await pageFor({width:1440,height:850},'update=1');await update.locator('.signal-core-update').click();assert.deepEqual(await update.evaluate(()=>window.polishCalls),['update']);assert.equal(await update.locator('.parity-brand-copy small').innerText(),'v1.3.0 β1');await update.close();cases++;
 const {page:profile,errors:profileErrors}=await pageFor({width:800,height:850},'mode=profiles');await profile.locator('.parity-server-row.is-active').waitFor();
 const row=await profile.locator('.parity-server-row.is-active').evaluate(e=>({border:getComputedStyle(e).borderWidth,width:e.getBoundingClientRect().width,parent:e.parentElement.getBoundingClientRect().width}));assert.notEqual(row.border,'0px');assert(Math.abs(row.width-row.parent)<3,'selection does not cover full row');assert.deepEqual(profileErrors,[]);await profile.close();cases++;
 const {page:core,errors:coreErrors}=await pageFor({width:800,height:850},'mihomo=1');await core.getByRole('button',{name:/Подключ/}).first().click();
 await core.waitForFunction(()=>window.nativeCalls.some(([cmd])=>cmd==='connect_mihomo_profile'));
 assert.deepEqual(await core.evaluate(()=>window.nativeCalls.find(([cmd])=>cmd==='connect_mihomo_profile')[1]),{profileId:'yaml-fixture'});
 assert.deepEqual(await core.evaluate(()=>window.polishCalls.filter(call=>call.startsWith('legacy:'))),[]);
 await core.locator('.universal-subscription-toggle').click();const group=core.getByRole('group',{name:'VPN',exact:true});await group.waitFor();await group.getByRole('button',{name:'VPN: REJECT',exact:true}).click();
 await core.waitForFunction(()=>window.nativeCalls.some(([cmd])=>cmd==='mihomo_select'));await core.waitForFunction(()=>document.querySelector('[aria-label="VPN: REJECT"]').getAttribute('aria-pressed')==='true');
 assert.deepEqual(await core.evaluate(()=>window.nativeCalls.find(([cmd])=>cmd==='mihomo_select')[1]),{profileId:'yaml-fixture',sessionId:'fixture-session',group:'VPN',name:'REJECT'});
 await core.getByRole('tab',{name:'Auto',exact:true}).click();assert(await core.getByRole('button',{name:'Auto: node',exact:true}).isDisabled());
 if(artifacts)await core.screenshot({path:resolve(artifacts,'mihomo-inline-groups.png')});assert.deepEqual(coreErrors,[]);assert.deepEqual(await core.evaluate(()=>window.unexpectedCalls),[]);await core.close();cases++;
 const {page:tray,errors:trayErrors}=await pageFor({width:360,height:760},'mode=tray');await tray.locator('.tray-card').waitFor();await tray.waitForTimeout(100);
 const trayBounds=await tray.locator('.tray-card').boundingBox();assert(trayBounds.width<=328&&trayBounds.height<590,'tray remains oversized');assert.equal(await tray.locator('.tray-utility-grid,.tray-maintenance').count(),0);
 if(artifacts)await tray.screenshot({path:resolve(artifacts,'tray.png')});assert.deepEqual(trayErrors,[]);await tray.close();cases++;
 console.log(`PASS: ${cases} shell/home/profile/dropdown/core/tray browser cases (isolated IPC)`);
} finally {await browser.close();await server.close();}
