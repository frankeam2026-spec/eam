<script setup lang="ts">import { tr } from '../../i18n';

import { ElInput, ElSelect, ElOption, ElTreeSelect, ElDatePicker } from 'element-plus';
import BusinessReferencePicker from '../business/BusinessReferencePicker.vue';
import AppDecimalInput from './AppDecimalInput.vue';
import {
  filterTreeOptions,
  type FilterField,
  type FilterCondition,
} from '../../query/advancedFilters';
import { dateRangeShortcuts } from '../../query/dateRangeShortcuts';
withDefaults(defineProps<{ field: FilterField; teleported?: boolean }>(), { teleported: true });
const model = defineModel<FilterCondition>({ required: true });
</script>
<template>
  <div class="filter-value-input">
    <BusinessReferencePicker
      v-if="field.businessSource"
      :object="field.businessSource.object"
      :source="field.businessSource.source"
      :model-value="model.values"
      :labels="model.valueLabels"
      multiple
      inactive
      @update:model-value="model.values = Array.isArray($event) ? $event : []"
      @labels="model.valueLabels = $event"
    />
    <ElSelect
      :teleported="teleported"
      v-else-if="field.type === 'choice'"
      v-model="model.values"
      multiple
      filterable
      clearable
      collapse-tags
      collapse-tags-tooltip
      :max-collapse-tags="2"
      :aria-label="field.label"
      :placeholder="tr('ui.equipment.text1406')"
    >
      <ElOption
        v-for="value in field.options"
        :key="value"
        :label="field.optionLabels?.[value] ?? value"
        :value="value"
      />
    </ElSelect>
    <ElTreeSelect
      :teleported="teleported"
      v-else-if="field.type === 'tree'"
      v-model="model.selectedNodeIds"
      :data="filterTreeOptions(field)"
      node-key="id"
      :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
      multiple
      show-checkbox
      check-strictly
      filterable
      clearable
      collapse-tags
      collapse-tags-tooltip
      :max-collapse-tags="2"
      :render-after-expand="false"
      :aria-label="field.label"
      :placeholder="tr('ui.equipment.text1407')"
    />
    <ElDatePicker
      :teleported="teleported"
      v-else-if="field.type === 'date'"
      :model-value="
        model.values.length === 2 && model.values.every(Boolean) ? model.values : undefined
      "
      :type="field.dateOnly ? 'daterange' : 'datetimerange'"
      :value-format="field.dateOnly ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss'"
      :format="field.dateOnly ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm'"
      :default-time="[new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]"
      :shortcuts="dateRangeShortcuts"
      range-separator="至"
      start-placeholder="开始日期"
      end-placeholder="结束日期"
      :aria-label="`${field.label}范围`"
      unlink-panels
      clearable
      @update:model-value="model.values = $event ? [...$event] : []"
    />
    <div v-else-if="field.type === 'number'" class="filter-range">
      <AppDecimalInput
        v-model="model.values[0]"
        :aria-label="`${field.label}最小值`"
        :placeholder="tr('ui.equipment.text1292')"
      />
      <span>{{ tr('ui.equipment.text0883') }}</span>
      <AppDecimalInput
        v-model="model.values[1]"
        :aria-label="`${field.label}最大值`"
        :placeholder="tr('ui.equipment.text1293')"
      />
    </div>
    <ElInput
      v-else
      v-model="model.values[0]"
      clearable
      :maxlength="200"
      :aria-label="field.label"
      :placeholder="`包含${field.label}，不区分大小写`"
    />
  </div>
</template>
<style scoped>
.filter-value-input {
  width: 100%;
  min-width: 0;
}
.filter-value-input :deep(.el-select),
.filter-value-input :deep(.el-date-editor) {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}
.filter-range {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}
.filter-range :deep(.el-input) {
  min-width: 0;
  width: 100%;
}
</style>
