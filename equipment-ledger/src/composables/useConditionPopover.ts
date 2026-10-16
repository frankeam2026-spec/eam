import { ref, type Ref } from 'vue';
import type { FilterCondition } from '../query/advancedFilters';

/** Shared draft/anchor orchestration; each list retains its own query and permission rules. */
export function useConditionPopover(
  open: Ref<boolean>,
  read: () => FilterCondition[],
  apply: (conditions: FilterCondition[]) => void,
) {
  const filterAnchor = ref<HTMLElement>();
  const editingFilter = ref<string>();
  function showFilter(anchor: HTMLElement, field?: string) {
    filterAnchor.value = anchor;
    editingFilter.value = field;
    open.value = true;
  }
  function replaceFilters(conditions: FilterCondition[]) {
    open.value = false;
    apply(conditions);
  }
  function confirmFilter(condition: FilterCondition) {
    const remaining = read().filter((item) => item.field !== condition.field);
    replaceFilters(
      condition.values.some((value) => String(value ?? '').trim())
        ? [...remaining, condition]
        : remaining,
    );
  }
  return { filterAnchor, editingFilter, showFilter, replaceFilters, confirmFilter };
}
