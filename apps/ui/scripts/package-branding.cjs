// Platform packaging of the approved master and code-native system glyph.
const fs=require('node:fs');
const path=require('node:path');
const sharp=require('sharp');
const root=path.resolve(__dirname,'../../..');
const source=path.join(root,'assets/branding/1.3.0-beta.1');
const icons=path.join(root,'apps/ui/src-tauri/icons');
async function main(){
 const cloudPath=fs.readFileSync(path.join(source,'cloud.svg'),'utf8').match(/ d="([^"]+)"/)[1];
 const component=path.join(root,'apps/ui/src/components/ConnectionStateIcon.tsx');
 const componentSource=fs.readFileSync(component,'utf8');
 if(!/<path d="M\d+ [^\"]+" \/>/.test(componentSource))throw Error('Missing connection cloud path');
 fs.writeFileSync(component,componentSource.replace('viewBox="160 152 704 704"','viewBox="160 160 704 704"').replace(/(<path d=")M\d+ [^\"]+" \/>/,`$1${cloudPath}" />`));
 const master=path.join(source,'app-icon-master.png');
 for(const name of fs.readdirSync(icons).filter(n=>n.endsWith('.png')&&n!=='tray.png')){
  const dst=path.join(icons,name), meta=await sharp(dst).metadata();
  await sharp(master).resize(meta.width,meta.height).png().toFile(dst+'.new');
  fs.renameSync(dst+'.new',dst);
 }
 await sharp(master).resize(1024,1024).png().toFile(path.join(root,'nimbo.png'));
 await sharp(master).resize(512,512).png().toFile(path.join(root,'apps/ui/src/assets/nimbo.png'));
 fs.copyFileSync(path.join(root,'nimbo.ico'),path.join(icons,'icon.ico'));
 await sharp(path.join(source,'cloud-template.svg')).resize(128,128).ensureAlpha().png().toFile(path.join(icons,'tray.png'));
 const chunks=[];
 for(const [type,size] of [['icp4',16],['icp5',32],['icp6',64],['ic07',128],['ic08',256],['ic09',512],['ic10',1024]]){
  const png=await sharp(master).resize(size,size).png().toBuffer();
  const header=Buffer.alloc(8);header.write(type);header.writeUInt32BE(8+png.length,4);chunks.push(header,png);
 }
 const head=Buffer.alloc(8);head.write('icns');head.writeUInt32BE(8+chunks.reduce((n,b)=>n+b.length,0),4);
 fs.writeFileSync(path.join(icons,'icon.icns'),Buffer.concat([head,...chunks]));
 // Native NSIS bitmap surfaces use the same cloud, never the old blue logo.
 const cloud=fs.readFileSync(path.join(source,'cloud.svg'),'utf8').match(/<path[^>]+\/>/)[0];
 for(const [name,w,h] of [['installer-header.bmp',150,57],['installer-welcome.bmp',164,314]]){
  const size=Math.min(w,h)*.9;
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}"><rect width="100%" height="100%" fill="#151515"/><svg x="${(w-size)/2}" y="${(h-size)/2}" width="${size}" height="${size}" viewBox="0 0 1024 1024">${cloud}</svg></svg>`;
  const rgb=await sharp(Buffer.from(svg)).removeAlpha().raw().toBuffer();
  const stride=(w*3+3)&~3, bmp=Buffer.alloc(54+stride*h);
  bmp.write('BM');bmp.writeUInt32LE(bmp.length,2);bmp.writeUInt32LE(54,10);bmp.writeUInt32LE(40,14);
  bmp.writeInt32LE(w,18);bmp.writeInt32LE(h,22);bmp.writeUInt16LE(1,26);bmp.writeUInt16LE(24,28);bmp.writeUInt32LE(stride*h,34);
  for(let y=0;y<h;y++)for(let x=0;x<w;x++){
   const s=(y*w+x)*3,d=54+(h-1-y)*stride+x*3;bmp[d]=rgb[s+2];bmp[d+1]=rgb[s+1];bmp[d+2]=rgb[s];
  }
  fs.writeFileSync(path.join(root,'apps/ui/src-tauri/windows',name),bmp);
 }
 console.log('Desktop PNG/ICO/ICNS/tray/UI and NSIS bitmaps packaged');
}
main().catch(e=>{console.error(e);process.exitCode=1;});
