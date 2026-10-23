import { ref, toValue, watch, type MaybeRefOrGetter } from 'vue';
import { PAGE_SIZES } from '../../utils/pagination';

export const EQUIPMENT_PAGE_SIZES = PAGE_SIZES;

/** Page-size preferences belong to one account and never change the query scope. */
export function useEquipmentPageSize(account: MaybeRefOrGetter<string>) {
  const pageSize = ref(20);
  const storageKey = () => {
    const identity = toValue(account);
    return identity ? `eam-equipment-page-size-${identity}` : '';
  };
  const valid = (value: number) => EQUIPMENT_PAGE_SIZES.includes(value);
  watch(
    () => toValue(account),
    () => {
      let saved = 20;
      try {
        const key = storageKey();
        const value = key ? Number(localStorage.getItem(key)) : 20;
        if (valid(value)) saved = value;
      } catch {
        /* Storage may be unavailable; the list still works. */
      }
      pageSize.value = saved;
    },
    { immediate: true, flush: 'sync' },
  );
  watch(
    pageSize,
    (value) => {
      if (!valid(value)) {
        pageSize.value = 20;
        return;
      }
      try {
        const key = storageKey();
        if (key) localStorage.setItem(key, String(value));
      } catch {
        /* Preference persistence must not block queries. */
      }
    },
    { flush: 'sync' },
  );
  return pageSize;
}
