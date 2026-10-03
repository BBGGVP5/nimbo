import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { createServer } from 'vite';
import { chromium } from '../../../installer/node_modules/playwright/index.mjs';

const server=await createServer({server:{host:'127.0.0.1',port:5198,strictPort:true}});await server.listen();
const browser=await chromium.launch({headless:true,...(process.env.NIMBO_CHROMIUM_PATH?{executablePath:process.env.NIMBO_CHROMIUM_PATH}:{})});
const artifacts=process.env.NIMBO_LAYOUT_ARTIFACT_DIR;
if(artifacts)await mkdir(artifacts,{recursive:true});
let cases=0;
try {
  for(const viewport of [{width:1100,height:800},{width:800,height:700},{width:360,height:760}]) for(const theme of ['dark','light']) for(const style of ['signal','material_you','dotted']) {
    const page=await browser.newPage({viewport,reducedMotion:'reduce'});
    const errors=[];page.on('pageerror',error=>errors.push(error.message));
    await page.addInitScript(()=>{
      let next=0,preferred='auto';window.__settingsCalls=[];window.__unexpectedSettingsCalls=[];
      window.__TAURI_EVENT_PLUGIN_INTERNALS__={unregisterListener:()=>{}};
      window.__TAURI_INTERNALS__={metadata:{currentWindow:{label:'main'},currentWebview:{label:'main'}},transformCallback:()=>++next,
        invoke:async(command,args)=>{
          if(command==='get_core_profiles')return {preferred_core:preferred,active_profile_id:null,profiles:[]};
          if(command==='get_core_availability')return ['xray','mihomo','awg'].map(core=>({core,selector_available:core!=='awg',binary_verified:core!=='awg',tun_available:core!=='awg',system_proxy_available:core!=='awg',inspect_available:core!=='awg'}));
          if(command==='get_mihomo_status')return {running:false,profile_id:null,session_id:null,native_generation:null,network_owner:'none'};
          if(command==='set_core_preference'){window.__settingsCalls.push([command,args.core]);preferred=args.core;return null;}
          if(command.includes('|listen'))return ++next;
          // Fail closed for accidental fixture side-effects, especially connect/install/repair.
          window.__unexpectedSettingsCalls.push(command);throw Error('Unexpected fixture IPC: '+command);
        }};
    });
    const url=`http://127.0.0.1:5198/tests/browser/settings-polish.html?theme=${theme}&style=${style}`;
    await page.goto(url);await page.locator('.parity-settings-group').first().waitFor();
    const overview=await page.evaluate(()=>{
      const groups=[...document.querySelectorAll('.parity-settings-group')].map(g=>{const r=g.getBoundingClientRect();return {top:r.top,bottom:r.bottom};});
      const rows=[...document.querySelectorAll('.parity-setting-link')].map(r=>r.getBoundingClientRect().height);
      const app=[...document.querySelectorAll('.parity-settings-group')].find(g=>g.querySelector('h2').textContent==='Приложение');
      return {groups,rows,app:[...app.querySelectorAll('strong')].map(e=>e.textContent),overflow:document.documentElement.scrollWidth>innerWidth};
    });
    assert.deepEqual(overview.app.slice(-2),['Обновления','О программе']);
    assert(Math.max(...overview.rows)-Math.min(...overview.rows)<1,'inconsistent overview row heights');
    if(viewport.width>640){assert.equal(overview.groups[0].top,overview.groups[1].top);assert.equal(overview.groups[0].bottom,overview.groups[1].bottom);assert.equal(overview.groups[2].top,overview.groups[3].top);}
    assert(!overview.overflow,'overview horizontal overflow');
    if(artifacts && viewport.width===1100 && style==='signal')await page.screenshot({path:resolve(artifacts,`settings-${theme}.png`),fullPage:true});
    await page.goto(url+'&section=latency');
    const core=page.getByLabel('Ядро подключения',{exact:true});await core.waitFor();await page.waitForFunction(()=>!document.querySelector('#desktop-core-preference').disabled);
    assert(await core.locator('option[value=awg]').isDisabled(),'unavailable core became selectable');
    await core.selectOption('mihomo');await page.waitForFunction(()=>window.__settingsCalls.length===1);
    await page.waitForFunction(()=>document.querySelector('#desktop-core-preference').value==='mihomo'&&!document.querySelector('#desktop-core-preference').disabled);
    assert.deepEqual(await page.evaluate(()=>window.__settingsCalls),[['set_core_preference','mihomo']],'core change must save once, never connect');
    const details=page.locator('.latency-routing-help');assert.equal(await details.getAttribute('open'),null);
    const row=page.locator('.settings-choice-row').filter({hasText:'Тестовый URL'}).first();
    const metrics=await row.evaluate(e=>{const r=e.getBoundingClientRect(),control=e.querySelector('.settings-choice-control').getBoundingClientRect(),header=e.querySelector('.settings-row-label-container').getBoundingClientRect();return {height:r.height,controlTop:control.top,headerBottom:header.bottom,overflow:e.scrollWidth>e.clientWidth};});
    assert(metrics.controlTop>=metrics.headerBottom,'URL choices beside narrow text column');
    assert(metrics.height<180,'URL control stretched by help paragraph');assert(!metrics.overflow);
    await details.locator('summary').focus();await page.keyboard.press('Enter');assert.equal(await details.getAttribute('open'),'');
    assert((await details.innerText()).includes('Маршруты ОС не изменяются'),'routing caveats lost');
    await details.locator('summary').click();
    const custom=page.getByRole('button',{name:'Свой URL',exact:true});await custom.click();
    const input=page.getByRole('textbox',{name:'Тестовый URL',exact:true});await input.fill('https://fixture.invalid/ping');await input.press('Tab');
    assert.equal(await input.inputValue(),'https://fixture.invalid/ping');
    const logo=page.locator('#logo-fixture .signal-sub-logo img');await logo.waitFor();
    const imageMetrics=await logo.evaluate(e=>({fit:getComputedStyle(e).objectFit,border:getComputedStyle(e.parentElement).borderWidth,background:getComputedStyle(e.parentElement).backgroundColor,filter:getComputedStyle(e).filter,natural:[e.naturalWidth,e.naturalHeight]}));
    if(style==='signal'){assert.equal(imageMetrics.fit,'contain','rectangular logo cropped');assert.equal(imageMetrics.border,'0px','logo double framed');assert.equal(imageMetrics.background,'rgba(0, 0, 0, 0)','home overrides provider background');}
    assert.equal(imageMetrics.filter,'none','provider artwork recolored');assert.deepEqual(imageMetrics.natural,[80,40]);
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'detail horizontal overflow');
    if(artifacts && viewport.width===1100 && style==='signal')await page.screenshot({path:resolve(artifacts,`latency-${theme}.png`),fullPage:true});
    if(artifacts && viewport.width===360 && theme==='dark' && style==='signal')await page.screenshot({path:resolve(artifacts,'latency-narrow.png'),fullPage:true});
    assert.deepEqual(await page.evaluate(()=>window.__unexpectedSettingsCalls),[],'unexpected IPC side-effect in settings');
    assert.deepEqual(errors,[]);cases++;await page.close();
  }
  console.log(`PASS: ${cases} desktop settings browser cases; aligned groups, compact URL, core capability/save/readback, artwork`);
} finally {await browser.close();await server.close();}
