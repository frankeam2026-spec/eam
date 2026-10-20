<script setup lang="ts">
import { tr } from '../../i18n';

import { ElInput, ElButton } from 'element-plus';
import AppIcon from './AppIcon.vue';
withDefaults(
  defineProps<{
    modelValue?: string;
    placeholder?: string;
    title?: string;
    disabled?: boolean;
    clearable?: boolean;
  }>(),
  { modelValue: '', placeholder: '', title: '' },
);
const emit = defineEmits<{ open: []; clear: [] }>();
</script>
<template>
  <div class="dialog-choice-input">
    <ElInput
      :model-value="modelValue"
      :placeholder="placeholder || tr('ui.equipment.text0096')"
      :disabled="disabled"
      :title="modelValue || title || tr('ui.common.dynamic55')"
      :aria-label="title || tr('ui.common.dynamic55')"
      aria-haspopup="dialog"
      readonly
      @click="!disabled && emit('open')"
      @keydown.enter.prevent="!disabled && emit('open')"
      @keydown.space.prevent="!disabled && emit('open')"
    >
      <template #append
        ><ElButton
          :disabled="disabled"
          :aria-label="title || tr('ui.common.dynamic55')"
          :title="title || tr('ui.common.dynamic55')"
          aria-haspopup="dialog"
          native-type="button"
          @click.stop="emit('open')"
          ><AppIcon name="Plus" /></ElButton
      ></template>
    </ElInput>
    <ElButton
      v-if="clearable && modelValue && !disabled"
      text
      :aria-label="tr('ui.equipment.text1357')"
      @click="emit('clear')"
      >{{ tr('ui.equipment.text1358') }}</ElButton
    >
  </div>
</template>
<style scoped>
.dialog-choice-input {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  gap: 4px;
}
.dialog-choice-input > .el-input {
  flex: 1;
  min-width: 0;
}
.dialog-choice-input :deep(.el-input:not(.is-disabled) .el-input__wrapper),
.dialog-choice-input :deep(.el-input:not(.is-disabled) .el-input__inner) {
  cursor: pointer;
}
</style>
