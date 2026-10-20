<script setup lang="ts">import { tr } from '../../i18n';

import { computed, ref } from 'vue';
import { ElButton, ElInput, ElSwitch, ElFormItem } from 'element-plus';
import AppIcon from '../common/AppIcon.vue';
import { type FieldOption } from '../../businessConfig';
const options = defineModel<FieldOption[]>({ required: true });
const props = withDefaults(
  defineProps<{ publishedIds?: string[]; disabled?: boolean; showErrors?: boolean }>(),
  { publishedIds: () => [], disabled: false, showErrors: false },
);
const touched = ref(new Set<string>());
const published = computed(() => new Set(props.publishedIds));
function add() {
  options.value = [
    ...options.value,
    { id: crypto.randomUUID(), name: '', enabled: true, sortOrder: options.value.length },
  ];
}
function move(index: number, offset: number) {
  const rows = [...options.value],
    next = index + offset;
  if (next < 0 || next >= rows.length) return;
  [rows[index], rows[next]] = [rows[next]!, rows[index]!];
  rows.forEach((o, i) => (o.sortOrder = i));
  options.value = rows;
}
function remove(option: FieldOption) {
  if (published.value.has(option.id)) return;
  options.value = options.value.filter((o) => o.id !== option.id);
  options.value.forEach((o, i) => (o.sortOrder = i));
}
function error(option: FieldOption) {
  if (!props.showErrors && !touched.value.has(option.id)) return '';
  if (!option.name.trim()) return tr('ui.validation.message34');
  if (
    options.value.filter(
      (o) => o.name.trim().toLocaleLowerCase() === option.name.trim().toLocaleLowerCase(),
    ).length > 1
  )
    return '选项名称重复';
  return '';
}
</script>
<template>
  <div class="business-options-editor">
    <div class="business-options-heading">
      <strong>{{ tr('ui.equipment.text1277') }}</strong><span>{{ options.length }} / 200</span>
    </div>
    <p v-if="!options.length" :class="{ 'is-error': showErrors }">{{ tr('ui.equipment.text1278') }}</p>
    <div v-for="(option, index) in options" :key="option.id" class="business-option-row">
      <ElFormItem :error="error(option)" class="business-option-name"
        ><ElInput
          v-model="option.name"
          maxlength="100"
          :placeholder="tr('ui.equipment.text1279')"
          :disabled="disabled"
          :aria-label="`选项${index + 1}名称`"
          @blur="touched.add(option.id)"
      /></ElFormItem>
      <div class="business-option-actions">
        <ElButton
          text
          :disabled="disabled || index === 0"
          :aria-label="`上移选项${index + 1}`"
          :title="tr('ui.equipment.text0382')"
          @click="move(index, -1)"
          ><AppIcon name="ArrowUp"
        /></ElButton>
        <ElButton
          text
          :disabled="disabled || index === options.length - 1"
          :aria-label="`下移选项${index + 1}`"
          :title="tr('ui.equipment.text0383')"
          @click="move(index, 1)"
          ><AppIcon name="ArrowDown"
        /></ElButton>
        <ElSwitch
          v-model="option.enabled"
          :disabled="disabled"
          :aria-label="`启用选项${index + 1}`"
          inline-prompt
          active-text="启用"
          inactive-text="停用"
        />
        <ElButton
          v-if="!published.has(option.id)"
          text
          type="danger"
          :disabled="disabled"
          :aria-label="`删除选项${index + 1}`"
          :title="tr('ui.equipment.text0146')"
          @click="remove(option)"
          ><AppIcon name="Close"
        /></ElButton>
      </div>
    </div>
    <ElButton
      v-if="!disabled"
      class="business-option-add"
      :disabled="options.length >= 200"
      @click="add"
      ><AppIcon name="Plus" />{{ tr('ui.equipment.text1280') }}</ElButton
    >
    <p v-if="published.size" class="business-options-note">{{ tr('ui.equipment.text1281') }}</p>
  </div>
</template>
<style scoped>
.business-options-editor {
  width: 100%;
  min-width: 0;
  font-size: 13px;
}
.business-options-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.business-options-heading span {
  font-size: 12px;
}
.business-option-row {
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.business-option-name {
  margin-bottom: 6px !important;
}
.business-option-name :deep(.el-form-item__content) {
  margin-left: 0 !important;
}
.business-option-actions {
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: flex-end;
}
.business-option-actions .el-button {
  margin: 0;
  padding: 4px;
  height: 24px;
  width: 24px;
}
.business-option-actions .el-switch {
  margin-left: auto;
  height: 24px;
}
.business-option-add {
  width: 100%;
}
.business-option-add .app-icon {
  margin-right: 6px;
}
.business-options-editor p {
  font-size: 12px;
  line-height: 1.7;
  margin: 8px 0;
}
.business-options-note {
  margin-bottom: 0;
}
.is-error {
  color: var(--el-color-danger);
}
</style>
