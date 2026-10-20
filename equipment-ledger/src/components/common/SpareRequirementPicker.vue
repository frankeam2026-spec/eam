<script setup lang="ts">import { tr } from '../../i18n';

import { ref } from 'vue';
import { ElButton, ElInputNumber, ElSelect, ElOption, ElMessage } from 'element-plus';
import type { StockRow } from '../../api';
const props = defineProps<{
  modelValue: { stockId: string; quantity: number }[];
  stocks: StockRow[];
}>();
const emit = defineEmits<{ 'update:modelValue': [{ stockId: string; quantity: number }[]] }>();
const selected = ref(''),
  quantity = ref(1);
function add() {
  if (!selected.value) {
    ElMessage.warning(tr('ui.equipment.text1087'));
    return;
  }
  const list = [...props.modelValue];
  const existing = list.find((x) => x.stockId === selected.value);
  if (existing) existing.quantity += quantity.value;
  else list.push({ stockId: selected.value, quantity: quantity.value });
  emit('update:modelValue', list);
  selected.value = '';
  quantity.value = 1;
}
</script>
<template>
  <div class="spare-requirements">
    <div class="spare-requirements-controls">
      <ElSelect v-model="selected" filterable :placeholder="tr('ui.equipment.text1584')"
        ><ElOption
          v-for="s in stocks"
          :key="s.id"
          :label="s.name + ' · ' + s.code + ' · ' + s.model"
          :value="s.id" /></ElSelect
      ><ElInputNumber
        v-model="quantity"
        :min="0.001"
        :precision="3"
        :step="1"
        controls-position="right"
      /><ElButton @click="add">{{ tr('ui.equipment.text0774') }}</ElButton>
    </div>
    <div v-for="(r, index) in modelValue" :key="r.stockId" class="spare-requirement">
      <span
        >{{ stocks.find((s) => s.id === r.stockId)?.name || r.stockId }}
        <small>{{ stocks.find((s) => s.id === r.stockId)?.model }}</small></span
      ><ElInputNumber
        :model-value="r.quantity"
        :min="0.001"
        :precision="3"
        controls-position="right"
        @update:model-value="
          (v) =>
            emit(
              'update:modelValue',
              modelValue.map((x, n) => (n === index ? { ...x, quantity: v || 1 } : x)),
            )
        "
      /><ElButton
        size="small"
        text
        type="danger"
        @click="
          emit(
            'update:modelValue',
            modelValue.filter((_, i) => i !== index),
          )
        "
        >{{ tr('ui.equipment.text0151') }}</ElButton
      >
    </div>
  </div>
</template>
<style scoped>
.spare-requirements {
  display: grid;
  gap: 10px;
  min-width: 0;
}
.spare-requirements-controls,
.spare-requirement {
  display: flex;
  gap: 8px;
  align-items: center;
}
.spare-requirements-controls > .el-select {
  flex: 1;
  min-width: 180px;
}
.spare-requirement {
  padding: 8px 12px;
  background: #f8faff;
  border: 1px solid #e4ebf4;
  border-radius: 4px;
}
.spare-requirement > span {
  flex: 1;
}
.spare-requirement small {
  color: #94a3b8;
  margin-left: 8px;
}
.spare-requirements :deep(.el-input-number) {
  width: 120px;
  font-size: 13px;
}
</style>
