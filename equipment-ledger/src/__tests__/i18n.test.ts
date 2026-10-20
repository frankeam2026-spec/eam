import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  currentLocale,
  chooseLanguage,
  elementLocale,
  enumLabel,
  resetLanguage,
  tr,
} from '../i18n';
import english from '../i18n/en-US.json';
afterEach(() => {
  resetLanguage();
  vi.unstubAllGlobals();
});
const fetcher = () =>
  vi.fn(
    async (input: string, init?: RequestInit) =>
      new Response(
        JSON.stringify(
          input.includes('/resources/')
            ? { data: { locale: 'en-US', messages: english } }
            : { data: { locale: 'en-US', effectiveLocale: 'en-US' } },
        ),
        { status: 200 },
      ),
  );
describe('language preferences and display', () => {
  it('switches without changing a business draft or canonical status', async () => {
    vi.stubGlobal('fetch', fetcher());
    const draft = { name: '客户设备', status: '处理中' };
    await chooseLanguage('en-US', true);
    expect(currentLocale.value).toBe('en-US');
    expect(document.documentElement.lang).toBe('en-US');
    expect(enumLabel('status', draft.status)).not.toBe(draft.status);
    expect(draft).toEqual({ name: '客户设备', status: '处理中' });
    expect(elementLocale.value.name).toBe('en-us');
    expect((elementLocale.value.el.pagination as Record<string, string>).pagesize).toBe('/page');
    expect((elementLocale.value.el.pagination as Record<string, string>).total).toBe(
      'Total {total}',
    );
  });
  it('keeps the active language when saving fails', async () => {
    const fn = fetcher();
    fn.mockImplementation(
      async (input: string) =>
        new Response(
          JSON.stringify(
            input.includes('/resources/')
              ? { data: { locale: 'en-US', messages: english } }
              : { message: 'Save failed' },
          ),
          { status: input.includes('/resources/') ? 200 : 500 },
        ),
    );
    vi.stubGlobal('fetch', fn);
    await expect(chooseLanguage('en-US', true)).rejects.toThrow('Save failed');
    expect(currentLocale.value).toBe('zh-CN');
  });
  it('ignores a resource response from a previous account', async () => {
    let finish!: (response: Response) => void;
    const fetcher = vi.fn(
      () =>
        new Promise<Response>((resolve) => {
          finish = resolve;
        }),
    );
    vi.stubGlobal('fetch', fetcher);
    const pending = chooseLanguage('en-US', true);
    resetLanguage();
    finish(
      new Response(JSON.stringify({ data: { locale: 'en-US', messages: english } }), {
        status: 200,
      }),
    );
    await pending;
    expect(currentLocale.value).toBe('zh-CN');
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it('never translates customer-defined names as enums', () => {
    expect(enumLabel('status', '客户自定义状态')).toBe('客户自定义状态');
    expect(tr('language.field.displayLanguage')).toBe('显示语言');
    expect(tr('unregistered.internal.key')).not.toContain('unregistered.internal.key');
  });
});
