<script setup lang="ts">
import { ElButton } from 'element-plus';

import { ref, watch } from 'vue';
import AppDialog from './AppDialog.vue';
import AppBusinessTable from './AppBusinessTable.vue';
import ListSearchInput from './ListSearchInput.vue';
import { listRepairs, type RepairRecord } from '../../api';
const props = defineProps<{ modelValue: boolean; equipmentCode: string }>();
const emit = defineEmits<{ 'update:modelValue': [boolean]; select: [RepairRecord] }>();
const rows = ref<RepairRecord[]>([]),
  page = ref(1),
  size = ref(10),
  total = ref(0),
  q = ref(''),
  error = ref(''),
  loading = ref(false);
let revision = 0;
async function load() {
  if (!props.modelValue) return;
  const seq = ++revision;
  loading.value = true;
  try {
    const r = await listRepairs({
      page: page.value,
      size: size.value,
      keyword: q.value,
      advanced: JSON.stringify([
        { field: 'equipmentCode', operator: 'eq', values: [props.equipmentCode] },
        {
          field: 'status',
          operator: 'in',
          values: ['待派工', '待接单', '处理中', '挂起', '待验收'],
        },
      ]),
    });
    if (seq === revision) {
      rows.value = r.data;
      total.value = r.page.total;
      error.value = '';
    }
  } catch (e) {
    if (seq === revision) error.value = e instanceof Error ? e.message : '工单加载失败';
  } finally {
    if (seq === revision) loading.value = false;
  }
}
watch(() => [props.modelValue, props.equipmentCode, page.value, size.value], load);
const columns = [
  { prop: 'orderNo' as const, label: '工单编号', minWidth: 160 },
  { prop: 'description' as const, label: '故障描述', minWidth: 230 },
  { prop: 'status' as const, label: '状态', minWidth: 100 },
  { prop: 'assignee' as const, label: '维修人', minWidth: 100 },
];
</script>
<template>
  <AppDialog
    :model-value="modelValue"
    title="关联同设备的有效工单"
    width="900px"
    @update:model-value="emit('update:modelValue', $event)"
    ><form
      @submit.prevent="
        page = 1;
        load();
      "
    >
      <ListSearchInput v-model="q" placeholder="搜索工单或故障描述" />
    </form>
    <div class="repair-picker-body">
      <AppBusinessTable
        v-model:page="page"
        v-model:page-size="size"
        :rows="rows"
        :columns="columns"
        :total="total"
        :error="error"
        :loading="loading"
        @retry="load"
        ><template #actions="{ row }"
          ><ElButton
            link
            type="primary"
            class=""
            @click="
              emit('select', row as RepairRecord);
              emit('update:modelValue', false);
            "
          >
            关联
          </ElButton></template
        ></AppBusinessTable
      >
    </div>
    <template #footer
      ><ElButton class="" @click="emit('update:modelValue', false)"> 取消 </ElButton></template
    ></AppDialog
  >
</template>
<style scoped>
.repair-picker-body {
  height: 430px;
  margin-top: 12px;
}
</style>
