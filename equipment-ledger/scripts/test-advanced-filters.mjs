import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(new URL('../src/query/advancedFilters.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
});
const { filterDraft, prepareFilter, filterError, matchesAdvanced, conditionLabel } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
);
const text = { key: 'name', label: '名称', type: 'text' };
const choice = { key: 'status', label: '状态', type: 'choice' };
const number = { key: 'amount', label: '金额', type: 'number' };
const tree = {
  key: 'location',
  label: '位置',
  type: 'tree',
  pathValue: true,
  nodes: [
    { id: 'a', name: '车间', parentId: '' },
    { id: 'b', name: '生产线', parentId: 'a' },
    { id: 'c', name: '工位', parentId: 'b' },
    { id: 'd', name: '仓库', parentId: '' },
  ],
};
const condition = (field, operator, values) => ({ field, operator, values });
const match = (conditions, row) => matchesAdvanced(conditions, (key) => row[key]);
assert.equal(filterDraft(text).operator, 'contains');
assert.equal(filterDraft(choice).operator, 'in');
assert.deepEqual(filterDraft(text, condition('name', 'empty', [])).values, []);
assert.equal(filterDraft(text, condition('name', 'eq', ['Pump'])).operator, 'contains');
assert.equal(match([condition('name', 'contains', ['PuMp'])], { name: 'pump-A' }), true);
assert.equal(match([condition('name', 'contains', ['%'])], { name: 'pump' }), false);
assert.equal(match([condition('name', 'eq', ['pump'])], { name: 'pump-A' }), false);
assert.equal(match([condition('status', 'in', ['正常', '故障'])], { status: '故障' }), true);
assert.equal(
  match([condition('status', 'in', ['正常']), condition('name', 'contains', ['pump'])], {
    status: '故障',
    name: 'pump',
  }),
  false,
);
assert.equal(match([condition('amount', 'range', ['', '10'])], { amount: 0 }), true);
assert.equal(match([condition('amount', 'range', ['2', ''])], { amount: 10 }), true);
assert.equal(
  filterError(number, condition('amount', 'range', ['10', '2'])),
  '最小值不能大于最大值',
);
assert.equal(filterError(number, condition('amount', 'range', ['oops'])), '请输入有效数值');
assert.equal(
  match([condition('date', 'range', ['2026-10-15', '2026-10-15'])], {
    date: '2026-10-15T23:59:59.999999',
  }),
  true,
);
assert.equal(
  match([condition('date', 'range', ['2026-10-15', '2026-10-15'])], {
    date: '2026-10-16T00:00:00',
  }),
  false,
);
assert.equal(
  match([condition('date', 'range', ['2026-10-15T12:00:00', '2026-10-15T13:00:00'])], {
    date: '2026-10-15T13:00:01',
  }),
  false,
);
const selected = prepareFilter(tree, { ...filterDraft(tree), selectedNodeIds: ['a', 'b'] });
assert.deepEqual(selected.values, ['车间', '车间 · 生产线', '车间 · 生产线 · 工位']);
assert.deepEqual(filterDraft(tree, selected).selectedNodeIds, ['a', 'b']);
assert.equal(match([selected], { location: '车间 · 生产线 · 工位' }), true);
assert.equal(match([selected], { location: '仓库' }), false);
assert.equal(conditionLabel(selected, [tree]), '位置：车间、车间 · 生产线（含下级）');
assert.deepEqual(
  prepareFilter(tree, { ...selected, selectedNodeIds: ['not-authorized'] }).values,
  [],
);
const draft = filterDraft(choice, condition('status', 'in', ['正常']));
draft.values.push('故障');
assert.deepEqual(filterDraft(choice).values, []);
console.log('筛选器规则检查通过（文本、多选、数值、日期边界、树形下级、标签、旧条件兼容）。');
