<script setup lang="ts">import { tr } from '../../i18n';

import AppIcon from './AppIcon.vue';
import { ref, watch } from 'vue';
const props = defineProps<{ fileId?: string; name?: string }>();
const failed = ref(false);
watch(
  () => props.fileId,
  () => {
    failed.value = false;
  },
);
</script>

<template>
  <span class="equipment-image" :title="name || tr('ui.equipment.text0132')">
    <img
      v-if="fileId && !failed"
      :src="`/api/v1/equipment/files/${fileId}/preview`"
      :alt="name || '设备图片'"
      loading="lazy"
      @error="failed = true"
    />
    <AppIcon name="Picture" v-else role="img" :aria-label="tr('ui.equipment.text1371')" />
  </span>
</template>

<style scoped>
.equipment-image {
  display: inline-flex;
  width: 40px;
  height: 40px;
  vertical-align: middle;
  border: 1px solid #e2e7ef;
  border-radius: 5px;
  overflow: hidden;
  background: #f6f8fc;
}
.equipment-image img,
.equipment-image .app-icon {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.equipment-image .app-icon {
  --app-icon-size: 26px;
  color: var(--brand, #205cf5);
}
</style>
