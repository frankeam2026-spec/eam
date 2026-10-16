<script setup lang="ts">
import { ref } from 'vue';
import { ElButton, ElTag } from 'element-plus';
import AppBusinessTable from './AppBusinessTable.vue';
import AppIcon from '../../src/components/common/AppIcon.vue';
import type { BusinessColumn } from './BusinessTable.types';

type EquipmentRow = { id: string; code: string; name: string; status: string; department: string };
const rows = ref<EquipmentRow[]>([]);
const columns: BusinessColumn<EquipmentRow>[] = [
  { prop: 'code', label: '设备编号', width: 180, fixed: 'left', sortable: true },
  { prop: 'name', label: '设备名称', minWidth: 200, sortable: true },
  { prop: 'status', label: '设备状态', width: 120 },
  { prop: 'department', label: '所属部门', minWidth: 180 },
];
const page = ref(1),
  pageSize = ref(10),
  total = ref(0);
// Supplied by the page's existing authorization logic; false means no edit button.
const canEdit = ref(false),
  canExport = ref(false);
</script>
<template>
  <div class="example-page">
    <AppBusinessTable
      :rows="rows"
      :columns="columns"
      row-key="id"
      :total="total"
      v-model:page="page"
      v-model:page-size="pageSize"
    >
      <template #code="{ row }"
        ><ElButton link type="primary">{{ row.code }}</ElButton></template
      >
      <template #status="{ row }"
        ><ElTag
          :type="row.status === '故障' ? 'danger' : row.status === '正常' ? 'success' : 'warning'"
          >{{ row.status }}</ElTag
        ></template
      >
      <template #actions
        ><ElButton v-if="canEdit" link type="primary">编辑</ElButton
        ><ElButton link type="primary">详情</ElButton></template
      >
      <template #footer-actions>
        <ElButton aria-label="设置列" title="设置列"><AppIcon name="Columns" /></ElButton>
        <ElButton v-if="canExport" aria-label="导出" title="导出"
          ><AppIcon name="Download"
        /></ElButton>
      </template>
    </AppBusinessTable>
  </div>
</template>
<style scoped>
.example-page {
  height: 600px;
  min-height: 0;
}
</style>
