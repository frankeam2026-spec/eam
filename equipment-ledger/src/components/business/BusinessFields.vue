<script setup lang="ts">import { tr } from '../../i18n';

import { ref, computed, watch, nextTick, inject } from 'vue';
import { ElAlert, ElButton, ElForm, ElTag, formContextKey, type FormItemRule } from 'element-plus';
import AppFormField from '../common/AppFormField.vue';
import DepartmentText from '../common/DepartmentText.vue';
import BusinessFieldInput from './BusinessFieldInput.vue';
import {
  businessApi,
  EXTENSION_GROUP,
  fieldVisible,
  fieldRequired,
  emptyValue,
  formLayout,
  fieldSpan,
  type FormLayout,
  type BusinessObject,
  type BusinessField,
  type CustomRecord,
  type FieldValue,
} from '../../businessConfig';
const props = withDefaults(
  defineProps<{
    object: BusinessObject;
    categoryId?: string;
    isNew?: boolean;
    readonly?: boolean;
    errors?: Record<string, string>;
  }>(),
  { categoryId: '', isNew: false, readonly: false },
);
const record = defineModel<CustomRecord>({ required: true });
const emit = defineEmits<{ initialize: []; ready: [] }>();
const parentForm = inject(formContextKey, undefined);
const formTag = parentForm ? 'div' : ElForm;
const editedFields = ref(new Set<string>());
watch(
  () => props.errors,
  () => {
    editedFields.value = new Set();
  },
);
const fields = ref<BusinessField[]>([]),
  loading = ref(true),
  error = ref(''),
  localErrors = ref<Record<string, string>>({}),
  referenceLabels = ref<Record<string, Record<string, string>>>({});
