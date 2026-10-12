<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{ status: string }>();
const colors: Record<string, [string, string]> = {
  待派工: ['#ae7100', '#fff5de'],
  待接单: ['#7953b3', '#f1eaff'],
  处理中: ['#205cf5', '#eaf1ff'],
  已撤销: ['#bb5663', '#fff0f1'],
  未开始: ['#a83999', '#fceafa'],
  待审核: ['#087b91', '#e9f8fb'],
  已审核: ['#16845b', '#eaf7f1'],
  审核不通过: ['#c74444', '#fff0f0'],
  已过期: ['#955200', '#fff0de'],
  待执行: ['#ae7100', '#fff5de'],
  执行中: ['#205cf5', '#eaf1ff'],
  待验收: ['#087b91', '#e9f8fb'],
  已完成: ['#16845b', '#eaf7f1'],
  挂起: ['#7953b3', '#f1eaff'],
  已作废: ['#727e90', '#f1f2f4'],
  过期作废: ['#bb5663', '#fff0f1'],
};
const style = computed(() => {
  const [color, background] = colors[props.status] || ['#727e90', '#f1f2f4'];
  return { color, background };
});
</script>

<template>
  <span class="maintenance-task-status" :style="style">{{ status }}</span>
</template>

<style scoped>
.maintenance-task-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  height: 26px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  vertical-align: middle;
}
</style>
