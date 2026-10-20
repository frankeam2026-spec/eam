import fs from 'node:fs';import assert from 'node:assert/strict';import { baseCompile } from '@intlify/message-compiler';
const locales=['zh-CN','zh-TW','en-US'];const source=JSON.parse(fs.readFileSync('src/i18n/source.json','utf8'));const issues=[];
const params=value=>[...value.matchAll(/\{([a-zA-Z0-9_]+)\}/g)].map(m=>m[1]).sort().join(',');
for(const locale of locales){const text=fs.readFileSync(`src/i18n/${locale}.json`,'utf8');const catalog=JSON.parse(text);assert.deepEqual(Object.keys(catalog).sort(),Object.keys(source).sort());assert.equal(text,fs.readFileSync(`backend/src/main/resources/i18n/${locale}.json`,'utf8'));
for(const [key,value]of Object.entries(catalog)){if(params(value)!==params(source[key]))issues.push(`${locale} ${key}: parameters differ`);if(!key.startsWith('calendar.'))baseCompile(value,{onError:e=>issues.push(`${locale} ${key}: ${e.message}`)});}}
if(issues.length){console.error(issues.join('\n'));process.exitCode=1;}else console.log(`PASS: ${Object.keys(source).length} stable keys in three languages; frontend/backend, parameters and message syntax match.`);
