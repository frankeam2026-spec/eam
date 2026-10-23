import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { ElButton, ElCheckbox, ElInput, ElSelect } from 'element-plus';
import dayjs from 'dayjs';
import NotificationAdmin from '../components/system/NotificationAdmin.vue';
import ReminderPolicyField from '../components/common/ReminderPolicyField.vue';
const mock = vi.hoisted(() => ({
  policies: vi.fn(),
  defaults: vi.fn(),
  policy: vi.fn(),
  savePolicy: vi.fn(),
  channels: vi.fn(),
  saveChannels: vi.fn(),
  can: vi.fn(),
  choices: vi.fn(),
  setDefault: vi.fn(),
  deliveries: vi.fn(),
  retention: vi.fn(),
  saveRetention: vi.fn(),
}));
vi.mock('../stores/auth', () => ({ useAuthStore: () => ({ can: mock.can }) }));
vi.mock('../composables/useUnsavedChanges', () => ({
  useUnsavedChanges: () => ({ markSaved: vi.fn(), permitLeave: vi.fn(async () => true) }),
}));
vi.mock('../notifications', async (original) => {
  const actual = await original<typeof import('../notifications')>();
  return { ...actual, notificationApi: { ...actual.notificationApi, ...mock } };
});
const dialog = defineComponent({
  props: ['modelValue'],
  template: '<div v-if="modelValue"><slot/><slot name="footer"/></div>',
});
const picker = defineComponent({
  props: ['modelValue', 'options', 'loadRows'],
  template: '<span class="test-picker">{{options.map(p=>p.name).join(",")}}</span>',
});
const config = {
  rowVersion: 0,
  systemEnabled: true,
  emailEnabled: false,
  smtpHost: '',
  smtpPort: 587,
  smtpSecurity: 'STARTTLS',
  smtpUsername: '',
  senderAddress: '',
  publicUrl: '',
  appStatus: 'NOT_INTEGRATED',
  hasPassword: true,
};
const policy = {
  id: '17',
  name: '维修提醒',
  businessKind: 'repair',
  enabled: true,
  rowVersion: 2,
  rules: [
    {
      id: '19',
      scene: 'WAIT_CLAIM',
      value: 30,
      unit: 'MINUTE',
      enabled: true,
      recipients: ['1'],
      cc: ['2'],
    },
  ],
  people: [
    { userId: '1', name: '张三' },
    { userId: '2', name: '李四' },
  ],
};
const globals = { stubs: { AppDialog: dialog, PagedChoiceField: picker } };
async function clickText(wrapper: ReturnType<typeof mount>, label: string) {
  const button = wrapper.findAllComponents(ElButton).find((b) => b.text() === label);
  expect(button, label).toBeDefined();
  await button!.trigger('click');
  await flushPromises();
}
beforeEach(() => {
  vi.clearAllMocks();
  mock.can.mockReturnValue(true);
  mock.policies.mockResolvedValue({ data: [policy], total: 1 });
  mock.defaults.mockResolvedValue({ data: [] });
  mock.policy.mockImplementation(async () => ({ data: JSON.parse(JSON.stringify(policy)) }));
  mock.savePolicy.mockResolvedValue({ data: policy });
  mock.channels.mockResolvedValue({ data: { ...config } });
  mock.saveChannels.mockResolvedValue({ data: { ...config, rowVersion: 1 } });
  mock.deliveries.mockResolvedValue({ data: [], total: 0 });
  mock.retention.mockResolvedValue({
    data: { rowVersion: 0, successDays: 90, exceptionDays: 180 },
  });
  mock.saveRetention.mockResolvedValue({
    data: { rowVersion: 1, successDays: 90, exceptionDays: 180 },
  });
});
describe('notification administration', () => {
  it('sets a policy as default from its row using the current binding version', async () => {
    mock.setDefault.mockResolvedValue({
      data: [{ businessKind: 'repair', policyId: '17', rowVersion: 1 }],
    });
    const wrapper = mount(NotificationAdmin, { props: { view: 'policies' }, global: globals });
    await flushPromises();
    mock.defaults.mockResolvedValue({
      data: [{ businessKind: 'repair', policyId: '17', rowVersion: 1 }],
    });
    await clickText(wrapper, '设为默认');
    expect(mock.setDefault).toHaveBeenCalledWith('repair', '17', 0);
    expect(wrapper.text()).toContain('当前默认：维修提醒');
    expect(wrapper.findAllComponents(ElButton).some((b) => b.text() === '保存默认策略')).toBe(
      false,
    );
    expect(
      wrapper
        .findAllComponents(ElButton)
        .find((b) => b.text() === '当前默认')!
        .props('disabled'),
    ).toBe(true);
    wrapper.unmount();
  });
  it('sends the default seven-day range and excludes inactive channels until requested', async () => {
    const wrapper = mount(NotificationAdmin, { props: { view: 'deliveries' }, global: globals });
    await flushPromises();
    expect(mock.deliveries).toHaveBeenLastCalledWith('', '', '', 1, 20, {
      from: dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
      to: dayjs().format('YYYY-MM-DD'),
      businessKind: '',
      recipientId: '',
      showInactive: false,
    });
    await wrapper.findComponent(ElCheckbox).vm.$emit('update:modelValue', true);
    await flushPromises();
    expect(mock.deliveries.mock.calls.at(-1)![5].showInactive).toBe(true);
    await clickText(wrapper, '保留设置');
    expect(wrapper.text()).toContain('待发送、发送中和自动重试中的记录不清理');
    await clickText(wrapper, '保存');
    expect(mock.saveRetention).toHaveBeenCalledWith(
      expect.objectContaining({ successDays: 90, exceptionDays: 180, rowVersion: 0 }),
    );
    wrapper.unmount();
  });
  it('keeps retention settings read-only without channel edit permission', async () => {
    mock.can.mockImplementation(
      (menu: string, action: string) => !(menu === 'notificationChannel' && action === '编辑'),
    );
    const wrapper = mount(NotificationAdmin, { props: { view: 'deliveries' }, global: globals });
    await flushPromises();
    await clickText(wrapper, '保留设置');
    expect(wrapper.findAllComponents(ElButton).some((b) => b.text() === '保存')).toBe(false);
    wrapper.unmount();
  });
  it('copies a rule with its people and clears its persistent ID before save', async () => {
    const wrapper = mount(NotificationAdmin, { props: { view: 'policies' }, global: globals });
    await flushPromises();
    await clickText(wrapper, '编辑');
    await clickText(wrapper, '复制');
    await clickText(wrapper, '保存');
    expect(mock.savePolicy).toHaveBeenCalledOnce();
    const sent = mock.savePolicy.mock.calls[0]![0];
    expect(sent.rules).toHaveLength(2);
    expect(sent.rules[0].id).toBe('19');
    expect(sent.rules[1].id).toBeUndefined();
    expect(sent.rules[1].recipients).toEqual(['1']);
    expect(sent.rules[1].cc).toEqual(['2']);
    wrapper.unmount();
  });
  it('shows App as unavailable and does not expose a saved SMTP password', async () => {
    const wrapper = mount(NotificationAdmin, { props: { view: 'channels' }, global: globals });
    await flushPromises();
    expect(wrapper.text()).toContain('未接入');
    const password = wrapper.find('input[autocomplete="new-password"]');
    expect((password.element as HTMLInputElement).value).toBe('');
    await password.setValue('new-test-password');
    await clickText(wrapper, '保存');
    expect(mock.saveChannels).toHaveBeenCalledOnce();
    expect(
      (wrapper.find('input[autocomplete="new-password"]').element as HTMLInputElement).value,
    ).toBe('');
    wrapper.unmount();
  });
  it('hides create actions for a user with view permission only', async () => {
    mock.can.mockReturnValue(false);
    const wrapper = mount(NotificationAdmin, { props: { view: 'policies' }, global: globals });
    await flushPromises();
    expect(wrapper.findAllComponents(ElButton).some((b) => b.text() === '新增策略')).toBe(false);
    expect(wrapper.findAllComponents(ElButton).some((b) => b.text() === '查看')).toBe(true);
    wrapper.unmount();
  });
  it('defaults plan bindings to inheritance and preserves named explicit policy selection', async () => {
    const wrapper = mount(ReminderPolicyField, {
      props: { kind: 'daily' },
      global: { stubs: { PagedChoiceField: picker } },
    });
    expect(wrapper.findComponent(ElSelect).props('modelValue')).toBe('DEFAULT');
    await wrapper.setProps({ mode: 'POLICY', id: '17', name: '设备提醒' });
    expect(wrapper.text()).toContain('设备提醒');
    await wrapper.findComponent(ElSelect).vm.$emit('update:modelValue', 'OFF');
    expect(wrapper.emitted('update:mode')?.[0]).toEqual(['OFF']);
    wrapper.unmount();
  });
});
