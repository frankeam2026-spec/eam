<script setup lang="ts">
import { computed, ref, nextTick, watch, onUnmounted } from 'vue';
import { ElButton, ElForm, ElTableColumn, ElMessage, ElAlert } from 'element-plus';
import { tr } from '../../i18n';
import {
  ApiError,
  equipmentChildCandidates,
  equipmentBomMaterials,
  saveEquipmentRelations,
  type EquipmentRecord,
} from '../../api';
import type { InventoryMaster } from '../../inventory';
import { useUnsavedChanges } from '../../composables/useUnsavedChanges';
import EquipmentPickerDialog from '../common/EquipmentPickerDialog.vue';
import MaterialPicker from '../common/MaterialPicker.vue';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import AppFormField from '../common/AppFormField.vue';
import AppFormFooter from '../common/AppFormFooter.vue';
import AppDecimalInput from '../common/AppDecimalInput.vue';
import AppIcon from '../common/AppIcon.vue';
const props = defineProps<{
  record: EquipmentRecord;
  kind: 'children' | 'bom';
  coreDirty: boolean;
}>();
const emit = defineEmits<{
  saved: [EquipmentRecord];
  busy: [boolean];
  dirty: [boolean];
  close: [];
}>();
const draft = ref<EquipmentRecord>(JSON.parse(JSON.stringify(props.record)));
const saving = ref(false),
  childPicker = ref(false),
  bomPicker = ref(false);
