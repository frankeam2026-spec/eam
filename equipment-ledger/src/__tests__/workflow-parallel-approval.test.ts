import { afterEach, describe, it, expect, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { ElSelect, ElInput, ElButton } from 'element-plus';
import RequisitionApproval from '../components/workflow/RequisitionApproval.vue';
import WorkflowBpmnCanvas from '../components/workflow/WorkflowBpmnCanvas.vue';
import { workflowApi, type WorkflowRound, type WorkflowNode } from '../workflow';
vi.mock('../stores/auth', () => ({ useAuthStore: () => ({ user: { id: 'user' } }) }));
afterEach(() => vi.restoreAllMocks());
describe('parallel approval selection', () => {
  it('shows recorded states without marking an unexecuted subprocess as completed', async () => {
    const nodes: WorkflowNode[] = ['start', 'pending', 'rejected', 'inner'].map((key) => ({
      key,
      name: key,
      type: key === 'start' || key === 'inner' ? 'START' : 'APPROVAL',
      next: null,
      parentKey: key === 'inner' ? 'sub' : null,
      approvalMode: 'ANY',
      users: [],
      roles: [],
      branches: [],
    }));
    const round: WorkflowRound = {
      id: 'instance',
      roundNo: 1,
      rowVersion: 1,
      state: 'RUNNING',
      templateName: 'template',
      startedAt: '',
      events: [],
      nodes,
      approvals: ['pending', 'rejected'].map((key) => ({
        id: key,
        user_id: 'other',
        user_name: 'name',
        node_key: key,
        node_name: key,
        state: key === 'pending' ? 'PENDING' : 'REJECTED',
        opinion: '',
        acted_at: null,
      })),
    };
    const wrapper = mount(RequisitionApproval, {
      props: { rounds: [round] },
      global: {
        stubs: {
          WorkflowBpmnCanvas: true,
          AppDialog: { template: '<div><slot/></div>' },
          AppBusinessTable: { template: '<div/>' },
          ElTableColumn: true,
        },
      },
    });
    await wrapper
      .findAllComponents(ElButton)
      .find((b) => b.text() === '流程进度')!
      .trigger('click');
    const graph = wrapper.getComponent(WorkflowBpmnCanvas);
    expect(graph.props('readonly')).toBe(true);
    expect(graph.props('nodeStates')).toEqual({
      start: 'APPROVED',
      pending: 'PENDING',
      rejected: 'REJECTED',
    });
    wrapper.unmount();
  });
  it('keeps opinions separate and submits the selected task for the same approver', async () => {
    const act = vi.spyOn(workflowApi, 'act').mockResolvedValue({});
    const round: WorkflowRound = {
      id: 'instance',
      roundNo: 1,
      rowVersion: 4,
      state: 'RUNNING',
      templateName: 'template',
      startedAt: '',
      events: [],
      approvals: ['A', 'B'].map((id) => ({
        id,
        user_id: 'user',
        user_name: '审批人',
        node_key: id,
        node_name: '节点' + id,
        state: 'PENDING',
        opinion: '',
        acted_at: null,
      })),
    };
    const wrapper = mount(RequisitionApproval, {
      props: { rounds: [round] },
      global: { stubs: { AppBusinessTable: { template: '<div/>' }, ElTableColumn: true } },
    });
    await wrapper.getComponent(ElInput).setValue('A comment');
    wrapper.getComponent(ElSelect).vm.$emit('update:modelValue', 'B');
    await flushPromises();
    await wrapper.getComponent(ElInput).setValue('B comment');
    wrapper.getComponent(ElSelect).vm.$emit('update:modelValue', 'A');
    await flushPromises();
    expect(wrapper.getComponent(ElInput).props('modelValue')).toBe('A comment');
    wrapper.getComponent(ElSelect).vm.$emit('update:modelValue', 'B');
    await flushPromises();
    await wrapper
      .findAllComponents(ElButton)
      .find((button) => button.text() === '通过')!
      .trigger('click');
    await flushPromises();
    expect(act).toHaveBeenCalledWith('instance', 'B', 4, true, 'B comment');
    wrapper.unmount();
  });
});
