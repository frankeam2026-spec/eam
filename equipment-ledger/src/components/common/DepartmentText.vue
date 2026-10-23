<script setup lang="ts">
import { computed, watch } from 'vue';
import { ElTooltip } from 'element-plus';
import { useOrganizationDisplayStore } from '../../stores/organizationDisplay';
import { useAuthStore } from '../../stores/auth';
import { formatOrganizationPath, organizationPath } from '../../utils/organizationPath';

type Node = { id: string; name: string; parentId?: string; type?: string };
const props = withDefaults(
  defineProps<{
    value?: string;
    departmentId?: string;
    nodes?: Node[];
    empty?: string;
    lookup?: boolean;
  }>(),
  { value: '', departmentId: '', empty: '', lookup: true },
);
const organizations = useOrganizationDisplayStore();
const auth = useAuthStore();
const path = computed(() => {
  const snapshot = formatOrganizationPath(props.value);
  if (snapshot.includes(' > ') || !props.lookup) return snapshot;
  const nodes = props.nodes?.length ? props.nodes : organizations.rows;
  if (props.departmentId && nodes.some((node) => node.id === props.departmentId))
    return organizationPath(props.departmentId, nodes);
  // A name is only enough for display when it identifies exactly one authorized department.
  const matches = nodes.filter((node) => node.type === '部门' && node.name === snapshot);
  return matches.length === 1 ? organizationPath(matches[0]!.id, nodes) : snapshot;
});
const leaf = computed(() => path.value.split(' > ').at(-1) || props.empty);
watch(
  () => [auth.user?.tenantId, auth.user?.id, props.value, props.departmentId],
  () => {
    if (
      props.lookup &&
      !formatOrganizationPath(props.value).includes(' > ') &&
      (props.value || props.departmentId) &&
      !props.nodes?.length
    )
      void organizations.load();
  },
  { immediate: true },
);
</script>

<template>
  <ElTooltip
    :content="path"
    :disabled="!path.includes(' > ')"
    :trigger="['hover', 'focus']"
    :show-after="200"
  >
    <span
      class="department-text"
      :tabindex="path.includes(' > ') ? 0 : undefined"
      :aria-label="path || empty"
      >{{ leaf }}</span
    >
  </ElTooltip>
</template>

<style scoped>
.department-text {
  display: inline-block;
  max-width: 100%;
  vertical-align: middle;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
