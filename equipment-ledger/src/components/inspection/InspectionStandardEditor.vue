<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import {
  ElForm,
  ElInput,
  ElSwitch,
  ElTreeSelect,
  type FormInstance,
  type FormRules,
} from 'element-plus';
import { ApiError } from '../../api';
import { tr } from '../../i18n';
import type { InspectionChoices, InspectionDefinition } from '../../inspection';
import { planningSelectors, type SelectorRow } from '../../selectors';
import TreeSelect, { type TreeSelectNode } from '../TreeSelect.vue';
import AppFormField from '../common/AppFormField.vue';
import PagedChoiceField from '../common/PagedChoiceField.vue';
import InspectionProjectEditor from '../common/InspectionProjectEditor.vue';

const model = defineModel<InspectionDefinition>({ required: true });
defineProps<{
  types: InspectionDefinition[];
  choices: InspectionChoices;
  departments: TreeSelectNode[];
  categories: { value: string; label: string; children?: unknown[] }[];
}>();
defineEmits<{ addType: []; equipment: [rows: SelectorRow[]] }>();
const selector = planningSelectors('inspection');
const form = ref<FormInstance>();
const root = ref<HTMLElement>();
const serverErrors = ref<Record<string, string>>({});
let failedValues: Record<string, string> = {};
const required = (message: string) => ({
  required: true,
  whitespace: true,
  message,
  trigger: ['blur', 'change'],
});
const rules = computed<FormRules>(() => {
  const result: FormRules = {
    name: [
      required(tr('ui.validation.message36')),
      { max: 150, message: tr('server.inspection.message0050'), trigger: ['blur', 'change'] },
    ],
    departmentId: [required(tr('ui.equipment.text0099'))],
    items: [
      {
        type: 'array',
        required: true,
        min: 1,
        max: 1000,
        message: tr('server.inspection.message0044'),
        trigger: 'change',
      },
    ],
  };
  model.value.items.forEach((item, index) => {
    const path = `items.${index}.`;
    // Avoid resolving the row's boolean "required" field through items[0].required in the array rule.
    result[path + 'required'] = [];
    result[path + 'name'] = [required(tr('ui.validation.message22'))];
    result[path + 'content'] = [required(tr('inspection.standard.contentRequired'))];
    result[path + 'typeId'] = [required(tr('ui.equipment.text1467'))];
    result[path + 'valueType'] = [required(tr('server.inspection.message0047'))];
    if (item.valueType === '选项') {
      result[path + 'options'] = [
        {
          type: 'array',
          required: true,
          min: 2,
          message: tr('ui.equipment.text1468'),
          trigger: 'change',
        },
      ];
      result[path + 'normalOptions'] = [
        {
          validator: (_rule, value, callback) =>
            callback(
              Array.isArray(value) &&
                value.length > 0 &&
                value.every((option) => item.options.includes(option))
                ? undefined
                : new Error(tr('ui.equipment.text0750')),
            ),
          trigger: 'change',
        },
      ];
    }
  });
  return result;
});
watch(
  () => model.value.items.map((item) => `${item.id}:${item.valueType}`),
  (types, previous) => {
    types.forEach((type, index) => {
      if (type === previous[index]) return;
      const paths = ['options', 'normalOptions', 'lower', 'upper'].map(
        (field) => `items.${index}.${field}`,
      );
      form.value?.clearValidate(paths);
      for (const path of paths) delete serverErrors.value[path];
    });
  },
  { flush: 'post' },
);
watch(
  () => model.value.items.length,
  (length) => {
    if (length > 0 && length <= 1000) form.value?.clearValidate('items');
  },
  { flush: 'post' },
);
function fieldValue(path: string): string {
  let value: unknown = model.value;
  for (const part of path.split('.'))
    value = (value as Record<string, unknown> | undefined)?.[part];
  return JSON.stringify(value) ?? '';
}
watch(
  model,
  () => {
    for (const path of Object.keys(serverErrors.value)) {
      if (fieldValue(path) !== failedValues[path]) delete serverErrors.value[path];
    }
  },
  { deep: true },
);
async function focusError() {
  await nextTick();
  const first = root.value?.querySelector<HTMLElement>('.el-form-item.is-error');
  first?.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' });
  first
    ?.querySelector<HTMLElement>('input,textarea,button,[tabindex="0"]')
    ?.focus({ preventScroll: true });
}
async function validate() {
  serverErrors.value = {};
  const valid = (await form.value?.validate().catch(() => false)) ?? false;
  if (!valid) await focusError();
  return valid;
}
async function capture(error: unknown) {
  if (!(error instanceof ApiError)) return false;
  const fields = Object.fromEntries(
    Object.entries(error.fieldErrors).filter(
      ([path]) =>
        [
          'code',
          'name',
          'departmentId',
          'requirement',
          'categoryIds',
          'equipmentIds',
          'remark',
          'enabled',
          'items',
        ].includes(path) ||
        /^items\.\d+\.(name|content|typeId|valueType|lower|upper|options|normalOptions|required|photoRequired|remark|references)$/.test(
          path,
        ),
    ),
  );
  if (!Object.keys(fields).length) return false;
  serverErrors.value = fields;
  failedValues = Object.fromEntries(Object.keys(fields).map((path) => [path, fieldValue(path)]));
  await focusError();
  return true;
}
defineExpose({ validate, capture });
</script>

