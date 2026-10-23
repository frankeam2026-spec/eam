import { afterEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { shallowMount, flushPromises } from '@vue/test-utils';
import { ElTooltip } from 'element-plus';
import DepartmentText from '../components/common/DepartmentText.vue';
import DepartmentValuesText from '../components/common/DepartmentValuesText.vue';
import { isDepartmentCustomColumn, type BusinessField } from '../businessConfig';
import { useAuthStore } from '../stores/auth';
import { useOrganizationDisplayStore } from '../stores/organizationDisplay';
import * as api from '../api';

const nodes = [
  { id: '1', name: '集团', parentId: '', type: '集团' },
  { id: '2', name: '公司', parentId: '1', type: '公司' },
  { id: '3', name: '部门', parentId: '2', type: '部门' },
] as api.OrganizationRecord[];
const wrappers: ReturnType<typeof shallowMount>[] = [];
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()));
function setup() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const auth = useAuthStore();
  auth.user = {
    id: 'u1',
    tenantId: 't1',
    account: 'u1',
    name: 'User',
    orgId: '3',
    company: '公司',
    department: '部门',
  };
  return { pinia, auth };
}
describe('department leaf and full-path display', () => {
  it('keeps each selected custom department identity and full label separate', () => {
    const fields = [
      { id: 'dept', type: 'DEPARTMENT', source: 'department' },
      { id: 'note', type: 'TEXT', source: '' },
    ] as BusinessField[];
    expect(isDepartmentCustomColumn(fields, 'custom:dept')).toBe(true);
    expect(isDepartmentCustomColumn(fields, 'custom:note')).toBe(false);
    const wrapper = shallowMount(DepartmentValuesText, {
      props: { value: '集团 > 公司 > 部门、集团 > 公司 > 另一个部门', ids: ['3', '9'] },
    });
    wrappers.push(wrapper);
    const departments = wrapper.findAllComponents(DepartmentText);
    expect(departments.map((item) => item.props('departmentId'))).toEqual(['3', '9']);
    expect(departments.map((item) => item.props('value'))).toEqual([
      '集团 > 公司 > 部门',
      '集团 > 公司 > 另一个部门',
    ]);
  });
  it('shows a leaf with an accessible full-path tooltip, keeping historical snapshots intact', () => {
    const { pinia } = setup();
    const read = vi.spyOn(api, 'listOrganizations');
    const wrapper = shallowMount(DepartmentText, {
      props: { value: '原集团 · 原公司 · 部门', lookup: false },
      global: { plugins: [pinia], renderStubDefaultSlot: true },
    });
    wrappers.push(wrapper);
    expect(wrapper.text()).toBe('部门');
    expect(wrapper.findComponent(ElTooltip).props('content')).toBe('原集团 > 原公司 > 部门');
    expect(wrapper.find('span').attributes('tabindex')).toBe('0');
    expect(wrapper.find('span').attributes('aria-label')).toBe('原集团 > 原公司 > 部门');
    expect(read).not.toHaveBeenCalled();
  });
  it('resolves by ID and does not invent a path for ambiguous same-named departments', async () => {
    const { pinia } = setup();
    const wrapper = shallowMount(DepartmentText, {
      props: { value: '部门', departmentId: '3', nodes },
      global: { plugins: [pinia], renderStubDefaultSlot: true },
    });
    wrappers.push(wrapper);
    expect(wrapper.findComponent(ElTooltip).props('content')).toBe('集团 > 公司 > 部门');
    await wrapper.setProps({
      departmentId: '',
      nodes: [...nodes, { id: '4', parentId: '2', name: '部门', type: '部门' }],
    });
    expect(wrapper.text()).toBe('部门');
    expect(wrapper.findComponent(ElTooltip).props('disabled')).toBe(true);
  });
  it('shares one authorized lookup and ignores a late response from a previous account', async () => {
    const { auth } = setup();
    let resolveOld!: (value: { data: api.OrganizationRecord[] }) => void;
    const read = vi
      .spyOn(api, 'listOrganizations')
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve;
          }),
      )
      .mockResolvedValueOnce({ data: [{ ...nodes[2]!, id: '9', name: '另一个部门' }] });
    const store = useOrganizationDisplayStore();
    const old = store.load();
    void store.load();
    expect(read).toHaveBeenCalledTimes(1);
    auth.user = { ...auth.user!, id: 'u2' };
    await store.load();
    resolveOld({ data: nodes });
    await old;
    await flushPromises();
    expect(store.rows.map((row) => row.id)).toEqual(['9']);
    expect(read).toHaveBeenCalledTimes(2);
  });
});
