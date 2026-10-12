<script setup lang="ts">
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
</script>
<template>
  <section class="inspection-projects">
    <div class="inspection-section-tools">
      <h3>
        检查项目 <span>{{ items.length }}</span>
      </h3>
      <ElButton type="primary" @click="add">＋ 添加项目</ElButton
      ><ElButton @click="$emit('addType')">巡检类型</ElButton
      ><ElDropdown :disabled="!selected.length" @command="openBatch"
        ><ElButton :disabled="!selected.length">批量操作⌄</ElButton
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
      <table class="business-table inspection-project-table">
        <colgroup>
          <col style="width: 44px" />
          <col style="width: 52px" />
          <col style="width: 200px" />
          <col style="width: 130px" />
          <col style="width: 260px" />
          <col style="width: 120px" />
          <col style="width: 100px" />
          <col style="width: 100px" />
          <col style="width: 220px" />
          <col style="width: 220px" />
          <col style="width: 82px" />
          <col style="width: 100px" />
          <col style="width: 200px" />
          <col style="width: 220px" />
          <col style="width: 70px" />
        </colgroup>
        <thead>
          <tr>
            <th class="project-select">
              <input type="checkbox" :checked="all" @change="selectAll" />
            </th>
            <th class="project-sequence">序号</th>
            <th class="project-name">项目名称 <b>*</b></th>
            <th>巡检类型</th>
            <th>方法及基准 <b>*</b></th>
            <th>结果类型</th>
            <th>下限</th>
            <th>上限</th>
            <th>可选项</th>
            <th>正常选项</th>
            <th>必填</th>
            <th>必须拍照</th>
            <th>备注</th>
            <th>参考附件</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in items" :key="item.id">
            <td class="project-select">
              <input v-model="selected" type="checkbox" :value="item.id" />
            </td>
            <td class="project-sequence">{{ index + 1 }}</td>
            <td class="project-name"><ElInput v-model="item.name" placeholder="项目名称" /></td>
            <td>
              <ElSelect v-model="item.typeId"
                ><ElOption v-for="t in types" :key="t.id" :value="t.id" :label="t.name"
              /></ElSelect>
            </td>
            <td>
              <ElInput
                v-model="item.content"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
                placeholder="检查方法及基准"
              />
            </td>
            <td>
              <ElSelect v-model="item.valueType"
                ><ElOption v-for="v in ['选项', '数值', '文本']" :key="v" :value="v"
              /></ElSelect>
            </td>
            <td>
              <AppDecimalInput
                v-if="item.valueType === '数值'"
                v-model="item.lower"
                placeholder="不限"
              /><span v-else></span>
            </td>
            <td>
              <AppDecimalInput
                v-if="item.valueType === '数值'"
                v-model="item.upper"
                placeholder="不限"
              /><span v-else></span>
            </td>
            <td>
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
              ><span v-else></span>
            </td>
            <td>
              <ElSelect
                v-if="item.valueType === '选项'"
                v-model="item.normalOptions"
                multiple
                placeholder="选择正常选项"
                ><ElOption v-for="o in item.options" :key="o" :value="o" /></ElSelect
              ><span v-else></span>
            </td>
            <td><ElSwitch v-model="item.required" /></td>
            <td><ElSwitch v-model="item.photoRequired" /></td>
            <td>
              <ElInput
                v-model="item.remark"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
              />
            </td>
            <td>
              <MaintenanceAttachments v-model="item.references" :upload-file="inspectionUpload" />
            </td>
            <td>
              <button
                class="inspection-text-button danger"
                @click="items = items.filter((i) => i.id !== item.id)"
              >
                删除
              </button>
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="15" class="inspection-empty">暂无检查项目，点击“添加项目”开始维护</td>
          </tr>
        </tbody>
      </table>
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
.inspection-project-table th,
.inspection-project-table td {
  padding: 8px 10px;
  border-bottom: 1px solid #e8edf3;
  vertical-align: middle;
  background: white;
}
.inspection-project-table th {
  background: #f5f7fa;
  position: sticky;
  top: 0;
  z-index: 3;
  text-align: left;
  height: 38px;
  font-weight: 700;
}
.inspection-project-table td {
  height: 46px;
}
.project-select,
.project-sequence,
.project-name {
  position: sticky;
  z-index: 2;
}
.project-select {
  left: 0;
}
.project-sequence {
  left: 44px;
}
.project-name {
  left: 96px;
  box-shadow: 1px 0 0 #e1e6ef;
}
.inspection-project-table th:is(.project-select, .project-sequence, .project-name) {
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
