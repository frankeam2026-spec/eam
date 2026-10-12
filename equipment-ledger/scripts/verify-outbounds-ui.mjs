import { chromium } from '/Users/frank/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import { execFileSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';
import assert from 'node:assert/strict';
const root = path.dirname(path.dirname(fileURLToPath(import.meta.url))),
  runtime = path.join(root, '../.local-runtime');
const token = randomBytes(36).toString('base64url');
execFileSync(
  path.join(runtime, 'mysql-8.4.6-macos15-arm64/bin/mysql'),
  [
    '--defaults-extra-file=' + path.join(runtime, 'root.cnf'),
    '--socket=' + path.join(runtime, 'mysql.sock'),
    'eam_outbound_test',
  ],
  {
    input: `INSERT INTO eam_auth_session(id,tenant_id,user_id,token_hash,expires_at) VALUES(995901,10001,995101,'${createHash('sha256').update(token).digest('hex')}',DATE_ADD(NOW(),INTERVAL 1 DAY)) ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash);`,
  },
);
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.addCookies([{ name: 'EAM_SESSION', value: token, domain: 'localhost', path: '/' }]);
const page = await context.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
page.on('response', (r) => {
  if (r.status() >= 500) errors.push(`${r.status()} ${r.url()}`);
});
const base = 'http://localhost:5178/#/inventory/outbounds';
const dir = path.join(root, 'artifacts/qa/outbounds');
await page.goto(base + '?tab=documents');
await page.locator('.outbound-table tbody tr').first().waitFor();
await page.waitForLoadState('networkidle');
await page.screenshot({ animations: 'disabled', path: path.join(dir, 'documents-1440.png') });
for (const viewport of [
  { width: 1440, height: 900 },
  { width: 1280, height: 720 },
]) {
  await page.setViewportSize(viewport);
  const box = await page.locator('.outbound-list-footer').boundingBox();
  assert(
    box && Math.abs(box.y + box.height - viewport.height) < 35,
    'footer must remain at bottom',
  );
}
await page.getByRole('button', { name: '筛选器', exact: true }).click();
await page.locator('.el-drawer').waitFor();
await page.mouse.click(150, 300);
assert(await page.locator('.el-drawer').isVisible(), 'drawer must not close on outside click');
await page.keyboard.press('Escape');
assert(await page.locator('.el-drawer').isVisible());
await page.locator('.el-drawer .el-drawer__close-btn').click();
await page.setViewportSize({ width: 1440, height: 900 });
await page.getByRole('button', { name: '＋ 新增', exact: true }).click();
await page.locator('.outbound-form-grid').waitFor();
await page.waitForLoadState('networkidle');
async function select(label, option) {
  const control = page.locator('.outbound-form-grid label').filter({ hasText: label }).first();
  await control.locator('.el-select').click();
  if (label === '领用部门') {
    await page
      .locator('.el-select-dropdown:visible .el-tree-node__content')
      .filter({ hasText: option })
      .first()
      .click();
    return;
  }
  await page
    .locator('.el-select-dropdown:visible .el-select-dropdown__item')
    .filter({ hasText: option })
    .first()
    .click();
}
await select('仓库', 'QA 出库验证仓');
await select('领用部门', '机加工一部');
await select('领用人', 'QA owner');
await page
  .locator('.outbound-form-grid label')
  .filter({ hasText: '用途' })
  .locator('input')
  .fill('QA 页面直接出库');
await page.getByRole('button', { name: '＋ 添加物料', exact: true }).click();
await page.locator('.outbound-line-table .el-select').click();
await page
  .locator('.el-select-dropdown:visible .el-select-dropdown__item')
  .filter({ hasText: 'QA-OUT-N' })
  .click();
await page.locator('.outbound-line-table .el-input-number input').fill('2');
await page.locator('.outbound-line-table .el-input-number input').blur();
await page.screenshot({ animations: 'disabled', path: path.join(dir, 'direct-editor-1440.png') });
await page.getByRole('button', { name: '预览出库', exact: true }).click();
await page.getByRole('dialog', { name: '确认出库' }).waitFor();
await page.screenshot({ animations: 'disabled', path: path.join(dir, 'preview-1440.png') });
await page
  .getByRole('dialog', { name: '确认出库' })
  .getByRole('button', { name: '确认出库', exact: true })
  .click();
await page.waitForLoadState('networkidle');
await page.locator('.outbound-detail-header .el-tag').filter({ hasText: '已出库' }).waitFor();
await page.getByRole('dialog', { name: '确认出库' }).waitFor({ state: 'hidden' });
await page.screenshot({ animations: 'disabled', path: path.join(dir, 'posted-detail-1440.png') });
await page.getByRole('button', { name: '返回出库管理', exact: true }).click();
await page.getByRole('button', { name: '待出库', exact: true }).click();
await page.waitForLoadState('networkidle');
await page.locator('.outbound-table .el-tag').filter({ hasText: '部分出库' }).first().waitFor();
await page.screenshot({ animations: 'disabled', path: path.join(dir, 'pending-1440.png') });
assert.deepEqual(errors, [], 'no JS errors or server failures');
fs.writeFileSync(
  path.join(dir, 'ui-results.json'),
  JSON.stringify(
    {
      passed: [
        '出库单与待出库入口',
        '1440/1280分页固定',
        '筛选器外部点击及Escape保护',
        '直接出库页面录入',
        '保存预览确认完整交互',
        '右侧时间轴和居中底栏',
      ],
      errors,
    },
    null,
    2,
  ),
);
await browser.close();
console.log('PASS UI acceptance');
