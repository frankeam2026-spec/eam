<script setup lang="ts">import { tr } from '../i18n';

import { computed } from 'vue';
import { ElTreeSelect } from 'element-plus';
export type TreeSelectNode = {
  id: string;
  name: string;
  parentId: string;
  status?: string;
  value?: string;
  disabled?: boolean;
};
const props = withDefaults(
  defineProps<{
    modelValue?: string;
    nodes: TreeSelectNode[];
    placeholder?: string;
    pathValue?: boolean;
    showFullPath?: boolean;
    showFullPathInDropdown?: boolean;
    disabled?: boolean;
  }>(),
  {
    modelValue: '',
    get placeholder() { return tr('ui.equipment.text0096'); },
    pathValue: false,
    showFullPath: false,
    showFullPathInDropdown: false,
    disabled: false,
  },
);
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
type Option = {
  id: string;
  label: string;
  path: string;
  displayPath: string;
  output: string;
  disabled?: boolean;
  children: Option[];
};
const options = computed(() => {
  const nodes = props.nodes.filter((node) => node.status !== '停用');
  const ids = new Set(nodes.map((node) => node.id));
  function branch(parent: string, path: string[], visited: Set<string>): Option[] {
    return nodes
      .filter(
        (node) => node.parentId === parent || (!parent && node.parentId && !ids.has(node.parentId)),
      )
      .filter((node) => !visited.has(node.id))
      .map((node) => {
        const names = [...path, node.name],
          next = new Set([...visited, node.id]);
        return {
          id: node.id,
          label: node.name,
          disabled: node.disabled,
          path: names.join(' · '),
          displayPath: names.join(' > '),
          output: node.value ?? (props.pathValue ? names.join(' · ') : node.name),
          children: branch(node.id, names, next),
        };
      });
  }
  return branch('', [], new Set());
});
const flatOptions = computed(() => {
  const result: Option[] = [];
  const add = (nodes: Option[]) =>
    nodes.forEach((node) => {
      result.push(node);
      add(node.children);
    });
  add(options.value);
  return result;
});
const selectedId = computed(
  () => flatOptions.value.find((node) => node.output === props.modelValue)?.id || undefined,
);
function change(id: string | undefined) {
  emit('update:modelValue', flatOptions.value.find((node) => node.id === id)?.output || '');
}
</script>
<template>
  <ElTreeSelect
    class="tree-select"
    :model-value="selectedId"
    :data="options"
    :props="{
      label: showFullPath ? 'displayPath' : pathValue ? 'path' : 'label',
      children: 'children',
      disabled: 'disabled',
    }"
    node-key="id"
    :title="
      showFullPath ? flatOptions.find((node) => node.id === selectedId)?.displayPath : undefined
    "
    :placeholder="placeholder"
    :disabled="disabled"
    filterable
    clearable
    check-strictly
    :render-after-expand="false"
    :teleported="true"
    popper-class="eam-tree-select-popper"
    @update:model-value="change"
  >
    <template #default="{ data }">
      <span :title="showFullPathInDropdown ? data.displayPath : undefined">
        {{ showFullPathInDropdown ? data.displayPath : pathValue ? data.path : data.label }}
      </span>
    </template>
  </ElTreeSelect>
</template>
<style scoped>
.tree-select {
  width: 100%;
  min-width: 0;
}
/* Select owns the border; its search input must not inherit native form input styling. */
.tree-select :deep(.el-select__input),
.tree-select :deep(.el-select__input:focus) {
  border: 0;
  border-radius: 0;
  box-shadow: none;
  outline: none;
  background: transparent;
  padding: 0;
  height: 24px;
  line-height: 24px;
  font-size: 13px;
}
</style>
