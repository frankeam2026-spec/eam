<script setup lang="ts">import { tr } from '../../i18n';

import { computed, ref, watch } from 'vue';
import { ElButton, ElInput, ElCheckboxGroup, ElCheckbox } from 'element-plus';
import AppDialog from './AppDialog.vue';
const open = defineModel<boolean>({ required: true });
const props = defineProps<{
  columns: { key: string; label: string }[];
  defaultKeys: string[];
  busy?: boolean;
}>();
const emit = defineEmits<{ confirm: [keys: string[]] }>();
const keys = ref<string[]>([]),
  search = ref('');
const visible = computed(() => props.columns.filter((c) => c.label.includes(search.value.trim())));
watch(open, (v) => {
  if (v) {
    keys.value = props.defaultKeys.filter((k) => props.columns.some((c) => c.key === k));
    search.value = '';
  }
});
</script>
<template>
  <AppDialog v-model="open" :title="tr('ui.equipment.text1400')" width="560px" :busy="busy">
    <div class="export-fields-tools">
      <ElInput v-model="search" clearable :placeholder="tr('ui.equipment.text1401')" /><ElButton
        @click="keys = columns.map((c) => c.key)"
        >{{ tr('ui.equipment.text1402') }}</ElButton
      ><ElButton @click="keys = []">{{ tr('ui.equipment.text1358') }}</ElButton>
    </div>
    <ElCheckboxGroup v-model="keys" class="export-fields-options"
      ><ElCheckbox v-for="c in visible" :key="c.key" :value="c.key">{{
        c.label
      }}</ElCheckbox></ElCheckboxGroup
    >
    <template #footer
      ><ElButton @click="open = false">{{ tr('ui.equipment.text0157') }}</ElButton
      ><ElButton
        type="primary"
        :loading="busy"
        :disabled="!keys.length"
        @click="emit('confirm', keys)"
        >{{ tr('ui.equipment.text0711') }}</ElButton
      ></template
    >
  </AppDialog>
</template>
<style scoped>
.export-fields-tools {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.export-fields-tools .el-button {
  margin: 0;
}
.export-fields-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  max-height: 360px;
  overflow: auto;
  gap: 4px 16px;
}
.export-fields-options :deep(.el-checkbox) {
  min-width: 0;
  margin: 0;
}
.export-fields-options :deep(.el-checkbox__label) {
  white-space: normal;
  overflow-wrap: anywhere;
}
</style>
