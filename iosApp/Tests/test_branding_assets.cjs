const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');

async function main() {
  const ios = path.resolve(__dirname, '..');
  const source = path.resolve(ios, '../assets/branding/1.3.0-beta.1');
  const catalog = path.join(ios, 'Branding/Branding.xcassets');
  const icon = path.join(ios, 'Nimbo/Assets.xcassets/AppIcon.appiconset/Nimbo-AppIcon-1024.png');
  const metadata = await sharp(icon).metadata();
  assert.equal(metadata.width, 1024);
  assert.equal(metadata.height, 1024);
  assert.equal(metadata.hasAlpha, false, 'AppIcon must have no alpha channel');
  const actual = await sharp(icon).raw().toBuffer();
  const expected = await sharp(path.join(source, 'app-icon-master.png')).resize(1024, 1024, { fit: 'fill', kernel: 'lanczos3' })
    .flatten({ background: '#000000' }).removeAlpha().raw().toBuffer();
  assert.deepEqual(actual, expected, 'AppIcon must be derived from the approved master');
  const template = await fs.readFile(path.join(catalog, 'NimboCloud.imageset/cloud-template.svg'), 'utf8');
  assert.equal(template, await fs.readFile(path.join(source, 'cloud-template.svg'), 'utf8'));
  const { data, info } = await sharp(Buffer.from(template)).resize(128, 128).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  let visible = 0, transparent = 0;
  for (let i = 0; i < data.length; i += info.channels) {
    if (data[i + 3] === 0) { transparent++; continue; }
    visible++;
    assert.equal(data[i], 255); assert.equal(data[i + 1], 255); assert.equal(data[i + 2], 255);
  }
  assert(visible > 3000 && transparent > 3000, 'Template must be a visible white cloud on alpha, not a black square');
  const contents = JSON.parse(await fs.readFile(path.join(catalog, 'NimboCloud.imageset/Contents.json')));
  assert.equal(contents.properties['template-rendering-intent'], 'template');
  assert.equal(contents.properties['preserves-vector-representation'], true);
  const symbol = await fs.readFile(path.join(catalog, 'NimboCloudSymbol.symbolset/NimboCloudSymbol.svg'), 'utf8');
  const symbolContents = JSON.parse(await fs.readFile(path.join(catalog, 'NimboCloudSymbol.symbolset/Contents.json')));
  assert.equal(symbolContents.symbols[0].filename, 'NimboCloudSymbol.svg');
  const cloudPath = template.match(/<path\b[^>]*\bd="([^"]+)"/)[1];
  const paths = [...symbol.matchAll(/<path\b[^>]*\bd="([^"]+)"/g)];
  assert.equal(paths.length, 27);
  for (const p of paths) assert.equal(p[1], cloudPath, 'Symbol variants must preserve the supplied cloud path');
  for (const size of ['S', 'M', 'L']) {
    assert(symbol.includes(`id="Baseline-${size}"`)); assert(symbol.includes(`id="Capline-${size}"`));
    for (const weight of ['Ultralight', 'Thin', 'Light', 'Regular', 'Medium', 'Semibold', 'Bold', 'Heavy', 'Black']) {
      assert(symbol.includes(`id="${weight}-${size}"`));
      assert(symbol.includes(`id="left-margin-${weight}-${size}"`));
      assert(symbol.includes(`id="right-margin-${weight}-${size}"`));
    }
  }
  assert(symbol.includes('Template v.3.0'));
  assert(!symbol.includes('<image'), 'Control symbol must contain vector paths, not an embedded bitmap');
  console.log('PASS: approved master pixels, 1024px opaque icon, white alpha template, exact symbol shape and all 27 variants/guides');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
