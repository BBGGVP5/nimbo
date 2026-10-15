// Asset invariants; read-only validation of generated and packaged images.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
const dir = path.join(root, 'assets/branding/1.3.0-beta.1');
async function main() {
  const {data, info} = await sharp(path.join(dir, 'app-icon-master.png'))
    .ensureAlpha().raw().toBuffer({resolveWithObject:true});
  assert.equal(info.width, info.height);
  let maxTint = 0, minAlpha = 255;
  for (let i=0;i<data.length;i+=4) {
    minAlpha = Math.min(minAlpha,data[i+3]);
    maxTint = Math.max(maxTint,Math.abs(data[i]-data[i+1]),Math.abs(data[i+1]-data[i+2]));
  }
  assert.equal(minAlpha,255,'Apple app-icon master must be opaque');
  assert.ok(maxTint < 16,'No noticeable colored tint');
  for (const [x,y] of [[0,0],[info.width-1,0],[0,info.height-1],[info.width-1,info.height-1]]) {
    assert.ok(data[(y*info.width+x)*4] < 55,'Corners must be black, not checkerboard');
  }
  const glyphPaths = ['cloud.svg','cloud-template.svg'].map(n => fs.readFileSync(path.join(dir,n),'utf8').match(/ d="([^"]+)"/)[1]);
  assert.equal(glyphPaths[0],glyphPaths[1]);
  const glyph = await sharp(path.join(dir,'cloud-template-128.png')).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  assert.equal(glyph.data[3],0,'System glyph needs transparent outer pixels');
  assert.equal(glyph.data[(64*128+64)*4+3],255,'Cloud must have opaque body');
  const png=await sharp(path.join(root,'nimbo.png')).metadata();
  assert.equal(png.width,1024); assert.equal(png.height,1024);
  const ico=fs.readFileSync(path.join(root,'nimbo.ico'));
  assert.equal(ico.readUInt16LE(2),1); assert.equal(ico.readUInt16LE(4),10);
  const sizes=[];
  for(let i=0;i<10;i++) {
    const e=6+i*16, size=ico[e]||256;
    const offset=ico.readUInt32LE(e+12), length=ico.readUInt32LE(e+8);
    assert.ok(offset+length<=ico.length);
    const m=await sharp(ico.subarray(offset,offset+length)).metadata();
    assert.equal(m.width,size); assert.equal(m.height,size); sizes.push(size);
  }
  assert.deepEqual(sizes,[256,128,96,64,48,40,32,24,20,16]);
  const result={passed:true,masterSize:info.width,opaque:true,maxRgbTint:maxTint,templateAlpha:true,icoSizes:sizes};
  fs.writeFileSync(path.join(dir,'master-verification.json'),JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify(result));
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
