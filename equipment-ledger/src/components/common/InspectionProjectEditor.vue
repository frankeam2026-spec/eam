<script setup lang="ts">
import { ElTableColumn } from 'element-plus';
import AppBusinessTable from './AppBusinessTable.vue';
import AppIcon from './AppIcon.vue';
import AppDecimalInput from './AppDecimalInput.vue';
import { computed, ref } from 'vue';
import {
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElSwitch,
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElMessage,
} from 'element-plus';
import type { MaintenanceItem } from '../../maintenance';
import { emptyItem } from '../../maintenance';
import { inspectionUpload, type InspectionDefinition } from '../../inspection';
import MaintenanceAttachments from './MaintenanceAttachments.vue';
import AppDialog from './AppDialog.vue';
const items = defineModel<MaintenanceItem[]>({ required: true });
const props = defineProps<{ types: InspectionDefinition[] }>();
defineEmits<{ addType: [] }>();
const selected = ref<string[]>([]),
  batchOpen = ref(false),
  batch = ref(''),
  batchType = ref(''),
  values = ref<string[]>([]);
const all = computed(
  () => items.value.length > 0 && items.value.every((i) => selected.value.includes(i.id)),
);
function selectAll() {
  selected.value = all.value ? [] : items.value.map((i) => i.id);
}
function add() {
  items.value.push(emptyItem(props.types[0]?.id || ''));
}
function openBatch(command: string) {
  if (command === 'delete') {
    items.value = items.value.filter((i) => !selected.value.includes(i.id));
    selected.value = [];
    return;
  }
  batch.value = command;
  values.value = [];
  batchType.value = '';
  batchOpen.value = true;
}
const sharedOptions = computed(() => {
  const target = items.value.filter((i) => selected.value.includes(i.id) && i.valueType === '选项');
  return target[0]?.options.filter((o) => target.every((i) => i.options.includes(o))) || [];
});
function apply() {
  if (batch.value === 'type' && !batchType.value) {
    ElMessage.warning('请选择巡检类型');
    return;
  }
  if (batch.value === 'options' && values.value.length < 2) {
    ElMessage.warning('请填写至少两个可选项');
    return;
  }
  if (batch.value === 'normal' && !values.value.length) {
    ElMessage.warning('请选择正常选项');
    return;
  }
  items.value.forEach((i) => {
    if (!selected.value.includes(i.id)) return;
    if (batch.value === 'type') i.typeId = batchType.value;
    else if (i.valueType === '选项') {
      if (batch.value === 'options') {
        i.options = [...values.value];
        i.normalOptions = i.normalOptions.filter((o) => i.options.includes(o));
      } else i.normalOptions = [...values.value];
    }
  });
  batchOpen.value = false;
}
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => items.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];
</script>
<template>
  <section class="inspection-projects">
    <div class="inspection-section-tools">
      <h3>
        检查项目 <span>{{ items.length }}</span>
      </h3>
      <ElButton type="primary" @click="add"><AppIcon name="Plus" /> 添加项目</ElButton
      ><ElButton @click="$emit('addType')">巡检类型</ElButton
      ><ElDropdown :disabled="!selected.length" @command="openBatch"
        ><ElButton :disabled="!selected.length">批量操作<AppIcon name="Down" /></ElButton
        ><template #dropdown
          ><ElDropdownMenu
            ><ElDropdownItem command="type">修改巡检类型</ElDropdownItem
            ><ElDropdownItem command="options">修改可选项</ElDropdownItem
            ><ElDropdownItem command="normal">修改正常选项</ElDropdownItem
            ><ElDropdownItem divided command="delete">删除</ElDropdownItem></ElDropdownMenu
          ></template
        ></ElDropdown
      ><span v-if="selected.length">已选 {{ selected.length }} 项</span>
    </div>
    <div class="inspection-project-scroll">
      <AppBusinessTable
        v-model:selection-ids="selected"
        :selection-width="48"
        selection-fixed="left"
        selection-align="center"
        selectable
        :rows="items"
        :pagination="false"
        :fill="false"
        :framed="false"
        class="business-table inspection-project-table"
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="55" :width="52" class-name="project-sequence" fixed="left"
          ><template #header><div class="table-header-label">序号</div></template
          ><template #default="{ row: item, $index: index }">{{
            index + 1
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="210" :width="200" class-name="project-name" fixed="left"
          ><template #header
            ><div class="table-header-label">项目名称 <b>*</b></div></template
          ><template #default="{ row: item, $index: index }"
            ><ElInput v-model="item.name" placeholder="项目名称" /></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="130" v-if="items.length > 0"
          ><template #header><div class="table-header-label">巡检类型</div></template
          ><template #default="{ row: item, $index: index }">
            <ElSelect v-model="item.typeId"
              ><ElOption v-for="t in types" :key="t.id" :value="t.id" :label="t.name"
            /></ElSelect> </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="260" v-if="items.length > 0"
          ><template #header
            ><div class="table-header-label">方法及基准 <b>*</b></div></template
          ><template #default="{ row: item, $index: index }">
            <ElInput
              v-model="item.content"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 4 }"
              placeholder="检查方法及基准"
            /> </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="120" v-if="items.length > 0"
          ><template #header><div class="table-header-label">结果类型</div></template
          ><template #default="{ row: item, $index: index }">
            <ElSelect v-model="item.valueType"
              ><ElOption v-for="v in ['选项', '数值', '文本']" :key="v" :value="v"
            /></ElSelect> </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="100" v-if="items.length > 0"
          ><template #header><div class="table-header-label">下限</div></template
          ><template #default="{ row: item, $index: index }">
            <AppDecimalInput
              v-if="item.valueType === '数值'"
              v-model="item.lower"
              placeholder="不限"
            /><span v-else></span> </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="100" v-if="items.length > 0"
          ><template #header><div class="table-header-label">上限</div></template
          ><template #default="{ row: item, $index: index }">
            <AppDecimalInput
              v-if="item.valueType === '数值'"
              v-model="item.upper"
              placeholder="不限"
            /><span v-else></span> </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="220" v-if="items.length > 0"
          ><template #header><div class="table-header-label">可选项</div></template
          ><template #default="{ row: item, $index: index }">
            <ElSelect
              v-if="item.valueType === '选项'"
              v-model="item.options"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="输入后回车"
              @change="
                item.normalOptions = item.normalOptions.filter((o) => item.options.includes(o))
              "
              ><ElOption v-for="o in item.options" :key="o" :value="o" /></ElSelect
            ><span v-else></span> </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="220" v-if="items.length > 0"
          ><template #header><div class="table-header-label">正常选项</div></template
          ><template #default="{ row: item, $index: index }">
            <ElSelect
              v-if="item.valueType === '选项'"
              v-model="item.normalOptions"
              multiple
              placeholder="选择正常选项"
              ><ElOption v-for="o in item.options" :key="o" :value="o" /></ElSelect
            ><span v-else></span> </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="82" v-if="items.length > 0"
          ><template #header><div class="table-header-label">必填</div></template
          ><template #default="{ row: item, $index: index }"
            ><ElSwitch v-model="item.required" /></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="100" v-if="items.length > 0"
          ><template #header><div class="table-header-label">必须拍照</div></template
          ><template #default="{ row: item, $index: index }"
            ><ElSwitch v-model="item.photoRequired" /></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="200" v-if="items.length > 0"
          ><template #header><div class="table-header-label">备注</div></template
          ><template #default="{ row: item, $index: index }">
            <ElInput
              v-model="item.remark"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 4 }"
            /> </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="220" v-if="items.length > 0"
          ><template #header><div class="table-header-label">参考附件</div></template
          ><template #default="{ row: item, $index: index }">
            <MaintenanceAttachments
              v-model="item.references"
              :upload-file="inspectionUpload"
            /> </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
          ><template #header><div class="table-header-label">操作</div></template
          ><template #default="{ row: item, $index: index }">
            <ElButton
              class="inspection-text-button danger"
              @click="items = items.filter((i) => i.id !== item.id)"
              type="danger"
              link
            >
              删除
            </ElButton>
          </template></ElTableColumn
        ><template #empty
          ><div v-if="!items.length">暂无检查项目，点击“添加项目”开始维护</div></template
        ></AppBusinessTable
      >
    </div>
    <AppDialog
      v-model="batchOpen"
      :title="
        batch === 'type' ? '修改巡检类型' : batch === 'options' ? '修改可选项' : '修改正常选项'
      "
      ><ElSelect v-if="batch === 'type'" v-model="batchType"
        ><ElOption v-for="t in types" :key="t.id" :value="t.id" :label="t.name" /></ElSelect
      ><ElSelect
        v-else
        v-model="values"
        multiple
        filterable
        :allow-create="batch === 'options'"
        default-first-option
        placeholder="输入后回车"
        ><ElOption
          v-for="o in batch === 'normal' ? sharedOptions : values"
          :key="o"
          :value="o" /></ElSelect
      ><template #footer
        ><div class="inspection-dialog-actions">
          <ElButton @click="batchOpen = false">取消</ElButton
          ><ElButton type="primary" @click="apply">确定</ElButton>
        </div></template
      ></AppDialog
    >
  </section>
