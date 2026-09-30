// Deterministic platform-size/ICO packaging only; artwork lives in the master.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
const brand = path.join(root, 'assets/branding/1.3.0-beta.1');

async function main() {
  const master = path.join(brand, 'app-icon-master.png');
  const meta = await sharp(master).metadata();
  if (meta.width !== meta.height) throw new Error('App master must be square');
  await sharp(master).resize(1024, 1024).png().toFile(path.join(root, 'nimbo.png'));
  const sizes = [16, 24, 32, 48, 64, 128, 256];
  const pngs = await Promise.all(sizes.map(s => sharp(master).resize(s, s).png().toBuffer()));
  const header = Buffer.alloc(6 + sizes.length * 16);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(sizes.length, 4);
  let offset = header.length;
  pngs.forEach((png, i) => {
    const entry = 6 + i * 16;
    header[entry] = header[entry + 1] = sizes[i] === 256 ? 0 : sizes[i];
    header.writeUInt16LE(1, entry + 4);
    header.writeUInt16LE(32, entry + 6);
    header.writeUInt32LE(png.length, entry + 8);
    header.writeUInt32LE(offset, entry + 12);
    offset += png.length;
  });
  fs.writeFileSync(path.join(root, 'nimbo.ico'), Buffer.concat([header, ...pngs]));
  // Preview the vector on its own transparency, not a colored system tile.
  await sharp(path.join(brand, 'cloud-template.svg')).resize(128, 128).png()
    .toFile(path.join(brand, 'cloud-template-128.png'));
  console.log(JSON.stringify({ master: meta.width, rootIcon: 1024, icoSizes: sizes }));
}
main().catch(e => { console.error(e.message); process.exitCode = 1; });
