const fs = require('fs');
const path = require('path');
const assert = require('assert/strict');
const sharp = require('sharp');
const root = path.resolve(__dirname, '../..');
const res = path.join(root, 'app/src/main/res');
const out = path.join(root, 'app/docs/branding-130-beta1');
const svg = fs.readFileSync(path.join(root, 'assets/branding/1.3.0-beta.1/cloud.svg'), 'utf8');
const cloudPath = svg.match(/ d="([^"]+)"/)[1];
const adaptiveSvg = `<svg width="432" height="432" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><g transform="translate(112.64 112.64) scale(.78)"><path fill="white" d="${cloudPath}"/></g></svg>`;
(async () => {
    const adaptiveXml = fs.readFileSync(path.join(res,'drawable/nimbo_cloud_adaptive.xml'),'utf8');
    assert(adaptiveXml.includes('android:scaleX="0.78"') && adaptiveXml.includes(cloudPath));
    const {data,info} = await sharp(Buffer.from(adaptiveSvg)).ensureAlpha().raw().toBuffer({resolveWithObject:true});
    let pixels = 0, radius = 0;
    for(let y=0;y<info.height;y++) for(let x=0;x<info.width;x++) {
        if(data[(y*info.width+x)*4+3]>8) {pixels++;radius=Math.max(radius,Math.hypot(x+.5-216,y+.5-216));}
    }
    assert(pixels>1000); assert(radius<=132, `Outside Android 66dp safe circle: ${radius}`);
    for(const [density,size] of [['mdpi',48],['hdpi',72],['xhdpi',96],['xxhdpi',144],['xxxhdpi',192]]) {
        const metadata=await sharp(path.join(res,`mipmap-${density}/ic_launcher.png`)).metadata();
        assert.equal(metadata.width,size);assert.equal(metadata.height,size);assert.equal(metadata.hasAlpha,false);
        const glyph=await sharp(path.join(res,`mipmap-${density}/ic_launcher_monochrome.png`)).ensureAlpha().raw().toBuffer({resolveWithObject:true});
        assert.equal(glyph.data[3],0);
        assert.equal(glyph.data[(Math.floor(glyph.info.height/2)*glyph.info.width+Math.floor(glyph.info.width/2))*4+3],255);
    }
    const inputs = [
        await sharp(path.join(root,'assets/branding/1.3.0-beta.1/app-icon-master.png')).resize(216,216).png().toBuffer(),
        await sharp({create:{width:216,height:216,channels:4,background:'#101010'}}).composite([{input:await sharp(Buffer.from(adaptiveSvg)).resize(216,216).png().toBuffer()}]).png().toBuffer(),
        await sharp({create:{width:216,height:216,channels:4,background:'#56755b'}}).composite([{input:await sharp(Buffer.from(adaptiveSvg)).resize(216,216).png().toBuffer()}]).png().toBuffer(),
        await sharp({create:{width:216,height:216,channels:4,background:'#292929'}}).composite([{input:await sharp(Buffer.from(fs.readFileSync(path.join(root,'assets/branding/1.3.0-beta.1/cloud-template.svg')))).resize(96,96).png().toBuffer(),left:60,top:60}]).png().toBuffer()
    ];
    await sharp({create:{width:936,height:264,channels:4,background:'#dedede'}}).composite(inputs.map((input,i)=>({input,left:12+i*234,top:12}))).png().toFile(path.join(out,'android-branding-preview.png'));
    fs.writeFileSync(path.join(out,'pixel-validation.json'),JSON.stringify({densityChecks:5,adaptiveCanvasDp:108,safeCircleDiameterDp:66,maxGlyphRadiusDp:radius/4,opaqueLauncher:true,transparentGlyph:true,previewColumns:['master raster','adaptive vector','themed glyph','QS/notification alpha glyph']},null,2));
    console.log(`PASS:5densities/opaque master/alpha glyph; adaptive maxradius ${(radius/4).toFixed(2)}dp <=33dp. Preview saved.`);
})().catch(error=>{console.error(error);process.exitCode=1;});
