<script setup lang="ts">
import { tr } from '../../i18n';

import { ElTableColumn } from 'element-plus';
import AppBusinessTable from './AppBusinessTable.vue';
import AppFormField from './AppFormField.vue';
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
const props = defineProps<{ types: InspectionDefinition[]; errors?: Record<string, string> }>();
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
    ElMessage.warning(tr('ui.equipment.text1467'));
    return;
  }
  if (batch.value === 'options' && values.value.length < 2) {
    ElMessage.warning(tr('ui.equipment.text1468'));
    return;
  }
  if (batch.value === 'normal' && !values.value.length) {
    ElMessage.warning(tr('ui.equipment.text0750'));
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
        {{ tr('ui.equipment.text0377') }} <span>{{ items.length }}</span>
      </h3>
      <ElButton type="primary" @click="add"
        ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0779') }}</ElButton
      ><ElButton @click="$emit('addType')">{{ tr('ui.equipment.text0325') }}</ElButton
      ><ElDropdown :disabled="!selected.length" @command="openBatch"
        ><ElButton :disabled="!selected.length"
          >{{ tr('ui.equipment.text0373') }}<AppIcon name="Down" /></ElButton
        ><template #dropdown
          ><ElDropdownMenu
            ><ElDropdownItem command="type">{{ tr('ui.equipment.text1469') }}</ElDropdownItem
            ><ElDropdownItem command="options">{{ tr('ui.equipment.text0777') }}</ElDropdownItem
            ><ElDropdownItem command="normal">{{ tr('ui.equipment.text0778') }}</ElDropdownItem
            ><ElDropdownItem divided command="delete">{{
              tr('ui.equipment.text0146')
            }}</ElDropdownItem></ElDropdownMenu
          ></template
        ></ElDropdown
      ><span v-if="selected.length"
        >{{ tr('ui.equipment.text0295') }} {{ selected.length }}
        {{ tr('ui.equipment.text0394') }}</span
      >
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
          ><template #header
            ><div class="table-header-label">{{ tr('ui.equipment.text0376') }}</div></template
          ><template #default="{ row: item, $index: index }">{{
            index + 1
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="210" :width="200" class-name="project-name" fixed="left"
          ><template #header
            ><div class="table-header-label">
              {{ tr('ui.equipment.text0398') }} <b>*</b>
            </div></template
          ><template #default="{ row: item, $index: index }"
            ><AppFormField
              label=""
              :prop="'items.' + index + '.name'"
              :error="errors?.['items.' + index + '.name']"
              label-width="0"
              ><ElInput
                v-model="item.name"
                :placeholder="
                  tr('ui.equipment.text0398')
                " /></AppFormField></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="130"
          ><template #header
            ><div class="table-header-label">{{ tr('ui.equipment.text0325') }}</div></template
          ><template #default="{ row: item, $index: index }"
            ><AppFormField
              label=""
              :prop="'items.' + index + '.typeId'"
              :error="errors?.['items.' + index + '.typeId']"
              label-width="0"
            >
              <ElSelect v-model="item.typeId"
                ><ElOption v-for="t in types" :key="t.id" :value="t.id" :label="t.name"
              /></ElSelect> </AppFormField></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="260"
          ><template #header
            ><div class="table-header-label">
              {{ tr('ui.equipment.text0399') }} <b>*</b>
            </div></template
          ><template #default="{ row: item, $index: index }"
            ><AppFormField
              label=""
              :prop="'items.' + index + '.content'"
              :error="errors?.['items.' + index + '.content']"
              label-width="0"
            >
              <ElInput
                v-model="item.content"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
                :placeholder="tr('ui.equipment.text1470')"
              /> </AppFormField></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="120"
          ><template #header
            ><div class="table-header-label">{{ tr('ui.equipment.text0400') }}</div></template
          ><template #default="{ row: item, $index: index }"
            ><AppFormField
              label=""
              :prop="'items.' + index + '.valueType'"
              :error="errors?.['items.' + index + '.valueType']"
              label-width="0"
            >
              <ElSelect v-model="item.valueType"
                ><ElOption v-for="v in ['选项', '数值', '文本']" :key="v" :value="v"
              /></ElSelect> </AppFormField></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="100"
          ><template #header
            ><div class="table-header-label">{{ tr('ui.equipment.text1471') }}</div></template
          ><template #default="{ row: item, $index: index }"
            ><AppFormField
              label=""
              :prop="'items.' + index + '.lower'"
              :error="errors?.['items.' + index + '.lower']"
              label-width="0"
            >
              <AppDecimalInput
                v-if="item.valueType === '数值'"
                v-model="item.lower"
                :placeholder="tr('ui.equipment.text0792')"
              /><span v-else></span> </AppFormField></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="100"
          ><template #header
            ><div class="table-header-label">{{ tr('ui.equipment.text1472') }}</div></template
          ><template #default="{ row: item, $index: index }"
            ><AppFormField
              label=""
              :prop="'items.' + index + '.upper'"
              :error="errors?.['items.' + index + '.upper']"
              label-width="0"
            >
              <AppDecimalInput
                v-if="item.valueType === '数值'"
                v-model="item.upper"
                :placeholder="tr('ui.equipment.text0792')"
              /><span v-else></span> </AppFormField></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="220"
          ><template #header
            ><div class="table-header-label">{{ tr('ui.equipment.text0787') }}</div></template
          ><template #default="{ row: item, $index: index }"
            ><AppFormField
              label=""
              :prop="'items.' + index + '.options'"
              :error="errors?.['items.' + index + '.options']"
              label-width="0"
            >
              <ElSelect
                v-if="item.valueType === '选项'"
                v-model="item.options"
                multiple
                filterable
                allow-create
                default-first-option
                :placeholder="tr('ui.equipment.text1473')"
                @change="
                  item.normalOptions = item.normalOptions.filter((o) => item.options.includes(o))
                "
                ><ElOption v-for="o in item.options" :key="o" :value="o" /></ElSelect
              ><span v-else></span> </AppFormField></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="220"
          ><template #header
            ><div class="table-header-label">{{ tr('ui.equipment.text0789') }}</div></template
          ><template #default="{ row: item, $index: index }"
            ><AppFormField
              label=""
              :prop="'items.' + index + '.normalOptions'"
              :error="errors?.['items.' + index + '.normalOptions']"
              label-width="0"
            >
              <ElSelect
                v-if="item.valueType === '选项'"
                v-model="item.normalOptions"
                multiple
                :placeholder="tr('ui.equipment.text0790')"
                ><ElOption v-for="o in item.options" :key="o" :value="o" /></ElSelect
              ><span v-else></span> </AppFormField></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="82"
          ><template #header
            ><div class="table-header-label">{{ tr('ui.equipment.text0794') }}</div></template
          ><template #default="{ row: item, $index: index }"
            ><AppFormField
              label=""
              :prop="'items.' + index + '.required'"
              :error="errors?.['items.' + index + '.required']"
              label-width="0"
              ><ElSwitch v-model="item.required" /></AppFormField></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="100"
          ><template #header
            ><div class="table-header-label">{{ tr('ui.equipment.text1474') }}</div></template
          ><template #default="{ row: item, $index: index }"
            ><AppFormField
              label=""
              :prop="'items.' + index + '.photoRequired'"
              :error="errors?.['items.' + index + '.photoRequired']"
              label-width="0"
              ><ElSwitch v-model="item.photoRequired" /></AppFormField></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="200"
          ><template #header
            ><div class="table-header-label">{{ tr('ui.equipment.text0130') }}</div></template
          ><template #default="{ row: item, $index: index }"
            ><AppFormField
              label=""
              :prop="'items.' + index + '.remark'"
              :error="errors?.['items.' + index + '.remark']"
              label-width="0"
            >
              <ElInput
                v-model="item.remark"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
              /> </AppFormField></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" :width="220"
          ><template #header
            ><div class="table-header-label">{{ tr('ui.equipment.text0798') }}</div></template
          ><template #default="{ row: item, $index: index }"
            ><AppFormField
              label=""
              :prop="'items.' + index + '.references'"
              :error="errors?.['items.' + index + '.references']"
              label-width="0"
            >
              <MaintenanceAttachments
                v-model="item.references"
                :upload-file="inspectionUpload"
              /> </AppFormField></template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
          ><template #header
            ><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
          ><template #default="{ row: item, $index: index }">
            <ElButton
              class="inspection-text-button danger"
              @click="items = items.filter((i) => i.id !== item.id)"
              type="danger"
              link
            >
              {{ tr('ui.equipment.text0146') }}
            </ElButton>
          </template></ElTableColumn
        ><template #empty
          ><div v-if="!items.length">{{ tr('ui.equipment.text1475') }}</div></template
        ></AppBusinessTable
      >
    </div>
    <AppDialog
      v-model="batchOpen"
      :title="
        batch === 'type'
          ? tr('ui.equipment.text1469')
          : batch === 'options'
            ? tr('ui.equipment.text0777')
            : tr('ui.equipment.text0778')
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
        :placeholder="tr('ui.equipment.text1473')"
        ><ElOption
          v-for="o in batch === 'normal' ? sharedOptions : values"
          :key="o"
          :value="o" /></ElSelect
      ><template #footer
        ><div class="inspection-dialog-actions">
          <ElButton @click="batchOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
          ><ElButton type="primary" @click="apply">{{ tr('ui.equipment.text0402') }}</ElButton>
        </div></template
      ></AppDialog
    >
  </section>
</template>
<style scoped>
.inspection-projects {
  width: 100%;
  min-width: 0;
}
.inspection-project-table :deep(.app-form-field) {
  margin: 0;
  padding-bottom: 18px;
}
.inspection-project-table :deep(.el-form-item__content) {
  min-width: 0;
}
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
