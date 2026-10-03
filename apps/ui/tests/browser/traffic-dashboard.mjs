import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { createServer } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { chromium } from '../../../installer/node_modules/playwright/index.mjs';

const artifacts = resolve(process.env.NIMBO_TRAFFIC_ARTIFACT_DIR ?? 'dist/traffic-dashboard-screenshots');
await mkdir(artifacts, { recursive: true });
const server = await createServer({ configFile: false, clearScreen: false, plugins: [react(), tailwindcss()], server: { host: '127.0.0.1', port: 0 } });
await server.listen();
let browser;
let cases = 0;
try {
  browser = await chromium.launch({ headless: true, ...(process.env.NIMBO_CHROMIUM_PATH ? { executablePath: process.env.NIMBO_CHROMIUM_PATH } : {}) });
  const origin = server.resolvedUrls.local[0];
  async function fixture(viewport = { width: 1100, height: 800 }) {
    const page = await browser.newPage({ viewport, reducedMotion: 'reduce' });
    const errors = []; page.on('pageerror', e => errors.push(e.message));
    // Isolated browser storage acts as native preference readback; only get/set_preferences is allowed.
    await page.addInitScript(() => {
      window.__trafficCalls = []; window.__unexpectedTrafficCalls = []; window.__failTrafficSave = false;
      window.__TAURI_INTERNALS__ = { invoke: async (command, args) => {
        window.__trafficCalls.push(command);
        if (command === 'get_preferences') return JSON.parse(localStorage.getItem('fixture.preferences') ?? '{}');
        if (command === 'set_preferences') {
          if (window.__failTrafficSave) throw Error('Fixture persistence failed');
          if (window.__dropAdPreference) return { ...args.preferences, ad_blocking_enabled: false };
          localStorage.setItem('fixture.preferences', JSON.stringify(args.preferences)); return args.preferences;
        }
        if (command === 'reset_traffic_totals') return { all_time_upload: 0, all_time_download: 0, monthly_upload: 0, monthly_download: 0, monthly_period: '2026-10' };
        window.__unexpectedTrafficCalls.push(command); throw Error('Unexpected fixture IPC: ' + command);
      } };
    });
    return { page, errors };
  }
  async function open(page, query = '') {
    await page.goto(`${origin}tests/browser/traffic-dashboard.html?${query}`);
    await page.locator('.traffic-dashboard,.routing-page').first().waitFor();
  }
  for (const viewport of [{ width: 1100, height: 800 }, { width: 800, height: 760 }, { width: 360, height: 760 }]) for (const theme of ['dark', 'light']) for (const style of ['signal', 'material_you', 'dotted']) {
    const { page, errors } = await fixture(viewport);
    await open(page, `theme=${theme}&style=${style}`);
    assert.equal(await page.getByTestId('upload-total').innerText(), '48.0 MB');
    assert.equal(await page.getByTestId('download-total').innerText(), '256 MB');
    assert.equal(await page.getByTestId('download-speed').innerText(), '1.50 MB/s');
    assert.equal(await page.getByTestId('tcp-count').innerText(), '24');
    assert.equal(await page.locator('.traffic-ring').getAttribute('data-state'), 'measured');
    assert.equal(await page.getByRole('switch', { name: 'Ad blocking' }).getAttribute('aria-checked'), 'false');
    assert(await page.getByText('Previous connection', { exact: true }).isVisible());
    assert.equal(await page.locator('.statistics-chart [data-series]').count(), 2);
    const layout = await page.evaluate(() => ({ overflow: document.documentElement.scrollWidth > innerWidth,
      rangeInk: [...document.querySelectorAll('.secondary-segments button')].map(e => ({ background: getComputedStyle(e).backgroundColor, border: getComputedStyle(e).borderColor })),
      volumes: [...document.querySelectorAll('.traffic-volume-card')].map(e => ({ top: e.getBoundingClientRect().top, height: e.getBoundingClientRect().height })) }));
    assert(!layout.overflow, `${style}/${theme}/${viewport.width}: horizontal overflow`);
    assert.notDeepEqual(layout.rangeInk[0], layout.rangeInk[1], `${style}/${theme}: selected range is indistinguishable`);
    assert.equal(layout.volumes[0].top, layout.volumes[1].top, 'traffic directions must stay adjacent on phones too');
    assert.equal(layout.volumes[0].height, layout.volumes[1].height);
    await page.screenshot({ path: resolve(artifacts, `statistics-${style}-${theme}-${viewport.width}.png`), fullPage: true });
    await page.getByRole('button', { name: 'This month', exact: true }).click();
    assert.equal(await page.getByTestId('upload-total').innerText(), '512 MB');
    assert.equal(await page.getByTestId('proxy-bytes').innerText(), '—');
    assert(await page.getByText('Route breakdown is available for the current session only.').isVisible());
    await page.getByRole('button', { name: 'All time', exact: true }).click();
    assert.equal(await page.getByTestId('upload-total').innerText(), '2.00 GB');
    await page.getByRole('button', { name: 'Current session', exact: true }).click();
    assert.equal(await page.getByTestId('proxy-bytes').innerText(), '260 MB');
    assert.deepEqual(errors, []); assert.deepEqual(await page.evaluate(() => window.__unexpectedTrafficCalls), []);
    cases++; await page.close();
  }
  for (const scenario of ['unavailable', 'legacy', 'xray', 'idle', 'offline']) {
    const { page, errors } = await fixture({ width: 360, height: 760 });
    await open(page, `scenario=${scenario}`);
    assert.equal(await page.getByTestId('tcp-count').innerText(), scenario === 'idle' ? '0' : '—');
    assert.equal(await page.locator('.traffic-ring').getAttribute('data-state'), scenario === 'idle' ? 'empty' : scenario === 'xray' ? 'measured' : 'unavailable');
    if (scenario === 'unavailable' || scenario === 'offline') {
      assert.equal(await page.getByTestId('upload-total').innerText(), '—'); assert.equal(await page.getByTestId('download-speed').innerText(), '—');
      if (scenario === 'unavailable') assert.equal(await page.locator('.statistics-sessions tbody tr').first().locator('td').nth(3).innerText(), '—');
    }
    if (scenario === 'idle') { assert.equal(await page.getByTestId('upload-total').innerText(), '0 B'); assert.equal(await page.locator('.traffic-ring-proxy').count(), 0); }
    await page.screenshot({ path: resolve(artifacts, `statistics-${scenario}-360.png`), fullPage: true });
    assert.deepEqual(errors, []); cases++; await page.close();
  }
  const { page, errors } = await fixture();
  await open(page);
  const toggle = page.getByRole('switch', { name: 'Ad blocking' });
  await toggle.focus(); await page.keyboard.press('Space');
  await page.waitForFunction(() => document.querySelector('[role=switch]').getAttribute('aria-checked') === 'true');
  assert(await page.getByText('On for the next connection', { exact: true }).isVisible());
  assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture.preferences')).tunnel_dns), 'fixture-preserved');
  assert.deepEqual(await page.evaluate(() => window.__trafficCalls), ['get_preferences', 'set_preferences']);
  await page.reload(); await toggle.waitFor(); assert.equal(await toggle.getAttribute('aria-checked'), 'true');
  await page.evaluate(() => { window.__failTrafficSave = true; });
  await toggle.click(); await page.getByRole('alert').waitFor();
  assert.equal(await toggle.getAttribute('aria-checked'), 'true'); assert(!(await toggle.isDisabled()));
  await page.evaluate(() => { window.__failTrafficSave = false; });
  await toggle.click(); await page.waitForFunction(() => document.querySelector('[role=switch]').getAttribute('aria-checked') === 'false');
  await page.reload(); await toggle.waitFor(); assert.equal(await toggle.getAttribute('aria-checked'), 'false');
  await page.evaluate(() => { window.__dropAdPreference = true; });
  await toggle.click(); await page.getByRole('alert').waitFor(); assert.equal(await toggle.getAttribute('aria-checked'), 'false');
  await open(page, 'page=routing'); await toggle.waitFor();
  await toggle.click(); await page.waitForFunction(() => document.querySelector('[role=switch]').getAttribute('aria-checked') === 'true');
  assert.deepEqual(await page.evaluate(() => window.__unexpectedTrafficCalls), []); assert.deepEqual(errors, []);
  cases++; await page.close();
  const russian = await fixture({ width: 360, height: 760 });
  await open(russian.page, 'language=ru');
  assert(await russian.page.getByRole('switch', { name: 'Блокировка рекламы' }).isVisible());
  assert(await russian.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  assert.deepEqual(russian.errors, []); cases++; await russian.page.close();
  const reset = await fixture();
  await open(reset.page);
  await reset.page.getByRole('button', { name: 'Reset', exact: true }).click();
  const dialog = reset.page.getByRole('dialog'); await dialog.waitFor();
  assert.deepEqual(await reset.page.evaluate(() => window.__trafficCalls), ['get_preferences']);
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click();
  assert.equal(await dialog.count(), 0);
  assert.equal(await reset.page.getByTestId('upload-total').innerText(), '48.0 MB');
  await reset.page.getByRole('button', { name: 'Reset', exact: true }).click();
  await dialog.getByRole('button', { name: 'Reset', exact: true }).click();
  await dialog.waitFor({ state: 'detached' });
  assert.deepEqual(await reset.page.evaluate(() => window.__trafficCalls), ['get_preferences', 'reset_traffic_totals']);
  assert(await reset.page.getByText('Previous connection', { exact: true }).isVisible());
  assert.deepEqual(await reset.page.evaluate(() => window.__unexpectedTrafficCalls), []); assert.deepEqual(reset.errors, []);
  cases++; await reset.page.close();
  console.log(`PASS: ${cases} traffic dashboard browser cases; 3 styles, 2 themes, 360/800/1100 widths, availability/ranges/history, preference readback/failure and keyboard control. Screenshots: ${artifacts}`);
} finally { if (browser) await browser.close(); await server.close(); }
