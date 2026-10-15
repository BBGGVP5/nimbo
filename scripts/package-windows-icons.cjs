// Windows-only derivative of the approved vector. Apple/Android masters stay opaque/unmodified.
const fs=require('node:fs');
const path=require('node:path');
const sharp=require('sharp');
const root=path.resolve(__dirname,'..');
const brand=path.join(root,'assets/branding/1.3.0-beta.1');
const output=path.join(root,'apps/ui/src-tauri/icons');
const sizes=[256,128,96,64,48,40,32,24,20,16];

async function main(){
 const master=fs.readFileSync(path.join(brand,'app-icon-master.svg'),'utf8');
 const square='<rect width="1024" height="1024" fill="url(#background)"/>';
 if(!master.includes(square))throw Error('Approved master background format changed');
 const windows=master.replace(square,'<rect x="16" y="16" width="992" height="992" rx="224" fill="url(#background)"/>');
 fs.writeFileSync(path.join(brand,'app-icon-windows.svg'),windows);
 async function render(size){
  const high=await sharp(Buffer.from(windows),{density:72*Math.max(1,size*4/1024)}).resize(size*4,size*4).ensureAlpha().png().toBuffer();
  const {data,info}=await sharp(high).resize(size,size,{kernel:'lanczos3'}).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  // Suppress sub-1/255 Lanczos ringing outside the vector mask; retain real AA coverage.
  for(let i=0;i<data.length;i+=4)if(data[i+3]<=1)data.fill(0,i,i+4);
  // These samples are wholly outside the rounded vector; remove low-resolution kernel lobes.
  for(const [x,y] of [[0,0],[size-1,0],[0,size-1],[size-1,size-1]]){const i=(y*size+x)*4;data.fill(0,i,i+4);}
  return sharp(data,{raw:{width:info.width,height:info.height,channels:4}}).png().toBuffer();
 }
 const frames=[];for(const size of sizes)frames.push(await render(size));
 const header=Buffer.alloc(6+sizes.length*16);header.writeUInt16LE(1,2);header.writeUInt16LE(sizes.length,4);
 let offset=header.length;
 for(let i=0;i<sizes.length;i++){
  const entry=6+i*16;header[entry]=header[entry+1]=sizes[i]===256?0:sizes[i];
  header.writeUInt16LE(1,entry+4);header.writeUInt16LE(32,entry+6);header.writeUInt32LE(frames[i].length,entry+8);header.writeUInt32LE(offset,entry+12);offset+=frames[i].length;
 }
 // Tauri decodes the FIRST ICO entry for its default window/taskbar icon. 16px first is pixelated.
 const ico=Buffer.concat([header,...frames]);
 fs.writeFileSync(path.join(output,'icon.ico'),ico);
 fs.writeFileSync(path.join(root,'nimbo.ico'),ico);
 for(const [name,size] of [['32x32.png',32],['128x128.png',128],['128x128@2x.png',256],['icon.png',512]]){
  fs.writeFileSync(path.join(output,name),size===512?await render(size):frames[sizes.indexOf(size)]);
 }
 console.log(JSON.stringify({windows:true,roundedAlpha:true,icoSizes:sizes,windowPng:512,masterUnmodified:true}));
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
