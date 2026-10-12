<script setup lang="ts">
withDefaults(defineProps<{ modelValue: string; placeholder?: string }>(), { placeholder: '搜索' });
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
</script>
<template>
  <div class="list-search-input">
    <input
      :value="modelValue"
      :placeholder="placeholder"
      :aria-label="placeholder"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
    <button class="list-search-submit" type="submit" aria-label="查询" title="查询">
      <svg
        viewBox="0 0 24 24"
        width="17"
        height="17"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        aria-hidden="true"
      >
        <circle cx="10.5" cy="10.5" r="6.5" />
        <path d="m15.5 15.5 4.5 4.5" />
      </svg>
    </button>
  </div>
</template>
<style scoped>
.list-search-input {
  display: flex;
  align-items: center;
  width: 320px;
  min-width: 160px;
  height: 34px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-bg-color);
  box-sizing: border-box;
}
.list-search-input:focus-within {
  border-color: var(--el-color-primary);
}
.list-search-input input {
  min-width: 0;
  width: 100%;
  height: 100%;
  padding: 0 10px;
  border: 0;
  outline: none;
  border-radius: 4px;
  background: transparent;
  color: var(--el-text-color-regular);
  font: inherit;
  font-size: 13px;
  box-sizing: border-box;
}
.list-search-input .list-search-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 34px;
  height: 32px;
  padding: 0;
  margin: 0;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}
.list-search-input .list-search-submit:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.list-search-input .list-search-submit:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}
@media (max-width: 700px) {
  .list-search-input {
    flex: 1 1 100%;
    width: 100%;
  }
}
</style>
