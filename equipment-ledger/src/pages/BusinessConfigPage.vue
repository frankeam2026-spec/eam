<script setup lang="ts">import { tr } from '../i18n';

import { computed, ref, onMounted } from 'vue';
import {
  ElTabs,
  ElTabPane,
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElSwitch,
  ElInputNumber,
  ElTableColumn,
  ElTag,
  ElForm,
  ElFormItem,
  ElAlert,
  ElMessage,
  type FormInstance,
} from 'element-plus';
import AppListToolbar from '../components/common/AppListToolbar.vue';
import AppBusinessTable from '../components/common/AppBusinessTable.vue';
import AppFormFooter from '../components/common/AppFormFooter.vue';
import AppDialog from '../components/common/AppDialog.vue';
import AppLoadState from '../components/common/AppLoadState.vue';
import BusinessFieldInput from '../components/business/BusinessFieldInput.vue';
import BusinessFormDesigner from '../components/business/BusinessFormDesigner.vue';
import BusinessRuleEditor from '../components/business/BusinessRuleEditor.vue';
import BusinessOptionsEditor from '../components/business/BusinessOptionsEditor.vue';
import {
  businessApi,
  newField,
  FIELD_TYPES,
  formLayout,
  fieldSpan,
  inlineOptions,
  optionError,
  type BusinessField,
  type BusinessObject,
  type Schema,
  type FieldType,
} from '../businessConfig';
import { useAuthStore } from '../stores/auth';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { confirmAction } from '../services/dialog';
const auth = useAuthStore(),
  object = ref<BusinessObject>('equipment'),
  schema = ref<Schema>({
    draft: { fields: [] },
    published: { fields: [] },
    version: 0,
    publishedVersion: 0,
  });
const objects = ref<{ key: BusinessObject; name: string }[]>([]),
  loading = ref(true),
  busy = ref(false),
  error = ref('');
const designer = ref<InstanceType<typeof BusinessFormDesigner>>();
const fieldOptionErrors = ref(false);
const fieldOpen = ref(false),
  field = ref(newField()),
  fieldTab = ref('basic'),
  fieldForm = ref<FormInstance>();
const versionsOpen = ref(false),
  versions = ref<{ version: number; created_at: string; actor_id: string }[]>([]),
  reviewOpen = ref(false),
  eventsOpen = ref(false),
  events = ref<{ actor: string; action: string; time: string; details: unknown }[]>([]);
