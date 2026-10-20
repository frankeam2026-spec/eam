import { ref } from 'vue';
import { ApiError } from '../api';
/** Known server field paths render at their controls; callers show other business errors once. */
export function useApiFieldErrors(fields: readonly string[]) {
  const errors = ref<Record<string, string>>({}),
    known = new Set(fields);
  function capture(error: unknown) {
    if (!(error instanceof ApiError)) return false;
    const matched = Object.fromEntries(
      Object.entries(error.fieldErrors).filter(([key]) => known.has(key)),
    );
    if (!Object.keys(matched).length) return false;
    errors.value = matched;
    return true;
  }
  function clear(field?: string) {
    if (field) delete errors.value[field];
    else errors.value = {};
  }
  return { errors, capture, clear };
}
