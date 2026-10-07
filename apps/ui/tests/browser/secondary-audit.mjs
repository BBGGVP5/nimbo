import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createServer} from 'vite';
import {chromium} from '../../../installer/node_modules/playwright/index.mjs';
const server=await createServer({server:{hmr:false,host:'127.0.0.1',port:5197,strictPort:true}});await server.listen();
const browser=await chromium.launch({headless:true,executablePath:process.env.NIMBO_CHROMIUM_PATH});
const artifacts=process.env.NIMBO_LAYOUT_ARTIFACT_DIR;if(artifacts)await mkdir(artifacts,{recursive:true});let cases=0;
try{
 {
  const page=await browser.newPage({viewport:{width:360,height:900}});
  await page.addInitScript(()=>{window.auditVisibility='hidden';Object.defineProperty(document,'visibilityState',{get:()=>window.auditVisibility,configurable:true});});
  await page.goto('http://127.0.0.1:5197/tests/browser/secondary-audit.html?mode=sync&native=1');await page.waitForTimeout(900);
  assert.equal(await page.evaluate(()=>window.auditCalls.filter(x=>x==='sync-status').length),0,'hidden sync polling still active');
  await page.evaluate(()=>{window.auditVisibility='visible';document.dispatchEvent(new Event('visibilitychange'));});await page.waitForTimeout(80);
  assert.equal(await page.evaluate(()=>window.auditCalls.filter(x=>x==='sync-status').length),1);
  await page.evaluate(()=>{window.auditVisibility='hidden';document.dispatchEvent(new Event('visibilitychange'));});const count=await page.evaluate(()=>window.auditCalls.filter(x=>x==='sync-status').length);await page.waitForTimeout(900);assert.equal(await page.evaluate(()=>window.auditCalls.filter(x=>x==='sync-status').length),count);
  await page.close();cases++;
 }
 for(const mode of ['routing','modules','apps','connections','logs','sync','mihomo'])for(const width of [320,800,1440])for(const theme of ['dark','light']){
  const page=await browser.newPage({viewport:{width,height:960},reducedMotion:'reduce'}),errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto(`http://127.0.0.1:5197/tests/browser/secondary-audit.html?mode=${mode}&theme=${theme}`);await page.waitForTimeout(200);
  const layout=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,offenders:[...document.querySelectorAll('main *')].filter(e=>e.getBoundingClientRect().right>innerWidth+1&&getComputedStyle(e).position!=='fixed').map(e=>e.className).slice(0,6)}));
  assert(!layout.overflow,`${mode}/${width}/${theme} overflow: ${JSON.stringify(layout.offenders)}`);assert.deepEqual(errors,[],mode);
  assert.equal(await page.locator('h1').count(),1,`${mode} missing consistent page title`);
  if(artifacts&&width===320&&theme==='dark')await page.screenshot({path:resolve(artifacts,`secondary-${mode}-320.png`),fullPage:true});
  await page.close();cases++;
 }
 for(const mode of ['routing','modules','apps','connections','logs','sync']){
  const page=await browser.newPage({viewport:{width:360,height:900}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(`http://127.0.0.1:5197/tests/browser/secondary-audit.html?mode=${mode}&fail=1`);await page.waitForTimeout(200);
  assert.deepEqual(errors,[],mode);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.close();cases++;
 }
 for(const mode of ['routing','modules','apps']){
  const page=await browser.newPage({viewport:{width:320,height:960}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(`http://127.0.0.1:5197/tests/browser/secondary-audit.html?mode=${mode}&populated=1`);await page.waitForTimeout(180);
  assert.deepEqual(errors,[],mode);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`${mode}: populated long titles overflow`);
  if(mode==='modules'){
   await page.getByRole('button',{name:'Редактировать',exact:true}).click();await page.getByRole('dialog').waitFor();
   const rect=await page.getByRole('dialog').boundingBox();assert(rect.x>=0&&rect.x+rect.width<=321);await page.keyboard.press('Escape');assert.equal(await page.getByRole('dialog').count(),0);
  }
  await page.close();cases++;
 }
 for(const mode of ['routing','modules','apps','connections','logs','sync','mihomo'])for(const style of ['material_you','dotted'])for(const width of [360,800]){
  const page=await browser.newPage({viewport:{width,height:960},reducedMotion:'reduce'}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(`http://127.0.0.1:5197/tests/browser/secondary-audit.html?mode=${mode}&style=${style}&theme=${width===360?'dark':'light'}`);await page.waitForTimeout(160);
  assert.deepEqual(errors,[],`${mode}/${style}`);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`${mode}/${style}/${width}: overflow`);
  await page.close();cases++;
 }
 console.log(`PASS: ${cases} secondary production-route audit cases (isolated data only)`);
}finally{await browser.close();await server.close();}