const fieldErrors = ref<Record<string, string>>({});
const error = ref('');
const title = computed(() =>
  props.kind === 'bom' ? tr('ui.equipment.text0153') : tr('ui.equipment.text0148'),
);
const { dirty, markSaved, permitLeave } = useUnsavedChanges(
  () => (props.kind === 'bom' ? draft.value.relatedSpares : draft.value.children),
  () => true,
  () => saving.value,
  {
    localOnly: true,
  },
);
markSaved();
watch(dirty, (value) => emit('dirty', value));
onUnmounted(() => emit('dirty', false));
defineExpose({ permitLeave });
type EquipmentChild = NonNullable<EquipmentRecord['children']>[number];
type EquipmentBom = NonNullable<EquipmentRecord['relatedSpares']>[number];
function addChild() {
  childPicker.value = true;
}
function selectChildren(rows: EquipmentRecord[]) {
  const ids = new Set((draft.value.children || []).map((x) => x.equipmentId));
  draft.value.children = [
    ...(draft.value.children || []),
    ...rows
      .filter((r) => r.id !== props.record.id && !ids.has(r.id))
      .map((r) => ({
        equipmentId: r.id,
        code: r.code,
        name: r.name,
        model: r.model,
        brand: r.brand,
      })),
  ];
}
function selectMaterials(rows: InventoryMaster[]) {
  const ids = new Set((draft.value.relatedSpares || []).map((x) => x.materialId));
  draft.value.relatedSpares = [
    ...(draft.value.relatedSpares || []),
    ...rows
      .filter((r) => !ids.has(r.id))
      .map((r) => ({
        materialId: r.id,
        code: r.code,
        name: r.name,
        specification: r.model || '',
        unit: r.unit || '',
        quantity: '1.00',
      })),
  ];
}
function loadChildren(q: string, page: number, size: number) {
  return equipmentChildCandidates(
    props.record.id,
    props.record.departmentOrgId || '',
    q,
    page,
    size,
  );
}
async function focusError() {
  await nextTick();
  const first = document.querySelector<HTMLElement>('.equipment-relations-workspace .is-error');
  first?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  first?.querySelector<HTMLInputElement>('input')?.focus({ preventScroll: true });
}
async function save() {
  if (saving.value) return;
  fieldErrors.value = {};
  if (props.kind === 'bom') {
    (draft.value.relatedSpares || []).forEach((row, i) => {
      if (row.legacy) return;
      const value = String(row.quantity ?? '').trim();
      const key = 'relatedSpares.' + i + '.quantity';
      if (
        !/^\d+(\.\d{1,2})?$/.test(value) ||
        Number(value) <= 0 ||
        value.split('.')[0]!.length > 16
      )
        fieldErrors.value[key] = tr('equipment.editor.quantityError');
    });
    if (Object.keys(fieldErrors.value).length) {
      await focusError();
      return;
    }
  }
  saving.value = true;
  emit('busy', true);
  error.value = '';
  try {
    const result = await saveEquipmentRelations(
      props.record.id,
      props.kind,
      props.record.rowVersion,
      props.kind === 'bom' ? draft.value.relatedSpares || [] : draft.value.children || [],
    );
    draft.value = JSON.parse(JSON.stringify(result.data));
    markSaved();
    emit('saved', result.data);
    ElMessage.success(tr('equipment.editor.relationSaved'));
  } catch (e) {
    if (e instanceof ApiError && Object.keys(e.fieldErrors).length) {
      fieldErrors.value = e.fieldErrors;
      await focusError();
    }
    error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  } finally {
    saving.value = false;
    emit('busy', false);
  }
}
</script>
<template>
  <div class="equipment-relations-workspace">
    <div class="relations-body">
      <ElAlert
        v-if="coreDirty"
        :title="tr('equipment.editor.coreDraftRetained')"
        type="info"
        :closable="false"
        show-icon
      />
      <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <ElForm :model="draft" :disabled="saving" class="relations-form" @submit.prevent>
        <section v-if="kind === 'children'" class="form-section relation-section">
          <div class="relation-title">
            <h3><i></i>{{ tr('ui.equipment.text0148') }}</h3>
            <ElButton class="relation-add" @click="addChild">
              <AppIcon name="Plus" /> {{ tr('ui.equipment.text0149') }}
            </ElButton>
          </div>
          <div class="relation-table-wrap">
            <AppBusinessTable
              :rows="draft.children"
              :pagination="false"
              :fill="false"
              :framed="false"
              class="business-table relation-table"
              ><!-- @vue-generic {EquipmentChild} -->
              <ElTableColumn :min-width="150"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0078') }}</div></template
                ><template #default="{ row: child, $index: index }">{{
                  child.code
                }}</template></ElTableColumn
              ><!-- @vue-generic {EquipmentChild} -->
              <ElTableColumn :min-width="210"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0081') }}</div></template
                ><template #default="{ row: child, $index: index }"
                  >{{ child.name
                  }}<small v-if="child.legacy" class="legacy-relation">{{
                    tr('ui.equipment.text0150')
                  }}</small></template
                ></ElTableColumn
              ><!-- @vue-generic {EquipmentChild} -->
              <ElTableColumn :min-width="150"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0093') }}</div></template
                ><template #default="{ row: child, $index: index }">{{
                  child.brand
                }}</template></ElTableColumn
              ><!-- @vue-generic {EquipmentChild} -->
              <ElTableColumn :min-width="150"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0091') }}</div></template
                ><template #default="{ row: child, $index: index }">{{
                  child.model
                }}</template></ElTableColumn
              ><!-- @vue-generic {EquipmentChild} -->
              <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
                ><template #default="{ row: child, $index: index }">
                  <ElButton
                    type="danger"
                    link
                    class="relation-delete"
                    @click="draft.children?.splice(index, 1)"
                  >
                    {{ tr('ui.equipment.text0151') }}
                  </ElButton>
                </template></ElTableColumn
              ><template #empty
                ><div v-if="!draft.children?.length">
                  {{ tr('ui.equipment.text0152') }}
                </div></template
              ></AppBusinessTable
            >
          </div>
        </section>
        <section v-else class="form-section relation-section">
          <div class="relation-title">
            <h3><i></i>{{ tr('ui.equipment.text0153') }}</h3>
            <ElButton class="relation-add" @click="bomPicker = true">
              <AppIcon name="Plus" /> {{ tr('ui.equipment.text0149') }}
            </ElButton>
          </div>
          <div class="relation-table-wrap">
            <AppBusinessTable
              :rows="draft.relatedSpares"
              :pagination="false"
              :fill="false"
              :framed="false"
              class="business-table relation-table"
              ><!-- @vue-generic {EquipmentBom} -->
              <ElTableColumn :min-width="150"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0154') }}</div></template
                ><template #default="{ row: part, $index: index }">{{
                  part.code
                }}</template></ElTableColumn
              ><!-- @vue-generic {EquipmentBom} -->
              <ElTableColumn :min-width="210"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0155') }}</div></template
                ><template #default="{ row: part, $index: index }"
                  >{{ part.name
                  }}<small v-if="part.legacy" class="legacy-relation">{{
                    tr('ui.equipment.text0150')
                  }}</small></template
                ></ElTableColumn
              ><!-- @vue-generic {EquipmentBom} -->
              <ElTableColumn :min-width="150"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0091') }}</div></template
                ><template #default="{ row: part, $index: index }">{{
                  part.specification
                }}</template></ElTableColumn
              ><!-- @vue-generic {EquipmentBom} -->
              <ElTableColumn :min-width="100"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0095') }}</div></template
                ><template #default="{ row: part }">{{ part.unit }}</template></ElTableColumn
              ><!-- @vue-generic {EquipmentBom} -->
              <ElTableColumn :min-width="150"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0144') }}</div></template
                ><template #default="{ row: part, $index: index }"
                  ><span v-if="part.legacy">{{ part.quantity }}</span
                  ><AppFormField
                    v-else
                    :data-field="'relatedSpares.' + index + '.quantity'"
                    :prop="'relatedSpares.' + index + '.quantity'"
                    :error="fieldErrors['relatedSpares.' + index + '.quantity']"
                    label=""
                    :label-width="0"
                    ><AppDecimalInput
                      v-model="part.quantity" /></AppFormField></template></ElTableColumn
              ><!-- @vue-generic {EquipmentBom} -->
              <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
                ><template #default="{ row: part, $index: index }">
                  <ElButton
                    type="danger"
                    link
                    class="relation-delete"
                    @click="draft.relatedSpares?.splice(index, 1)"
                  >
                    {{ tr('ui.equipment.text0151') }}
                  </ElButton>
                </template></ElTableColumn
              ><template #empty
                ><div v-if="!draft.relatedSpares?.length">
                  {{ tr('ui.equipment.text0156') }}
                </div></template
              ></AppBusinessTable
            >
          </div>
        </section>
      </ElForm>
    </div>
    <AppFormFooter>
      <template #hint>{{
        dirty ? tr('equipment.editor.unsaved') : tr('equipment.editor.noChanges')
      }}</template>
      <ElButton :disabled="saving" @click="emit('close')">{{
        tr('equipment.editor.backEdit')
      }}</ElButton>
      <ElButton type="primary" :loading="saving" @click="save">{{
        tr('equipment.editor.saveRelation', { p0: title })
      }}</ElButton>
    </AppFormFooter>
  </div>
  <EquipmentPickerDialog
    v-model="childPicker"
    multiple
    :load-rows="loadChildren"
    :excluded-ids="(draft.children || []).map((x) => x.equipmentId || '')"
    @select-multiple="selectChildren"
  />
  <MaterialPicker
    v-model="bomPicker"
    :load-rows="equipmentBomMaterials"
    :disabled-ids="(draft.relatedSpares || []).map((x) => x.materialId || '')"
    @select="selectMaterials"
  />
</template>
<style scoped>
.equipment-relations-workspace {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
}
.relations-body {
  overflow: auto;
  padding: 16px;
  flex: 1;
}
.relations-body > .el-alert {
  margin-bottom: 16px;
}
.form-section {
  padding: 20px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.relation-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.relation-title h3 {
  margin: 0;
  font-size: 14px;
}
.relation-table :deep(.el-input),
.relation-table :deep(.el-select) {
  width: 100%;
  min-width: 0;
}
.relation-table :deep(.app-form-field) {
  margin: 0;
}
.legacy-relation {
  display: block;
  color: var(--warning);
}
</style>
