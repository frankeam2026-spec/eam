import { computed, onMounted, onUnmounted, ref } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { confirmUnsavedChanges } from '../components/common/AppUnsavedChangesConfirm';

/** Protect persisted forms and task drafts without treating their initial load as an edit. */
export function useUnsavedChanges(
  source: () => unknown,
  active: () => boolean,
  busy: () => boolean = () => false,
  options: {
    localOnly?: boolean;
  } = {},
) {
  const baseline = ref<string>();
  function snapshot(value: unknown): string {
    return JSON.stringify(value, (_key, item) =>
      item && typeof item === 'object' && !Array.isArray(item)
        ? Object.fromEntries(Object.entries(item).sort(([a], [b]) => a.localeCompare(b)))
        : item,
    );
  }
  const dirty = computed(
    () => active() && baseline.value !== undefined && snapshot(source()) !== baseline.value,
  );
  function markSaved(value: unknown = source()) {
    baseline.value = snapshot(value);
  }
  let pending: Promise<boolean> | undefined;
  async function permitLeave(): Promise<boolean> {
    if (busy()) return false;
    if (!dirty.value) return true;
    if (pending) return (await pending) && !busy();
    pending = confirmUnsavedChanges()
      .then((discard) => {
        if (!discard) return false;
        // Discard is explicit consent. Mark this edit acknowledged so a caller's
        // permitLeave followed by the route guard does not ask a second time.
        markSaved();
        return true;
      })
      .finally(() => {
        pending = undefined;
      });
    return (await pending) && !busy();
  }
  if (!options.localOnly) {
    onBeforeRouteLeave(permitLeave);
    onBeforeRouteUpdate(permitLeave);
  }
  function unload(event: BeforeUnloadEvent) {
    if (dirty.value || busy()) {
      event.preventDefault();
      event.returnValue = '';
    }
  }
  if (!options.localOnly) {
    onMounted(() => window.addEventListener('beforeunload', unload));
    onUnmounted(() => window.removeEventListener('beforeunload', unload));
  }
  return { dirty, markSaved, permitLeave };
}
