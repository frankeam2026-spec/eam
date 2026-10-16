<script setup lang="ts">
import { ElTableColumn } from 'element-plus';
import AppBusinessTable from './AppBusinessTable.vue';
import AppDecimalInput from './AppDecimalInput.vue';
import { formatNumber } from '../../utils/number';
import { ElInput, ElSelect, ElOption, ElSwitch, ElTag } from 'element-plus';
import type { RepairAttachment } from '../../api';
import type { MaintenanceItem, ItemResult } from '../../maintenance';
import MaintenanceAttachments from './MaintenanceAttachments.vue';

const props = withDefaults(
  defineProps<{
    items: MaintenanceItem[];
    modelValue: ItemResult[];
    readonly?: boolean;
    hideModule?: boolean;
    compact?: boolean;
    label?: string;
    uploadFile?: (file: File) => Promise<RepairAttachment>;
  }>(),
  { readonly: false, hideModule: false, compact: false, label: '保养' },
);
const emit = defineEmits<{ 'update:modelValue': [ItemResult[]] }>();
function result(id: string): ItemResult {
  return (
    props.modelValue.find((r) => r.id === id) || {
      id,
      value: '',
      resolved: false,
      measure: '',
      remark: '',
      images: [],
    }
  );
}
function update(id: string, patch: Partial<ItemResult>) {
  if (props.readonly) return;
  const next = { ...result(id), ...patch };
  emit(
    'update:modelValue',
    props.items.map((item) => (item.id === id ? next : result(item.id))),
  );
}
function abnormal(item: MaintenanceItem) {
  const r = result(item.id);
  if (!r.value.trim()) return false;
  if (props.readonly && typeof r.abnormal === 'boolean') return r.abnormal;
  if (item.valueType === '选项') return !item.normalOptions.includes(r.value);
  if (item.valueType === '数值') {
    const n = Number(r.value);
    return (
      !Number.isFinite(n) ||
      (item.lower !== '' && n < Number(item.lower)) ||
      (item.upper !== '' && n > Number(item.upper))
    );
  }
  return false;
}
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => props.items;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];
</script>

