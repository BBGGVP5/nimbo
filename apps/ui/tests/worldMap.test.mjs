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

test('clusters merge flags whose circles (with badge) overlap, keep distant ones apart', () => {
  const markers = [
    { x: 100, y: 100, weight: 16, item: 'FI' },
    { x: 120, y: 110, weight: 8, item: 'LV' },
    { x: 300, y: 100, weight: 8, item: 'RU' },
  ];
  const clusters = geo.clusterMarkers(markers, 21);
  assert.equal(clusters.length, 2);
  assert.equal(clusters[0].members.map((m) => m.item).join(','), 'FI,LV');
  assert.equal(clusters[0].x, 100, 'cluster stays on its heaviest flag');
  for (let i = 0; i < clusters.length; i++) for (let j = i + 1; j < clusters.length; j++) {
    assert.ok(Math.hypot(clusters[i].x - clusters[j].x, clusters[i].y - clusters[j].y) >= 42);
  }
});

test('edge anchor points from the centre to an off-screen marker', () => {
  const rect = { left: 24, top: 24, right: 976, bottom: 476 };
  assert.equal(geo.edgeAnchor(500, 250, rect), null);
  const left = geo.edgeAnchor(-400, 250, rect);
  assert.ok(Math.abs(left.x - 24) < 1e-9);
  assert.ok(Math.abs(left.y - 250) < 1e-9);
  assert.ok(Math.abs(Math.abs(left.angle) - Math.PI) < 1e-9);
});

test('cloudflare trace country', () => {
  assert.equal(geo.parseTraceCountry('fl=1\nip=203.0.113.1\nloc=de\ncolo=FRA\n'), 'DE');
  assert.equal(geo.parseTraceCountry('loc=XX\n'), null);
  assert.equal(geo.parseTraceCountry('loc=T1\n'), null);
  assert.equal(geo.parseTraceCountry('no country'), null);
});

test('zoom is bounded: whole world at most, a few degrees at least', () => {
  const view = geo.fitView([data.countries.FI, data.countries.RU], 1000, 500);
  const far = geo.clampView({ ...view, ppd: 0.01 }, 1000, 500);
  // Самый мелкий масштаб — мир по ширине (с запасом 2%).
  assert.ok(1000 / (far.ppd * far.kx) <= 360 / 0.98 + 0.01);
  const near = geo.clampView({ ...view, ppd: 10_000 }, 1000, 500);
  assert.ok(500 / near.ppd >= 5.9);
  // Плавный перелёт сходится к цели.
  let current = far;
  for (let i = 0; i < 80; i++) current = geo.approachView(current, view, 0.2);
  assert.ok(geo.viewsClose(current, view));
});
