/* Package approved branding using Node/sharp; never regenerate the master.
 * Run: NODE_PATH=<directory containing sharp> node iosApp/Tools/package-branding.cjs
 */
const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');

const ios = path.resolve(__dirname, '..');
const source = path.resolve(ios, '../assets/branding/1.3.0-beta.1');
const catalog = path.join(ios, 'Branding/Branding.xcassets');
const info = { author: 'xcode', version: 1 };

async function write(relative, content) {
  const target = path.resolve(ios, relative);
  if (!target.startsWith(ios + path.sep)) throw new Error('Output must stay inside iosApp');
  await fs.mkdir(path.dirname(target), { recursive: true });
  await fs.writeFile(target, content);
}
async function json(relative, value) { await write(relative, JSON.stringify(value, null, 2) + '\n'); }

function symbolSVG(cloudPath) {
  // Apple symbol template v3. All variants retain the exact brand outline.
  // The cloud's bounds are x184...840, y296...728. Fit its height to the
  // template's 70.54pt cap height, and align its bottom with each baseline.
  const weights = ['Ultralight', 'Thin', 'Light', 'Regular', 'Medium', 'Semibold', 'Bold', 'Heavy', 'Black'];
  const sizes = [['S', 696], ['M', 1126], ['L', 1556]];
  const scale = 70.54 / 432;
  const width = 656 * scale;
  const guides = [], variants = [];
  for (const [size, baseline] of sizes) {
    guides.push(`<line id="Baseline-${size}" x1="100" y1="${baseline}" x2="3200" y2="${baseline}"/>`,
      `<line id="Capline-${size}" x1="100" y1="${baseline - 70.54}" x2="3200" y2="${baseline - 70.54}"/>`);
    weights.forEach((weight, index) => {
      const x = 300 + index * 330;
      const id = `${weight}-${size}`;
      guides.push(`<line id="left-margin-${id}" x1="${x - 4}" y1="${baseline - 85}" x2="${x - 4}" y2="${baseline + 10}"/>`,
        `<line id="right-margin-${id}" x1="${x + width + 4}" y1="${baseline - 85}" x2="${x + width + 4}" y2="${baseline + 10}"/>`);
      variants.push(`<g id="${id}" transform="translate(${x} ${baseline})"><path fill="#000" transform="scale(${scale}) translate(-184 -728)" d="${cloudPath}"/></g>`);
    });
  }
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" version="1.1" width="3300" height="2200" viewBox="0 0 3300 2200">
  <g id="Notes"><text id="template-version">Template v.3.0</text></g>
  <g id="Guides" fill="none" stroke="#00AEEF" stroke-width="0.5">${guides.join('\n    ')}</g>
  <g id="Symbols">${variants.join('\n    ')}</g>
</svg>
`;
}

async function main() {
  const master = await fs.readFile(path.join(source, 'app-icon-master.png'));
  const icon = await sharp(master).resize(1024, 1024, { fit: 'fill', kernel: 'lanczos3' })
    .flatten({ background: '#000000' }).removeAlpha().png().toBuffer();
  await write('Nimbo/Assets.xcassets/AppIcon.appiconset/Nimbo-AppIcon-1024.png', icon);
  const cloud = await fs.readFile(path.join(source, 'cloud-template.svg'), 'utf8');
  const cloudPath = cloud.match(/<path\b[^>]*\bd="([^"]+)"/)[1];
  await json('Branding/Branding.xcassets/Contents.json', { info });
  await write('Branding/Branding.xcassets/NimboCloud.imageset/cloud-template.svg', cloud);
  await json('Branding/Branding.xcassets/NimboCloud.imageset/Contents.json', {
    images: [{ filename: 'cloud-template.svg', idiom: 'universal' }], info,
    properties: { 'preserves-vector-representation': true, 'template-rendering-intent': 'template' }
  });
  await write('Branding/Branding.xcassets/NimboCloudSymbol.symbolset/NimboCloudSymbol.svg', symbolSVG(cloudPath));
  await json('Branding/Branding.xcassets/NimboCloudSymbol.symbolset/Contents.json', {
    symbols: [{ filename: 'NimboCloudSymbol.svg', idiom: 'universal' }], info
  });
  console.log('Packaged 1024px opaque AppIcon, alpha template and Control Center symbol under ' + catalog);
}
if (require.main === module) main().catch(error => { console.error(error); process.exitCode = 1; });
