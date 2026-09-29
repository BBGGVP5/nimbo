import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root = new URL('../../../', import.meta.url);
const read = p => fs.readFileSync(new URL(p,root));
const text = p => read(p).toString('utf8');
test('Beta1 versions agree across desktop app and installer metadata',()=>{
 for(const app of ['ui','installer']) for(const file of ['package.json','package-lock.json','src-tauri/tauri.conf.json']){
  const data=JSON.parse(text(`apps/${app}/${file}`));assert.equal(data.version,'1.3.0-beta.1');
  if(data.packages)assert.equal(data.packages[''].version,'1.3.0-beta.1');
 }
 assert.match(text('Cargo.toml'),/\[workspace.package\]\s+version = "1.3.0-beta.1"/);
});
test('Packaged app and tray icons are RGBA PNGs with valid dimensions',()=>{
 for(const [name,size] of [['32x32.png',32],['128x128.png',128],['128x128@2x.png',256],['tray.png',128]]){
  const b=read(`apps/ui/src-tauri/icons/${name}`);assert.equal(b.readUInt32BE(16),size);assert.equal(b.readUInt32BE(20),size);
  assert.equal(b[25],6,`${name} is RGBA for Tauri packaging`);
 }
 const ico=read('apps/ui/src-tauri/icons/icon.ico');assert.equal(ico.readUInt16LE(2),1);assert.equal(ico.readUInt16LE(4),7);
 const icns=read('apps/ui/src-tauri/icons/icon.icns');assert.equal(icns.toString('ascii',0,4),'icns');assert.equal(icns.readUInt32BE(4),icns.length);
 assert.deepEqual(ico,read('nimbo.ico'));
});
test('Beta defaults preserve explicitly saved stable preferences',()=>{
 const api=text('apps/ui/src/lib/api.ts');
 assert.ok(api.includes('update_channel: value?.update_channel === "stable" ? "stable" : "beta"'));
 assert.ok(api.includes('browserUpdateInfo(channel: UpdateChannel = "beta")'));
 const rust=text('apps/ui/src-tauri/src/state.rs');
 assert.match(rust,/pub enum UpdateChannel \{\s+Stable,\s+#\[default\]\s+Beta,/);
 assert.ok(rust.includes('fn beta_upgrade_preserves_explicit_stable_channel'));
});
