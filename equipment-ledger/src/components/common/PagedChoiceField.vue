<script setup lang="ts">
import { tr } from '../../i18n';
import DepartmentText from './DepartmentText.vue';

import DialogChoiceInput from './DialogChoiceInput.vue';
import { computed, onUnmounted, ref, watch } from 'vue';
import { ElButton, ElTag, ElTableColumn } from 'element-plus';
import AppDialog from './AppDialog.vue';
import AppBusinessTable from './AppBusinessTable.vue';
import AppPagination from './AppPagination.vue';
import ListSearchInput from './ListSearchInput.vue';
import type { SelectorRow, SelectorPage } from '../../selectors';
const props = withDefaults(
  defineProps<{
    modelValue: string | string[];
    options: SelectorRow[];
    multiple?: boolean;
    disabled?: boolean;
    clearable?: boolean;
    placeholder?: string;
    title?: string;
    context?: string;
    presentation?: 'field' | 'input';
    selectionUnit?: string;
    columns?: { key: string; label: string; minWidth?: number }[];
    loadRows: (q: string, page: number, size: number) => Promise<SelectorPage<SelectorRow>>;
  }>(),
  {
    multiple: false,
    presentation: 'input',
    selectionUnit: '项',
    placeholder: '',
    title: '',
    columns: () => [
      {
        key: 'name',
        get label() {
          return tr('ui.equipment.text1168');
        },
        minWidth: 180,
      },
      {
        key: 'account',
        get label() {
          return tr('ui.equipment.text1570');
        },
        minWidth: 130,
      },
      {
        key: 'department',
        get label() {
          return tr('ui.equipment.text0098');
        },
        minWidth: 180,
      },
    ],
  },
);
const emit = defineEmits<{ 'update:modelValue': [string | string[]]; select: [SelectorRow[]] }>();
const open = ref(false),
  q = ref(''),
  applied = ref(''),
  page = ref(1),
  size = ref(20),
  total = ref(0),
  loading = ref(false),
  error = ref(''),
  rows = ref<SelectorRow[]>([]),
  picked = ref<string[]>([]),
  known = ref(new Map<string, SelectorRow>()),
  showSelected = ref(false);
