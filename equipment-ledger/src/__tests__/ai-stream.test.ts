import { afterEach, describe, expect, it, vi } from 'vitest';
import { requestStream } from '../api';
describe('validated assistant event stream', () => {
  afterEach(() => vi.unstubAllGlobals());
  it('handles UTF-8 and CRLF split across network chunks', async () => {
    const bytes = new TextEncoder().encode(
      'event: status\r\ndata: PROCESSING\r\n\r\nevent: answer\r\ndata: {"answer":"润滑","citations":[]}\r\n\r\n',
    );
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          new ReadableStream({
            start(c) {
              for (const byte of bytes) c.enqueue(new Uint8Array([byte]));
              c.close();
            },
          }),
        ),
      ),
    );
    expect(await requestStream('/test', { method: 'POST', body: '{}' })).toEqual({
      answer: '润滑',
      citations: [],
    });
  });
  it('preserves retryable business failure status', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            'event: failure\ndata: {"message":"重新发送","status":422,"code":"AI_TURN_FAILED"}\n\n',
          ),
        ),
    );
    await expect(requestStream('/test', { method: 'POST', body: '{}' })).rejects.toMatchObject({
      status: 422,
      code: 'AI_TURN_FAILED',
      message: '重新发送',
    });
  });
  it('does not accept a progress event as a completed answer', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('event: status\ndata: PROCESSING\n\n')),
    );
    await expect(requestStream('/test', { method: 'POST', body: '{}' })).rejects.toThrow();
  });
});
