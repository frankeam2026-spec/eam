import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import { ElButton } from 'element-plus';
import InspectionTaskSummary from '../components/inspection/InspectionTaskSummary.vue';
import type { InspectionTask } from '../inspection';

describe('inspection task summary', () => {
  it('shows the single device and keeps inspector and actual times together', async () => {
    const wrapper = mount(InspectionTaskSummary, {
      props: {
        users: [],
        task: {
          kind: 'daily',
          planId: '17',
          planName: '车间日常点检',
          owners: [],
          ownerName: '张三',
          equipment: [{ id: '8', name: '数控车床', code: 'EQ-008', model: 'CK6150' }],
          dueAt: '2026-10-23T08:00:00',
          expiresAt: '2026-10-23T16:00:00',
          shiftDate: '2026-10-23',
          shiftName: '白班',
          result: {
            items: [],
            startedAt: '2026-10-23T08:01:00',
            finishedAt: '2026-10-23T08:05:00',
          },
          reviewMode: '无需验收',
        } as unknown as InspectionTask,
      },
    });
    expect(wrapper.find('.inspection-task-device-summary').text()).toContain('数控车床');
    expect(wrapper.find('.inspection-task-device-summary').text()).toContain('EQ-008');
    expect(wrapper.find('.inspection-task-device-summary').text()).toContain('CK6150');
    const execution = wrapper.find('.inspection-task-execution-summary');
    expect(execution.findAll('div > span').map((n) => n.text())).toEqual([
      '点检人',
      '实际开始',
      '实际完成',
    ]);
    expect(execution.text()).toContain('张三');
    expect(wrapper.text()).not.toContain('实际执行人');
    expect(wrapper.text()).not.toContain('应检设备');
    await wrapper.findComponent(ElButton).trigger('click');
    expect(wrapper.emitted('plan')).toHaveLength(1);
    wrapper.unmount();
  });
});
