const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const sharp=require('sharp');
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
