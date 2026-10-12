<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import AppDialog from './AppDialog.vue';
import ListSearchInput from './ListSearchInput.vue';
import { repairUserChoices, type DispatchUser } from '../../api';
const props = withDefaults(
  defineProps<{ modelValue: boolean; selected?: string[]; title?: string; busy?: boolean }>(),
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
      rows.value = (await repairUserChoices()).data;
    } catch (e) {
      error.value = e instanceof Error ? e.message : '人员加载失败';
    } finally {
      loading.value = false;
    }
  },
);
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
      <table class="business-table">
        <thead>
          <tr>
            <th class="pick-check"></th>
            <th>姓名</th>
            <th>部门</th>
            <th>账号</th>
            <th>未完成工单</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="user in filtered"
            :key="user.id"
            :class="{ picked: picked.includes(user.id) }"
            @click="
              picked = picked.includes(user.id)
                ? picked.filter((x) => x !== user.id)
                : [...picked, user.id]
            "
          >
            <td class="pick-check">
              <input
                v-model="picked"
                type="checkbox"
                :value="user.id"
                :aria-label="`选择${user.name}`"
                @click.stop
              />
            </td>
            <td>
              <strong>{{ user.name }}</strong>
            </td>
            <td>{{ user.department }}</td>
            <td>{{ user.account }}</td>
            <td>
              <span class="load-count" :class="{ occupied: user.unfinished > 0 }">{{
                user.unfinished
              }}</span>
            </td>
          </tr>
          <tr v-if="!filtered.length">
            <td colspan="5">{{ loading ? '加载中…' : '没有匹配的人员' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <template #footer
      ><button class="btn secondary" :disabled="busy" @click="emit('update:modelValue', false)">
        取消</button
      ><button
        class="btn primary"
        :disabled="busy || !picked.length"
        @click="emit('confirm', picked)"
      >
        确定
      </button></template
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
.picker-table th,
.picker-table td {
  height: 42px;
  padding: 8px 12px;
  font-size: 13px;
  border-bottom: 1px solid #edf0f5;
}
.picker-table th {
  background: #f5f7fa;
  position: sticky;
  top: 0;
}
.picker-table tr {
  cursor: pointer;
}
.picker-table tr.picked {
  background: var(--primary-light, #eef3ff);
}
.picker-table .pick-check {
  width: 42px;
  text-align: center;
}
.picker-table input {
  accent-color: var(--primary, #2458f5);
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
