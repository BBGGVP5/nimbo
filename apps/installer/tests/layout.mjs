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
  for (const viewport of [{width:1080,height:680},{width:780,height:520},{width:360,height:520}]) {
    for (const theme of ['dark','light']) {
      for (const style of ['signal','material_you','dotted']) {
        const context=await browser.newContext({viewport,reducedMotion:'reduce'});
        const page=await context.newPage();
        await mockRecovery(page,{theme,style});
        await page.goto(`http://127.0.0.1:${server.address().port}/`);
        await page.getByRole('button',{name:'Установить обновление',exact:true}).click();
        await page.locator('.permission-recovery').waitFor();
        const action=page.getByRole('button',{name:'Исправить и продолжить',exact:true});
        assert(await action.isDisabled(),'recovery must require unchecked consent');
        assert.deepEqual(await page.evaluate(()=>window.__testCalls),['install'],'no repair on panel mount');
        assert(!await page.locator('body').innerText().then(t=>t.includes('NIMBO_PERMISSIONS_REPAIR_AVAILABLE')),'raw code exposed');
        assert(await page.evaluate(()=>document.querySelector('.install-panel').scrollWidth<=document.querySelector('.install-panel').clientWidth+1),'recovery horizontal overflow');
        if(viewport.width>=780) assert(await page.evaluate(()=>document.querySelector('.install-panel').scrollHeight<=document.querySelector('.install-panel').clientHeight+1),'recovery initial scrollbar');
        if(screenshots && viewport.width===1080 && style==='signal')await page.screenshot({path:resolve(screenshots,`permissions-${theme}.png`)});
        await page.getByRole('button',{name:'Не сейчас',exact:true}).click();
        assert.deepEqual(await page.evaluate(()=>window.__testCalls),['install'],'cancel must not mutate permissions or retry install');
        await page.getByRole('button',{name:'Установить обновление',exact:true}).click();
        const checkbox=page.getByRole('checkbox',{name:'Разрешаю исправить это разрешение на корне диска.'});
        await checkbox.focus();await page.keyboard.press('Space');
        assert(await action.isEnabled(),'keyboard consent must enable recovery');
        await action.click();
        await page.getByRole('button',{name:'Исправляем…',exact:true}).waitFor();
        assert(await page.getByRole('button',{name:'Не сейчас',exact:true}).isDisabled(),'cancel during repair must be disabled');
        assert(await page.getByRole('button',{name:'Исправляем…',exact:true}).isDisabled(),'duplicate repair must be disabled');
        assert.deepEqual(await page.evaluate(()=>window.__testCalls),['install','install','repair']);
        await page.evaluate(()=>window.__completeRepair());
        await page.getByRole('heading',{name:'Nimbo установлен',exact:true}).waitFor();
        assert.deepEqual(await page.evaluate(()=>window.__testCalls),['install','install','repair','install'],'exactly one repair then install retry');
        cases++;await context.close();
      }
    }
  }
  for (const failure of ['unsupported','uac']) {
    const page=await browser.newPage({viewport:{width:1080,height:680}});
    await mockRecovery(page,{theme:'dark',style:'signal',failure});
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.getByRole('button',{name:'Установить обновление',exact:true}).click();
    if(failure==='unsupported') {
      await page.locator('.error-box').waitFor();
      assert.equal(await page.locator('.permission-recovery').count(),0,'unsafe unknown ACL must never offer recovery');
    } else {
      await page.getByRole('checkbox',{name:'Разрешаю исправить это разрешение на корне диска.'}).check();
      await page.getByRole('button',{name:'Исправить и продолжить',exact:true}).click();
      await page.getByRole('alert').waitFor();
      assert((await page.getByRole('alert').innerText()).includes('отменена'));
      assert(await page.getByRole('button',{name:'Исправить и продолжить',exact:true}).isEnabled(),'UAC cancellation must allow explicit retry');
      assert.deepEqual(await page.evaluate(()=>window.__testCalls),['install','repair'],'UAC cancellation must not retry installation');
    }
    cases++;await page.close();
  }
  for (const viewport of [{width:1080,height:680},{width:900,height:600},{width:780,height:520},{width:360,height:520}]) {
    for (const theme of ['dark','light']) for (const style of ['signal','material_you','dotted']) {
      for (const mode of ['install','update','uninstall-retain','uninstall-remove']) {
        const context=await browser.newContext({viewport,reducedMotion:'reduce',deviceScaleFactor:2});
        const page=await context.newPage();
        const errors=[];page.on('pageerror',error=>errors.push(error.message));
        const path='C:\\Users\\Очень длинное имя пользователя\\AppData\\Local\\Programs\\' + 'Nimbo'.repeat(18);
        await mockCompletion(page,{theme,style,mode,path});
        await page.goto(`http://127.0.0.1:${server.address().port}/`);
        const uninstall=mode.startsWith('uninstall');
        if(mode==='uninstall-remove')await page.getByRole('checkbox').check();
        await page.getByRole('button',{name:uninstall?'Удалить Nimbo':mode==='update'?'Установить обновление':'Установить',exact:true}).click();
        await page.getByRole('heading',{name:uninstall?'Nimbo удалён':'Nimbo установлен',exact:true}).waitFor();
        const metrics=await page.evaluate(()=>{
          const bounds=selector=>{const r=document.querySelector(selector)?.getBoundingClientRect();return r?{top:r.top,bottom:r.bottom,height:r.height,left:r.left,right:r.right}:null;};
          return {panel:bounds('.install-panel'),content:bounds('.done-content'),actions:bounds('.done-actions'),path:bounds('.done-path'),
            heading:bounds('.done-screen h1'),message:bounds('.done-screen p'),buttons:[...document.querySelectorAll('.done-actions button')].map(b=>({top:b.getBoundingClientRect().top,bottom:b.getBoundingClientRect().bottom})),
            overflow:[...document.querySelectorAll('.install-panel,.done-screen,.done-path')].some(p=>p.scrollWidth>p.clientWidth+1),
            scroll:document.querySelector('.install-panel').scrollHeight>document.querySelector('.install-panel').clientHeight+1,
            selectable:document.querySelector('.done-path')?getComputedStyle(document.querySelector('.done-path')).userSelect:null,
            checkOffset:getComputedStyle(document.querySelector('.done-check')).strokeDashoffset};
        });
        const label=`completion/${mode}/${style}/${theme}/${viewport.width}`;
        assert.equal(metrics.checkOffset,'0px',`reduced-motion hides success check: ${label}`);
        assert(metrics.actions.top-(metrics.path??metrics.message??metrics.heading).bottom<=40,`detached completion actions: ${label} ${JSON.stringify(metrics)}`);
        assert(metrics.content && (viewport.width<780 || metrics.content.height<420),`unbounded completion block: ${label}`);
        assert(!metrics.overflow,`completion horizontal overflow: ${label}`);
        if(viewport.width>=780) {
          assert(!metrics.scroll,`completion scrollbar: ${label}`);
          assert(Math.abs((metrics.content.top+metrics.content.bottom)-(metrics.panel.top+metrics.panel.bottom))<16,`completion not centered: ${label}`);
          assert(metrics.buttons.every(b=>Math.abs(b.top-metrics.buttons[0].top)<1),`desktop actions stacked: ${label}`);
          assert(metrics.actions.bottom<=viewport.height,`clipped completion actions: ${label}`);
        }
        if(mode!=='uninstall-remove') {
          assert.equal(await page.locator('.done-path').innerText(),path,label);
          assert.equal(metrics.selectable,'text',label);
        } else {
          assert.equal(await page.locator('.done-path').count(),0,'deleted data must not be shown as retained');
          assert((await page.locator('.done-screen').innerText()).includes('пользовательские данные удалены'));
        }
        if(screenshots && viewport.width===1080 && style==='signal' && mode==='update')await page.screenshot({path:resolve(screenshots,`completion-${theme}@2x.png`)});
        if(screenshots && viewport.width===360 && style==='signal' && theme==='dark' && mode==='install')await page.screenshot({path:resolve(screenshots,'completion-narrow.png'),fullPage:true});
        const action=page.locator('.done-actions').getByRole('button',{name:uninstall?'Закрыть':'Открыть Nimbo',exact:true});
        await action.scrollIntoViewIfNeeded();await action.click();
        assert.deepEqual(await page.evaluate(()=>window.__completionCalls),uninstall?[['uninstall',mode==='uninstall-remove'],['close']]:[['install'],['open',path],['close']],label);
        assert.deepEqual(errors,[],label);
        cases++;await context.close();
      }
    }
  }
  if(screenshots) for(const theme of ['dark','light']) {
    const page=await browser.newPage({viewport:{width:1080,height:680},deviceScaleFactor:2,reducedMotion:'reduce'});
    await mockCompletion(page,{theme,style:'signal',mode:'update',path:'C:\\Users\\Danila\\AppData\\Local\\Programs\\Nimbo'});
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.getByRole('button',{name:'Установить обновление',exact:true}).click();
    await page.getByRole('heading',{name:'Nimbo установлен',exact:true}).waitFor();
    await page.screenshot({path:resolve(screenshots,`completion-normal-${theme}@2x.png`)});await page.close();
  }
  console.log(`PASS: ${cases} layout cases; wizard/recovery/completion; centered results, adjacent actions, selectable paths and narrow fallback`);
} finally {await browser.close();await new Promise(done=>server.close(done));}

