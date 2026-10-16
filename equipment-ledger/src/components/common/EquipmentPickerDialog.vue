<script setup lang="ts">
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
  size = ref(10),
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
    if (seq === revision) error.value = e instanceof Error ? e.message : '设备加载失败';
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
  { prop: 'code' as const, label: '设备编号', minWidth: 160, overflowTooltip: true },
  { prop: 'name' as const, label: '设备名称', minWidth: 160, overflowTooltip: true },
  { prop: 'model' as const, label: '规格型号', minWidth: 160, overflowTooltip: true },
  { prop: 'department' as const, label: '所属部门', minWidth: 150, overflowTooltip: true },
];
</script>
<template>
  <AppDialog
    :model-value="modelValue"
    title="选择设备"
    width="920px"
    @update:model-value="emit('update:modelValue', $event)"
    ><form @submit.prevent="search">
      <ListSearchInput v-model="q" placeholder="搜索设备编号、名称、型号" />
    </form>
    <p v-if="multiple">已选 {{ selected.length }} 项</p>
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
        ><template v-if="!multiple" #actions="{ row }"
          ><ElButton
            link
            type="primary"
            @click="
              emit('select', row as EquipmentRecord);
              emit('update:modelValue', false);
            "
          >
            选择
          </ElButton></template
        ></AppBusinessTable
      >
    </div>
    <template #footer
      ><ElButton @click="emit('update:modelValue', false)">取消</ElButton
      ><ElButton
        type="primary"
        v-if="multiple"
        :disabled="!selected.length"
        @click="
          emit('selectMultiple', selected);
          emit('update:modelValue', false);
        "
      >
        确定
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
