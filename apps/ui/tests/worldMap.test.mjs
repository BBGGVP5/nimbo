import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';

const source = readFileSync(new URL('../src/lib/worldMap.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
const geo = {};
vm.runInNewContext(code, { exports: geo, Intl, Float32Array, Math });

const copy = readFileSync(new URL('../src/assets/geo/nimbo-world-map.json', import.meta.url), 'utf8');
const shared = readFileSync(new URL('../../../assets/geo/nimbo-world-map.json', import.meta.url), 'utf8');
const data = JSON.parse(copy);

test('copy of the shared map stays identical to assets/geo', () => {
  assert.equal(copy, shared);
});

test('land decodes to cell centres inside the grid', () => {
  const land = geo.decodeLand(data);
  assert.ok(land.length / 2 > 5000, 'expected thousands of land dots');
  for (let i = 0; i < land.length; i += 2) {
    assert.ok(land[i] > -180 && land[i] < 180);
    assert.ok(land[i + 1] < 80 && land[i + 1] > -56);
  }
  // Москва и Франкфурт — суша, середина Атлантики — нет.
  const has = (lat, lon) => {
    for (let i = 0; i < land.length; i += 2) if (Math.abs(land[i] - lon) <= 0.5 && Math.abs(land[i + 1] - lat) <= 0.5) return true;
    return false;
  };
  assert.ok(has(55.5, 37.5));
  assert.ok(has(50.5, 8.5));
  assert.ok(!has(30.5, -40.5));
});

test('home location: timezone, then locale region, then Moscow', () => {
  assert.deepEqual([...geo.resolveHomeLocation(data, 'Europe/Berlin', ['ru-RU']).point], data.timezones['Europe/Berlin']);
  const byLocale = geo.resolveHomeLocation(data, 'Mars/Base', ['de-DE']);
  assert.equal(byLocale.source, 'locale');
  assert.deepEqual([...byLocale.point], data.countries.DE);
  const fallback = geo.resolveHomeLocation(data, undefined, []);
  assert.equal(fallback.source, 'fallback');
  assert.deepEqual([...fallback.point], [55.76, 37.62]);
});

test('viewport keeps every point inside the inner rect above the card', () => {
  const points = [data.countries.RU, data.countries.US, data.countries.JP, data.countries.DE];
  const width = 1000, height = 500, insets = { top: 8, right: 0, bottom: 180, left: 0 };
  const vp = geo.fitViewport(points, width, height, { insets });
  for (const [lat, lon] of points) {
    const [x, y] = geo.project(vp, lat, lon);
    assert.ok(x >= 0 && x <= width, `x ${x}`);
    assert.ok(y >= insets.top && y <= height - insets.bottom, `y ${y}`);
  }
  // Одна страна не раздувается на весь экран.
  const single = geo.fitViewport([data.countries.DE], width, height);
  assert.ok(width / single.pxPerLon > 40);
});