async function mockRecovery(page,options) {
  await page.addInitScript(({theme,style,failure})=>{
    let next=0,repaired=false;window.__testCalls=[];
    window.__TAURI_EVENT_PLUGIN_INTERNALS__={unregisterListener:()=>{}};
    window.__TAURI_INTERNALS__={metadata:{currentWindow:{label:'main'},currentWebview:{label:'main'}},transformCallback:()=>++next,
      invoke:async(command,args)=>{
        if(command==='get_installer_mode')return 'install';
        if(command==='read_app_theme')return {theme_mode:theme,ui_style:style};
        if(command==='probe_installation')return {default_install_dir:'C:\\Users\\User\\AppData\\Local\\Programs\\Nimbo',product_version:'1.3.0-beta.1',product_arch:'Windows x64',platform:'windows',existing_install:true,helper_installed:true,helper_running:true};
        if(command==='install_nimbo') {
          window.__testCalls.push('install');
          if(failure==='unsupported')throw 'Этот случай нельзя исправить автоматически. Прежняя установка сохранена.';
          if(!repaired)throw 'NIMBO_PERMISSIONS_REPAIR_AVAILABLE';
          return {install_dir:'C:\\Apps\\Nimbo',app_exe:'C:\\Apps\\Nimbo\\Nimbo.exe'};
        }
        if(command==='repair_install_permissions') {
          if(args.consent!==true)throw 'missing consent';
          window.__testCalls.push('repair');
          if(failure==='uac')throw 'Установка системной службы отменена в запросе прав администратора.';
          await new Promise(resolve=>{window.__completeRepair=()=>{repaired=true;resolve();};});
          return null;
        }
        if(command.includes('|listen'))return ++next;
        return null;
      }};
  },options);
}

