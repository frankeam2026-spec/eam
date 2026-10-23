import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { reactive } from 'vue';
import { ElButton, ElDatePicker } from 'element-plus';
import DailyRosterEditor from '../components/inspection/DailyRosterEditor.vue';
import PagedChoiceField from '../components/common/PagedChoiceField.vue';
import AppDialog from '../components/common/AppDialog.vue';
import { emptyArrangement } from '../inspection';
import { putDate } from '../utils/dailyStaffing';
const wrappers: ReturnType<typeof mount>[] = [];
afterEach(() => wrappers.splice(0).forEach((w) => w.unmount()));
async function editor() {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
  const a = reactive(emptyArrangement('2026-10-19'));
  a.personMode = 'roster';
  a.frequency = 'shift';
  a.slots = [
    { id: 'day', name: '白班', start: '08:00:00', end: '20:00:00' },
    { id: 'night', name: '晚班', start: '20:00:00', end: '08:00:00' },
  ];
  a.regularRoster = [
    {
      key: 'day',
      weekday: 0,
      slotId: 'day',
      start: '00:00:00',
      end: '00:00:00',
      users: ['1'],
      unassigned: false,
    },
  ];
  putDate(a, '2026-10-19', 'day', ['3']);
  const wrapper = mount(DailyRosterEditor, {
    props: {
      modelValue: a,
      startDate: '2026-10-19',
      endDate: '2026-11-30',
      errors: {},
      choices: {
        equipment: [],
        users: [
          { id: '1', name: '张三' },
          { id: '2', name: '李四' },
          { id: '3', name: '王五' },
        ],
        organizations: [],
        categories: [],
      },
    },
    global: {
      stubs: {
        PagedChoiceField: {
          props: ['modelValue', 'title'],
          template: '<div class="choice">{{ title }}</div>',
        },
        AppDialog: {
          props: ['modelValue'],
          template:
            '<section v-if="modelValue" class="batch-dialog"><slot/><slot name="footer"/></section>',
        },
      },
    },
  });
  wrappers.push(wrapper);
  await flushPromises();
  const click = async (text: string) => {
    const b = wrapper.findAllComponents(ElButton).find((b) => b.text() === text);
    expect(b, text).toBeTruthy();
    await b!.trigger('click');
    await flushPromises();
  };
  await wrapper
    .findAll('.el-radio-button')
    .find((r) => r.text() === '日期调整')!
    .find('input')
    .setValue(true);
  await flushPromises();
  return { wrapper, a, click };
}
describe('daily staffing bulk calendar', () => {
  it('fills a week using existing shifts, preserves overrides and supports undo', async () => {
    const { wrapper, a, click } = await editor();
    expect(wrapper.text()).toContain('白班');
    expect(wrapper.text()).toContain('晚班');
    expect(wrapper.text()).toContain('王五');
    await click('批量排班');
    const dialog = wrapper.findComponent(AppDialog);
    expect(dialog.props('modelValue')).toBe(true);
    const people = dialog.findAllComponents(PagedChoiceField);
    expect(people).toHaveLength(2);
    people[0]!.vm.$emit('update:modelValue', ['1']);
    people[1]!.vm.$emit('update:modelValue', ['2']);
    await flushPromises();
    await click('应用到草稿');
    expect(a.dateRoster).toHaveLength(14);
    expect(a.dateRoster.find((r) => r.date === '2026-10-19' && r.slotId === 'day')?.users).toEqual([
      '3',
    ]);
    expect(
      a.dateRoster.find((r) => r.date === '2026-10-25' && r.slotId === 'night')?.users,
    ).toEqual(['2']);
    await click('撤销本次操作');
    expect(a.dateRoster).toHaveLength(1);
    expect(a.dateRoster[0]?.users).toEqual(['3']);
  });
  it('keeps calendar edits isolated from another arrangement and shows date errors', async () => {
    const { wrapper, a } = await editor();
    const other = emptyArrangement('2026-10-19');
    await wrapper.setProps({
      errors: { 'arrangements.0.dateRoster.0': '排班规则重复或覆盖时间重叠，请调整' },
    });
    await flushPromises();
    expect(wrapper.text()).toContain('排班规则重复或覆盖时间重叠，请调整');
    const none = wrapper.findAllComponents(ElButton).find((b) => b.text() === '无人值班')!;
    await none.trigger('click');
    await flushPromises();
    expect(a.dateRoster[0]?.unassigned).toBe(true);
    expect(a.dateRoster[0]?.users).toEqual([]);
    expect(other.dateRoster).toEqual([]);
  });
});
