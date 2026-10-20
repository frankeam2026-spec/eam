import { chromium } from '/Users/frank/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import http from 'node:http';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const out=path.join(root,'artifacts/qa/equipment-import');fs.mkdirSync(out,{recursive:true});
execFileSync('python3',[path.join(root,'scripts/qa/equipment-import-ui-fixtures.py')],{stdio:'inherit'});
const cases=JSON.parse(fs.readFileSync(path.join(root,'../.local-runtime/equipment-import-qa/report.json'),'utf8'));
const errorsCase=cases.find(x=>x.case==='multi-field-errors').batch;
const browser=await chromium.launch({channel:'chrome',headless:true});
const context=await browser.newContext();const page=await context.newPage();const errors=[];
page.on('pageerror',e=>errors.push(e.message));
// Stream browser uploads to the isolated API; never send business traffic to the demo database.
const proxy=http.createServer((req,res)=>{
 const api=req.url.startsWith('/api/v1/');const port=api?18081:5177;
 const headers={...req.headers,host:`127.0.0.1:${port}`};
 if(headers.origin)headers.origin=`http://127.0.0.1:${port}`;
 const upstream=http.request({hostname:'127.0.0.1',port,path:req.url,method:req.method,headers},reply=>{
  res.writeHead(reply.statusCode,reply.headers);reply.pipe(res);
 });
 upstream.on('error',e=>{res.writeHead(502);res.end(e.message);});req.pipe(upstream);
});
await new Promise(resolve=>proxy.listen(0,'127.0.0.1',resolve));
const uiOrigin=`http://127.0.0.1:${proxy.address().port}`;
const measurements=[];
for(const viewport of [{width:1280,height:720},{width:1440,height:900}]){
 await page.setViewportSize(viewport);
 for(const scene of ['start','errors']){
  await page.goto(uiOrigin+'/#/equipment/import'+(scene==='errors'?'?batch='+errorsCase.id:''));
  await page.locator('.import-page').waitFor();
  if(scene==='errors')await page.locator('.import-issue').first().waitFor();
  await page.waitForTimeout(250);
  const result=await page.evaluate(()=>{
   const root=document.documentElement;const body=document.querySelector('.import-body');const footer=document.querySelector('.import-dialog-footer');const dialog=document.querySelector('.equipment-import-dialog');
   return {documentWidth:root.scrollWidth,viewportWidth:innerWidth,viewportHeight:innerHeight,bodyHeight:body?.clientHeight,dialogBottom:dialog?.getBoundingClientRect().bottom,footer:footer?JSON.parse(JSON.stringify(footer.getBoundingClientRect())):null};
  });
  assert(result.documentWidth<=viewport.width+1,'No page-level horizontal scrolling');
  assert(result.footer && result.dialogBottom && result.footer.bottom<=result.dialogBottom && result.dialogBottom-result.footer.bottom<30,'Footer stays inside the dialog bottom');
  measurements.push({viewport,scene,...result});
  await page.screenshot({path:path.join(out,`${scene}-${viewport.width}x${viewport.height}.png`),animations:'disabled'});
 }
}
// Actual upload, report download, corrected re-upload, confirm, refresh and result drill-through.
await page.setViewportSize({width:1440,height:900});
await page.goto(uiOrigin+'/#/equipment/import');
await page.locator('.import-upload input[type=file]').setInputFiles(path.join(root,'../.local-runtime/equipment-import-qa/ui-errors.xlsx'));
await page.getByText('需要修改',{exact:true}).waitFor();
await page.locator('.import-issue').first().waitFor();
assert(await page.getByRole('button',{name:'确认导入',exact:true}).isDisabled());
const downloadPromise=page.waitForEvent('download');
await page.getByRole('button',{name:'下载校验结果',exact:true}).click();
const download=await downloadPromise;await download.saveAs(path.join(out,'ui-validation-report.xlsx'));
await page.locator('input[type=file]').last().setInputFiles(path.join(root,'../.local-runtime/equipment-import-qa/ui-valid.xlsx'));
await page.getByText('校验通过',{exact:true}).first().waitFor();
await page.getByRole('button',{name:'确认导入',exact:true}).click();
await page.getByText('导入成功',{exact:true}).waitFor();
assert(page.url().includes('batch='));
await page.reload();await page.getByText('导入成功',{exact:true}).waitFor();
await page.getByRole('button',{name:'查看本次设备',exact:true}).click();
await page.getByText(/^UI-IMPORT-/).first().waitFor();
await page.locator('.import-page .el-loading-mask').waitFor({state:'hidden'});
await page.screenshot({path:path.join(out,'success-1440x900.png'),animations:'disabled'});
measurements.push({interaction:'upload-error-report-correct-confirm-reload-results',passed:true});
assert.deepEqual(errors,[],'No runtime page errors');
fs.writeFileSync(path.join(out,'layout.json'),JSON.stringify({measurements,errors},null,2));
await browser.close();await new Promise(resolve=>proxy.close(resolve));console.log(JSON.stringify({measurements,errors}));
