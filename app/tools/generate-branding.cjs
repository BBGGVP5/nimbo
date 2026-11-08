// Android packaging only. Never modifies the main-owned branding masters or signed APKs.
const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '../..');
const res = path.join(root, 'app/src/main/res');
const brand = path.join(root, 'assets/branding/1.3.0-beta.1');
const cloud = fs.readFileSync(path.join(brand, 'cloud.svg'), 'utf8');
const template = fs.readFileSync(path.join(brand, 'cloud-template.svg'), 'utf8');
const cloudPath = cloud.match(/ d="([^"]+)"/)[1];
if (template.match(/ d="([^"]+)"/)[1] !== cloudPath) throw Error('Brand paths differ');
const header = '<?xml version="1.0" encoding="utf-8"?>\n';
const ns = 'xmlns:android="http://schemas.android.com/apk/res/android"';
const write = (name, text) => { const target = path.join(res, name); fs.mkdirSync(path.dirname(target), {recursive:true}); fs.writeFileSync(target, text); };
const vector = (size, viewport, group, color = '#FFFFFFFF') => `${header}<!-- Generated from assets/branding/1.3.0-beta.1/cloud.svg. -->\n<vector ${ns} android:width="${size}dp" android:height="${size}dp" android:viewportWidth="${viewport}" android:viewportHeight="${viewport}">\n    <group ${group}>\n        <path android:fillColor="${color}" android:pathData="${cloudPath}" />\n    </group>\n</vector>\n`;
const adaptiveGroup = 'android:pivotX="512" android:pivotY="512" android:scaleX="0.78" android:scaleY="0.78"';
const adaptiveSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024"><g transform="translate(112.64 112.64) scale(.78)"><path fill="#fff" d="${cloudPath}"/></g></svg>`;
const alias = (ref) => `${header}<layer-list ${ns}><item android:drawable="@drawable/${ref}" /></layer-list>\n`;
const adaptive = (bg, fg = 'nimbo_cloud_adaptive') => `${header}<adaptive-icon ${ns}>\n    <background android:drawable="@drawable/${bg}" />\n    <foreground android:drawable="@drawable/${fg}" />\n    <monochrome android:drawable="@drawable/nimbo_cloud_adaptive" />\n</adaptive-icon>\n`;

(async () => {
    write('drawable/nimbo_cloud.xml', vector(24, 704, 'android:translateX="-160" android:translateY="-160"'));
    write('drawable/nimbo_cloud_canvas.xml', vector(108, 1024, 'android:scaleX="1"'));
    write('drawable/nimbo_cloud_canvas_dark.xml', vector(108, 1024, 'android:scaleX="1"', '#FF171717'));
    write('drawable/nimbo_cloud_adaptive.xml', vector(108, 1024, adaptiveGroup));
    write('drawable/nimbo_cloud_adaptive_dark.xml', vector(108, 1024, adaptiveGroup, '#FF171717'));
    for (const name of ['icon_notification', 'icon_notification_nimbo_blue', 'icon_quick_settings']) {
        write(`drawable/${name}.xml`, vector(24, 704, 'android:translateX="-160" android:translateY="-160"'));
    }
    write('drawable/ic_launcher_background.xml', `${header}<shape ${ns} android:shape="rectangle"><solid android:color="#101010" /></shape>\n`);
    write('values/ic_launcher_background.xml', `${header}<resources><color name="ic_launcher_background">#101010</color></resources>\n`);
    // Retain old resource names for external launchers/aliases; none keeps the old badge/keyhole.
    for (const file of fs.readdirSync(path.join(res, 'drawable')).filter(n => /^ic_launcher_.*\.xml$/.test(n) && n !== 'ic_launcher_background.xml')) {
        write(`drawable/${file}`, alias('nimbo_cloud_adaptive'));
    }
    for (const file of fs.readdirSync(path.join(res, 'mipmap-anydpi-v26'))) {
        if (!/^ic_(launcher|alias_)/.test(file)) continue;
        const old = fs.readFileSync(path.join(res, 'mipmap-anydpi-v26', file), 'utf8');
        const bg = old.match(/<background android:drawable="@drawable\/([^"]+)"/)?.[1];
        if (!bg) throw Error(`Missing adaptive background ${file}`);
        write(`mipmap-anydpi-v26/${file}`, adaptive(bg, file === 'ic_alias_0005.xml' ? 'nimbo_cloud_adaptive_dark' : 'nimbo_cloud_adaptive'));
    }
    for (const file of fs.readdirSync(path.join(res, 'mipmap-anydpi')).filter(n => /^ic_alias_\d+\.xml$/.test(n))) {
        const id = file.match(/\d+/)[0];
        const adaptiveFile = fs.readFileSync(path.join(res, 'mipmap-anydpi-v26', file), 'utf8');
        const bg = adaptiveFile.match(/<background android:drawable="@drawable\/([^"]+)"/)[1];
        const glyph = id === '0005' ? 'nimbo_cloud_canvas_dark' : 'nimbo_cloud_canvas';
        const layers = `${header}<layer-list ${ns}>\n    <item android:drawable="@drawable/${bg}" />\n    <item android:drawable="@drawable/${glyph}" />\n</layer-list>\n`;
        write(`mipmap-anydpi/${file}`, layers);
        write(`drawable/icon_alias_${id}.xml`, layers);
        write(`drawable/ic_alias_fg_${id}.xml`, alias(id === '0005' ? 'nimbo_cloud_adaptive_dark' : 'nimbo_cloud_adaptive'));
        await sharp(Buffer.from(cloud)).resize(432,432).png().toFile(path.join(res, `drawable/sprite_${id}.png`));
    }
    for (const [density, size, scale] of [['mdpi',48,1], ['hdpi',72,1.5], ['xhdpi',96,2], ['xxhdpi',144,3], ['xxxhdpi',192,4]]) {
        const dir = path.join(res, `mipmap-${density}`);
        for (const name of ['ic_launcher','ic_launcher_round','ic_launcher_nimbo_blue_v2','ic_launcher_nimbo_blue_v2_round']) {
            await sharp(path.join(brand, 'app-icon-master.png')).resize(size,size).removeAlpha().png().toFile(path.join(dir, `${name}.png`));
        }
        for (const name of ['ic_launcher_foreground','ic_launcher_monochrome']) {
            await sharp(Buffer.from(adaptiveSvg)).resize(Math.round(108*scale),Math.round(108*scale)).png().toFile(path.join(dir, `${name}.png`));
        }
        await sharp(Buffer.from(template)).resize(Math.round(24*scale),Math.round(24*scale)).png().toFile(path.join(res,`drawable-${density}/ic_stat_name.png`));
    }
    await sharp(path.join(brand, 'app-icon-master.png')).resize(192,192).removeAlpha().png().toFile(path.join(res, 'drawable-nodpi/nimbo_beta_notification.png'));
    await sharp(Buffer.from(template.replace('#fff', '#000'))).resize(192,192).png().toFile(path.join(res, 'drawable/icon_black.png'));
    // Use exactly the same canonical path in the legacy drawable's context-free fallback.
    const kotlin = path.join(root, 'app/src/main/java/com/danila/nimbo/utils/NimboBranding.kt');
    const source = fs.readFileSync(kotlin,'utf8').replace(/const val CLOUD_PATH = "[^"]*"/, `const val CLOUD_PATH = "${cloudPath}"`);
    fs.writeFileSync(kotlin,source);
    console.log('Android branding generated; no source masters or signed APKs changed.');
})().catch(error => { console.error(error); process.exitCode=1; });
