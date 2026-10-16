<script setup lang="ts">
import 'element-plus/es/components/tabs/style/css';
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { ElTree, ElButton, ElTabs, ElTabPane } from 'element-plus';
import AppIcon from './AppIcon.vue';
import { request } from '../../api';
import { locationPath } from '../../utils/locationPath';
type Node = {
  id: string;
  name: string;
  parentId?: string;
  type?: string;
  companyId?: string;
  children?: Node[];
};
export type EquipmentTreeSelection = {
  type: string;
  id: string;
  names: string[];
  ids: string[];
  companyId?: string;
};
const props = defineProps<{
  categories: Node[];
  locations: Node[];
  selection?: EquipmentTreeSelection | null;
}>();
const emit = defineEmits<{ select: [EquipmentTreeSelection | null] }>();
const organizations = ref<Node[]>([]),
  mode = ref('organization'),
  hidden = ref(true),
  expanded = ref(true),
  error = ref('');
const tree = ref<InstanceType<typeof ElTree>>();
const nodes = computed(() => {
  const flat =
    mode.value === 'organization'
      ? organizations.value
      : mode.value === 'location'
        ? props.locations
        : props.categories;
  const mapped = new Map(
    flat.map((n) => [String(n.id), { ...n, id: String(n.id), children: [] as Node[] }]),
  );
  const roots: Node[] = [];
  for (const n of mapped.values()) {
    const parent = mapped.get(String(n.parentId || ''));
    if (parent && parent !== n) parent.children.push(n);
    else roots.push(n);
  }
  return roots;
});
watch(
  () => [props.selection, hidden.value, nodes.value],
  async () => {
    await nextTick();
    tree.value?.setCurrentKey(props.selection?.id);
  },
  { flush: 'post' },
);
async function revealSelection() {
  if (!props.selection) return;
  mode.value = props.selection.type;
  hidden.value = false;
  await nextTick();
  tree.value?.setCurrentKey(props.selection.id);
  let node = tree.value?.getNode(props.selection.id);
  while (node?.parent) {
    node.parent.expanded = true;
    node = node.parent;
  }
  await nextTick();
  tree.value?.$el.querySelector('.is-current')?.scrollIntoView({ block: 'nearest' });
}
defineExpose({ revealSelection });
function choose(n: Node) {
  const ids: string[] = [],
    names: string[] = [];
  function visit(x: Node) {
    ids.push(x.id);
    names.push(x.name);
    if (mode.value === 'location') names.push(locationPath(x, props.locations));
    x.children?.forEach(visit);
  }
  visit(n);
  emit('select', { type: mode.value, id: n.id, ids, names, companyId: n.companyId });
}
function switchMode(value: string) {
  mode.value = value;
  expanded.value = true;
  emit('select', null);
}
function expand() {
  expanded.value = !expanded.value;
  const store = tree.value?.store;
  if (store)
    Object.values(store.nodesMap).forEach((n: any) => {
      n.expanded = expanded.value;
    });
}
onMounted(async () => {
  try {
    organizations.value = (await request<{ data: Node[] }>('/equipment/navigation-tree')).data;
  } catch (e) {
    error.value = e instanceof Error ? e.message : '组织加载失败';
  }
});
</script>
<template>
  <aside class="equipment-navigation" :class="{ collapsed: hidden }">
    <ElButton
      class="tree-collapse"
      :title="hidden ? '展开筛选树' : '收起筛选树'"
      @click="hidden = !hidden"
    >
      <AppIcon :name="hidden ? 'Right' : 'Left'" />
    </ElButton>
    <template v-if="!hidden">
      <ElTabs class="tree-tabs" :model-value="mode" @tab-change="switchMode(String($event))">
        <ElTabPane
          v-for="(name, key) in { organization: '公司部门', location: '位置', category: '类别' }"
          :key="key"
          :label="name"
          :name="key"
        />
      </ElTabs>
      <div class="tree-tools">
        <ElButton
          @click="
            emit('select', null);
            tree?.setCurrentKey(undefined);
          "
        >
          全部</ElButton
        ><ElButton @click="expand">{{ expanded ? '全部收起' : '全部展开' }}</ElButton>
      </div>
      <p v-if="error && mode === 'organization'" class="tree-error">{{ error }}</p>
      <ElTree
        v-else
        :key="mode"
        ref="tree"
        :data="nodes"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        default-expand-all
        highlight-current
        :expand-on-click-node="false"
        @node-click="choose"
        ><template #default="{ data }"
          ><span class="tree-label"
            ><AppIcon
              :name="
                mode === 'category'
                  ? 'Grid'
                  : mode === 'location'
                    ? 'Location'
                    : data.type === '部门'
                      ? 'Warehouse'
                      : 'Organization'
              "
            /><span :title="data.name">{{ data.name }}</span></span
          ></template
        ></ElTree
      >
    </template>
  </aside>
</template>
<style scoped>
.equipment-navigation {
  position: relative;
  width: 232px;
  min-width: 232px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 6px;
  overflow: visible;
  display: flex;
  flex-direction: column;
  margin: 0;
  min-height: 0;
  align-self: stretch;
}
.equipment-navigation.collapsed {
  width: 0;
  min-width: 0;
  border: 0;
  background: transparent;
}
.tree-collapse {
  position: absolute;
  right: -11px;
  top: 48%;
  z-index: 3;
  width: 22px;
  height: 44px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  color: var(--brand);
  cursor: pointer;
  display: grid;
  place-items: center;
}
.tree-tabs {
  flex: none;
  padding: 6px 10px 0;
}
.tree-tabs :deep(.el-tabs__header) {
  margin: 0;
}
.tree-tabs :deep(.el-tabs__item) {
  font-size: 13px;
  height: 34px;
  padding: 0 8px;
}
.tree-collapse {
  padding: 0;
  margin: 0;
}
.tree-tools :deep(.el-button) {
  height: 28px;
  padding: 0;
  font-size: 13px;
}
.tree-tools {
  display: flex;
  justify-content: space-between;
  padding: 12px;
}
.tree-tools button {
  border: 0;
  background: transparent;
  color: var(--brand);
  font: inherit;
  cursor: pointer;
}
.equipment-navigation :deep(.el-tree) {
  flex: 1;
  overflow: auto;
  padding: 0 8px 12px;
  font-size: 13px;
}
.equipment-navigation :deep(.el-tree-node__content) {
  height: 34px;
  border-radius: 4px;
}
.tree-label {
  display: flex;
  gap: 7px;
  align-items: center;
  min-width: 0;
}
.tree-label > .app-icon {
  color: var(--brand);
  flex-shrink: 0;
}
.tree-label > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tree-error {
  padding: 12px;
  color: #c53a45;
}
</style>
