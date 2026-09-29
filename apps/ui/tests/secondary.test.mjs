import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';
import React from 'react';
import * as jsx from 'react/jsx-runtime';
import { renderToStaticMarkup } from 'react-dom/server';
const compile=p=>ts.transpileModule(readFileSync(new URL(p,import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,target:ts.ScriptTarget.ES2020}}).outputText;
const data={};vm.runInNewContext(compile('../src/lib/statisticsPresentation.ts'),{exports:data});
test('traffic plots share a scale and use only supplied measurements',()=>{
 const paths=data.trafficPaths([{download:100,upload:10},{download:200,upload:20}]);
 assert.equal(paths.max,200);assert.equal(paths.count,2);
 assert.equal(paths.download,'M0,90 L640,8');assert.equal(paths.upload,'M0,163.8 L640,155.6');
});
test('empty, single and invalid samples cannot fabricate a history',()=>{
 for(const samples of [[],[{download:1,upload:0}],[{download:NaN,upload:2}],[{download:-1,upload:0}]]){
  const p=data.trafficPaths(samples);assert.equal(p.download,'');assert.equal(p.upload,'');
 }
});
const ui={};vm.runInNewContext(compile('../src/components/Secondary.tsx'),{exports:ui,require:n=>n==='../lib/i18n'?{useMessages:()=>({common:{locale:'ru'}})}:jsx});
test('secondary states expose actual loading/errors and keep detail as escaped text',()=>{
 const loading=renderToStaticMarkup(React.createElement(ui.StatePanel,{title:'Loading',busy:true}));assert(loading.includes('aria-busy="true"'));assert(loading.includes('role="status"'));
 const error=renderToStaticMarkup(React.createElement(ui.StatePanel,{title:'Failure',detail:'<script>bad</script>',error:true}));assert(error.includes('role="alert"'));assert(error.includes('&lt;script&gt;'));assert(!error.includes('<script>'));
});
test('page headers and metrics retain semantic headings and supplied values',()=>{
 const header=renderToStaticMarkup(React.createElement(ui.PageHeader,{title:'Tools',description:'Actual data'}));assert(header.includes('<h1'));assert(header.includes('Actual data'));
 const metric=renderToStaticMarkup(React.createElement(ui.Metric,{label:'Unavailable',value:'—'}));assert(metric.includes('<strong>—</strong>'));assert(!metric.includes('0 B'));
});
