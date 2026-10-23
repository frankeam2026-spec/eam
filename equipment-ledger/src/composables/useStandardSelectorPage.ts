import { tr } from '../i18n';
import { onUnmounted, ref, watch, type Ref } from 'vue';
import { planningSelectors, type PlanningModule } from '../selectors';

/** The candidate page is separate from the selected standard/item cache. */
export function useStandardSelectorPage<T extends { id: string }>(
  module: PlanningModule,
  open: Ref<boolean>,
  equipmentId: Readonly<Ref<string>>,
  all: Ref<boolean>,
  selected: Ref<string>,
  remember: (rows: T[]) => void,
) {
  const rows = ref<T[]>([]) as Ref<T[]>;
  const search = ref(''),
    page = ref(1),
    total = ref(0),
    loading = ref(false),
    error = ref('');
  const size = ref(20);
  let revision = 0;
  async function load() {
    if (!open.value) return;
    const seq = ++revision;
    loading.value = true;
    error.value = '';
    rows.value = [];
    try {
      const result = await planningSelectors(module).standards<T>(
        search.value,
        page.value,
        size.value,
        all.value ? '' : equipmentId.value,
      );
      if (seq !== revision) return;
      rows.value = result.data;
      total.value = result.page.total;
      remember(result.data);
      if (!result.data.some((row) => row.id === selected.value))
        selected.value = result.data[0]?.id || '';
    } catch (e) {
      if (seq === revision) {
        error.value = e instanceof Error ? e.message : tr('ui.equipment.text1618');
        selected.value = '';
      }
    } finally {
      if (seq === revision) loading.value = false;
    }
  }
  watch([open, all, equipmentId, search, size], () => {
    if (!open.value) {
      revision++;
      loading.value = false;
      return;
    }
    if (page.value !== 1) page.value = 1;
    else void load();
  });
  watch(page, load);
  onUnmounted(() => revision++);
  return { rows, search, page, total, size, loading, error, load };
}
