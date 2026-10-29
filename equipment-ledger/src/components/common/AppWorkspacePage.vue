<template>
  <div class="app-workspace-page"><slot /></div>
</template>

<style scoped>
.app-workspace-page {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  padding: var(--page-inset);
  overflow: hidden;
}
.app-workspace-page > :deep(*) {
  flex: 1;
  min-width: 0;
  min-height: 0;
}
</style>
