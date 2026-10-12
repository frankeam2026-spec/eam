import { computed, onMounted, onUnmounted, ref } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { ElMessageBox } from 'element-plus';

/** Protect persisted forms and task drafts without treating their initial load as an edit. */
export function useUnsavedChanges(
  source: () => unknown,
  active: () => boolean,
  busy: () => boolean = () => false,
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
    pending = ElMessageBox.confirm(
      '内容尚未保存。废弃将离开；保存将保留当前编辑内容并留在本页。',
      '离开当前页面？',
      {
        confirmButtonText: '保存',
        cancelButtonText: '废弃',
        type: 'warning',
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
        autofocus: false,
        customClass: 'eam-unsaved-confirmation',
        confirmButtonClass: 'unsaved-continue-button',
      },
    )
      .then(() => false)
      .catch((action) => {
        if (action !== 'cancel') return false;
        // Discard is explicit consent. Mark this edit acknowledged so a caller's
        // permitLeave followed by the route guard does not ask a second time.
        markSaved();
        return true;
      })
      .finally(() => {
        pending = undefined;
      });
    requestAnimationFrame(() =>
      document
        .querySelector<HTMLButtonElement>('.eam-unsaved-confirmation .unsaved-continue-button')
        ?.focus(),
    );
    return (await pending) && !busy();
  }
  onBeforeRouteLeave(permitLeave);
  onBeforeRouteUpdate(permitLeave);
  function unload(event: BeforeUnloadEvent) {
    if (dirty.value || busy()) {
      event.preventDefault();
      event.returnValue = '';
    }
  }
  onMounted(() => window.addEventListener('beforeunload', unload));
  onUnmounted(() => window.removeEventListener('beforeunload', unload));
  return { dirty, markSaved, permitLeave };
}
