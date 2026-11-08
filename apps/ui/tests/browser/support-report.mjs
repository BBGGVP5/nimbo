import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createServer} from 'vite';
import {chromium} from '../../../installer/node_modules/playwright/index.mjs';
const server=await createServer({server:{hmr:false,host:'127.0.0.1',port:5196,strictPort:true}});await server.listen();
const browser=await chromium.launch({headless:true,executablePath:process.env.NIMBO_CHROMIUM_PATH});
const artifacts=process.env.NIMBO_LAYOUT_ARTIFACT_DIR;if(artifacts)await mkdir(artifacts,{recursive:true});
let cases=0;
try{
 for(const width of [360,1100])for(const theme of ['dark','light']){
  const page=await browser.newPage({viewport:{width,height:800},reducedMotion:'reduce'});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(`http://127.0.0.1:5196/tests/browser/support-report.html?theme=${theme}`);
  await page.getByRole('button',{name:'Отчёт об ошибке',exact:true}).click();
  assert.deepEqual(await page.evaluate(()=>window.supportFixture.calls),[],'opening must not collect anything');
  assert.equal(await page.getByRole('button',{name:'Скопировать отчёт',exact:true}).count(),0);
  await page.getByRole('button',{name:'Подготовить отчёт',exact:true}).click();
  const preview=page.getByLabel('Предпросмотр отчёта',{exact:true});await preview.waitFor();
  const text=await preview.innerText();assert(!text.includes('SECRET'));assert.deepEqual(await page.evaluate(()=>window.supportFixture.copies),[]);
  assert.equal(JSON.parse(text).connection.state,'connected');
  if(artifacts)await page.screenshot({path:resolve(artifacts,`support-${theme}-${width}.png`)});
  const bounds=await page.locator('dialog').evaluate(d=>({left:d.getBoundingClientRect().left,right:d.getBoundingClientRect().right,width:innerWidth,overflow:d.scrollWidth>d.clientWidth+1}));
  assert(bounds.left>=0&&bounds.right<=bounds.width&&!bounds.overflow);
  await page.getByRole('button',{name:'Скопировать отчёт',exact:true}).click();
  await page.getByRole('status').filter({hasText:'Скопировано.'}).waitFor();
  assert.deepEqual(await page.evaluate(()=>window.supportFixture.copies),[text]);
  await page.keyboard.press('Escape');assert.equal(await page.locator('dialog').count(),0);
  assert.deepEqual(errors,[]);await page.close();cases++;
 }
 for(const mode of ['fail','clipboardFail','delay']){
  const page=await browser.newPage({viewport:{width:360,height:800}});
  await page.goto(`http://127.0.0.1:5196/tests/browser/support-report.html?${mode}`);
  await page.getByRole('button',{name:'Отчёт об ошибке',exact:true}).click();await page.getByRole('button',{name:'Подготовить отчёт',exact:true}).click();
  if(mode==='delay'){
   await page.getByRole('button',{name:'Закрыть',exact:true}).click();
   await page.getByRole('button',{name:'Отчёт об ошибке',exact:true}).click();
   await page.waitForTimeout(500);assert.equal(await page.getByLabel('Предпросмотр отчёта',{exact:true}).count(),0,'closed generation cannot reappear');
  }else if(mode==='fail'){
   await page.getByRole('status').filter({hasText:'Данные недоступны'}).waitFor();assert.equal(await page.getByLabel('Предпросмотр отчёта',{exact:true}).count(),0);
  }else{
   await page.getByLabel('Предпросмотр отчёта',{exact:true}).waitFor();await page.getByRole('button',{name:'Скопировать отчёт',exact:true}).click();await page.getByRole('status').filter({hasText:'Не удалось скопировать'}).waitFor();
  }
  assert.deepEqual(await page.evaluate(()=>window.supportFixture.copies),[]);assert(!(await page.locator('dialog').innerText()).includes('SECRET'));await page.close();cases++;
 }
 console.log(`support report browser: ${cases} cases passed`);
}finally{await browser.close();await server.close();}
