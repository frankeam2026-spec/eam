import { ref, toValue, watch, type MaybeRefOrGetter } from 'vue';
/** Preserves existing storage keys; column identity remains independent of display labels. */
export function useColumnPreferences(
  namespace: string,
  resource: MaybeRefOrGetter<string>,
  defaults: MaybeRefOrGetter<string[]>,
) {
  const visibleColumns = ref<string[]>([]),
    widths = ref<Record<string, number>>({});
  const key = (part: string) =>
    `${namespace}-${part}${toValue(resource) ? '-' + toValue(resource) : ''}`;
  function restore() {
    try {
      const saved: unknown = JSON.parse(localStorage.getItem(key('columns')) || 'null');
      visibleColumns.value =
        Array.isArray(saved) && saved.length && saved.every((value) => typeof value === 'string')
          ? [...new Set(saved)]
          : [...toValue(defaults)];
    } catch {
      visibleColumns.value = [...toValue(defaults)];
    }
    try {
      const saved: unknown = JSON.parse(localStorage.getItem(key('widths')) || '{}');
      widths.value =
        saved && typeof saved === 'object' && !Array.isArray(saved)
          ? Object.fromEntries(
              Object.entries(saved).filter(
                ([, value]) => typeof value === 'number' && Number.isFinite(value) && value > 0,
              ),
            )
          : {};
    } catch {
      widths.value = {};
    }
  }
  function persistWidths() {
    localStorage.setItem(key('widths'), JSON.stringify(widths.value));
  }
  watch(() => toValue(resource), restore, { immediate: true });
  watch(visibleColumns, (value) => localStorage.setItem(key('columns'), JSON.stringify(value)), {
    deep: true,
  });
  return { visibleColumns, widths, restore, persistWidths };
}