<template>
  <div ref="root" class="inspection-standard-editor">
    <ElForm
      ref="form"
      :model="model"
      :rules="rules"
      :validate-on-rule-change="false"
      label-width="100px"
      @submit.prevent
    >
      <section class="inspection-form-card">
        <div class="inspection-form-grid">
          <AppFormField :label="tr('ui.equipment.text0310')" prop="code" :error="serverErrors.code">
            <ElInput
              v-model="model.code"
              :placeholder="tr('ui.equipment.text0312')"
              maxlength="64"
            />
          </AppFormField>
          <AppFormField :label="tr('ui.equipment.text0313')" prop="name" :error="serverErrors.name">
            <ElInput v-model="model.name" maxlength="150" />
          </AppFormField>
          <AppFormField
            :label="tr('ui.equipment.text0098')"
            prop="departmentId"
            :error="serverErrors.departmentId"
          >
            <TreeSelect
              v-model="model.departmentId"
              :nodes="departments"
              :placeholder="tr('ui.equipment.text0315')"
            />
          </AppFormField>
          <AppFormField
            class="wide"
            :label="tr('ui.equipment.text0316')"
            prop="requirement"
            :error="serverErrors.requirement"
          >
            <ElInput
              v-model="model.requirement"
              type="textarea"
              :rows="2"
              :placeholder="tr('ui.equipment.text0317')"
            />
          </AppFormField>
          <AppFormField
            class="wide"
            :label="tr('ui.equipment.text0318')"
            prop="categoryIds"
            :error="serverErrors.categoryIds"
          >
            <ElTreeSelect
              v-model="model.categoryIds"
              :data="categories"
              multiple
              check-strictly
              filterable
              clearable
              :placeholder="tr('ui.equipment.text0319')"
            />
          </AppFormField>
          <AppFormField
            class="wide"
            :label="tr('ui.equipment.text0320')"
            prop="equipmentIds"
            :error="serverErrors.equipmentIds"
          >
            <PagedChoiceField
              v-model="model.equipmentIds"
              multiple
              :options="choices.equipment"
              :load-rows="selector.equipment"
              :columns="[
                { key: 'code', label: tr('ui.equipment.text0864') },
                { key: 'name', label: tr('ui.equipment.text0081') },
                { key: 'model', label: tr('ui.equipment.text0091') },
                { key: 'department', label: tr('ui.equipment.text0098') },
              ]"
              :title="tr('ui.equipment.text0220')"
              :placeholder="tr('ui.equipment.text0321')"
              @select="$emit('equipment', $event)"
            />
          </AppFormField>
          <AppFormField
            class="wide"
            :label="tr('ui.equipment.text0177')"
            prop="remark"
            :error="serverErrors.remark"
          >
            <ElInput v-model="model.remark" type="textarea" :rows="2" />
          </AppFormField>
          <AppFormField
            :label="tr('ui.equipment.text0216')"
            prop="enabled"
            :error="serverErrors.enabled"
          >
            <ElSwitch v-model="model.enabled" />
          </AppFormField>
        </div>
      </section>
      <AppFormField
        label=""
        prop="items"
        :error="serverErrors.items"
        label-width="0"
        class="projects-field"
      >
        <InspectionProjectEditor
          v-model="model.items"
          :types="types"
          :errors="serverErrors"
          @add-type="$emit('addType')"
        />
      </AppFormField>
    </ElForm>
  </div>
</template>

<style scoped>
.inspection-form-grid > .app-form-field {
  min-width: 0;
  margin-bottom: 0;
}
.inspection-form-grid {
  row-gap: 24px;
}
.inspection-standard-editor :deep(.el-form-item__content) {
  min-width: 0;
}
.projects-field {
  margin-top: 20px;
}
.projects-field :deep(> .el-form-item__content) {
  display: block;
}
</style>
