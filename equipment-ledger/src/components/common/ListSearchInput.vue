<script setup lang="ts">
import { ElInput, ElButton } from 'element-plus';
import AppIcon from './AppIcon.vue';
withDefaults(defineProps<{ modelValue: string; placeholder?: string }>(), { placeholder: '搜索' });
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
</script>
<template>
  <ElInput
    class="list-search-input"
    :model-value="modelValue"
    :placeholder="placeholder"
    :aria-label="placeholder"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #suffix
      ><ElButton text native-type="submit" class="list-search-submit" aria-label="查询" title="查询"
        ><AppIcon name="Search" /></ElButton
    ></template>
  </ElInput>
</template>
<style scoped>
.list-search-input {
  width: 320px;
  min-width: 160px;
  height: 34px;
}
.list-search-input :deep(.el-input__inner) {
  height: 32px;
  border: 0;
  box-shadow: none;
  padding: 0;
  background: transparent;
  font-size: 13px;
}
.list-search-submit {
  width: 26px;
  height: 26px;
  padding: 0;
  margin: 0;
}
@media (max-width: 700px) {
  .list-search-input {
    flex: 1 1 100%;
    width: 100%;
  }
}
</style>
