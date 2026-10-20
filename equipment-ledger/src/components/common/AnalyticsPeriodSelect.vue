<script setup lang="ts">import { tr } from '../../i18n';

import { computed } from 'vue';
import { ElSelect, ElOption, ElDatePicker, ElFormItem } from 'element-plus';
import {
  periodOptions,
  widgetPeriodOptions,
  periodError,
  type AnalyticsPeriod,
} from '../../analytics';
const model = defineModel<AnalyticsPeriod>({ required: true });
defineProps<{ independent?: boolean; disabled?: boolean }>();
const range = computed({
  get: () => (model.value.from && model.value.to ? [model.value.from, model.value.to] : []),
  set: (v: string[] | null) => {
    model.value = { preset: 'custom', from: v?.[0] || '', to: v?.[1] || '' };
  },
});
</script>
<template>
  <ElFormItem class="period-validation" :error="periodError(model)"
    ><div class="analysis-period">
      <ElSelect
        :model-value="model.preset"
        :disabled="disabled"
        :aria-label="tr('ui.equipment.text1298')"
        @update:model-value="model = { preset: String($event) }"
        ><ElOption
          v-for="p in independent ? widgetPeriodOptions : periodOptions"
          :key="p.value"
          :value="p.value"
          :label="p.label" /></ElSelect
      ><ElDatePicker
        v-if="model.preset === 'custom'"
        v-model="range"
        :disabled="disabled"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        range-separator="至"
        :clearable="false"
        :unlink-panels="true"
      /></div
  ></ElFormItem>
</template>
<style scoped>
.period-validation {
  margin-bottom: 0;
  min-width: 150px;
  max-width: 100%;
}
.period-validation.is-error {
  padding-bottom: 18px;
}
.period-validation :deep(.el-form-item__content) {
  line-height: normal;
  min-width: 0;
  width: 100%;
  display: block;
}
.analysis-period {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}
.analysis-period > .el-select {
  min-width: 150px;
  flex: 1 0 150px;
  width: 150px;
}
.analysis-period :deep(.el-date-editor) {
  max-width: 100%;
  width: 255px;
  box-sizing: border-box;
}
</style>