<template>
  <div class="task-project-table-scroll">
    <AppBusinessTable
      :rows="items"
      :pagination="false"
      :fill="false"
      :framed="false"
      class="business-table task-project-table"
      ><!-- @vue-generic {TableColumnRow1} -->
      <ElTableColumn :min-width="55" :width="52" class-name="fixed-sequence"
        ><template #header><div class="table-header-label">序号</div></template
        ><template #default="{ row: item, $index: index }">{{ index + 1 }}</template></ElTableColumn
      ><!-- @vue-generic {TableColumnRow1} -->
      <ElTableColumn v-if="!hideModule" :min-width="150" :width="130" class-name="fixed-module"
        ><template #header><div class="table-header-label">模块</div></template
        ><template #default="{ row: item, $index: index }">{{
          item.module || ''
        }}</template></ElTableColumn
      ><!-- @vue-generic {TableColumnRow1} -->
      <ElTableColumn :min-width="210" :width="190" class-name="fixed-name"
        ><template #header><div class="table-header-label">项目名称</div></template
        ><template #default="{ row: item, $index: index }">
          <strong>{{ item.name }}</strong>
        </template></ElTableColumn
      ><!-- @vue-generic {TableColumnRow1} -->
      <ElTableColumn :min-width="210" :width="260" class-name="long-text"
        ><template #header
          ><div class="table-header-label">
            {{ label === '保养' ? '保养内容' : '方法及基准' }}
          </div></template
        ><template #default="{ row: item, $index: index }">
          {{ item.content || '' }}
          <details
            v-if="compact && (item.remark || item.references?.length)"
            class="check-reference"
          >
            <summary>参考资料</summary>
            <p v-if="item.remark">{{ item.remark }}</p>
            <MaintenanceAttachments :model-value="item.references || []" readonly />
          </details> </template></ElTableColumn
      ><!-- @vue-generic {TableColumnRow1} -->
      <ElTableColumn :min-width="150" :width="310"
        ><template #header
          ><div class="table-header-label">{{ label }}结果</div></template
        ><template #default="{ row: item, $index: index }">
          <div class="result-value">
            <span v-if="item.required && !readonly" class="required-mark" title="必填">*</span>
            <template v-if="readonly"
              ><span>{{
                result(item.id).skipped
                  ? '已跳过'
                  : (item.valueType === '数值'
                      ? formatNumber(result(item.id).value)
                      : result(item.id).value) ||
                    (result(item.id).implicitNormal ? '正常（仅异常填报）' : '')
              }}</span></template
            >
            <ElSelect
              v-else-if="item.valueType === '选项'"
              :model-value="result(item.id).value"
              placeholder="选择结果"
              @update:model-value="update(item.id, { value: String($event) })"
            >
              <ElOption
                v-for="option in item.options"
                :key="option"
                :label="option"
                :value="option"
              />
            </ElSelect>
            <AppDecimalInput
              v-else-if="item.valueType === '数值'"
              :model-value="result(item.id).value"
              placeholder="输入测量值"
              @update:model-value="update(item.id, { value: $event })"
            />
            <ElInput
              v-else
              :model-value="result(item.id).value"
              type="text"
              placeholder="填写结果"
              @update:model-value="update(item.id, { value: $event })"
            />
            <span v-if="item.valueType === '数值'" class="numeric-interval"
              >{{ formatNumber(item.lower) || '−∞' }} ～
              {{ formatNumber(item.upper) || '+∞' }}</span
            >
          </div>
          <small
            v-if="item.valueType === '选项' && item.normalOptions.length"
            class="normal-options"
            >正常选项：{{ item.normalOptions.join('、') }}</small
          >
        </template></ElTableColumn
      ><!-- @vue-generic {TableColumnRow1} -->
      <ElTableColumn :min-width="150" :width="92"
        ><template #header><div class="table-header-label">判定</div></template
        ><template #default="{ row: item, $index: index }">
          <ElTag
            :type="
              !result(item.id).value && !result(item.id).implicitNormal
                ? 'info'
                : abnormal(item)
                  ? 'danger'
                  : 'success'
            "
            size="small"
            >{{
              result(item.id).skipped
                ? '已跳过'
                : !result(item.id).value && !result(item.id).implicitNormal
                  ? '未填写'
                  : abnormal(item)
                    ? '异常'
                    : '正常'
            }}</ElTag
          >
        </template></ElTableColumn
      ><!-- @vue-generic {TableColumnRow1} -->
      <ElTableColumn v-if="!compact" :min-width="150" :width="110"
        ><template #header><div class="table-header-label">异常已处理</div></template
        ><template #default="{ row: item, $index: index }">
          <template v-if="readonly">{{
            abnormal(item) ? (result(item.id).resolved ? '是' : '否') : ''
          }}</template>
          <ElSwitch
            v-else-if="abnormal(item)"
            :model-value="result(item.id).resolved"
            :aria-label="item.name + '异常已处理'"
            @update:model-value="update(item.id, { resolved: Boolean($event) })"
          />
          <span v-else class="muted"></span> </template></ElTableColumn
      ><!-- @vue-generic {TableColumnRow1} -->
      <ElTableColumn :min-width="150" :width="240" class-name="long-text"
        ><template #header
          ><div class="table-header-label">
            {{ compact ? '异常处理 / 说明' : '处理措施' }}
          </div></template
        ><template #default="{ row: item, $index: index }">
          <div v-if="compact && abnormal(item)" class="check-resolution">
            <span>异常已处理</span
            ><span v-if="readonly">{{ result(item.id).resolved ? '是' : '否' }}</span
            ><ElSwitch
              v-else
              :model-value="result(item.id).resolved"
              @update:model-value="update(item.id, { resolved: Boolean($event) })"
            />
          </div>
          <template v-if="readonly">{{ result(item.id).measure || '' }}</template>
          <div v-else-if="abnormal(item)" class="result-value">
            <span v-if="result(item.id).resolved" class="required-mark">*</span
            ><ElInput
              :model-value="result(item.id).measure"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 4 }"
              placeholder="填写处理措施"
              @update:model-value="update(item.id, { measure: $event })"
            />
          </div>
          <span v-else class="muted"></span>
          <p v-if="compact && readonly && result(item.id).remark" class="check-note">
            {{ result(item.id).remark }}
          </p>
          <ElInput
            v-if="compact && !readonly"
            :model-value="result(item.id).remark"
            class="check-note"
            placeholder="检查说明（选填）"
            @update:model-value="update(item.id, { remark: $event })"
          /> </template></ElTableColumn
      ><!-- @vue-generic {TableColumnRow1} -->
      <ElTableColumn v-if="!compact" :min-width="210" :width="220" class-name="long-text"
        ><template #header><div class="table-header-label">项目说明</div></template
        ><template #default="{ row: item, $index: index }">
          <template v-if="readonly">{{ result(item.id).remark || '' }}</template>
          <ElInput
            v-else
            :model-value="result(item.id).remark"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="填写说明"
            @update:model-value="update(item.id, { remark: $event })"
          /> </template></ElTableColumn
      ><!-- @vue-generic {TableColumnRow1} -->
      <ElTableColumn :min-width="150" :width="210"
        ><template #header
          ><div class="table-header-label">{{ label }}图片</div></template
        ><template #default="{ row: item, $index: index }">
          <span v-if="item.photoRequired && !readonly" class="required-mark">*</span
          ><MaintenanceAttachments
            :model-value="result(item.id).images || []"
            :readonly="readonly"
            images-only
            :upload-file="uploadFile"
            @update:model-value="update(item.id, { images: $event })"
          /> </template></ElTableColumn
      ><!-- @vue-generic {TableColumnRow1} -->
      <ElTableColumn v-if="!compact" :min-width="150" :width="220" class-name="long-text"
        ><template #header><div class="table-header-label">参考说明 / 附件</div></template
        ><template #default="{ row: item, $index: index }">
          <p v-if="item.remark" class="reference-note">{{ item.remark }}</p>
          <MaintenanceAttachments
            :model-value="item.references || []"
            readonly
          /> </template></ElTableColumn
      ><template #empty
        ><div v-if="!items.length">暂无{{ label }}项目</div></template
      ></AppBusinessTable
    >
  </div>
