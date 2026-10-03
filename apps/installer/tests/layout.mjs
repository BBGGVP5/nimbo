import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdir } from 'node:fs/promises';
import { resolve, extname, sep } from 'node:path';
import { chromium } from 'playwright';

// Production assets, mocked IPC only: never installs or changes the host.
const root = resolve('dist');
const server = createServer(async (request, response) => {
  try {
    const path = resolve(root, '.' + new URL(request.url, 'http://localhost').pathname.replace(/\/$/, '/index.html'));
    assert(path.startsWith(root + sep));
    response.setHeader('Content-Type', ({'.html':'text/html','.js':'text/javascript','.css':'text/css','.png':'image/png'})[extname(path)] ?? 'application/octet-stream');
    response.end(await readFile(path));
  } catch { response.writeHead(404); response.end(); }
});
await new Promise(done => server.listen(0, '127.0.0.1', done));
const browser = await chromium.launch({ headless:true, ...(process.env.NIMBO_CHROMIUM_PATH ? {executablePath:process.env.NIMBO_CHROMIUM_PATH} : {}) });
const screenshots = process.env.NIMBO_LAYOUT_ARTIFACT_DIR;
if (screenshots) await mkdir(screenshots,{recursive:true});
let cases = 0;
try {
  for (const viewport of [{width:1080,height:680},{width:900,height:600},{width:780,height:520}]) {
    for (const mode of ['install','update','uninstall']) {
      for (const style of ['signal','material_you','dotted']) {
      for (const theme of ['dark','light']) {
        const context = await browser.newContext({viewport,reducedMotion:'reduce'});
        const page = await context.newPage();
        const errors=[]; page.on('pageerror', error=>errors.push(error.message));
        await page.addInitScript(({mode,theme,style})=> {
          const path='C:\\Users\\Очень длинное имя пользователя\\AppData\\Local\\Programs\\Nimbo';
          let next=0;const callbacks=new Map();
          window.__TAURI_EVENT_PLUGIN_INTERNALS__={unregisterListener:()=>{}};
          window.__TAURI_INTERNALS__={metadata:{currentWindow:{label:'main'},currentWebview:{label:'main'}},
            transformCallback:fn=>{callbacks.set(++next,fn);return next;},
            invoke:async(command,args)=> {
              if(command==='get_installer_mode')return mode==='uninstall'?'uninstall':'install';
              if(command==='read_app_theme')return {ui_style:style,theme_mode:theme};
              if(command==='probe_installation')return {default_install_dir:path,product_version:'1.3.0-beta.1',product_arch:'Windows x64',platform:'windows',existing_install:mode==='update',helper_installed:true,helper_running:true};
              if(command==='probe_uninstallation')return {install_dir:path,product_version:'1.3.0-beta.1',product_arch:'Windows x64',platform:'windows',helper_installed:true,helper_running:true,user_data_dir:'C:\\Users\\User\\AppData\\Roaming\\Nimbo',user_data_present:true};
              if(command==='choose_install_directory')return 'C:\\Apps\\Nimbo';
              if(command==='install_nimbo')throw 'Невозможно безопасно установить системную службу: на диске есть права записи у обычного пользователя. Прежняя установка сохранена.';
              if(command.includes('|listen'))return ++next;
              return null;
            }};
        },{mode,theme,style});
        await page.goto(`http://127.0.0.1:${server.address().port}/`);
        await page.locator('.path-input').waitFor();
        await page.locator('.hero-title').waitFor();
        const metrics=await page.evaluate(()=> {
          const panels=[...document.querySelectorAll('.installer-shell,.side-rail,.install-panel')];
          const actions=document.querySelector('.actions').getBoundingClientRect();
          const input=document.querySelector('.path-input');const field=input.closest('.path-row-field');
          return {overflow:panels.map(p=>({height:p.clientHeight,scroll:p.scrollHeight,width:p.clientWidth,scrollWidth:p.scrollWidth})),actions:{top:actions.top,bottom:actions.bottom},border:getComputedStyle(input).borderWidth,inputHeight:input.getBoundingClientRect().height,fieldHeight:field.getBoundingClientRect().height};
        });
        const label=`${mode}/${style}/${theme}/${viewport.width}x${viewport.height}`;
        assert(metrics.overflow.every(p=>p.scroll<=p.height+1),`initial scrollbar: ${label} ${JSON.stringify(metrics)}`);
        assert(metrics.overflow.every(p=>p.scrollWidth<=p.width+1),`horizontal overflow: ${label}`);
        assert(metrics.actions.top>=0 && metrics.actions.bottom<=viewport.height,`clipped actions: ${label}`);
        assert.equal(metrics.border,'0px',`nested path border: ${label}`);
        assert(metrics.fieldHeight>=metrics.inputHeight,`clipped path field: ${label}`);
        assert.deepEqual(errors,[],label);
        if(screenshots && viewport.width===1080 && mode==='update' && style==='signal')await page.screenshot({path:resolve(screenshots,`installer-${theme}.png`)});
        if(mode==='update') {
          await page.getByRole('button',{name:'Установить обновление',exact:true}).click();
          await page.locator('.error-box').waitFor();
          await page.locator('.actions').scrollIntoViewIfNeeded();
          assert(await page.locator('.primary-button').isVisible(),'retry action lost after error');
          assert(await page.evaluate(()=>document.querySelector('.install-panel').scrollWidth<=document.querySelector('.install-panel').clientWidth+1),'long error creates horizontal scrollbar');
        }
        cases++;await context.close();
      }
      }
    }
  }
  const page=await browser.newPage({viewport:{width:360,height:520}});
  await page.goto(`http://127.0.0.1:${server.address().port}/`);
  await page.locator('.path-input').waitFor();
  assert(await page.evaluate(()=>document.querySelector('.installer-shell').scrollWidth<=innerWidth),'narrow fallback horizontal overflow');
  await page.locator('.primary-button').scrollIntoViewIfNeeded();
  assert(await page.locator('.primary-button').isVisible(),'narrow fallback lost action');
  cases++;await page.close();
  console.log(`PASS: ${cases} layout cases; no initial scrollbar/clipped actions; single-line path; error/narrow fallback`);
} finally {await browser.close();await new Promise(done=>server.close(done));}
