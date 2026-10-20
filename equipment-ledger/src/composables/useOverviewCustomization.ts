import { tr } from '../i18n';
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import {
  overviewApi,
  overviewLabels,
  type OverviewBlock,
  type OverviewColumn,
  type OverviewLayout,
  type OverviewState,
} from '../homeOverview';
import { isAnalytics, type AnalyticsMetric, periodError } from '../analytics';
import { useUnsavedChanges } from './useUnsavedChanges';
const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value));
export function useOverviewCustomization() {
  const state = ref<OverviewState>();
  const layout = ref<OverviewLayout>({
    metrics: ['pending', 'active', 'today', 'notification'],
    blocks: [],
  });
  const editing = ref(false),
    saving = ref(false),
    initializing = ref(false),
    error = ref('');
  const form = ref<FormInstance>();
  const selectedType = ref('');
  const dragId = ref('');
  const { markSaved, permitLeave } = useUnsavedChanges(
    () => layout.value,
    () => editing.value,
    () => saving.value || initializing.value,
  );
  const rules = computed<FormRules>(() =>
    Object.fromEntries(
      layout.value.blocks.map((block, index) => [
        `blocks.${index}.title`,
        [
          { required: true, whitespace: true, get message() { return tr('ui.equipment.text1555'); }, trigger: 'blur' },
          { max: 80, get message() { return tr('ui.equipment.text1614'); }, trigger: 'blur' },
        ],
      ]),
    ),
  );
  const available = computed(() =>
    (state.value?.catalog || []).filter(
      (type) =>
        type.startsWith('analytics:') || !layout.value.blocks.some((block) => block.type === type),
    ),
  );
  function apply(next: OverviewState) {
    error.value = '';
    state.value = next;
    layout.value = {
      layoutMode: 'work',
      period: { preset: 'month' },
      companyIds: [],
      departmentIds: [],
      ...copy(next.layout),
    };
    editing.value = false;
    markSaved();
  }
  async function load(templateId?: string) {
    if (!(await permitLeave())) return false;
    initializing.value = true;
    error.value = '';
    try {
      apply(await overviewApi.load(templateId));
      return true;
    } catch (e) {
      error.value = e instanceof Error ? e.message : tr('ui.equipment.text1615');
      return false;
    } finally {
      initializing.value = false;
    }
  }
  function begin() {
    error.value = '';
    markSaved();
    editing.value = true;
  }
  async function cancel() {
    if (!(await permitLeave()) || !state.value) return;
    layout.value = copy(state.value.layout);
    editing.value = false;
    markSaved();
  }
  function restore() {
    if (state.value) layout.value = copy(state.value.defaultLayout);
  }
  async function save() {
    if (
      !state.value ||
      saving.value ||
      periodError(layout.value.period) ||
      layout.value.blocks.some((b) => periodError(b.period))
    )
      return false;
    saving.value = true;
    try {
      if (!(await form.value?.validate().catch(() => false))) return false;
      apply(await overviewApi.save(state.value, layout.value));
      ElMessage.success(tr('ui.equipment.text1616'));
      return true;
    } catch (e) {
      error.value = e instanceof Error ? e.message : tr('ui.equipment.text1617');
      return false;
    } finally {
      saving.value = false;
    }
  }
  function add(metric?: AnalyticsMetric) {
    const type = selectedType.value;
    if (!available.value.includes(type) || layout.value.blocks.length >= 12) return;
    layout.value.blocks.push({
      id: crypto.randomUUID(),
      type,
      title: overviewLabels[type] || type,
      column: ['anomalies', 'shortcuts', 'surplus', 'inventory'].includes(type) ? 'side' : 'main',
      width: 12,
      limit: 5,
      scope: type === 'load' ? 'team' : 'personal',
      ...(isAnalytics({ type })
        ? { topN: 10, period: { preset: metric?.period || 'follow' } }
        : {}),
      module: '',
    });
    selectedType.value = '';
  }
  function remove(id: string) {
    if (layout.value.blocks.length > 1)
      layout.value.blocks = layout.value.blocks.filter((block) => block.id !== id);
  }
  function update(block: OverviewBlock) {
    layout.value.blocks = layout.value.blocks.map((value) =>
      value.id === block.id ? block : value,
    );
  }
  function move(id: string, offset: number) {
    const block = layout.value.blocks.find((value) => value.id === id);
    if (!block) return;
    const siblings = layout.value.blocks.filter((value) => value.column === block.column);
    const target = siblings[siblings.findIndex((value) => value.id === id) + offset];
    if (!target) return;
    const next = [...layout.value.blocks];
    const from = next.findIndex((value) => value.id === id),
      to = next.findIndex((value) => value.id === target.id);
    [next[from], next[to]] = [next[to]!, next[from]!];
    layout.value.blocks = next;
  }
  function drop(id: string, column: OverviewColumn, targetId?: string) {
    const block = layout.value.blocks.find((value) => value.id === id);
    if (!editing.value || !block || (block.type === 'work' && column === 'side') || id === targetId)
      return;
    const next = layout.value.blocks.filter((value) => value.id !== id);
    const targetIndex = targetId ? next.findIndex((value) => value.id === targetId) : -1;
    next.splice(targetIndex < 0 ? next.length : targetIndex, 0, { ...block, column });
    layout.value.blocks = next;
    dragId.value = '';
  }
  return {
    state,
    layout,
    editing,
    saving,
    initializing,
    error,
    form,
    rules,
    available,
    selectedType,
    dragId,
    load,
    begin,
    cancel,
    restore,
    save,
    add,
    remove,
    update,
    move,
    drop,
  };
}
