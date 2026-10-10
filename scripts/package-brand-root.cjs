// Deterministic platform-size/ICO packaging only; artwork lives in the master.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const {execFileSync}=require('node:child_process');
const root = path.resolve(__dirname, '..');
const brand = path.join(root, 'assets/branding/1.3.0-beta.1');

async function main() {
  const master = path.join(brand, 'app-icon-master.png');
  const meta = await sharp(master).metadata();
  if (meta.width !== meta.height) throw new Error('App master must be square');
  await sharp(master).resize(1024, 1024).png().toFile(path.join(root, 'nimbo.png'));
  // Do not regress Windows to a rectangular 16px-first ICO on branding regeneration.
  execFileSync(process.execPath,[path.join(__dirname,'package-windows-icons.cjs')],{stdio:'inherit'});
  // Preview the vector on its own transparency, not a colored system tile.
  await sharp(path.join(brand, 'cloud-template.svg')).resize(128, 128).png()
    .toFile(path.join(brand, 'cloud-template-128.png'));
  console.log(JSON.stringify({ master: meta.width, rootIcon: 1024, windowsIco:'rounded-high-resolution-first' }));
}
main().catch(e => { console.error(e.message); process.exitCode = 1; });