const layout = ref<FormLayout>({ columns: 2, maxWidth: 0 });
const values = computed(() => record.value.customFields || {});
const visible = computed(() =>
  fields.value
    .filter((f) => fieldVisible(f, fields.value, values.value, props.categoryId))
    .sort((a, b) => a.sortOrder - b.sortOrder),
);
const retained = computed(() =>
  fields.value.filter(
    (f) =>
      !fieldVisible(f, fields.value, values.value, props.categoryId) &&
      !emptyValue(values.value[f.id]),
  ),
);
let revision = 0;
async function reload(initial = false) {
  const rev = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const r = await businessApi.runtime(props.object);
    if (rev !== revision) return;
    fields.value = r.configuration.fields;
    layout.value = formLayout(r.configuration);
    if (!props.readonly) {
      if (initial) emit('initialize');
      const next = { ...values.value };
      if (props.isNew)
        for (const f of fields.value)
          if (!(f.id in next) && f.enabled && f.defaultValue !== undefined)
            next[f.id] = structuredClone(f.defaultValue);
      record.value.customFields = next;
      record.value.customSchemaVersion = r.version;
      if (initial) {
        await nextTick();
        emit('ready');
      }
    }
  } catch (e) {
    if (rev === revision) error.value = e instanceof Error ? e.message : tr('ui.equipment.text1246');
  } finally {
    if (rev === revision) loading.value = false;
  }
}
watch(
  () => [props.object, (record.value as { id?: string }).id, props.readonly],
  () => void reload(true),
  { immediate: true },
);
function set(id: string, value: FieldValue | undefined) {
  record.value.customFields = { ...values.value, [id]: value ?? null };
  delete localErrors.value[id];
  editedFields.value.add(id);
}
function labels(id: string) {
  const result = {
    ...fields.value.find((f) => f.id === id)?.defaultLabels,
    ...referenceLabels.value[id],
  };
  const value = values.value[id];
  const ids = Array.isArray(value) ? value : value ? [String(value)] : [];
  const names = (record.value.customFieldLabels?.[id] || '').split('、');
  ids.forEach((v, i) => {
    if (names[i]) result[v] = names[i]!;
  });
  return result;
}
function fieldRules(field: BusinessField): FormItemRule[] {
  if (props.readonly) return [];
  return [
    {
      required: fieldRequired(field, fields.value, values.value, props.categoryId),
      trigger: ['blur', 'change'],
      validator: (_rule, value, callback) => {
        const missing =
          fieldRequired(field, fields.value, values.value, props.categoryId) && emptyValue(value);
        callback(missing ? new Error(tr('server.business.message0051',{p0:field.name})) : undefined);
      },
    },
  ];
}
function validate() {
  localErrors.value = {};
  if (loading.value || error.value) return false;
  for (const f of visible.value) {
    const v = values.value[f.id];
    if (fieldRequired(f, fields.value, values.value, props.categoryId) && emptyValue(v))
      localErrors.value[f.id] = tr('server.business.message0051',{p0:f.name});
    if (f.type === 'NUMBER' && !emptyValue(v)) {
      const n = Number(v);
      if (!Number.isFinite(n)) localErrors.value[f.id] = tr('ui.validation.message32');
      else if ((f.min !== '' && n < Number(f.min)) || (f.max !== '' && n > Number(f.max)))
        localErrors.value[f.id] = tr('ui.validation.message33');
    }
  }
  return !Object.keys(localErrors.value).length;
}
function prepare() {
  return Object.fromEntries(
    visible.value.filter((f) => f.id in values.value).map((f) => [f.id, values.value[f.id]!]),
  );
}
function display(f: BusinessField) {
  const v = values.value[f.id];
  if (emptyValue(v)) return '';
  if (record.value.customFieldLabels?.[f.id]) return record.value.customFieldLabels[f.id];
  if (f.type === 'BOOLEAN') return v ? '是' : '否';
  if (f.type === 'NUMBER') return Number(v).toFixed(2);
  return Array.isArray(v) ? v.join('、') : String(v).replace('T', ' ');
}
function departmentValues(field: BusinessField) {
  const value = values.value[field.id];
  const ids = Array.isArray(value) ? value.map(String) : value ? [String(value)] : [];
  const captions = (display(field) || '').split('、');
  const names = labels(field.id);
  return ids.map((id, index) => ({ id, name: names[id] || captions[index] || '' }));
}
defineExpose({ validate, reload, prepare, loading, fields });
</script>
<template>
  <ElAlert v-if="error" type="error" :closable="false" :title="error"
    ><ElButton text @click="reload()">{{ tr('ui.equipment.text1247') }}</ElButton></ElAlert
  >
  <p v-else-if="loading" class="business-fields-loading">{{ tr('ui.equipment.text1248') }}</p>
  <template v-else>
    <section
      v-if="visible.length || (readonly && retained.length)"
      class="business-fields-section"
      :style="{ maxWidth: layout.maxWidth ? `${layout.maxWidth}px` : undefined }"
    >
      <h4>{{ tr('common.form.extension') }}</h4>
      <component
        v-if="visible.length"
        :is="formTag"
        :model="values"
        label-width="112px"
        class="business-fields-grid"
        :style="{
          '--business-columns': layout.columns,
        }"
      >
        <AppFormField
          v-for="f in visible"
          :key="f.id"
          :label="f.name"
          :prop="parentForm ? `customFields.${f.id}` : f.id"
          :data-field="`customFields.${f.id}`"
          :style="{ '--business-span': fieldSpan(f, layout.columns) }"
          :required="!readonly && fieldRequired(f, fields, values, categoryId)"
          :rules="fieldRules(f)"
          :error="
            readonly
              ? ''
              : (!editedFields.has(f.id) ? errors?.[`customFields.${f.id}`] : '') ||
                localErrors[f.id]
          "
        >
          <span v-if="readonly" class="business-field-value"><template v-if="f.type === 'DEPARTMENT' || f.source === 'department'"><template v-for="(department, index) in departmentValues(f)" :key="department.id"><span v-if="index">、</span><DepartmentText :value="department.name" :department-id="department.id" /></template></template><template v-else>{{ display(f) }}</template></span>
          <div v-else class="business-field-control">
            <BusinessFieldInput
              :model-value="values[f.id]"
              :field="f"
              :object="object"
              :labels="labels(f.id)"
              @update:model-value="set(f.id, $event)"
              @labels="referenceLabels[f.id] = $event"
            /><small v-if="f.description">{{ f.description }}</small>
          </div>
        </AppFormField></component
      >
      <details v-if="readonly && retained.length" class="business-fields-history">
        <summary>{{ tr('ui.equipment.text1249') }}{{ retained.length }}）</summary>
        <dl>
          <template v-for="f in retained" :key="f.id"
            ><dt>{{ f.name }} <ElTag v-if="!f.enabled" size="small" type="info">{{ tr('ui.equipment.text0262') }}</ElTag></dt>
            <dd><template v-if="f.type === 'DEPARTMENT' || f.source === 'department'"><template v-for="(department, index) in departmentValues(f)" :key="department.id"><span v-if="index">、</span><DepartmentText :value="department.name" :department-id="department.id" /></template></template><template v-else>{{ display(f) }}</template></dd></template
          >
        </dl>
      </details>
    </section>
  </template>
</template>
<style scoped>
.business-fields-section {
  padding: 12px;
  background: var(--surface, #fff);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  box-sizing: border-box;
  container-type: inline-size;
}
.business-fields-section h4 {
  font-size: 14px;
  margin: 0 0 12px;
  font-weight: 600;
  color: var(--text);
}
.business-fields-grid {
  display: grid;
  --business-effective-columns: var(--business-columns, 2);
  grid-template-columns: repeat(var(--business-effective-columns), minmax(0, 1fr));
  column-gap: 20px;
  row-gap: 12px;
}
.business-fields-grid > :deep(.app-form-field) {
  margin-bottom: 0;
}
.business-fields-grid > :deep(*) {
  min-width: 0;
  grid-column: span min(var(--business-span, 1), var(--business-effective-columns));
}
.business-field-control {
  width: 100%;
  min-width: 0;
}
.business-field-control small {
  font-size: 12px;
  display: block;
  color: var(--text);
  line-height: 1.5;
  margin-top: 3px;
}
.business-field-value {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.business-fields-history {
  padding: 12px 0;
}
.business-fields-history dl {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 8px;
}
.business-fields-history dd {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.business-fields-loading {
  font-size: 13px;
}
.business-fields-section :deep(.el-date-editor),
.business-fields-section :deep(.el-select) {
  width: 100%;
}
@container (max-width:900px) {
  .business-fields-grid {
    --business-effective-columns: min(var(--business-columns, 2), 2);
  }
}
@container (max-width:620px) {
  .business-fields-grid {
    --business-effective-columns: 1;
  }
}
</style>