async function mockCompletion(page,options) {
  await page.addInitScript(({theme,style,mode,path})=>{
    let next=0;window.__completionCalls=[];
    window.__TAURI_EVENT_PLUGIN_INTERNALS__={unregisterListener:()=>{}};
    window.__TAURI_INTERNALS__={metadata:{currentWindow:{label:'main'},currentWebview:{label:'main'}},transformCallback:()=>++next,
      invoke:async(command,args)=>{
        if(command==='get_installer_mode')return mode.startsWith('uninstall')?'uninstall':'install';
        if(command==='read_app_theme')return {theme_mode:theme,ui_style:style};
        if(command==='probe_installation')return {default_install_dir:'C:\\Apps\\Nimbo',product_version:'1.3.0-beta.1',product_arch:'Windows x64',platform:'windows',existing_install:mode==='update',helper_installed:true,helper_running:true};
        if(command==='probe_uninstallation')return {install_dir:'C:\\Apps\\Nimbo',user_data_dir:path,user_data_present:true,platform:'windows',helper_installed:true};
        if(command==='install_nimbo'){window.__completionCalls.push(['install']);return {install_dir:path,app_exe:path+'\\Nimbo.exe'};}
        if(command==='uninstall_nimbo'){window.__completionCalls.push(['uninstall',args.options.remove_user_data]);return {install_dir:'C:\\Apps\\Nimbo',removed_user_data:args.options.remove_user_data};}
        if(command==='open_nimbo')window.__completionCalls.push(['open',args.installDir]);
        if(command==='plugin:window|close')window.__completionCalls.push(['close']);
        if(command.includes('|listen'))return ++next;
        return null;
      }};
  },options);
}
