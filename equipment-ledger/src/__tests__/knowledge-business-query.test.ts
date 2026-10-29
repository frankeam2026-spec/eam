import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import Card from '../components/knowledge/KnowledgeBusinessQuery.vue';
import type { BusinessQuery } from '../components/knowledge/businessQueries';
const request = vi.hoisted(() => vi.fn());
vi.mock('../api', () => ({ request }));
vi.mock('../i18n', () => ({ tr: (key: string) => key }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
function result(): BusinessQuery {
  return {
    title: '设备台账',
    range: '全部',
    asOf: '2026-10-29',
    timeField: '',
    equipmentId: '',
    total: 60,
    identities: { category: 'categoryId' },
    columns: [
      { key: 'category', label: '分类' },
      { key: 'count', label: '数量' },
    ],
    data: [{ category: '冲压机', categoryId: '9007199254740993', count: 3 }],
    plan: {
      domain: 'equipment',
      operation: 'GROUP',
      groupBy: ['category'],
      aggregate: 'count',
      measure: '',
      sortBy: '',
      direction: 'desc',
      timeField: 'createdAt',
      period: 'lastMonth',
      from: '',
      to: '',
      bucket: 'month',
      where: { field: 'status', op: 'eq', values: ['在用'] },
    },
  };
}
describe('assistant business query results', () => {
  beforeEach(() => request.mockReset());
  it('retains validated plan and equipment when changing database pagination', async () => {
    const value = result();
    value.equipmentId = '9007199254740993';
    request.mockResolvedValue(value);
    const wrapper = mount(Card, {
      props: { result: value },
      global: { stubs: { AppBusinessTable: true, AppPagination: true } },
    });
    wrapper.getComponent({ name: 'AppPagination' }).vm.$emit('update:page', 2);
    await flushPromises();
    expect(request.mock.calls[0]![0]).toBe('/knowledge/assistant/business-query');
    expect(JSON.parse(request.mock.calls[0]![1].body)).toEqual({
      plan: value.plan,
      equipmentId: value.equipmentId,
      page: 2,
      size: 20,
    });
    wrapper.getComponent({ name: 'AppPagination' }).vm.$emit('update:pageSize', 100);
    await flushPromises();
    expect(JSON.parse(request.mock.calls.at(-1)![1].body).size).toBe(100);
    wrapper.unmount();
  });
  it('drills down by both stable identity and label while keeping the original filters', async () => {
    const value = result();
    request.mockResolvedValue({
      ...value,
      data: [],
      total: 0,
      plan: { ...value.plan, operation: 'LIST' },
    });
    const wrapper = mount(Card, {
      props: { result: value },
      global: { stubs: { AppPagination: true } },
    });
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '冲压机')!
      .trigger('click');
    await flushPromises();
    const body = JSON.parse(request.mock.calls[0]![1].body);
    expect(body.plan.operation).toBe('LIST');
    expect(body.plan.period).toBe('lastMonth');
    expect(body.plan.where.children).toEqual([
      value.plan.where,
      { field: 'category', op: 'eq', values: ['冲压机'] },
      { field: 'categoryId', op: 'eq', values: ['9007199254740993'] },
    ]);
    expect(value.plan.operation).toBe('GROUP');
    wrapper.unmount();
  });
});
