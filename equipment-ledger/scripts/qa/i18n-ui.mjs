import { chromium } from '/Users/frank/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import http from 'node:http';import fs from 'node:fs';import assert from 'node:assert/strict';
const out='artifacts/qa/i18n';fs.mkdirSync(out,{recursive:true});
const proxy=http.createServer((req,res)=>{const port=req.url.startsWith('/api/v1/')?18083:5177;const headers={...req.headers,host:`127.0.0.1:${port}`};if(headers.origin)headers.origin=`http://127.0.0.1:${port}`;const up=http.request({hostname:'127.0.0.1',port,path:req.url,method:req.method,headers},reply=>{res.writeHead(reply.statusCode,reply.headers);reply.pipe(res);});up.on('error',()=>{res.writeHead(502);res.end();});req.pipe(up);});
await new Promise(r=>proxy.listen(0,'127.0.0.1',r));const origin=`http://127.0.0.1:${proxy.address().port}`;
await fetch('http://127.0.0.1:18083/api/v1/auth/settings/locale',{method:'PATCH',headers:{'Content-Type':'application/json','Origin':'http://127.0.0.1:18083'},body:JSON.stringify({locale:'zh-CN'})});
const browser=await chromium.launch({channel:'chrome',headless:true});const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
try {
await page.goto(origin+'/#/personal-settings');await page.waitForTimeout(3000);
await page.screenshot({animations:'disabled',path:out+'/personal-settings-zh-CN.png'});
fs.writeFileSync(out+'/initial.txt',await page.locator('body').innerText());
console.log('initial', (await page.locator('body').innerText()).slice(0,1500));
await page.getByRole('combobox').first().locator('xpath=ancestor::div[contains(@class,"el-select__wrapper")]').click();await page.getByRole('option',{name:'English',exact:true}).click();await page.waitForFunction(()=>document.documentElement.lang==='en-US');
for(const viewport of [{width:1280,height:720},{width:1440,height:900}]){await page.setViewportSize(viewport);for(const route of ['personal-settings','equipment','languages','maintenance/tasks','inventory/outbounds']){
 await page.goto(origin+'/#/'+route);await page.waitForTimeout(1400);const text=await page.locator('body').innerText();fs.writeFileSync(out+`/${route.replaceAll('/','-')}-${viewport.width}.txt`,text);await page.screenshot({animations:'disabled',path:out+`/${route.replaceAll('/','-')}-${viewport.width}.png`});const width=await page.evaluate(()=>document.documentElement.scrollWidth);assert(width<=viewport.width+1,'Page horizontal overflow: '+route);}}
await page.goto(origin+'/#/languages');await page.waitForTimeout(1000);await page.locator('input[type=file]').setInputFiles('examples/languages/fr-FR.json');await page.locator('.el-dialog').waitFor();await page.screenshot({animations:'disabled',path:out+'/language-import-validation.png'});await page.locator('.el-dialog__footer button').last().click();await page.locator('.el-dialog').waitFor({state:'hidden'});
await page.goto(origin+'/#/equipment');await page.waitForTimeout(1500);await page.getByRole('button',{name:'Add',exact:true}).click();await page.waitForTimeout(1800);console.log('editor URL',page.url());fs.writeFileSync(out+'/editor.txt',await page.locator('body').innerText());
const inputs=page.locator('input');console.log('editor inputs',await inputs.count());
// Directly exercise the same public language module while a live business draft is present.

await page.evaluate(async()=>{const input=document.querySelector('.equipment-editor [data-field="name"] input');if(!input)throw new Error('Equipment name field missing');input.value='I18N-DRAFT';input.dispatchEvent(new Event('input',{bubbles:true}));const entry=performance.getEntriesByType('resource').find(e=>new URL(e.name).pathname==='/src/i18n/index.ts');if(!entry)throw new Error('Loaded language runtime missing');const language=await import(entry.name);await language.chooseLanguage('zh-TW',true);});
await page.waitForFunction(()=>document.documentElement.lang==='zh-TW');assert.equal(await page.locator('.equipment-editor [data-field="name"] input').inputValue(),'I18N-DRAFT');await page.locator('.equipment-editor').getByText('設備名稱',{exact:true}).first().waitFor();await page.screenshot({animations:'disabled',path:out+'/equipment-editor-zh-TW.png'});fs.writeFileSync(out+'/editor-zh-TW.txt',await page.locator('body').innerText());
console.log('errors',JSON.stringify(errors));fs.writeFileSync(out+'/result.json',JSON.stringify({errors},null,2));assert.deepEqual(errors,[]);
}finally {await browser.close();await new Promise(r=>proxy.close(r));}
