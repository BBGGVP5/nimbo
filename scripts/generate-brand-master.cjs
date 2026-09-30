// Code-native reconstruction of the approved 2026-09-23 cloud.
// The reference PNG is preserved verbatim, never cropped or edited here.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const brand = path.resolve(__dirname, '../assets/branding/1.3.0-beta.1');
const cloudPath = 'M329 728C244 728 184 669 184 587C184 507 254 429 344 428C370 350 440 296 526 296C620 296 682 357 694 454C777 452 840 512 840 591C840 624 830 650 809 657C769 672 673 639 630 601C603 577 590 548 593 512C569 531 570 569 588 600C624 663 687 704 764 706C740 722 710 728 681 728Z';
const svg = (viewBox, size, content) => `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="${viewBox}">\n${content}\n</svg>\n`;
const glyph = `<path fill="#fff" d="${cloudPath}"/>`;
const master = svg('0 0 1024 1024', 1024, `  <defs>
    <linearGradient id="background" x2="0.25" y2="1"><stop stop-color="#292929"/><stop offset="1" stop-color="#171717"/></linearGradient>
    <linearGradient id="cloud" x1="0.35" y1="0" x2="0.65" y2="1"><stop stop-color="#ffffff"/><stop offset="0.55" stop-color="#f9f9f9"/><stop offset="1" stop-color="#e5e5e5"/></linearGradient>
  </defs>
  <rect width="1024" height="1024" fill="url(#background)"/>
  <path fill="url(#cloud)" d="${cloudPath}"/>`);
async function main() {
  fs.writeFileSync(path.join(brand,'cloud.svg'),svg('0 0 1024 1024',1024,`  ${glyph}`));
  fs.writeFileSync(path.join(brand,'cloud-template.svg'),svg('160 160 704 704',128,`  ${glyph}`));
  fs.writeFileSync(path.join(brand,'app-icon-master.svg'),master);
  await sharp(Buffer.from(master)).removeAlpha().png().toFile(path.join(brand,'app-icon-master.png'));
  console.log('Generated centered cloud vector and full-bleed 1024px master; reference PNG untouched.');
}
main().catch(error=>{console.error(error);process.exitCode=1;});