const ids = computed(() =>
  Array.isArray(props.modelValue) ? props.modelValue : props.modelValue ? [props.modelValue] : [],
);
const caption = computed(() =>
  ids.value
    .map((id) =>
      ((row) => (row ? (row.code ? row.code + ' · ' + row.name : row.name) : id))(
        props.options.find((row) => row.id === id),
      ),
    )
    .join('、'),
);
watch(
  () => props.context,
  () => {
    open.value = false;
    revision++;
  },
);
let revision = 0;
onUnmounted(() => revision++);
async function load() {
  if (!open.value || showSelected.value) return;
  const seq = ++revision;
  loading.value = true;
  error.value = '';
  rows.value = [];
  try {
    const result = await props.loadRows(applied.value, page.value, size.value);
    if (seq !== revision) return;
    rows.value = result.data;
    total.value = result.page.total;
    result.data.forEach((row) => known.value.set(row.id, row));
  } catch (e) {
    if (seq === revision)
      error.value = e instanceof Error ? e.message : tr('ui.equipment.text0160');
  } finally {
    if (seq === revision) loading.value = false;
  }
}
function show() {
  if (props.disabled) return;
  picked.value = [...ids.value];
  known.value = new Map(props.options.map((row) => [row.id, row]));
  q.value = applied.value = '';
  page.value = 1;
  showSelected.value = false;
  open.value = true;
}
watch([open, page, size, applied, showSelected], () => {
  if (!open.value || showSelected.value) {
    revision++;
    loading.value = false;
  } else void load();
});
function search() {
  const value = q.value.trim();
  if (applied.value === value && page.value === 1) void load();
  else {
    applied.value = value;
    page.value = 1;
  }
}
const visible = computed(() =>
  showSelected.value
    ? picked.value.map((id) => known.value.get(id) || { id, name: '已选对象不可用' })
    : rows.value,
);
function confirm() {
  const selected = picked.value.map(
    (id) => known.value.get(id) || props.options.find((row) => row.id === id) || { id, name: id },
  );
  emit('select', selected);
  emit('update:modelValue', props.multiple ? [...picked.value] : picked.value[0] || '');
  open.value = false;
}
function choose(row: SelectorRow) {
  if (!props.multiple) picked.value = [row.id];
}
</script>
<template>
  <DialogChoiceInput
    :model-value="caption"
    :placeholder="placeholder || tr('ui.equipment.text0096')"
    :disabled="disabled"
    :title="title || tr('ui.common.dynamic60')"
    :clearable="clearable"
    @open="show"
    @clear="emit('update:modelValue', multiple ? [] : '')"
  />
  <AppDialog v-model="open" :title="title || tr('ui.common.dynamic60')" width="880px">
    <form class="paged-choice-tools" @submit.prevent="search">
      <ListSearchInput v-model="q" :placeholder="tr('ui.equipment.text1482')" /><ElButton
        text
        @click="showSelected = !showSelected"
        >{{ showSelected ? tr('ui.equipment.text0387') : tr('ui.equipment.text0388') }}（{{
          picked.length
        }}）</ElButton
      ><ElButton v-if="picked.length" text @click="picked = []">{{
        tr('ui.equipment.text1571')
      }}</ElButton>
    </form>
    <AppBusinessTable
      v-model:selection-ids="picked"
      :rows="visible"
      :selectable="multiple"
      :pagination="false"
      :fill="false"
      :loading="loading"
      :error="error"
      :max-height="350"
      row-key="id"
      highlight-current-row
      @row-click="choose"
      @retry="load"
    >
      <ElTableColumn
        v-for="column in columns"
        :key="column.key"
        :prop="column.key"
        :label="column.label"
        :min-width="column.minWidth || 150"
        show-overflow-tooltip
      >
        <template v-if="column.key === 'department'" #default="{ row }">
          <DepartmentText :value="row.departmentPath || row.department" :department-id="row.departmentOrgId || row.departmentId" />
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="!multiple" :label="tr('ui.equipment.text0145')" fixed="right" width="156"
        ><template #default="{ row }"
          ><ElButton link type="primary" @click="picked = [row.id]">{{
            picked.includes(row.id) ? tr('ui.equipment.text0295') : tr('ui.equipment.text0848')
          }}</ElButton></template
        ></ElTableColumn
      >
      <ElTableColumn
        v-else-if="showSelected"
        :label="tr('ui.equipment.text0145')"
        fixed="right"
        width="156"
        ><template #default="{ row }"
          ><ElButton link type="primary" @click="picked = picked.filter((id) => id !== row.id)">{{
            tr('ui.equipment.text0151')
          }}</ElButton></template
        ></ElTableColumn
      >
    </AppBusinessTable>
    <div class="paged-choice-page">
      <span v-if="!showSelected"
        >{{ tr('ui.equipment.text0309') }} {{ total }} {{ tr('ui.equipment.text0296') }}</span
      ><AppPagination
        v-if="!showSelected"
        v-model:page="page"
        v-model:page-size="size"
        :total="total"
      /><span v-else>{{ tr('ui.equipment.text0295') }}{{ picked.length }}{{ selectionUnit }}</span>
    </div>
    <template #footer
      ><ElButton @click="open = false">{{ tr('ui.equipment.text0157') }}</ElButton
      ><ElButton type="primary" :disabled="loading || !!error" @click="confirm">{{
        tr('ui.equipment.text1572')
      }}</ElButton></template
    >
  </AppDialog>
</template>
<style scoped>
.paged-choice-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.paged-choice-page {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  min-height: 34px;
}
</style>
