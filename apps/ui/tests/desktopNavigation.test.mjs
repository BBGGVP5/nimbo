import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, NavLink, useLocation } from 'react-router-dom';
import * as jsx from 'react/jsx-runtime';

function loadModule(path, imports = {}) {
  const source = readFileSync(new URL(path, import.meta.url), 'utf8');
  const output = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2020,
  } }).outputText;
  const exports = {};
  vm.runInNewContext(output, { exports, require: (name) => imports[name] ?? (() => { throw Error(`Unexpected import ${name}`); })() });
  return exports;
}

test('desktop navigation includes every top-level page without overfilling compact windows', () => {
  const { desktopNavItems, activityNavItems, compactDestination } = loadModule('../src/lib/desktopNavigation.ts');
  const routes = desktopNavItems.map((item) => item.to);
  assert.deepEqual([...routes].sort(), [
    '/', '/subscriptions', '/statistics', '/routing', '/routing/modules', '/apps',
    '/notifications', '/sync', '/settings',
  ].sort());
  assert.deepEqual(Array.from(activityNavItems,item=>item.to),['/statistics','/connections','/tunnel-logs']);
  for (const path of ['/statistics','/connections','/tunnel-logs']) assert.equal(compactDestination(path),'/statistics');
  assert.equal(compactDestination('/mihomo'),'/subscriptions');
  assert.equal(new Set(routes).size, routes.length);
  assert.equal(desktopNavItems.find((item) => item.to === '/routing').end, true);
  assert.equal(desktopNavItems.find((item) => item.to === '/routing/modules').end, true);
  assert.deepEqual(Array.from(desktopNavItems.filter((item) => !item.compactHide), (item) => item.to),
    ['/', '/subscriptions', '/statistics', '/settings']);
  assert(desktopNavItems.filter((item) => item.group).length >= 2);
  const css = readFileSync(new URL('../src/styles.css', import.meta.url), 'utf8');
  assert.match(css, /\.signal-rail-slot\[data-compact-hide="true"\]\s*\{\s*display:\s*none;/);
});

test('only the actual sidebar route is active for formerly hidden pages', () => {
  const { desktopNavItems } = loadModule('../src/lib/desktopNavigation.ts');
  const { SignalSidebar } = loadModule('../src/components/SignalSidebar.tsx', {
    'react/jsx-runtime': jsx,
    'react-router-dom': { NavLink, useLocation },
    './ConnectionStateIcon': { ConnectionStateIcon: () => React.createElement('span') },
  });
  for (const route of ['/routing/modules', '/notifications', '/sync']) {
    const html = renderToStaticMarkup(React.createElement(MemoryRouter, { initialEntries: [route] },
      React.createElement(SignalSidebar, {
        labels: { notifications: { unread: 'unread' } },
        items: desktopNavItems, label: (key) => key, unread: 0, version: 'V1',
        coreLabel: 'VPN', coreState: 'idle', width: 232,
      })));
    assert.equal((html.match(/aria-current="page"/g) ?? []).length, 1, `active route: ${route}`);
  }
});

test('full YAML tools remain accessible without a duplicate top-level profiles destination', () => {
  const { desktopNavItems } = loadModule('../src/lib/desktopNavigation.ts');
  assert(!desktopNavItems.some(item => item.to === '/mihomo'));
  const app = readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8');
  assert.match(app, /<Route path="\/mihomo" element={<MihomoProfiles/);
  const core = readFileSync(new URL('../src/components/CorePreferenceSetting.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(core, /href="#\/mihomo"/, 'advanced button removed by design');
});

test('universal compact bar uses the same four primary destinations', () => {
  const css = readFileSync(new URL('../src/universal.css', import.meta.url), 'utf8');
  assert(css.includes('.signal-rail-slot:not([data-compact-hide="true"]) { display: block; }'),
    'compact CSS must use the route inventory visibility flag');
  assert(!css.includes('.signal-rail-slot:is([data-nav-key='),
    'compact CSS must not duplicate the four route names');
});

test('full-width sidebar begins after the icon-rail breakpoint', () => {
  const css = readFileSync(new URL('../src/universal.css', import.meta.url), 'utf8');
  assert.match(css, /@media\(min-width:1200px\)\s*\{\s*body\[data-ui-style="signal"\] \.signal-rail/);
  assert.doesNotMatch(css, /@media\(min-width:1101px\)\s*\{\s*body\[data-ui-style="signal"\] \.signal-rail/);
});
