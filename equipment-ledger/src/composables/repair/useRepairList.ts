import { watch, type MaybeRefOrGetter } from 'vue';
import { listRepairs } from '../../api';
import type { FilterCondition } from '../../query/advancedFilters';
import { usePagedRows } from '../usePagedRows';
import { useColumnPreferences } from '../useColumnPreferences';

export function repairListParameters<T extends Record<string, string>>(
  query: T,
  conditions: FilterCondition[],
  sort: string | null,
  direction: string | null,
) {
  return {
    ...query,
    queue: 'all',
    relation: conditions.find((c) => c.field === 'relation')?.values.join(',') || '',
    sortBy: sort || undefined,
    direction: direction || undefined,
    advanced: JSON.stringify(conditions.filter((c) => c.field !== 'relation')),
  };
}
export const useRepairList = () => usePagedRows(listRepairs);

export function useRepairColumns(defaults: MaybeRefOrGetter<string[]>) {
  const stored = localStorage.getItem('eam-repair-columns');
  const preferences = useColumnPreferences('eam-repair', '', defaults);
  preferences.visibleColumns.value = [
    ...new Set(
      preferences.visibleColumns.value.map((key) => (key === 'faultAt' ? 'reportedAt' : key)),
    ),
  ];
  if (stored && !localStorage.getItem('eam-repair-status-column-v2')) {
    localStorage.setItem('eam-repair-status-column-v2', '1');
    if (!preferences.visibleColumns.value.includes('status'))
      preferences.visibleColumns.value.push('status');
  }
  watch(preferences.widths, preferences.persistWidths, { deep: true });
  return preferences;
}
