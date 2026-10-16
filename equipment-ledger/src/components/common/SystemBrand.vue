<script setup lang="ts">
import { ref, watch } from 'vue';
import { defaultBranding } from '../../branding';
const props = defineProps<{ name: string; logoUrl: string }>();
const failed = ref(false);
watch(
  () => props.logoUrl,
  () => {
    failed.value = false;
  },
);
</script>
<template>
  <div class="system-brand" :title="name">
    <img
      class="brand-logo"
      :src="failed ? defaultBranding.logoUrl : logoUrl"
      alt="系统Logo"
      @error="failed = true"
    /><strong>{{ name }}</strong>
  </div>
</template>
<style scoped>
.system-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.brand-logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
  flex-shrink: 0;
  border-radius: 6px;
}
strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 17px;
  font-weight: 650;
}
</style>
