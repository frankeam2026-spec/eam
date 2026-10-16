<script setup lang="ts">
import AppIcon from './AppIcon.vue';
defineProps<{ type: '集团' | '公司' | '部门' }>();
</script>
<template>
  <AppIcon class="organization-icon" :name="type === '部门' ? 'Warehouse' : 'Organization'" />
</template>
<style scoped>
.organization-icon {
  --app-icon-size: 18px;
  width: 18px;
  height: 18px;
  display: inline-flex;
  flex: none;
}
</style>
