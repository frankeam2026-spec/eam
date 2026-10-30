import { it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import WorkflowTrialInputs from '../components/workflow/WorkflowTrialInputs.vue';
import WorkflowConditionTree from '../components/workflow/WorkflowConditionTree.vue';
import { ElOption } from 'element-plus';
import type { Condition, WorkflowVariable } from '../workflow';

const variables: WorkflowVariable[] = [
  { key: 'severity', label: '故障等级', type: 'TEXT', available: true, nullable: false },
];
it('only exposes line collection controls when the selected business provides lines', async () => {
  const inputs = mount(WorkflowTrialInputs, {
    props: { modelValue: {}, lines: [], companyId: '71002', variables },
    global: { stubs: { PagedChoiceField: true, AppBusinessTable: true } },
  });
  expect(inputs.find('h3').exists()).toBe(false);
  const condition: Condition = {
    op: 'AND',
    field: null,
    literal: null,
    type: 'BOOLEAN',
    children: [],
  };
  const tree = mount(WorkflowConditionTree, {
    props: { modelValue: condition, companyId: '71002', variables },
  });
  expect(tree.findAllComponents(ElOption).map((o) => o.props('value'))).not.toContain('ANY');
  const withLines = [
    ...variables,
    { key: 'lines.quantity', label: '数量', type: 'NUMBER', available: true, nullable: false },
  ];
  await inputs.setProps({ variables: withLines });
  await tree.setProps({ variables: withLines });
  expect(inputs.find('h3').exists()).toBe(true);
  expect(tree.findAllComponents(ElOption).map((o) => o.props('value'))).toContain('ANY');
  inputs.unmount();
  tree.unmount();
});