const guard = useUnsavedChanges(
  () => schema.value.draft,
  () => !loading.value,
  () => busy.value,
);
const fieldGuard = useUnsavedChanges(
  () => field.value,
  () => fieldOpen.value,
  () => busy.value,
);
const editable = computed(() => auth.can('businessConfig', '编辑'));
const publishedIds = computed(() => new Set(schema.value.published.fields.map((f) => f.id)));
const differences = computed(() => {
  const old = new Map(schema.value.published.fields.map((f) => [f.id, f]));
  const changes = schema.value.draft.fields
    .filter((f) => JSON.stringify(f) !== JSON.stringify(old.get(f.id)))
    .map((f) => ({
      name: f.name,
      action: !old.has(f.id) ? '新增' : !f.enabled ? '停用' : '修改',
      required: f.required ? '必填' : '选填',
    }));
  if (
    JSON.stringify(formLayout(schema.value.draft)) !==
    JSON.stringify(formLayout(schema.value.published))
  )
    changes.unshift({
      name: '表单布局',
      action: '修改',
      required: `${formLayout(schema.value.draft).columns} 列`,
    });
  return changes;
});
let revision = 0;
async function load() {
  const r = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const [catalog, current] = await Promise.all([
      businessApi.objects(),
      businessApi.schema(object.value),
    ]);
    if (r !== revision) return;
    objects.value = catalog.data;
    schema.value = current;
    guard.markSaved();
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('ui.equipment.text0160');
  } finally {
    if (r === revision) loading.value = false;
  }
}
async function changeObject(key: string) {
  if (key === object.value) return;
  if (!(await guard.permitLeave())) return;
  object.value = key as BusinessObject;
  await load();
}
function editField(row?: BusinessField) {
  field.value = row
    ? JSON.parse(JSON.stringify(row))
    : { ...newField(), sortOrder: schema.value.draft.fields.length };
  field.value.span = fieldSpan(field.value, formLayout(schema.value.draft).columns);
  fieldOptionErrors.value = false;
  fieldTab.value = 'basic';
  fieldOpen.value = true;
  fieldGuard.markSaved();
}
function typeChanged(type: FieldType) {
  field.value.source = type === 'USER' ? 'user' : type === 'DEPARTMENT' ? 'department' : '';
  if (['CHOICE', 'MULTI_CHOICE'].includes(type)) field.value.options = [];
  else delete field.value.options;
  field.value.multiple = false;
  delete field.value.defaultValue;
  delete field.value.defaultLabels;
  if (type === 'LONG_TEXT') {
    field.value.width = 'full';
    field.value.maxLength = 4000;
  } else field.value.maxLength = 200;
  field.value.span = type === 'LONG_TEXT' ? formLayout(schema.value.draft).columns : 1;
}
async function applyField() {
  const error = optionError(field.value);
  if (error) {
    fieldTab.value = 'basic';
    fieldOptionErrors.value = true;
    ElMessage.error(error);
    return;
  }
  if (!(await fieldForm.value?.validate().catch(() => false))) {
    fieldTab.value = 'basic';
    return;
  }
  field.value.width = field.value.span === formLayout(schema.value.draft).columns ? 'full' : 'half';
  const index = schema.value.draft.fields.findIndex((f) => f.id === field.value.id);
  const copy = JSON.parse(JSON.stringify(field.value));
  if (index >= 0) schema.value.draft.fields[index] = copy;
  else schema.value.draft.fields.push(copy);
  fieldGuard.markSaved();
  fieldOpen.value = false;
}
async function removeField(row: BusinessField) {
  if (publishedIds.value.has(row.id)) {
    row.enabled = !row.enabled;
    return;
  }
  if (!(await confirmAction(tr('ui.common.dynamic61',{p0:row.name})))) return;
  schema.value.draft.fields = schema.value.draft.fields.filter((f) => f.id !== row.id);
}
async function save() {
  const validation = designer.value?.validate();
  if (validation) {
    ElMessage.error(validation);
    return false;
  }
  busy.value = true;
  try {
    schema.value = await businessApi.save(object.value, schema.value.draft, schema.value.version);
    guard.markSaved();
    ElMessage.success(tr('ui.equipment.text1632'));
    return true;
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0195'));
    return false;
  } finally {
    busy.value = false;
  }
}
async function review() {
  if (guard.dirty.value && !(await save())) return;
  reviewOpen.value = true;
}
async function publish() {
  busy.value = true;
  try {
    schema.value = await businessApi.publish(object.value, schema.value.version);
    guard.markSaved();
    reviewOpen.value = false;
    ElMessage.success(tr('ui.equipment.text1633'));
    window.dispatchEvent(
      new CustomEvent('eam-business-config-published', { detail: object.value }),
    );
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0259'));
  } finally {
    busy.value = false;
  }
}
async function showVersions() {
  try {
    versions.value = (await businessApi.versions(object.value)).data;
    versionsOpen.value = true;
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
async function restore(version: number) {
  if (!(await guard.permitLeave())) return;
  busy.value = true;
  try {
    schema.value = await businessApi.restore(object.value, version, schema.value.version);
    guard.markSaved();
    versionsOpen.value = false;
    ElMessage.success(tr('ui.equipment.text1634'));
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
async function showEvents() {
  try {
    events.value = (await businessApi.events(object.value)).data;
    eventsOpen.value = true;
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
async function cancel() {
  if (!(await guard.permitLeave())) return;
  await load();
}
onMounted(load);
</script>
<template>
  <section class="content business-config-page">
    <AppLoadState v-if="loading || error" :loading="loading" :error="error" @retry="load" />
    <template v-else
      ><div class="business-config-fields">
        <AppListToolbar>
          <ElSelect
            :model-value="object"
            :aria-label="tr('ui.equipment.text1635')"
            class="business-object-select"
            @update:model-value="changeObject(String($event))"
          >
            <ElOption v-for="o in objects" :key="o.key" :value="o.key" :label="o.name" />
          </ElSelect>
          <ElTag>{{ tr('ui.equipment.text1636') }} {{ schema.publishedVersion }}</ElTag>
          <ElTag v-if="guard.dirty.value" type="warning">{{ tr('ui.equipment.text0681') }}</ElTag>
          <div class="business-toolbar-right">
            <ElButton @click="showVersions">{{ tr('ui.equipment.text1637') }}</ElButton
            ><ElButton @click="showEvents">{{ tr('ui.equipment.text1638') }}</ElButton>
          </div>
        </AppListToolbar>
        <BusinessFormDesigner
          ref="designer"
          v-model="schema.draft"
          :published-fields="schema.published.fields"
          :published-ids="publishedIds"
          :readonly="!editable"
          @edit="editField"
          @remove="removeField"
        />
      </div>
      <AppFormFooter
        ><ElButton :disabled="busy" @click="cancel">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton v-if="editable" :loading="busy" @click="save">{{ tr('ui.equipment.text0275') }}</ElButton
        ><ElButton
          v-if="auth.can('businessConfig', '发布')"
          type="primary"
          :loading="busy"
          @click="review"
          >{{ tr('ui.equipment.text0257') }}</ElButton
        ></AppFormFooter
      ></template
    >
    <AppDialog
      v-model="fieldOpen"
      :title="editable ? tr('ui.equipment.text1639') : tr('ui.equipment.text1640')"
      width="900px"
      :busy="busy"
      :before-close="fieldGuard.permitLeave"
      ><ElTabs v-model="fieldTab"
        ><ElTabPane :label="tr('ui.equipment.text1641')" name="basic" /><ElTabPane :label="tr('ui.equipment.text1642')" name="rules"
      /></ElTabs>
      <ElForm
        ref="fieldForm"
        :model="field"
        label-width="100px"
        :disabled="!editable"
        class="business-field-settings"
        v-show="fieldTab === 'basic'"
      >
        <ElFormItem
          :label="tr('ui.equipment.text1267')"
          prop="name"
          :rules="[{ required: true, message: '请输入字段名称', trigger: 'blur' }]"
          ><ElInput v-model="field.name" maxlength="100"
        /></ElFormItem>
        <ElFormItem :label="tr('ui.equipment.text1643')"
          ><ElSelect
            v-model="field.type"
            :disabled="publishedIds.has(field.id)"
            @change="typeChanged"
            ><ElOption
              v-for="(name, key) in FIELD_TYPES"
              :key="key"
              :value="key"
              :label="name" /></ElSelect
        ></ElFormItem>
        <div v-if="inlineOptions(field)" class="business-config-wide">
          <BusinessOptionsEditor
            :model-value="field.options || []"
            :published-ids="
              schema.published.fields.find((f) => f.id === field.id)?.options?.map((o) => o.id) ||
              []
            "
            :disabled="!editable"
            :show-errors="fieldOptionErrors"
            @update:model-value="field.options = $event"
          />
        </div>
        <ElFormItem :label="tr('ui.equipment.text1644')"
          ><ElSelect v-model="field.span"
            ><ElOption
              v-for="n in formLayout(schema.draft).columns"
              :key="n"
              :value="n"
              :label="`${n} 列（${Math.round((n / formLayout(schema.draft).columns) * 100)}%）`" /></ElSelect></ElFormItem
        ><ElFormItem :label="tr('ui.equipment.text0406')"
          ><ElInputNumber
            v-model="field.sortOrder"
            :min="0"
            :max="999999"
            controls-position="right"
        /></ElFormItem>
        <ElFormItem :label="tr('ui.equipment.text0794')"><ElSwitch v-model="field.required" /></ElFormItem
        ><ElFormItem :label="tr('ui.equipment.text0216')"><ElSwitch v-model="field.enabled" /></ElFormItem
        ><ElFormItem v-if="['USER', 'DEPARTMENT'].includes(field.type)" :label="tr('ui.equipment.text1645')"
          ><ElSwitch v-model="field.multiple" :disabled="publishedIds.has(field.id)"
        /></ElFormItem>
        <ElFormItem v-if="['TEXT', 'LONG_TEXT'].includes(field.type)" :label="tr('ui.equipment.text1646')"
          ><ElInputNumber
            v-model="field.maxLength"
            :min="1"
            :max="20000"
            controls-position="right" /></ElFormItem
        ><ElFormItem v-if="field.type === 'NUMBER'" :label="tr('ui.equipment.text0791')"
          ><ElInput v-model="field.min" /></ElFormItem
        ><ElFormItem v-if="field.type === 'NUMBER'" :label="tr('ui.equipment.text0793')"
          ><ElInput v-model="field.max"
        /></ElFormItem>
        <ElFormItem :label="tr('ui.equipment.text1647')"><ElSwitch v-model="field.listable" /></ElFormItem
        ><ElFormItem :label="tr('ui.equipment.text1648')"><ElSwitch v-model="field.filterable" /></ElFormItem
        ><ElFormItem :label="tr('ui.equipment.text1649')"><ElSwitch v-model="field.exportable" /></ElFormItem
        ><ElFormItem :label="tr('ui.equipment.text1270')"
          ><ElInput v-model="field.placeholder" maxlength="200" /></ElFormItem
        ><ElFormItem :label="tr('ui.equipment.text1650')"
          ><ElInput v-model="field.description" maxlength="500"
        /></ElFormItem>
        <ElFormItem :label="tr('ui.equipment.text1651')" class="business-config-wide"
          ><BusinessFieldInput
            v-model="field.defaultValue"
            :object="object"
            :field="field"
            :labels="field.defaultLabels"
            @labels="field.defaultLabels = $event" /></ElFormItem
      ></ElForm>
      <div v-if="fieldTab === 'rules'">
        <h4>{{ tr('ui.equipment.text1652') }}</h4>
        <p>{{ tr('ui.equipment.text1653') }}</p>
        <BusinessRuleEditor
          v-model="field.visibleWhen"
          :object="object"
          :fields="schema.draft.fields"
          :field-id="field.id"
          :disabled="!editable"
        />
        <h4>{{ tr('ui.equipment.text1654') }}</h4>
        <p>{{ tr('ui.equipment.text1655') }}</p>
        <BusinessRuleEditor
          v-model="field.requiredWhen"
          :object="object"
          :fields="schema.draft.fields"
          :field-id="field.id"
          :disabled="!editable"
        />
      </div>
      <template #footer
        ><ElButton
          @click="
            async () => {
              if (await fieldGuard.permitLeave()) fieldOpen = false;
            }
          "
          >{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton v-if="editable" type="primary" @click="applyField">{{ tr('ui.equipment.text0402') }}</ElButton></template
      ></AppDialog
    >
    <AppDialog v-model="reviewOpen" :title="tr('ui.equipment.text1656')" width="620px" :busy="busy"
      ><ElAlert
        :title="tr('ui.equipment.text1657')"
        type="info"
        :closable="false"
      /><AppBusinessTable :rows="differences" :pagination="false"
        ><ElTableColumn :label="tr('ui.equipment.text0943')" prop="name" /><ElTableColumn
          :label="tr('ui.equipment.text1658')"
          prop="action"
          width="100" /><ElTableColumn
          :label="tr('ui.equipment.text1659')"
          prop="required"
          width="100" /></AppBusinessTable
      ><template #footer
        ><ElButton @click="reviewOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="publish">{{ tr('ui.equipment.text1660') }}</ElButton></template
      ></AppDialog
    >
    <AppDialog v-model="versionsOpen" :title="tr('ui.equipment.text1637')" width="650px"
      ><AppBusinessTable :rows="versions" :pagination="false"
        ><ElTableColumn :label="tr('ui.equipment.text1661')" prop="version" width="90" /><ElTableColumn
          :label="tr('ui.equipment.text1662')"
          prop="created_at"
        /><ElTableColumn v-if="editable" :label="tr('ui.equipment.text0145')" width="160" fixed="right"
          ><template #default="{ row }"
            ><ElButton link type="primary" @click="restore(row.version)"
              >{{ tr('ui.equipment.text1663') }}</ElButton
            ></template
          ></ElTableColumn
        ></AppBusinessTable
      ></AppDialog
    >
    <AppDialog v-model="eventsOpen" :title="tr('ui.equipment.text1664')" width="700px"
      ><AppBusinessTable :rows="events" :pagination="false"
        ><ElTableColumn :label="tr('ui.equipment.text1115')" prop="actor" width="120" /><ElTableColumn
          :label="tr('ui.equipment.text0145')"
          prop="action"
          min-width="180" /><ElTableColumn :label="tr('ui.equipment.text0174')" prop="time" width="190" /></AppBusinessTable
    ></AppDialog>
  </section>
</template>
<style scoped>
.business-toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}
.business-config-page {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  box-sizing: border-box;
}
.business-config-fields {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  /* gap: 12px; */
}
.business-object-select {
  width: 180px;
}
.business-toolbar-right {
  margin-left: auto;
  display: flex;
  gap: 12px;
  align-items: center;
}
.business-toolbar-right .el-button {
  margin: 0;
}
.business-field-settings {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
.business-config-wide {
  grid-column: 1/-1;
}
.business-field-settings :deep(.el-select),
.business-field-settings :deep(.el-input-number) {
  width: 100%;
}
.business-config-page p {
  font-size: 13px;
  color: var(--text);
}
</style>
