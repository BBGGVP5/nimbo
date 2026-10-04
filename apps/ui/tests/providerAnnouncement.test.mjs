import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import * as React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import * as jsx from 'react/jsx-runtime';
import ts from 'typescript';

const source = readFileSync(new URL('../src/pages/profiles/ProviderAnnouncement.tsx', import.meta.url), 'utf8');
const js = ts.transpileModule(source, {
  compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.ESNext },
}).outputText.replace(/^import .*?;\r?\n/gm, '').replace(/^export /gm, '');
const bindings = Object.fromEntries(Object.entries(React).filter(([key]) => key.startsWith('use')));
const Component = new Function(...Object.keys(bindings), '_jsx', '_jsxs', js + ';return ProviderAnnouncement;')(
  ...Object.values(bindings), jsx.jsx, jsx.jsxs,
);
const render = description => renderToStaticMarkup(jsx.jsx(Component, {
  description, labels: { common: { locale: 'ru', description: 'Описание' } },
}));

test('subscription announcement preserves source whitespace and all lines before disclosure', () => {
  const description = '  Provider\n\nsecond line  \n' + 'new location\n'.repeat(20) + '  ';
  const html = render(description);
  assert(html.includes(description), 'provider text was trimmed, flattened or truncated');
  assert.doesNotMatch(html, /<h[1-6]\b/, 'generated description heading is redundant');
});

test('provider-authored markup is displayed literally, never executed', () => {
  const html = render('First\n<script>alert("source")</script>\n<a href="https://fixture.invalid">Link</a>');
  assert(html.includes('&lt;script&gt;'));
  assert(html.includes('&lt;a href='));
  assert.doesNotMatch(html, /<(?:script|a)\b/);
});

test('absent or whitespace-only provider announcements render nothing', () => {
  for (const value of [undefined, null, '', ' \n\t ']) assert.equal(render(value), '');
});
