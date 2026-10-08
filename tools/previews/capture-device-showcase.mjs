import assert from 'node:assert/strict';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { chromium } from '../../apps/installer/node_modules/playwright/index.mjs';

const root=fileURLToPath(new URL('../../',import.meta.url));
const browser=await chromium.launch({headless:true,...(process.env.NIMBO_CHROMIUM_PATH?{executablePath:process.env.NIMBO_CHROMIUM_PATH}:{})});
try {
 for(const view of ['home','settings']) {
  const page=await browser.newPage({viewport:{width:1800,height:1100},deviceScaleFactor:1.5});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('https://**',route=>route.abort());await page.route('http://**',route=>route.abort());
  const url=pathToFileURL(resolve(root,'docs/previews/device-showcase.html'));url.searchParams.set('view',view);
  await page.goto(url.href);await page.evaluate(()=>document.fonts.ready);
  assert(await page.evaluate(()=>[...document.images].every(i=>i.complete&&i.naturalWidth>0)),'Missing preview image');
  assert(await page.evaluate(()=>[...document.querySelectorAll('.device')].every(e=>{const r=e.getBoundingClientRect();return r.left>=0&&r.top>=0&&r.right<=innerWidth&&r.bottom<=innerHeight;})),'Device clipped');
  assert.deepEqual(errors,[]);
  await page.screenshot({path:resolve(root,`docs/previews/1.3.0-beta.1/devices-${view}.png`)});
  await page.close();
 }
 console.log('PASS: two device galleries, loaded local images/fonts, unclipped monitor/phones, no external network');
}finally{await browser.close();}