</template>
<style scoped>
.inspection-project-scroll {
  overflow: auto;
  border: 1px solid #e1e6ef;
  border-radius: 5px;
  max-height: 480px;
}
.inspection-project-table {
  width: 2218px;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
}
.inspection-project-table :deep(th),
.inspection-project-table :deep(td) {
  vertical-align: middle;
}
.inspection-project-table :deep(th) {
  position: sticky;
  top: 0;
  z-index: 3;
}
.project-sequence,
.project-name {
  position: sticky;
  z-index: 2;
}
.project-sequence {
  left: 44px;
}
.project-name {
  left: 96px;
  box-shadow: 1px 0 0 #e1e6ef;
}
.inspection-project-table :deep(.project-sequence, .project-name) {
  z-index: 4;
}
.inspection-project-table :deep(.el-input__wrapper),
.inspection-project-table :deep(.el-select__wrapper) {
  box-shadow: none;
  background: transparent;
  padding: 5px 6px;
  min-height: 32px;
}
.inspection-project-table :deep(.el-textarea__inner) {
  box-shadow: none;
  background: transparent;
  padding: 5px 6px;
}
.inspection-project-table :deep(.el-input__wrapper:hover),
.inspection-project-table :deep(.el-select__wrapper:hover),
.inspection-project-table :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px #cbd6e5 inset;
}
.inspection-project-table b {
  color: #c74444;
}
.inspection-section-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.inspection-section-tools h3 {
  margin: 0 auto 0 0;
  font-size: 14px;
}
.inspection-section-tools h3 span {
  font-size: 13px;
  margin-left: 6px;
  font-weight: 400;
}
</style>
