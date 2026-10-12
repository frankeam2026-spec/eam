<template>
  <footer class="page-form-footer">
    <div class="page-form-hint"><slot name="hint" /></div>
    <div class="page-form-actions"><slot /></div>
  </footer>
</template>

<style scoped>
.page-form-footer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  flex-shrink: 0;
  min-height: 60px;
  padding: 10px 20px;
  background: #fff;
  border-top: 1px solid #e4e9f0;
  z-index: 2;
}
.page-form-hint {
  min-width: 0;
  padding-right: 16px;
  font-size: 13px;
  color: var(--text-secondary);
}
.page-form-actions {
  grid-column: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
.page-form-actions :deep(button) {
  min-width: 100px;
  height: 36px;
  font-size: 13px;
}
.page-form-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
@media (max-width: 600px) {
  .page-form-footer {
    grid-template-columns: 1fr;
  }
  .page-form-hint {
    display: none;
  }
  .page-form-actions {
    grid-column: 1;
  }
}
</style>
