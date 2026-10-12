<script setup lang="ts">
import { ElButton } from 'element-plus';
defineProps<{ loading: boolean; error?: string }>();
defineEmits<{ retry: [] }>();
</script>

<template>
  <div class="app-load-state" :aria-busy="loading" role="status" aria-live="polite">
    <svg v-if="loading" class="loading-icon" viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
    </svg>
    <svg v-else viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v6m0 3v.1" />
    </svg>
    <strong>{{ loading ? '正在加载' : '暂时无法加载' }}</strong>
    <p>{{ loading ? '正在获取最新数据…' : error || '请重试或返回列表。' }}</p>
    <ElButton v-if="!loading" type="primary" plain @click="$emit('retry')">重新加载</ElButton>
  </div>
</template>

<style scoped>
.app-load-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 220px;
  padding: 24px;
  color: var(--text);
  font-size: 13px;
  text-align: center;
}
.app-load-state svg {
  width: 32px;
  height: 32px;
  fill: none;
  stroke: var(--brand);
  stroke-width: 1.6;
  stroke-linecap: round;
}
.app-load-state strong {
  font-size: 13px;
  font-weight: 600;
}
.app-load-state p {
  margin: 0;
  max-width: 560px;
  overflow-wrap: anywhere;
  line-height: 1.6;
}
.loading-icon {
  stroke-dasharray: 42 16;
  animation: load-spin 1s linear infinite;
}
@keyframes load-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .loading-icon {
    animation: none;
  }
}
</style>