</template>

<style scoped>
.task-project-table-scroll {
  max-width: 100%;
  overflow: auto;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
}
.task-project-table {
  width: 2034px;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
  color: #263246;
}
.task-project-table :deep(th),
.task-project-table :deep(td) {
  vertical-align: middle;
}
.task-project-table :deep(tbody tr:last-child td) {
  border-bottom: 0;
}
.fixed-sequence,
.fixed-module,
.fixed-name {
  position: sticky;
  z-index: 1;
}
.fixed-sequence {
  left: 0;
  text-align: center !important;
}
.fixed-module {
  left: 52px;
}
.fixed-name {
  left: 182px;
  box-shadow: 1px 0 0 #e3e8f0;
}
.task-project-table :deep(th.fixed-sequence),
.task-project-table :deep(th.fixed-module),
.task-project-table :deep(th.fixed-name) {
  z-index: 2;
}
.fixed-name strong {
  font-weight: 600;
  color: #344156;
}
.long-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.6;
}
.result-value {
  display: flex;
  align-items: center;
  gap: 8px;
}
.result-value .el-input,
.result-value .el-select,
.result-value .el-textarea {
  flex: 1;
  min-width: 0;
}
.numeric-interval {
  flex: none;
  color: var(--text-secondary);
  white-space: nowrap;
}
.required-mark {
  color: #e35166;
}
.normal-options {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
  margin-top: 6px;
}
.reference-note {
  margin: 0 0 6px;
}
.muted,
.empty {
  color: var(--text-secondary);
}
.empty {
  height: 100px !important;
  text-align: center;
}
</style>

<style scoped>
.check-reference {
  margin-top: 8px;
  font-size: 13px;
}
.check-reference summary {
  color: var(--brand);
  cursor: pointer;
}
.check-resolution {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.check-note {
  margin-top: 8px;
}
</style>
