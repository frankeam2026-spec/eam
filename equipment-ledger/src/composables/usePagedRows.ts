import { ref, type Ref } from 'vue';

export type QueryPage<T> = { data: T[]; page: { total: number } };
/** Owns the applied page. A late success or failure never replaces a newer result. */
export function usePagedRows<T, P extends unknown[]>(
  read: (...parameters: P) => Promise<QueryPage<T>>,
) {
  const rows = ref<T[]>([]) as Ref<T[]>;
  const total = ref(0);
  let revision = 0;
  async function refresh(...parameters: P) {
    const current = ++revision;
    try {
      const result = await read(...parameters);
      if (current !== revision) return false;
      rows.value = result.data;
      total.value = result.page.total;
      return true;
    } catch (error) {
      if (current !== revision) return false;
      throw error;
    }
  }
  function invalidate() {
    ++revision;
  }
  return { rows, total, refresh, invalidate };
}
