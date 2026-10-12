<script setup lang="ts">
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
  <span class="equipment-image" :title="name || '设备图片'">
    <img
      v-if="fileId && !failed"
      :src="`/api/v1/equipment/files/${fileId}/preview`"
      :alt="name || '设备图片'"
      loading="lazy"
      @error="failed = true"
    />
    <svg v-else viewBox="0 0 48 48" role="img" aria-label="暂无设备图片">
      <rect
        x="9"
        y="11"
        width="30"
        height="26"
        rx="5"
        fill="#edf3ff"
        stroke="#b7c9ed"
        stroke-width="1.5"
      />
      <circle cx="19" cy="20" r="3" fill="#87a8e5" />
      <path
        d="m11 33 9-8 6 5 7-9 4 5v6a3 3 0 0 1-3 3H14a3 3 0 0 1-3-2Z"
        fill="#b7cef4"
        stroke="#8fafe5"
        stroke-width="1.2"
        stroke-linejoin="round"
      />
    </svg>
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
.equipment-image svg {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
</style>
