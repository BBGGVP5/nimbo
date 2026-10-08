const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const sharp=require('sharp');
const os=require('node:os');
const {execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..');
const icons=path.join(root,'apps/ui/src-tauri/icons');

test('Windows ICO starts with high-resolution artwork and covers common DPI sizes',async()=>{
 const ico=fs.readFileSync(path.join(icons,'icon.ico'));assert.equal(ico.readUInt16LE(2),1);
 assert.deepEqual(ico,fs.readFileSync(path.join(root,'nimbo.ico')),'shared Windows ICO diverged');
 const sizes=Array.from({length:ico.readUInt16LE(4)},(_,i)=>ico[6+i*16]||256);
 assert.equal(sizes[0],256,'Tauri codegen decodes entry zero, not the largest available frame');
 assert.deepEqual(sizes,[256,128,96,64,48,40,32,24,20,16]);
 for(let i=0;i<sizes.length;i++){
  const offset=ico.readUInt32LE(6+i*16+12),length=ico.readUInt32LE(6+i*16+8);assert(offset+length<=ico.length);
  const {data,info}=await sharp(ico.subarray(offset,offset+length)).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  assert.equal(info.width,sizes[i]);assert.equal(info.height,sizes[i]);checkRounded(data,info);
 }
});
function checkRounded(data,info){
 for(const [x,y] of [[0,0],[info.width-1,0],[0,info.height-1],[info.width-1,info.height-1]])assert.equal(data[(y*info.width+x)*4+3],0,'transparent rounded corners');
 assert.equal(data[(Math.floor(info.height/2)*info.width+Math.floor(info.width/2))*4+3],255,'opaque cloud');
 let partial=0,contrast=0;for(let i=0;i<data.length;i+=4){if(data[i+3]>0&&data[i+3]<255)partial++;contrast=Math.max(contrast,data[i]);}
 assert(partial>0,'supersampled edges must contain fractional alpha');assert(contrast>220,'white cloud remains legible');
}
test('Windows PNGs are smooth RGBA derivatives of the approved vector, not a rectangular raster',async()=>{
 for(const [name,size] of [['32x32.png',32],['128x128.png',128],['128x128@2x.png',256],['icon.png',512]]){
  const {data,info}=await sharp(path.join(icons,name)).ensureAlpha().raw().toBuffer({resolveWithObject:true});assert.equal(info.width,size);assert.equal(info.height,size);checkRounded(data,info);
 }
 const dir=path.join(root,'assets/branding/1.3.0-beta.1');const windows=fs.readFileSync(path.join(dir,'app-icon-windows.svg'),'utf8');const master=fs.readFileSync(path.join(dir,'app-icon-master.svg'),'utf8');
 assert.equal(windows.match(/ d="([^"]+)"/)[1],master.match(/ d="([^"]+)"/)[1],'approved cloud contour changed');assert.match(windows,/rx="224"/);
 const apple=await sharp(path.join(dir,'app-icon-master.png')).ensureAlpha().raw().toBuffer();for(let i=3;i<apple.length;i+=4)assert.equal(apple[i],255,'Apple master must stay opaque');
});

test('app, installer, README and beta notes use the rounded shared artwork',()=>{
 const ico=path.join(icons,'icon.ico');
 for(const file of ['apps/ui/src-tauri/tauri.conf.json','apps/installer/src-tauri/tauri.conf.json']){
  const location=path.join(root,file),config=JSON.parse(fs.readFileSync(location,'utf8'));
  assert(config.bundle.icon.some(item=>path.resolve(path.dirname(location),item)===ico));
 }
 assert.match(fs.readFileSync(path.join(root,'README.md'),'utf8'),/src="\.\/apps\/ui\/src-tauri\/icons\/icon\.png"/);
 assert.match(fs.readFileSync(path.join(root,'docs/releases/1.3.0-beta.1.md'),'utf8'),/7e45a161d871b080f18e709d017b5753b1e18af1\/apps\/ui\/src-tauri\/icons\/icon\.png/);
});

test('full desktop branding regeneration cannot replace rounded Windows icons with the square Apple master',async()=>{
 const fixture=fs.mkdtempSync(path.join(os.tmpdir(),'nimbo-branding-'));
 const intended=fs.realpathSync(fixture),tempRoot=fs.realpathSync(os.tmpdir());
 assert(intended.startsWith(tempRoot+path.sep));
 try{
  function copy(relative){const destination=path.join(fixture,relative);fs.mkdirSync(path.dirname(destination),{recursive:true});fs.copyFileSync(path.join(root,relative),destination);}
  for(const file of ['apps/ui/scripts/package-branding.cjs','scripts/package-windows-icons.cjs','apps/ui/src/components/ConnectionStateIcon.tsx','nimbo.ico',
   ...['app-icon-master.png','app-icon-master.svg','cloud.svg','cloud-template.svg'].map(name=>'assets/branding/1.3.0-beta.1/'+name),
   ...['32x32.png','128x128.png','128x128@2x.png','icon.png','tray.png'].map(name=>'apps/ui/src-tauri/icons/'+name)])copy(file);
  fs.mkdirSync(path.join(fixture,'apps/ui/src/assets'),{recursive:true});fs.mkdirSync(path.join(fixture,'apps/ui/src-tauri/windows'),{recursive:true});
  const master=fs.readFileSync(path.join(fixture,'assets/branding/1.3.0-beta.1/app-icon-master.png'));
  const component=path.join(fixture,'apps/ui/src/components/ConnectionStateIcon.tsx');
  const power=fs.readFileSync(component,'utf8').match(/connection-state-power[^>]*>\s*<path d="([^"]+)"/)[1];
  execFileSync(process.execPath,[path.join(fixture,'apps/ui/scripts/package-branding.cjs')],{stdio:'pipe',env:{...process.env,NODE_PATH:path.dirname(path.dirname(require.resolve('sharp/package.json')))}});
  assert.deepEqual(fs.readFileSync(path.join(fixture,'assets/branding/1.3.0-beta.1/app-icon-master.png')),master);
  assert.equal(fs.readFileSync(component,'utf8').match(/connection-state-power[^>]*>\s*<path d="([^"]+)"/)[1],power,'branding must not overwrite the power layer');
  for(const name of ['32x32.png','128x128.png','128x128@2x.png','icon.png']){
   const {data,info}=await sharp(path.join(fixture,'apps/ui/src-tauri/icons',name)).ensureAlpha().raw().toBuffer({resolveWithObject:true});checkRounded(data,info);
  }
 }finally{
  // Delete only this verified, uniquely created test directory; never a computed workspace root.
  assert.equal(fs.realpathSync(fixture),intended);assert(path.basename(intended).startsWith('nimbo-branding-'));
  fs.rmSync(fixture,{recursive:true,force:true});
 }
});
