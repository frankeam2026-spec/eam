import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import Assistant from '../components/knowledge/XiaozhuAssistant.vue';
const request = vi.hoisted(() => vi.fn());
const stream = vi.hoisted(() => vi.fn());
vi.mock('../api', () => ({ request, requestStream: stream }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('../i18n', () => ({ tr: (key: string) => key }));
vi.mock('../stores/auth', () => ({ useAuthStore: () => ({ user: { id: '1', tenantId: '10' } }) }));
describe('Xiaozhu assistant', () => {
  beforeEach(() => {
    request.mockReset();
    stream.mockReset();
  });
  it('sends explicit questions and renders source text without HTML execution', async () => {
    request.mockResolvedValueOnce({ id: '100' });
    stream.mockResolvedValue({
      answer: 'Check the manual',
      citations: [
        {
          chunkId: '2',
          documentId: '3',
          title: 'Manual',
          pageNo: 4,
          excerpt: '<img src=x onerror=alert(1)>',
        },
      ],
    });
    const wrapper = mount(Assistant, { global: { stubs: { AppIcon: true } } });
    await wrapper.get('button.xiaozhu-float').trigger('click');
    expect(wrapper.emitted('layout')?.at(-1)).toEqual([{ open: true, overlay: false }]);
    await wrapper.get('textarea').setValue('P-103 lubricant?');
    const send = wrapper.findAll('button').find((b) => b.text() === 'xiaozhu.send')!;
    await send.trigger('click');
    await flushPromises();
    expect(request.mock.calls[0]![0]).toBe('/knowledge/assistant/conversations');
    expect(stream.mock.calls[0]![0]).toBe('/knowledge/assistant/conversations/100/stream');
    expect(JSON.parse(stream.mock.calls[0]![1].body)).toMatchObject({
      question: 'P-103 lubricant?',
    });
    expect(JSON.parse(stream.mock.calls[0]![1].body).requestKey).toMatch(/^[0-9a-f-]{36}$/);
    expect(wrapper.text()).toContain('Check the manual');
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>');
    expect(wrapper.find('img').exists()).toBe(false);
    wrapper.unmount();
  });
  it.each([{ terminal: true }, { terminal: false }])(
    'retries correctly after terminal=$terminal without duplicating the question',
    async ({ terminal }) => {
      request.mockResolvedValueOnce({ id: '100' });
      stream
        .mockRejectedValueOnce(
          terminal
            ? Object.assign(new Error('failed'), { status: 503, code: 'AI_TURN_FAILED' })
            : new Error('connection lost'),
        )
        .mockResolvedValueOnce({ answer: 'answer', citations: [] });
      const wrapper = mount(Assistant, { global: { stubs: { AppIcon: true } } });
      await wrapper.get('button.xiaozhu-float').trigger('click');
      await wrapper.get('textarea').setValue('question');
      const send = () => wrapper.findAll('button').find((b) => b.text() === 'xiaozhu.send')!;
      await send().trigger('click');
      await flushPromises();
      await send().trigger('click');
      await flushPromises();
      const keys = stream.mock.calls.map(([, options]) => JSON.parse(options.body).requestKey);
      if (terminal) expect(keys[0]).not.toBe(keys[1]);
      else expect(keys[0]).toBe(keys[1]);
      expect(wrapper.findAll('article.user')).toHaveLength(1);
      wrapper.unmount();
    },
  );
});
