const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const crypto=require('node:crypto');
const sharp=require('sharp');
const root=path.resolve(__dirname,'..');
const brand=path.join(root,'assets/branding/1.3.0-beta.1');
const [desktop,site]=process.argv.slice(2);
const out=path.join(root,'.codex-tmp/core-selection-branding');
const hash=f=>crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');
const cloud=fs.readFileSync(path.join(brand,'cloud.svg'),'utf8');
const d=cloud.match(/ d="([^"]+)"/)[1];
async function main(){
 const original='C:/Users/Danila/.codex/generated_images/01a07d45-5f94-7d82-be22-ddbeebd93614/exec-8d5e4717-4f38-4cda-8df7-40f2140ae47f.png';
 assert.equal(hash(original),hash(path.join(brand,'approved-reference-2026-09-23.png')));
 const copies=[path.join(desktop,'assets/branding/1.3.0-beta.1/app-icon-master.png'),path.join(site,'public/assets/app-icon-master.png')];
 for(const copy of copies)assert.equal(hash(copy),hash(path.join(brand,'app-icon-master.png')));
 for(const file of [path.join(desktop,'assets/branding/1.3.0-beta.1/cloud.svg'),path.join(desktop,'apps/ui/src/components/ConnectionStateIcon.tsx'),path.join(site,'public/assets/cloud.svg'),path.join(site,'public/assets/favicon.svg'),path.join(site,'public/index.html')])assert(fs.readFileSync(file,'utf8').includes(d),file);
 const masterSvg=fs.readFileSync(path.join(brand,'app-icon-master.svg'),'utf8');
 assert(!/rx=|clipPath|<image/.test(masterSvg),'Master must have no baked-in tile or embedded screenshot');
 const radii=[];
 for(const density of ['mdpi','hdpi','xhdpi','xxhdpi','xxxhdpi']){
  const f=path.join(root,`app/src/main/res/mipmap-${density}/ic_launcher_monochrome.png`);
  const {data,info}=await sharp(f).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  let max=0;
  for(let y=0;y<info.height;y++)for(let x=0;x<info.width;x++)if(data[(y*info.width+x)*4+3]>8)max=Math.max(max,Math.hypot(x+.5-info.width/2,y+.5-info.height/2)*108/info.width);
  assert(max<33);radii.push({density,radiusDp:max});
 }
 const template=fs.readFileSync(path.join(brand,'cloud-template.svg'),'utf8');
 const png=await sharp(Buffer.from(template)).resize(128,128).ensureAlpha().png().toBuffer();
 assert.deepEqual(await sharp(path.join(desktop,'apps/ui/src-tauri/icons/tray.png')).raw().toBuffer(),await sharp(png).raw().toBuffer());
 const small=[];
 for(const size of [16,20,24,32,48]){
  const {data,info}=await sharp(Buffer.from(template)).resize(size,size).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const at=(x,y)=>data[(y*info.width+x)*4+3];
  assert(at(0,0)===0);assert(at(Math.floor(size*.5),Math.floor(size*.5))>240);
  assert(at(Math.floor(size*.76),Math.floor(size*.70))<128,'Swoosh remains open at '+size);
  small.push(size);
 }
 // Contact sheet rendered entirely from canonical vectors.
 const preview=`<svg xmlns="http://www.w3.org/2000/svg" width="960" height="450"><defs><clipPath id="circle"><circle cx="370" cy="145" r="100"/></clipPath><clipPath id="round"><rect x="490" y="45" width="200" height="200" rx="46"/></clipPath></defs><rect width="960" height="450" fill="#d5d5d5"/><g font-family="Arial" font-size="16" fill="#171717"><text x="30" y="30">Full-bleed master</text><text x="270" y="30">Android circle</text><text x="490" y="30">iOS system mask</text><text x="730" y="30">System glyph</text></g><svg x="30" y="45" width="200" height="200" viewBox="0 0 1024 1024">${masterSvg.replace(/^[\s\S]*?<svg[^>]*>/,'').replace('</svg>','')}</svg><g clip-path="url(#circle)"><rect x="270" y="45" width="200" height="200" fill="#101010"/><svg x="270" y="45" width="200" height="200" viewBox="0 0 1024 1024"><g transform="translate(112.64 112.64) scale(.78)"><path fill="white" d="${d}"/></g></svg></g><g clip-path="url(#round)"><svg x="490" y="45" width="200" height="200" viewBox="0 0 1024 1024">${masterSvg.replace(/^[\s\S]*?<svg[^>]*>/,'').replace('</svg>','')}</svg></g><rect x="730" y="45" width="200" height="200" rx="24" fill="#303030"/><svg x="750" y="65" width="160" height="160" viewBox="160 160 704 704"><path fill="white" d="${d}"/></svg>${[16,20,24,32,48].map((s,i)=>`<rect x="${30+i*180}" y="290" width="150" height="100" rx="12" fill="#282828"/><svg x="${40+i*180}" y="310" width="${s}" height="${s}" viewBox="160 160 704 704"><path fill="white" d="${d}"/></svg><text x="${30+i*180}" y="420" font-family="Arial" font-size="16">${s}px monochrome</text>`).join('')}</svg>`;
 await sharp(Buffer.from(preview)).png().toFile(path.join(out,'asset-preview.png'));
 const result={passed:true,approvedReferenceSha256:hash(original),canonicalMasterSha256:hash(path.join(brand,'app-icon-master.png')),samePathAllPlatforms:true,rasterAdaptiveSafeZone:radii,smallGlyphSizes:small,noBakedInMasterMask:true,trayMatchesTemplate:true};
 fs.writeFileSync(path.join(out,'asset-verification.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
