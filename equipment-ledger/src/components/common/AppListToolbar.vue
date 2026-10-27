<template>
  <div class="app-list-toolbar"><slot /></div>
</template>
<style scoped>
.app-list-toolbar {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 8px 12px;
  /* margin-top: 8px; */
  margin-bottom: 8px;
  min-height: 50px;
  background: var(--el-bg-color);
  border-radius: 6px;
}
.app-list-toolbar :deep(form) {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}
.app-list-toolbar :deep(form > .el-input) {
  width: 320px;
  max-width: 100%;
}
.app-list-toolbar :deep(.el-button) {
  height: 34px;
  font-size: 13px;
}
.app-list-toolbar :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
