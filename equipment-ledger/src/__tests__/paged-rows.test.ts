import { describe, it, expect } from 'vitest';
import { usePagedRows, type QueryPage } from '../composables/usePagedRows';
function pending<T>() {
  let resolve!: (value: T) => void, reject!: (error: Error) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
describe('paged query lifecycle', () => {
  it('ignores late pages and late errors while preserving current rows and total', async () => {
    const first = pending<QueryPage<string>>(),
      second = pending<QueryPage<string>>();
    const list = usePagedRows((key: string) => (key === 'first' ? first.promise : second.promise));
    const old = list.refresh('first'),
      current = list.refresh('second');
    second.resolve({ data: ['current'], page: { total: 10005 } });
    expect(await current).toBe(true);
    first.reject(new Error('old failure'));
    expect(await old).toBe(false);
    expect(list.rows.value).toEqual(['current']);
    expect(list.total.value).toBe(10005);
  });
  it('invalidates outstanding reads on editor navigation or unmount and propagates current errors', async () => {
    const read = pending<QueryPage<string>>();
    const list = usePagedRows(() => read.promise);
    const result = list.refresh();
    list.invalidate();
    read.resolve({ data: ['stale'], page: { total: 1 } });
    expect(await result).toBe(false);
    expect(list.rows.value).toEqual([]);
    const failure = usePagedRows<string, []>(async () => {
      throw new Error('current failure');
    });
    await expect(failure.refresh()).rejects.toThrow('current failure');
  });
});
