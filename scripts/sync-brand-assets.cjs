// Explicit cross-workspace branding sync. Does not build or publish applications.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
const source = path.join(root, 'assets/branding/1.3.0-beta.1');
const [desktop, site] = process.argv.slice(2);
if (!desktop || !site) throw Error('Usage: node scripts/sync-brand-assets.cjs <desktop-root> <site-root>');
async function main() {
  for (const name of ['app-icon-master.png','app-icon-master.svg','cloud.svg','cloud-template.svg','README.md']) {
    fs.copyFileSync(path.join(source,name),path.join(desktop,'assets/branding/1.3.0-beta.1',name));
  }
  fs.copyFileSync(path.join(root,'nimbo.ico'),path.join(desktop,'nimbo.ico'));
  const assets = path.join(site,'public/assets');
  const logo = path.join(assets,'nimbo-logo.png');
  const {width,height} = await sharp(logo).metadata();
  await sharp(path.join(source,'app-icon-master.png')).resize(width,height,{fit:'contain',background:'#171717'}).png().toFile(logo+'.new');
  fs.renameSync(logo+'.new',logo);
  fs.copyFileSync(path.join(source,'app-icon-master.png'),path.join(assets,'app-icon-master.png'));
  fs.copyFileSync(path.join(source,'cloud.svg'),path.join(assets,'cloud.svg'));
  // A browser favicon owns its single outer mask; app masters remain full bleed.
  const master = fs.readFileSync(path.join(source,'app-icon-master.svg'),'utf8');
  const favicon = master.replace('<defs>', '<defs><clipPath id="tile"><rect width="1024" height="1024" rx="210"/></clipPath>')
    .replace('</defs>', '</defs><g clip-path="url(#tile)">').replace('</svg>', '</g></svg>');
  fs.writeFileSync(path.join(assets,'favicon.svg'),favicon);
  const cloudPath = fs.readFileSync(path.join(source,'cloud.svg'),'utf8').match(/ d="([^"]+)"/)[1];
  const index = path.join(site,'public/index.html');
  const html = fs.readFileSync(index,'utf8');
  if (!/<symbol id="demo-cloud"[^>]*><path d="[^"]+"/.test(html)) throw Error('Missing website demo cloud');
  fs.writeFileSync(index,html.replace(/(<symbol id="demo-cloud"[^>]*><path d=")[^"]+"/,`$1${cloudPath}"`));
  console.log(`Synced desktop canonical assets and website branding; logo dimensions preserved: ${width}x${height}`);
}
main().catch(error=>{console.error(error);process.exitCode=1;});
