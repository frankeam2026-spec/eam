<script setup lang="ts">
import { computed } from 'vue';
import { ElTree, ElInput } from 'element-plus';
import { ref } from 'vue';
import OrganizationIcon from './OrganizationIcon.vue';
type Org = { id: string; name: string; parentId?: string; type?: string };
const props = defineProps<{
  modelValue: string;
  organizations: Org[];
  records: { companyId?: string; departmentId?: string }[];
}>();
const emit = defineEmits<{ 'update:modelValue': [id: string] }>();
const search = ref('');
const nodes = computed(() => {
  const keep = new Set<string>();
  const byId = new Map(props.organizations.map((x) => [x.id, x]));
  for (const record of props.records)
    for (const id of [record.companyId, record.departmentId]) {
      let node = id ? byId.get(id) : undefined;
      const seen = new Set<string>();
      while (node && !seen.has(node.id)) {
        keep.add(node.id);
        seen.add(node.id);
        node = node.parentId ? byId.get(node.parentId) : undefined;
      }
    }
  if (search.value) {
    const matches = new Set<string>();
    for (const node of props.organizations.filter(
      (x) => keep.has(x.id) && x.name.includes(search.value),
    )) {
      let current: Org | undefined = node;
      const seen = new Set<string>();
      while (current && !seen.has(current.id)) {
        matches.add(current.id);
        seen.add(current.id);
        current = current.parentId ? byId.get(current.parentId) : undefined;
      }
    }
    for (const id of keep) if (!matches.has(id)) keep.delete(id);
  }
  type Node = Org & { children: Node[] };
  const map = new Map<string, Node>(
    props.organizations.filter((x) => keep.has(x.id)).map((x) => [x.id, { ...x, children: [] }]),
  );
  const result: Node[] = [];
  for (const node of map.values()) {
    const parent = node.parentId ? map.get(node.parentId) : undefined;
    if (parent) parent.children.push(node);
    else result.push(node);
  }
  return result;
});
</script>
<template>
  <aside class="task-organizations">
    <header>
      <strong>公司与部门</strong><button @click="emit('update:modelValue', '')">全部</button>
    </header>
    <ElInput v-model="search" placeholder="搜索公司、部门" clearable /><ElTree
      :data="nodes"
      node-key="id"
      :props="{ label: 'name', children: 'children' }"
      :current-node-key="modelValue"
      highlight-current
      default-expand-all
      :expand-on-click-node="false"
      @node-click="(node: Org) => emit('update:modelValue', node.id)"
      ><template #default="{ data }"
        ><span class="org-node"
          ><OrganizationIcon
            :type="data.type === '集团' ? '集团' : data.type === '公司' ? '公司' : '部门'"
          /><span>{{ data.name }}</span></span
        ></template
      ></ElTree
    >
    <p v-if="!nodes.length">暂无可查看的任务组织</p>
  </aside>
</template>
<style scoped>
.task-organizations {
  width: 218px;
  min-width: 218px;
  border: 1px solid var(--border-color, #e0e6ef);
  border-radius: 6px;
  background: var(--surface, #fff);
  padding: 12px;
  overflow: auto;
  min-height: 0;
  box-sizing: border-box;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
header button {
  border: 0;
  background: transparent;
  color: var(--brand, #205cf5);
  cursor: pointer;
}
.org-node {
  display: flex;
  gap: 7px;
  align-items: center;
  min-width: 0;
}
.org-node svg {
  color: var(--brand, #205cf5);
}
.org-node span {
  white-space: normal;
  line-height: 19px;
}
.task-organizations :deep(.el-tree) {
  margin-top: 10px;
  background: transparent;
}
.task-organizations :deep(.el-tree-node__content) {
  height: auto;
  min-height: 34px;
  padding: 4px 0;
}
.task-organizations p {
  padding-top: 20px;
}
</style>
