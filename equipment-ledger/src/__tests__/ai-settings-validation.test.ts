import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import AiSettingsPage from '../pages/AiSettingsPage.vue';
const request = vi.hoisted(() => vi.fn());
vi.mock('../api', () => ({ request }));
vi.mock('../stores/auth', () => ({ useAuthStore: () => ({ can: () => true }) }));
vi.mock('../i18n', () => ({ tr: (key: string) => key }));
vi.mock('../composables/useUnsavedChanges', () => ({
  useUnsavedChanges: () => ({ markSaved: vi.fn(), permitLeave: async () => true }),
}));
describe('AI settings field validation', () => {
  beforeEach(() => request.mockReset());
  it.each([
    ['https://token-plan.maas.qianwenaiapi.com/compatible-mode/v1', true],
    ['https://token-plan.maas.qianwenaiapi.com.evil.test/compatible-mode/v1', false],
    ['https://token-plan.maas.qianwenaiapi.com/other', false],
  ])('validates the Token Plan endpoint %s', async (baseUrl, valid) => {
    request.mockResolvedValue({
      baseUrl,
      chatModel: 'qwen3.7-plus',
      embeddingModel: 'text-embedding-v4',
      enabled: false,
    });
    const wrapper = mount(AiSettingsPage, {
      global: { stubs: { AppFormFooter: { template: '<footer><slot/></footer>' } } },
    });
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'ai.save')!
      .trigger('click');
    await flushPromises();
    await vi.waitFor(() => {
      if (valid)
        expect(request.mock.calls.some(([, options]) => options?.method === 'PUT')).toBe(true);
      else expect(wrapper.get('.el-form-item__error').text()).toBe('ai.validation.address');
    });
    if (!valid)
      expect(request.mock.calls.some(([, options]) => options?.method === 'PUT')).toBe(false);
    wrapper.unmount();
  });
  it('shows a missing embedding model inline and does not send the key to an invalid save', async () => {
    request.mockResolvedValue({
      baseUrl: 'https://test.aliyuncs.com/v1',
      chatModel: 'qwen-plus',
      embeddingModel: '',
      enabled: false,
    });
    const wrapper = mount(AiSettingsPage, {
      global: { stubs: { AppFormFooter: { template: '<footer><slot/></footer>' } } },
    });
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'ai.save')!
      .trigger('click');
    await flushPromises();
    expect(wrapper.findAll('.el-form-item.is-error')).toHaveLength(1);
    expect(wrapper.get('.el-form-item.is-error').text()).toContain('ai.embeddingModel');
    await vi.waitFor(() =>
      expect(wrapper.get('.el-form-item__error').text()).toBe('ai.validation.required'),
    );
    expect(request.mock.calls.some(([, options]) => options?.method === 'PUT')).toBe(false);
    wrapper.unmount();
  });
});
