import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { reactive } from 'vue';
import { ElButton } from 'element-plus';
import DailyArrangementEditor from '../components/inspection/DailyArrangementEditor.vue';
import { emptyInspection, emptyArrangement, inspectionApi } from '../inspection';

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
  vi.spyOn(inspectionApi, 'operationTeams').mockResolvedValue({ data: [] });
  const model = reactive(emptyInspection());
  model.startDate = '2026-10-21';
  model.details = [
    { key: 'd', equipmentId: 'eq', owners: ['override'], reviewers: [], projectRefs: [] },
  ];
  model.arrangements = [
    {
      ...emptyArrangement(model.startDate),
      name: '夜班',
      personMode: 'specified',
      startTime: '20:00:00',
      endTime: '08:00:00',
      slots: [{ id: 'night', name: '晚班', start: '20:00:00', end: '08:00:00' }],
      owners: ['user'],
      followUpUserId: 'user',
    },
    {
      ...emptyArrangement(model.startDate),
      name: '待配置班次',
      personMode: 'specified',
    },
  ];
  const wrapper = mount(DailyArrangementEditor, {
    props: {
      modelValue: model,
      choices: {
        equipment: [{ id: 'eq', name: '空压机' }],
        users: [{ id: 'user', name: '张三' }],
        organizations: [],
        categories: [],
      },
      errors: {},
      projectOptions: [],
      canCreateTeam: false,
      canEditTeam: false,
    },
    global: { stubs: { PagedChoiceField: true, AppDialog: true } },
  });
  wrappers.push(wrapper);
  await flushPromises();
  const click = async (text: string) => {
    const button = wrapper.findAllComponents(ElButton).find((b) => b.text() === text);
    expect(button, text).toBeTruthy();
    await button!.trigger('click');
    await flushPromises();
  };
  return { model, wrapper, click };
}

describe('daily arrangement editing', () => {
  it('summarizes overnight shifts and makes missing shifts explicit', async () => {
    const { wrapper } = await editor();
    expect(wrapper.find('.arrangement-editor').exists()).toBe(false);
    expect(wrapper.text()).toContain('20:00–08:00（次日）');
    expect(wrapper.text()).toContain('未设置班次，不生成任务');
    expect(wrapper.text()).toContain('张三');
    expect(wrapper.text()).toContain('1 台设备另有人员设置');
  });

  it('keeps edits and equipment overrides when the editor is collapsed', async () => {
    const { model, wrapper, click } = await editor();
    await click('编辑');
    expect(wrapper.find('.arrangement-editor').text()).toContain('设备人员例外');
    await wrapper.find('.arrangement-editor input').setValue('夜班润滑检查');
    await click('收起编辑');
    expect(model.arrangements[0]!.name).toBe('夜班润滑检查');
    expect(model.details[0]!.owners).toEqual(['override']);
    await click('编辑');
    expect(wrapper.find('.arrangement-summary').text()).toContain('次日');
    expect(wrapper.find<HTMLInputElement>('.arrangement-editor input').element.value).toBe(
      '夜班润滑检查',
    );
  });

  it('opens the arrangement containing a server field error and preserves other arrangements', async () => {
    const { model, wrapper } = await editor();
    await wrapper.setProps({ errors: { 'arrangements.1.slots': '班次时间无效' } });
    await flushPromises();
    expect(wrapper.find<HTMLInputElement>('.arrangement-editor input').element.value).toBe(
      '待配置班次',
    );
    expect(wrapper.find('.arrangement-editor').text()).toContain('班次时间无效');
    expect(model.arrangements[0]!.startTime).toBe('20:00:00');
  });

  it('offers shifts directly without frequency controls and keeps list and detail separate', async () => {
    const { wrapper, click } = await editor();
    await click('编辑');
    expect(wrapper.find('.arrangement-list .arrangement-editor').exists()).toBe(false);
    expect(wrapper.find('.arrangement-list').exists()).toBe(true);
    expect(wrapper.find('.arrangement-editor-heading').text()).toContain('编辑：夜班');
    expect(wrapper.find('.arrangement-editor').text()).toContain('新增班次');
    expect(wrapper.find('.arrangement-editor').text()).not.toContain('每日多时间段');
    expect(wrapper.find('.arrangement-editor').text()).not.toContain('固定时间间隔');
  });
});
