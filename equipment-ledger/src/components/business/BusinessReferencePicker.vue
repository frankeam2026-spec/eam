<script setup lang="ts">import { tr } from '../../i18n';

import DialogChoiceInput from '../common/DialogChoiceInput.vue';
import { ref, computed, watch } from 'vue';
import { ElButton, ElInput, ElTag, ElTableColumn, ElMessage } from 'element-plus';
import AppDialog from '../common/AppDialog.vue';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import AppPagination from '../common/AppPagination.vue';
import ListSearchInput from '../common/ListSearchInput.vue';
import { businessApi, type BusinessObject, type Choice } from '../../businessConfig';
const props = withDefaults(
  defineProps<{
    object: BusinessObject;
    source: string;
    modelValue: string | string[] | null;
    multiple?: boolean;
    labels?: Record<string, string>;
    disabled?: boolean;
    inactive?: boolean;
    placeholder?: string;
  }>(),
  { multiple: false, disabled: false, inactive: false, get placeholder() { return tr('ui.equipment.text0096'); } },
);
const emit = defineEmits<{
  'update:modelValue': [string | string[] | null];
  labels: [Record<string, string>];
}>();
const open = ref(false),
  loading = ref(false),
  error = ref(''),
  query = ref(''),
  applied = ref(''),
  page = ref(1),
  size = ref(20),
  total = ref(0),
  rows = ref<Choice[]>([]),
  selected = ref<string[]>([]);
const known = ref<Record<string, string>>({});
watch(
  () => props.labels,
  (v) => Object.assign(known.value, v || {}),
  { immediate: true, deep: true },
);
const ids = computed(() =>
  Array.isArray(props.modelValue) ? props.modelValue : props.modelValue ? [props.modelValue] : [],
);
const caption = computed(() => ids.value.map((id) => known.value[id] || '已选择').join('、'));
let revision = 0;
async function load() {
  const r = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const result = await businessApi.choices(
      props.object,
      props.source,
      applied.value,
      page.value,
      size.value,
      props.inactive,
    );
    if (r !== revision) return;
    rows.value = result.data;
    total.value = result.total;
    result.data.forEach((x) => (known.value[x.id] = x.name));
  } catch (e) {
    if (r === revision) error.value = e instanceof Error ? e.message : tr('ui.equipment.text0160');
  } finally {
    if (r === revision) loading.value = false;
  }
}
async function show() {
  selected.value = [...ids.value];
  query.value = '';
  applied.value = '';
  page.value = 1;
  open.value = true;
  await load();
}
function search() {
  applied.value = query.value;
  page.value = 1;
  void load();
}
watch([page, size], () => {
  if (open.value) void load();
});
function confirm() {
  if (!props.multiple && selected.value.length > 1) {
    ElMessage.warning(tr('ui.equipment.text1282'));
    return;
  }
  emit('update:modelValue', props.multiple ? [...selected.value] : selected.value[0] || null);
  emit('labels', Object.fromEntries(selected.value.map((id) => [id, known.value[id] || id])));
  open.value = false;
}
function clear() {
  emit('update:modelValue', props.multiple ? [] : null);
}
function selectSingle(row: Choice) {
  if (!props.multiple) selected.value = [row.id];
}
</script>
<template>
  <DialogChoiceInput
    :model-value="caption"
    :placeholder="placeholder"
    :title="placeholder"
    :disabled="disabled"
    clearable
    @open="show"
    @clear="clear"
  />
  <AppDialog v-model="open" :title="placeholder" width="760px"
    ><form class="business-picker-search" @submit.prevent="search">
      <ListSearchInput v-model="query" :placeholder="tr('ui.equipment.text1283')" />
    </form>
    <p v-if="error" role="alert">{{ error }} <ElButton text @click="load">{{ tr('ui.equipment.text0299') }}</ElButton></p>
    <AppBusinessTable
      v-model:selection-ids="selected"
      :rows="rows"
      row-key="id"
      :selectable="multiple"
      :pagination="false"
      :max-height="350"
      @row-click="selectSingle"
      highlight-current-row
    >
      <ElTableColumn :label="tr('ui.equipment.text0404')" prop="name" min-width="250" show-overflow-tooltip /><ElTableColumn
        :label="tr('ui.equipment.text0170')"
        width="100"
        ><template #default="{ row }"
          ><ElTag :type="row.enabled ? 'success' : 'info'">{{
            row.enabled ? tr('ui.equipment.text0216') : tr('ui.equipment.text0260')
          }}</ElTag></template
        ></ElTableColumn
      >
      <ElTableColumn v-if="!multiple" :label="tr('ui.equipment.text0848')" width="90"
        ><template #default="{ row }"
          ><ElButton link type="primary" @click="selected = [row.id]">{{
            selected.includes(row.id) ? tr('ui.equipment.text0295') : tr('ui.equipment.text0848')
          }}</ElButton></template
        ></ElTableColumn
      >
    </AppBusinessTable>
    <div class="business-picker-footer">
      <AppPagination v-model:page="page" v-model:page-size="size" :total="total" /><span
        >{{ tr('ui.equipment.text0295') }} {{ selected.length }} {{ tr('ui.equipment.text0394') }}</span
      >
    </div>
    <template #footer
      ><ElButton @click="open = false">{{ tr('ui.equipment.text0157') }}</ElButton
      ><ElButton type="primary" :disabled="loading || !!error" @click="confirm"
        >{{ tr('ui.equipment.text0402') }}</ElButton
      ></template
    >
  </AppDialog>
</template>
<style scoped>
.business-picker-search {
  display: flex;
  margin-bottom: 12px;
}
.business-picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
</style>
