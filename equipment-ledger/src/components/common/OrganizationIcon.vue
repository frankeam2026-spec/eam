<script setup lang="ts">
defineProps<{ type: '集团' | '公司' | '部门' }>();
</script>
<template>
  <svg
    class="organization-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="type === '公司' || type === '集团'">
      <rect x="8" y="2" width="8" height="8" rx="1" />
      <rect x="2" y="14" width="8" height="8" rx="1" />
      <rect x="14" y="14" width="8" height="8" rx="1" />
      <path d="M12 10v2M6 14v-2h12v2" />
    </template>
    <template v-else>
      <path d="M5 21V3h14v18M3 21h18M10 21v-5h4v5M9 7h1M14 7h1M9 11h1M14 11h1" />
    </template>
  </svg>
</template>
<style scoped>
.organization-icon {
  width: 18px;
  height: 18px;
  display: block;
  flex: none;
}
</style>
