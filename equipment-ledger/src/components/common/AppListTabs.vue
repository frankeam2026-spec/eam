<script setup lang="ts">
import { enumLabel } from '../../i18n';
import { ElTabs, ElTabPane } from 'element-plus';
import 'element-plus/es/components/tabs/style/css';
defineProps<{ active: string; items: { key: string; label: string; count?: number }[] }>();
const emit = defineEmits<{ select: [key: string]; reselect: [key: string] }>();
</script>
<template>
  <ElTabs
    class="app-list-tabs"
    :model-value="active"
    @tab-change="emit('select', String($event))"
    @tab-click="(tab) => tab.paneName === active && emit('reselect', String(tab.paneName))"
  >
    <ElTabPane v-for="item in items" :key="item.key" :name="item.key">
      <template #label
        >{{ enumLabel('tab',item.label)
        }}<span v-if="item.count !== undefined" class="list-tab-count">{{
          item.count
        }}</span></template
      >
    </ElTabPane>
  </ElTabs>
</template>
<style scoped>
.app-list-tabs {
  display: block;
  flex: none;
  min-width: 0;
  margin-bottom: 8px;
}
.app-list-tabs :deep(.el-tabs__header) {
  margin: 0;
}
.app-list-tabs :deep(.el-tabs__item) {
  height: 36px;
  font-size: 13px;
}
.list-tab-count {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
}
</style>
