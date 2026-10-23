import { describe, expect, it } from 'vitest';
import { shallowMount } from '@vue/test-utils';
import { ElTreeSelect } from 'element-plus';
import TreeSelect from '../components/TreeSelect.vue';
import { formatOrganizationPath, organizationPath } from '../utils/organizationPath';
import { conditionLabel, filterTreeOptions, type FilterField } from '../query/advancedFilters';

const nodes = [
  { id: '1', name: '集团', parentId: '' },
  { id: '2', name: '公司', parentId: '1' },
  { id: '3', name: '部门', parentId: '2' },
];
describe('organization path display compatibility', () => {
  it('compacts department filter labels while preserving full tooltips and query identities', () => {
    const condition = {
      field: 'department',
      operator: 'in',
      values: ['3'],
      valueLabels: { '3': '集团 · 公司 · 部门' },
    };
    const fields: FilterField[] = [
      {
        key: 'department',
        label: '部门',
        type: 'choice',
        businessSource: { object: 'equipment', source: 'department', department: true },
      },
    ];
    expect(conditionLabel(condition, fields, true)).toBe('部门：部门（含下级）');
    expect(conditionLabel(condition, fields)).toBe('部门：集团 > 公司 > 部门（含下级）');
    expect(condition.values).toEqual(['3']);
    expect(condition.valueLabels['3']).toBe('集团 · 公司 · 部门');
  });
  it('formats legacy separators while preserving ordinary names and canonical paths', () => {
    expect(formatOrganizationPath(' 集团 · 公司 • 部门 ')).toBe('集团 > 公司 > 部门');
    expect(formatOrganizationPath('集团>公司>部门')).toBe('集团 > 公司 > 部门');
    expect(formatOrganizationPath('A.B研发部')).toBe('A.B研发部');
    expect(formatOrganizationPath(undefined)).toBe('');
    expect(organizationPath('3', nodes)).toBe('集团 > 公司 > 部门');
  });
  it('displays arrows without rewriting IDs or legacy query values', async () => {
    const wrapper = shallowMount(TreeSelect, {
      global: { stubs: { ElTooltip: { template: '<slot />' } } },
      props: {
        modelValue: '3',
        nodes: nodes.map((node) => ({ ...node, value: node.id })),
        pathValue: true,
      },
    });
    const select = wrapper.findComponent(ElTreeSelect);
    const root = select.props('data')![0]!;
    expect(root.children[0].children[0].path).toBe('集团 > 公司 > 部门');
    expect(select.props('modelValue')).toBe('3');
    select.vm.$emit('update:model-value', '2');
    expect(wrapper.emitted('update:modelValue')).toEqual([['2']]);
    wrapper.unmount();
    const filter = filterTreeOptions({
      key: 'department',
      label: '部门',
      type: 'tree',
      pathValue: true,
      nodes,
    });
    expect(filter[0]!.children[0]!.children[0]!.label).toBe('集团 > 公司 > 部门');
    expect(filter[0]!.children[0]!.children[0]!.output).toBe('集团 · 公司 · 部门');
  });
});
