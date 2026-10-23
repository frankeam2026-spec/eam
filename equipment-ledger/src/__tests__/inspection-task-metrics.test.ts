import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import { ElProgress } from 'element-plus';
import InspectionTaskMetrics from '../components/inspection/InspectionTaskMetrics.vue';
import type { InspectionTask } from '../inspection';
import type { ItemResult } from '../maintenance';

const task = { kind: 'daily' as const, expected: 1, checked: 1, missing: 0, abnormal: 0,
  skipped: 0, equipmentCount: 1, workSeconds: 766, overdue: false, items: [] };
describe('inspection statistics', () => {
  it('keeps zero exceptions neutral and shows completed progress and elapsed time', () => {
    const wrapper = mount(InspectionTaskMetrics, { props: { task } });
    expect(wrapper.find('[data-metric="abnormal"]').classes()).not.toContain('danger');
    expect(wrapper.find('[data-metric="missing"]').classes()).not.toContain('warning');
    expect(wrapper.findComponent(ElProgress).props('percentage')).toBe(100);
    expect(wrapper.find('.inspection-metric-duration').text()).toContain('点检用时12分46秒');
    wrapper.unmount();
  });
  it('previews entered results and skipped devices without changing the saved counters', async () => {
    const source = { ...task, expected: 2, checked: 0, missing: 2, items: [
      { id: 'a', equipmentId: 'eq1' }, { id: 'b', equipmentId: 'eq2' },
    ] as InspectionTask['items'] };
    const wrapper = mount(InspectionTaskMetrics, { props: { task: source, results: [] } });
    expect(wrapper.findComponent(ElProgress).props('percentage')).toBe(0);
    await wrapper.setProps({ results: [{ id: 'a', value: '0', abnormal: true } as ItemResult], skippedEquipmentIds: ['eq2'] });
    expect(wrapper.findComponent(ElProgress).props('percentage')).toBe(50);
    expect(wrapper.find('[data-metric="missing"] dd').text()).toBe('0');
    expect(wrapper.find('[data-metric="abnormal"]').classes()).toContain('danger');
    expect(wrapper.find('[data-metric="skipped"] dd').text()).toBe('1');
    expect(source.checked).toBe(0);
    expect(source.missing).toBe(2);
    wrapper.unmount();
  });
});
