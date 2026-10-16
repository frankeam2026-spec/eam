<script setup lang="ts">
import { ElTableColumn } from 'element-plus';
import { ElButton } from 'element-plus';

import AppBusinessTable from './AppBusinessTable.vue';
import { computed, ref, watch } from 'vue';
import AppDialog from './AppDialog.vue';
import ListSearchInput from './ListSearchInput.vue';
import { repairUserChoices, type DispatchUser } from '../../api';
const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    selected?: string[];
    title?: string;
    busy?: boolean;
    loadUsers?: () => Promise<{ data: DispatchUser[] }>;
  }>(),
  { selected: () => [], title: '选择人员', busy: false },
);
const emit = defineEmits<{ 'update:modelValue': [boolean]; confirm: [string[]] }>();
const rows = ref<DispatchUser[]>([]),
  picked = ref<string[]>([]),
  search = ref(''),
  loading = ref(false),
  error = ref('');
const filtered = computed(() =>
  rows.value.filter((x) =>
    [x.name, x.account, x.department].some((v) => v.includes(search.value.trim())),
  ),
);
watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return;
    picked.value = [...props.selected];
    search.value = '';
    error.value = '';
    loading.value = true;
    try {
      rows.value = (await (props.loadUsers || repairUserChoices)()).data;
    } catch (e) {
      error.value = e instanceof Error ? e.message : '人员加载失败';
    } finally {
      loading.value = false;
    }
  },
);
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => filtered.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];
</script>
<template>
  <AppDialog
    :model-value="modelValue"
    :title="title"
    width="680px"
    :busy="busy"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="picker-tools">
      <span>已选 {{ picked.length }} 人</span
      ><ListSearchInput v-model="search" placeholder="搜索姓名、账号、部门" />
    </div>
    <p v-if="error" class="picker-error">{{ error }}</p>
    <div class="picker-table">
      <AppBusinessTable
        v-model:selection-ids="picked"
        :selection-width="48"
        selection-fixed="left"
        selection-align="center"
        selectable
        :rows="filtered"
        :pagination="false"
        :fill="false"
        :framed="false"
        class="business-table"
        :row-class="(user, tableRowIndex) => ({ picked: picked.includes(user.id) })"
        @row-click="
          (user) => {
            picked = picked.includes(user.id)
              ? picked.filter((x) => x !== user.id)
              : [...picked, user.id];
          }
        "
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">姓名</div></template
          ><template #default="{ row: user, $index: tableRowIndex }">
            <strong>{{ user.name }}</strong>
          </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">部门</div></template
          ><template #default="{ row: user, $index: tableRowIndex }">{{
            user.department
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">账号</div></template
          ><template #default="{ row: user, $index: tableRowIndex }">{{
            user.account
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">未完成工单</div></template
          ><template #default="{ row: user, $index: tableRowIndex }">
            <span class="load-count" :class="{ occupied: user.unfinished > 0 }">{{
              user.unfinished
            }}</span>
          </template></ElTableColumn
        ><template #empty
          ><div v-if="!filtered.length">{{ loading ? '加载中…' : '没有匹配的人员' }}</div></template
        ></AppBusinessTable
      >
    </div>
    <template #footer
      ><ElButton class="" :disabled="busy" @click="emit('update:modelValue', false)"> 取消</ElButton
      ><ElButton
        class=""
        :disabled="busy || !picked.length"
        @click="emit('confirm', picked)"
        type="primary"
      >
        确定
      </ElButton></template
    >
  </AppDialog>
</template>
<style scoped>
.picker-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #64748b;
  gap: 12px;
}
.picker-table {
  max-height: 380px;
  overflow: auto;
  border: 1px solid #e1e7ef;
  border-radius: 4px;
}
.picker-table table {
  width: 100%;
  min-width: 0;
  table-layout: fixed;
}
.picker-table :deep(th) {
  position: sticky;
  top: 0;
}
.picker-table :deep(tr) {
  cursor: pointer;
}
.picker-table :deep(tr.picked) {
  background: var(--primary-light, #eef3ff);
}
.load-count {
  padding: 3px 10px;
  background: #eef7f4;
  color: #168967;
  border-radius: 4px;
}
.load-count.occupied {
  background: #fff3e3;
  color: #ac6b10;
}
.picker-error {
  color: #d74343;
}
</style>
