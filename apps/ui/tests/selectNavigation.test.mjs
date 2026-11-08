import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

const source = readFileSync(new URL('../src/lib/selectNavigation.ts', import.meta.url), 'utf8');
const exports = {};
vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, { exports });
const { nextSelectOption, selectPlacement, findSelectOption } = exports;
const options = [{ label: 'Авто' }, { label: 'AWG', disabled: true }, { label: 'Mihomo' }, { label: 'Xray' }];

test('selector arrows wrap, skip disabled entries and handle empty lists', () => {
  assert.equal(nextSelectOption(options, 0, 'ArrowDown'), 2);
  assert.equal(nextSelectOption(options, 0, 'ArrowUp'), 3);
  assert.equal(nextSelectOption(options, 2, 'Home'), 0);
  assert.equal(nextSelectOption(options, 0, 'End'), 3);
  assert.equal(nextSelectOption([], 0, 'ArrowDown'), -1);
  assert.equal(nextSelectOption([{ disabled: true }], 0, 'End'), -1);
});
test('typeahead supports Russian and skips unavailable cores', () => {
  assert.equal(findSelectOption(options, 'ав', 2), 0);
  assert.equal(findSelectOption(options, 'x', 0), 3);
  assert.equal(findSelectOption(options, 'a', 0), -1);
});
test('dropdown stays in a narrow viewport and opens above a low trigger', () => {
  const bottom = selectPlacement({ left: 300, top: 680, bottom: 724, width: 220 }, 250, { width: 360, height: 760 });
  assert(bottom.left >= 12 && bottom.left + bottom.width <= 348);
  assert(bottom.top < 680);
  assert(bottom.top + bottom.maxHeight <= 748);
  const tiny = selectPlacement({ left: 0, top: 45, bottom: 89, width: 800 }, 400, { width: 320, height: 200 });
  assert(tiny.width <= 296 && tiny.top >= 12 && tiny.top + tiny.maxHeight <= 188);
});
