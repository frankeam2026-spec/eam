<script setup lang="ts">
import DepartmentText from './DepartmentText.vue';
import { tr } from '../../i18n';

import { ElButton } from 'element-plus';
import { ref, watch } from 'vue';
import AppDialog from './AppDialog.vue';
import AppBusinessTable from './AppBusinessTable.vue';
import ListSearchInput from './ListSearchInput.vue';
import { listEquipment, type EquipmentRecord } from '../../api';
const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    multiple?: boolean;
    excludedIds?: string[];
    loadRows?: (
      q: string,
      page: number,
      size: number,
    ) => Promise<{ data: EquipmentRecord[]; page: { total: number } }>;
  }>(),
  { multiple: false, excludedIds: () => [] },
);
const selected = ref<EquipmentRecord[]>([]);
const emit = defineEmits<{
  'update:modelValue': [boolean];
  select: [EquipmentRecord];
  selectMultiple: [EquipmentRecord[]];
}>();
const rows = ref<EquipmentRecord[]>([]),
  page = ref(1),
  size = ref(20),
  total = ref(0),
  q = ref(''),
  applied = ref(''),
  error = ref(''),
  loading = ref(false);
let revision = 0;
async function load() {
  if (!props.modelValue) return;
  const seq = ++revision;
  loading.value = true;
  try {
    const r = props.loadRows
      ? await props.loadRows(applied.value, page.value, size.value)
      : await listEquipment({ page: page.value, size: size.value, keyword: applied.value });
    if (seq === revision) {
      rows.value = r.data;
      total.value = r.page.total;
      error.value = '';
    }
  } catch (e) {
    if (seq === revision) error.value = e instanceof Error ? e.message : tr('ui.equipment.text1050');
  } finally {
    if (seq === revision) loading.value = false;
  }
}
watch(
  () => props.modelValue,
  (open) => {
    ++revision;
    selected.value = [];
    if (open) {
      q.value = applied.value = '';
      page.value = 1;
      rows.value = [];
      void load();
    }
  },
);
watch([page, size], load);
function search() {
  applied.value = q.value.trim();
  page.value = 1;
  load();
}
const columns = [
  { prop: 'code' as const, get label() { return tr('ui.equipment.text0078'); }, minWidth: 160, overflowTooltip: true },
  { prop: 'name' as const, get label() { return tr('ui.equipment.text0081'); }, minWidth: 160, overflowTooltip: true },
  { prop: 'model' as const, get label() { return tr('ui.equipment.text0091'); }, minWidth: 160, overflowTooltip: true },
  { prop: 'department' as const, get label() { return tr('ui.equipment.text0098'); }, minWidth: 150 },
];
</script>
<template>
  <AppDialog
    :model-value="modelValue"
    :title="tr('ui.equipment.text0220')"
    width="920px"
    @update:model-value="emit('update:modelValue', $event)"
    ><form @submit.prevent="search">
      <ListSearchInput v-model="q" :placeholder="tr('ui.equipment.text0389')" />
    </form>
    <p v-if="multiple">{{ tr('ui.equipment.text0295') }} {{ selected.length }} {{ tr('ui.equipment.text0394') }}</p>
    <div class="equipment-picker-body">
      <AppBusinessTable
        v-model:page="page"
        v-model:page-size="size"
        v-model:selection="selected"
        :rows="rows"
        :selectable="multiple"
        :can-select-row="(row) => !excludedIds.includes(String(row.id))"
        :columns="
          multiple
            ? [...columns, { prop: 'brand', label: '品牌', minWidth: 120, overflowTooltip: true }]
            : columns
        "
        :total="total"
        :loading="loading"
        :error="error"
        @retry="load"
        ><template #department="{ row }"><DepartmentText :value="row.departmentPath || row.department" :department-id="row.departmentOrgId" /></template><template v-if="!multiple" #actions="{ row }"
          ><ElButton
            link
            type="primary"
            @click="
              emit('select', row as EquipmentRecord);
              emit('update:modelValue', false);
            "
          >
            {{ tr('ui.equipment.text0848') }}
          </ElButton></template
        ></AppBusinessTable
      >
    </div>
    <template #footer
      ><ElButton @click="emit('update:modelValue', false)">{{ tr('ui.equipment.text0157') }}</ElButton
      ><ElButton
        type="primary"
        v-if="multiple"
        :disabled="!selected.length"
        @click="
          emit('selectMultiple', selected);
          emit('update:modelValue', false);
        "
      >
        {{ tr('ui.equipment.text0402') }}
      </ElButton></template
    ></AppDialog
  >
</template>
<style scoped>
.equipment-picker-body {
  height: min(440px, calc(100dvh - 260px));
  margin-top: 12px;
}
</style>
