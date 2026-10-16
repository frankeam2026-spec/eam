<script setup lang="ts">
import 'element-plus/es/components/upload/style/css';
import EquipmentPickerDialog from './common/EquipmentPickerDialog.vue';
import MaterialPicker from './common/MaterialPicker.vue';
import type { InventoryMaster } from '../inventory';
import AppFormField from './common/AppFormField.vue';
import AppAttachments from './common/AppAttachments.vue';
import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import AppDecimalInput from './common/AppDecimalInput.vue';
import AppBackButton from './common/AppBackButton.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import { computed, ref, watch, nextTick } from 'vue';
import { storeToRefs } from 'pinia';
import {
  ElForm,
  ElInput,
  ElInputNumber,
  ElSelect,
  ElOption,
  ElDatePicker,
  ElMessage,
  ElButton,
  ElUpload,
  ElTableColumn,
  type UploadRequestOptions,
} from 'element-plus';
import {
  equipmentChildCandidates,
  equipmentBomMaterials,
  uploadEquipmentFile,
  equipmentBatchOptions,
  type EquipmentRecord,
  type OrganizationRecord,
} from '../api';
import { useBaseDataStore } from '../stores/baseData';
import { EQUIPMENT_STATES } from '../config/equipment';
import TreeSelect from './TreeSelect.vue';
import { locationPath } from '../utils/locationPath';
import BooleanSwitch from './BooleanSwitch.vue';
const editing = defineModel<EquipmentRecord | null>({ required: true });
const drawer = defineModel<boolean>('open', { required: true });
const props = defineProps<{ isNew: boolean; busy: boolean }>();
const emit = defineEmits<{ save: []; close: []; uploading: [boolean] }>();
const { categoryData, locationData, unitData, useStateData, sourceData } =
  storeToRefs(useBaseDataStore());
const categoryTreeOptions = computed(() =>
  categoryData.value.filter((x) => x.status === '启用').map((x) => ({ ...x, value: x.id })),
);
const locationTreeOptions = computed(() => {
  const companyId =
    departmentCompany(resolvedDepartment.value?.id || '')?.id || editing.value?.companyOrgId;
  return locationData.value
    .filter((x) => x.status === '启用' && (!companyId || !x.companyId || x.companyId === companyId))
    .map((x) => ({ ...x, value: x.id }));
});
const locationValue = computed({
  get: () => editing.value?.locationId || '',
  set: (id: string) => {
    if (!editing.value) return;
    editing.value.locationId = id;
    const node = locationData.value.find((x) => x.id === id);
    editing.value.functionLocation = node ? locationPath(node, locationData.value) : '';
  },
});
function baseSelection(
  field: 'category' | 'unit' | 'useState' | 'source',
  collection: typeof categoryData,
) {
  const idField = (
    { category: 'categoryId', unit: 'unitId', useState: 'useStateId', source: 'sourceId' } as const
  )[field];
  return computed({
    get: () => editing.value?.[idField] || '',
    set: (id: string) => {
      if (!editing.value) return;
      editing.value[idField] = id;
      editing.value[field] = collection.value.find((x) => x.id === id)?.name || '';
    },
  });
}
const categoryValue = baseSelection('category', categoryData),
  unitValue = baseSelection('unit', unitData),
  useStateValue = baseSelection('useState', useStateData),
  sourceValue = baseSelection('source', sourceData);
const employees = ref<{ id: string; name: string; account: string }[]>([]);
const ownerValue = computed({
  get: () => editing.value?.ownerIds ?? (editing.value?.ownerId ? [editing.value.ownerId] : []),
  set: (ids: string[]) => {
    if (!editing.value) return;
    const old = editing.value.owners || [];
    editing.value.ownerIds = ids;
    editing.value.ownerId = ids[0] || '';
    editing.value.owners = ids.map((id) => ({
      id,
      name:
        employees.value.find((u) => u.id === id)?.name || old.find((u) => u.id === id)?.name || '',
    }));
    editing.value.owner = editing.value.owners.map((u) => u.name).join('、');
  },
});
const retainedOwners = computed(() =>
  (editing.value?.owners || []).filter((u) => !employees.value.some((x) => x.id === u.id)),
);
const fieldErrors = ref<Record<string, string>>({});
const requiredFields: [keyof EquipmentRecord, string][] = [
  ['name', '请输入设备名称'],
  ['categoryId', '请选择设备类别'],
  ['unitId', '请选择单位'],
  ['departmentOrgId', '请选择所属部门'],
  ['locationId', '请选择位置'],
  ['state', '请选择设备状态'],
  ['useStateId', '请选择使用状态'],
];
const formRules = computed(() => ({
  ...Object.fromEntries(
    requiredFields.map(([key, message]) => [
      key,
      [{ required: true, message, trigger: ['blur', 'change'] }],
    ]),
  ),
  ...Object.fromEntries(
    (editing.value?.relatedSpares || []).map((row, index) => [
      'relatedSpares.' + index + '.quantity',
      row.legacy
        ? []
        : [
            {
              validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
                const message = bomQuantityError(value);
                callback(message ? new Error(message) : undefined);
              },
              trigger: 'blur',
            },
          ],
    ]),
  ),
}));
async function showErrors(errors: Record<string, string>) {
  fieldErrors.value = errors;
  await nextTick();
  const first = document.querySelector<HTMLElement>(
    '.equipment-editor [data-field="' + Object.keys(errors)[0] + '"]',
  );
  first?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  first?.querySelector<HTMLElement>('input,button')?.focus({ preventScroll: true });
}
function bomQuantityError(value: string) {
  if (!String(value ?? '').trim()) return '请输入数量';
  if (!/^\d+(\.\d+)?$/.test(String(value))) return '请输入有效数量';
  if (Number(value) <= 0) return '数量须大于0';
  if (!/^\d+(\.\d{1,2})?$/.test(String(value))) return '最多两位小数';
  if (!Number.isFinite(Number(value)) || String(value).split('.')[0]!.length > 16)
    return '数量超出范围';
  return '';
}
function validate() {
  const errors = Object.fromEntries(
    requiredFields
      .filter(([key]) => !String(editing.value?.[key] ?? '').trim())
      .map(([key, message]) => [key, message]),
  );
  editing.value?.relatedSpares?.forEach((row, index) => {
    if (row.legacy) return;
    const message = bomQuantityError(row.quantity);
    if (message) errors['relatedSpares.' + index + '.quantity'] = message;
  });
  void showErrors(errors);
  return Object.values(errors);
}
watch(
  () => editing.value,
  () => {
    for (const [key] of requiredFields)
      if (String(editing.value?.[key] ?? '').trim()) delete fieldErrors.value[key];
    editing.value?.relatedSpares?.forEach((row, i) => {
      if (!bomQuantityError(row.quantity))
        delete fieldErrors.value['relatedSpares.' + i + '.quantity'];
    });
  },
  { deep: true },
);
watch(drawer, (open) => {
  if (open) fieldErrors.value = {};
});
watch(
  () => editing.value?.code,
  () => {
    delete fieldErrors.value.code;
  },
);
defineExpose({ validate, showErrors });
const organizations = ref<OrganizationRecord[]>([]);
const organizationError = ref('');
async function loadOrganizations() {
  try {
    const result = await equipmentBatchOptions(props.isNew ? '新增' : '编辑');
    organizations.value = result.departments;
    employees.value = result.users;
    if (
      props.isNew &&
      editing.value &&
      resolvedDepartment.value &&
      !resolvedDepartment.value.disabled
    ) {
      departmentId.value = resolvedDepartment.value.id;
    }
    organizationError.value = '';
  } catch (e) {
    organizationError.value = e instanceof Error ? e.message : '部门加载失败';
  }
}
watch(
  () => [drawer.value, props.isNew],
  () => {
    if (drawer.value) void loadOrganizations();
  },
  { immediate: true },
);
function departmentCompany(id: string) {
  const visited = new Set<string>();
  let node = organizations.value.find((x) => x.id === id);
  while (node && !visited.has(node.id)) {
    visited.add(node.id);
    if (node.type === '公司' || node.type === '集团') return node;
    node = organizations.value.find((x) => x.id === node!.parentId);
  }
}
const resolvedDepartment = computed(() =>
  organizations.value.find(
    (x) =>
      x.type === '部门' &&
      (editing.value?.departmentOrgId
        ? x.id === editing.value.departmentOrgId
        : x.name === editing.value?.department &&
          departmentCompany(x.id)?.name === editing.value?.company),
  ),
);
const departmentNodes = computed(() => {
  const nodes = organizations.value.map((x) => ({
    ...x,
    value: x.id,
    disabled: x.disabled || x.type !== '部门',
  }));
  if (!resolvedDepartment.value && editing.value?.department) {
    nodes.push({
      id: 'legacy-department',
      value: 'legacy-department',
      name: editing.value.department,
      type: '部门',
      parentId: '',
      disabled: true,
      code: '',
      manager: '',
      status: '启用',
    });
  }
  return nodes;
});
const departmentId = computed({
  get: () => resolvedDepartment.value?.id || (editing.value?.department ? 'legacy-department' : ''),
  set: (id: string) => {
    if (!editing.value) return;
    const department = organizations.value.find((x) => x.id === id);
    editing.value.departmentOrgId = id;
    editing.value.department = department?.name || '';
    editing.value.company = departmentCompany(id)?.name || '';
    editing.value.companyOrgId = departmentCompany(id)?.id;
    if (
      editing.value.locationId &&
      !locationData.value.some(
        (x) => x.id === editing.value!.locationId && x.companyId === editing.value!.companyOrgId,
      )
    ) {
      editing.value.locationId = '';
      editing.value.functionLocation = '';
    }
  },
});
const childPicker = ref(false),
  bomPicker = ref(false);
function addChild() {
  if (!editing.value?.departmentOrgId) {
    void showErrors({ departmentOrgId: '请先选择所属部门' });
    return;
  }
  childPicker.value = true;
}
function selectChildren(rows: EquipmentRecord[]) {
  if (!editing.value) return;
  const existing = new Set((editing.value.children || []).map((x) => x.equipmentId));
  editing.value.children = [
    ...(editing.value.children || []),
    ...rows
      .filter((r) => r.id !== editing.value?.id && !existing.has(r.id))
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
  if (!editing.value) return;
  const existing = new Set((editing.value.relatedSpares || []).map((x) => x.materialId));
  editing.value.relatedSpares = [
    ...(editing.value.relatedSpares || []),
    ...rows
      .filter((r) => !existing.has(r.id))
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
    props.isNew ? '' : editing.value?.id || '',
    editing.value?.departmentOrgId || '',
    q,
    page,
    size,
  );
}
const uploading = ref(false);
let uploadCount = 0;
let documentUploads: Promise<unknown> = Promise.resolve();
function beginUpload() {
  uploadCount++;
  uploading.value = true;
  emit('uploading', true);
}
function endUpload() {
  uploadCount--;
  uploading.value = uploadCount > 0;
  emit('uploading', uploading.value);
}
function uploadError(error: Error) {
  ElMessage.error(error.message || '上传失败');
}
function imageAllowed(file: File) {
  if (!editing.value || props.busy) return false;
  if (!/\.(png|jpe?g|gif)$/i.test(file.name) || file.size > 20 * 1024 * 1024) {
    ElMessage.warning('请选择不超过 20MB 的 PNG、JPG 或 GIF 图片');
    return false;
  }
  return true;
}
function uploadDocument({ file }: UploadRequestOptions) {
  const draft = editing.value;
  if (!draft) return Promise.reject(new Error('请先打开设备编辑页面'));
  beginUpload();
  const request = documentUploads
    .then(async () => {
      const stored = await uploadEquipmentFile(file);
      draft.documents = [
        ...(draft.documents || []),
        {
          id: stored.id,
          url: stored.url,
          size: stored.size,
          originalName: stored.name,
          code: '',
          name: stored.name,
          type: stored.name.split('.').pop() || '',
          security: '普通',
          quantity: 1,
          remark: '',
        },
      ];
      ElMessage.success('附件上传成功，保存设备后生效');
      return stored;
    })
    .finally(endUpload);
  documentUploads = request.catch(() => undefined);
  return request;
}
type EquipmentPicture = NonNullable<EquipmentRecord['images']>[number];
function pictures(draft: EquipmentRecord): EquipmentPicture[] {
  return (
    draft.images ??
    (draft.imageFileId ? [{ id: draft.imageFileId, name: draft.imageName || '' }] : [])
  );
}
const imageList = computed(() => (editing.value ? pictures(editing.value) : []));
const equipmentImageFiles = computed(() =>
  imageList.value.map((image) => ({
    ...image,
    isImage: true,
    url: `/api/v1/equipment/files/${image.id}/preview`,
  })),
);
function selectImages(files: File[]) {
  for (const file of files) if (imageAllowed(file)) uploadImage(file).catch(uploadError);
}
function setPictures(draft: EquipmentRecord, images: EquipmentPicture[]) {
  draft.images = images;
  draft.imageFileId = images[0]?.id || '';
  draft.imageName = images[0]?.name || '';
}
function removeImage(id: string) {
  if (editing.value)
    setPictures(
      editing.value,
      pictures(editing.value).filter((image) => image.id !== id),
    );
}
function makeFirstImage(id: string) {
  const draft = editing.value;
  if (draft)
    setPictures(draft, [
      ...pictures(draft).filter((image) => image.id === id),
      ...pictures(draft).filter((image) => image.id !== id),
    ]);
}
let imageUploads: Promise<unknown> = Promise.resolve();
function uploadImage(file: File) {
  const draft = editing.value;
  if (!draft) return Promise.reject(new Error('请先打开设备编辑页面'));
  beginUpload();
  const request = imageUploads
    .then(async () => {
      const stored = await uploadEquipmentFile(file);
      setPictures(draft, [...pictures(draft), { id: stored.id, name: stored.name }]);
      return stored;
    })
    .finally(endUpload);
  imageUploads = request.catch(() => undefined);
  return request;
}
type EquipmentDocument = NonNullable<EquipmentRecord['documents']>[number];
type EquipmentChild = NonNullable<EquipmentRecord['children']>[number];
type EquipmentBom = NonNullable<EquipmentRecord['relatedSpares']>[number];
</script>
<template>
  <Transition name="fade"
    ><div v-if="drawer" class="drawer-mask equipment-editor-mask">
      <section
        class="drawer equipment-editor"
        role="dialog"
        aria-modal="true"
        :aria-label="isNew ? '设备登记' : '编辑设备'"
      >
        <header class="drawer-head editor-head">
          <div class="editor-title">
            <AppBackButton label="返回设备台账" :disabled="busy" @click="emit('close')" />
            <div class="editor-heading-line">
              <span>设备台账</span><i>/</i>
              <h2>{{ isNew ? '设备登记' : '编辑设备' }}</h2>
            </div>
            <span v-if="!isNew" class="drawer-code">{{ editing?.code }}</span>
          </div>
          <ElButton
            text
            class="close-btn"
            aria-label="关闭"
            :disabled="busy"
            @click="emit('close')"
          >
            <AppIcon name="Close" />
          </ElButton>
        </header>
        <ElForm
          class="drawer-body editor-body equipment-form"
          v-if="editing"
          :disabled="busy"
          :model="editing"
          :rules="formRules"
          :validate-on-rule-change="false"
          label-width="76px"
          @submit.prevent
        >
          <div class="editor-layout">
            <div class="editor-main-column">
              <section class="form-section">
                <div class="editor-form-groups">
                  <section class="editor-field-group">
                    <h4>基本信息</h4>
                    <div class="editor-grid">
                      <AppFormField
                        data-field="code"
                        prop="code"
                        label="设备编号"
                        :error="fieldErrors.code"
                        ><ElInput
                          v-model="editing.code"
                          :placeholder="isNew ? '不填写自动生成' : '请输入设备编号'"
                          maxlength="32" /></AppFormField
                      ><AppFormField
                        data-field="name"
                        prop="name"
                        label="设备名称"
                        :error="fieldErrors.name"
                        required
                        ><ElInput
                          v-model="editing.name"
                          placeholder="请输入设备名称"
                          maxlength="100" /></AppFormField
                      ><AppFormField
                        data-field="categoryId"
                        prop="categoryId"
                        label="类别"
                        :error="fieldErrors.categoryId"
                        required
                        ><TreeSelect
                          v-model="categoryValue"
                          :nodes="categoryTreeOptions"
                          placeholder="请选择设备类别"
                      /></AppFormField>
                      <label class="editor-field"
                        ><span>资产编号</span
                        ><ElInput v-model="editing.assetNo" placeholder="请输入资产编号" /></label
                      ><label class="editor-field"
                        ><span>序列号</span
                        ><ElInput v-model="editing.serial" placeholder="请输入序列号" /></label
                      ><label class="editor-field"
                        ><span>电子标签码</span
                        ><ElInput v-model="editing.tagCode" placeholder="请输入电子标签码"
                      /></label>
                      <label class="editor-field"
                        ><span>规格型号</span
                        ><ElInput v-model="editing.model" placeholder="请输入规格型号" /></label
                      ><label class="editor-field"
                        ><span>品牌</span
                        ><ElInput v-model="editing.brand" placeholder="请输入品牌" /></label
                      ><AppFormField
                        data-field="unitId"
                        prop="unitId"
                        label="单位"
                        :error="fieldErrors.unitId"
                        required
                        ><ElSelect v-model="unitValue" placeholder="请选择" clearable
                          ><ElOption
                            v-for="item in unitData.filter((x) => x.status === '启用')"
                            :key="item.id"
                            :label="item.name"
                            :value="item.id" /></ElSelect
                      ></AppFormField>
                    </div>
                  </section>
                  <section class="editor-field-group">
                    <h4>归属与位置</h4>
                    <div class="editor-grid">
                      <AppFormField
                        data-field="departmentOrgId"
                        prop="departmentOrgId"
                        label="所属部门"
                        :error="fieldErrors.departmentOrgId"
                        required
                        class="editor-span-2"
                        ><TreeSelect
                          v-model="departmentId"
                          :nodes="departmentNodes"
                          path-value
                          placeholder="请选择所属部门"
                        />
                        <small
                          v-if="!resolvedDepartment && editing.department && organizations.length"
                        >
                          原部门尚未维护，请选择有效部门
                        </small></AppFormField
                      ><ElButton
                        type="primary"
                        link
                        v-if="organizationError"
                        native-type="button"
                        @click="loadOrganizations"
                      >
                        部门加载失败，点击重试</ElButton
                      ><label class="editor-field owner-field"
                        ><span>负责人</span
                        ><ElSelect
                          v-model="ownerValue"
                          multiple
                          filterable
                          clearable
                          collapse-tags
                          collapse-tags-tooltip
                          :max-collapse-tags="2"
                          placeholder="未设置"
                          ><ElOption
                            v-for="u in retainedOwners"
                            :key="u.id"
                            :value="u.id"
                            :label="u.name"
                            disabled
                          />
                          <ElOption
                            v-for="u in employees"
                            :key="u.id"
                            :value="u.id"
                            :label="u.name + '（' + u.account + '）'"
                          />
                        </ElSelect>
                        <small v-if="!ownerValue.length && editing.owner"
                          >原负责人：{{ editing.owner }}（历史记录）<ElButton
                            type="primary"
                            link
                            native-type="button"
                            @click="ownerValue = []"
                          >
                            清除
                          </ElButton></small
                        >
                      </label>
                      <AppFormField
                        data-field="locationId"
                        prop="locationId"
                        label="位置"
                        :error="fieldErrors.locationId"
                        required
                        class="editor-span-2"
                        ><TreeSelect
                          v-model="locationValue"
                          :nodes="locationTreeOptions"
                          placeholder="请选择位置"
                      /></AppFormField>
                    </div>
                  </section>
                  <section class="editor-field-group">
                    <h4>使用与生命周期</h4>
                    <div class="editor-grid">
                      <AppFormField
                        data-field="state"
                        prop="state"
                        label="设备状态"
                        :error="fieldErrors.state"
                        required
                        ><ElSelect v-model="editing.state" placeholder="请选择" clearable
                          ><ElOption
                            v-for="state in EQUIPMENT_STATES"
                            :key="state"
                            :label="state"
                            :value="state" /></ElSelect></AppFormField
                      ><AppFormField
                        data-field="useStateId"
                        prop="useStateId"
                        label="使用状态"
                        :error="fieldErrors.useStateId"
                        required
                        ><ElSelect v-model="useStateValue" placeholder="请选择" clearable
                          ><ElOption
                            v-for="item in useStateData.filter((x) => x.status === '启用')"
                            :key="item.id"
                            :label="item.name"
                            :value="item.id" /></ElSelect></AppFormField
                      ><label class="editor-field"
                        ><span>设备等级</span
                        ><ElSelect v-model="editing.level" placeholder="请选择" clearable
                          ><ElOption label="关键设备" value="关键设备" /><ElOption
                            label="主要设备"
                            value="主要设备" /><ElOption
                            label="一般设备"
                            value="一般设备" /></ElSelect
                      ></label>
                      <label class="editor-field"
                        ><span>设备来源</span
                        ><ElSelect v-model="sourceValue" placeholder="请选择" clearable
                          ><ElOption
                            v-for="item in sourceData.filter((x) => x.status === '启用')"
                            :key="item.id"
                            :label="item.name"
                            :value="item.id" /></ElSelect></label
                      ><label class="editor-field"
                        ><span>供应商</span
                        ><ElInput
                          v-model="editing.supplier"
                          placeholder="请输入供应商名称" /></label
                      ><label class="editor-field"
                        ><span>采购金额</span
                        ><AppDecimalInput v-model="editing.amount" placeholder="请输入金额"
                      /></label>
                      <label class="editor-field"
                        ><span>购置日期</span
                        ><ElDatePicker
                          v-model="editing.purchaseDate"
                          type="date"
                          value-format="YYYY-MM-DD"
                          format="YYYY-MM-DD"
                          placeholder="请选择日期" /></label
                      ><label class="editor-field"
                        ><span>启用日期</span
                        ><ElDatePicker
                          v-model="editing.inServiceDate"
                          type="date"
                          value-format="YYYY-MM-DD"
                          format="YYYY-MM-DD"
                          placeholder="请选择日期" /></label
                      ><label class="editor-field"
                        ><span>保修期至</span
                        ><ElDatePicker
                          v-model="editing.warranty"
                          type="date"
                          value-format="YYYY-MM-DD"
                          format="YYYY-MM-DD"
                          placeholder="请选择日期"
                      /></label>
                      <label class="editor-field"
                        ><span>预计报废日期</span
                        ><ElDatePicker
                          v-model="editing.retirementDate"
                          type="date"
                          value-format="YYYY-MM-DD"
                          format="YYYY-MM-DD"
                          placeholder="请选择日期"
                      /></label>
                      <!-- <div class="boolean-group">
                        <label class="editor-field"
                          ><span>特种设备</span
                          ><BooleanSwitch v-model="editing.specialEquipment" /></label
                        ><label class="editor-field"
                          ><span>计量设备</span><BooleanSwitch v-model="editing.metrologyEquipment"
                        /></label>
                         <label class="editor-field"
                          ><span>开启折旧</span
                          ><BooleanSwitch v-model="editing.depreciationEnabled"
                        /></label>
                      </div> -->
                    </div>
                  </section>
                  <section class="editor-field-group">
                    <h4>技术与补充信息</h4>
                    <div class="editor-grid">
                      <label class="editor-field editor-span-3 editor-textarea"
                        ><span>技术参数</span
                        ><ElInput
                          v-model="editing.technicalParams"
                          :rows="3"
                          maxlength="2000"
                          placeholder="填写设备技术参数"
                          type="textarea" /></label
                      ><label class="editor-field editor-span-3 editor-textarea"
                        ><span>备注</span
                        ><ElInput
                          v-model="editing.remark"
                          :rows="3"
                          maxlength="255"
                          placeholder="填写备注"
                          type="textarea"
                      /></label>
                    </div>
                    <div class="image-upload-row">
                      <span>设备图片</span>
                      <AppAttachments
                        :files="equipmentImageFiles"
                        images-only
                        :disabled="busy"
                        :uploading="uploading"
                        show-cover
                        accept=".png,.jpg,.jpeg,.gif"
                        @select="selectImages"
                        @remove="removeImage"
                        @set-cover="makeFirstImage"
                      />
                    </div>
                  </section>
                </div>
              </section>
            </div>
            <aside class="editor-side-column">
              <section class="form-section relation-section">
                <div class="relation-title">
                  <h3><i></i>设备相关文件</h3>
                  <ElUpload
                    multiple
                    :show-file-list="false"
                    :disabled="busy || uploading"
                    accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx,.txt,.zip,.csv"
                    :http-request="uploadDocument"
                    :on-error="uploadError"
                  >
                    <ElButton class="relation-add" :disabled="busy">
                      <AppIcon v-if="!uploading" name="Upload" />{{
                        uploading ? '上传中…' : '上传'
                      }}
                    </ElButton></ElUpload
                  >
                </div>
                <div class="relation-table-wrap">
                  <AppBusinessTable
                    :rows="editing.documents"
                    :pagination="false"
                    :fill="false"
                    :framed="false"
                    class="business-table relation-table"
                    ><!-- @vue-generic {EquipmentDocument} -->
                    <ElTableColumn :min-width="150"
                      ><template #header><div class="table-header-label">资料编号</div></template
                      ><template #default="{ row: doc, $index: index }"
                        ><ElInput v-model="doc.code" placeholder="编号" /></template></ElTableColumn
                    ><!-- @vue-generic {EquipmentDocument} -->
                    <ElTableColumn :min-width="210"
                      ><template #header><div class="table-header-label">资料名称</div></template
                      ><template #default="{ row: doc, $index: index }">
                        <ElInput v-model="doc.name" placeholder="资料名称" /><a
                          v-if="doc.id"
                          :href="`/api/v1/equipment/files/${doc.id}`"
                          target="_blank"
                          rel="noopener"
                          >下载</a
                        ><span v-else>未上传文件</span>
                      </template></ElTableColumn
                    ><!-- @vue-generic {EquipmentDocument} -->
                    <ElTableColumn :min-width="150"
                      ><template #header><div class="table-header-label">资料类型</div></template
                      ><template #default="{ row: doc, $index: index }"
                        ><ElInput v-model="doc.type" placeholder="类型" /></template></ElTableColumn
                    ><!-- @vue-generic {EquipmentDocument} -->
                    <ElTableColumn :min-width="150"
                      ><template #header><div class="table-header-label">文档密级</div></template
                      ><template #default="{ row: doc, $index: index }">
                        <ElSelect v-model="doc.security"
                          ><ElOption
                            v-for="level in ['普通', '内部', '机密']"
                            :key="level"
                            :value="level"
                            :label="level"
                        /></ElSelect> </template></ElTableColumn
                    ><!-- @vue-generic {EquipmentDocument} -->
                    <ElTableColumn :min-width="150"
                      ><template #header><div class="table-header-label">数量</div></template
                      ><template #default="{ row: doc, $index: index }"
                        ><ElInputNumber
                          v-model="doc.quantity"
                          :min="1"
                          :precision="2"
                          controls-position="right" /></template></ElTableColumn
                    ><!-- @vue-generic {EquipmentDocument} -->
                    <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
                      ><template #header><div class="table-header-label">操作</div></template
                      ><template #default="{ row: doc, $index: index }">
                        <ElButton
                          type="danger"
                          link
                          class="relation-delete"
                          @click="editing?.documents?.splice(index, 1)"
                        >
                          删除
                        </ElButton>
                      </template></ElTableColumn
                    ><template #empty
                      ><div v-if="!editing.documents?.length">暂无相关文件</div></template
                    ></AppBusinessTable
                  >
                </div>
              </section>
              <section class="form-section relation-section">
                <div class="relation-title">
                  <h3><i></i>子设备</h3>
                  <ElButton class="relation-add" @click="addChild">
                    <AppIcon name="Plus" /> 新增
                  </ElButton>
                </div>
                <div class="relation-table-wrap">
                  <AppBusinessTable
                    :rows="editing.children"
                    :pagination="false"
                    :fill="false"
                    :framed="false"
                    class="business-table relation-table"
                    ><!-- @vue-generic {EquipmentChild} -->
                    <ElTableColumn :min-width="150"
                      ><template #header><div class="table-header-label">设备编号</div></template
                      ><template #default="{ row: child, $index: index }">{{
                        child.code
                      }}</template></ElTableColumn
                    ><!-- @vue-generic {EquipmentChild} -->
                    <ElTableColumn :min-width="210"
                      ><template #header><div class="table-header-label">设备名称</div></template
                      ><template #default="{ row: child, $index: index }"
                        >{{ child.name
                        }}<small v-if="child.legacy" class="legacy-relation"
                          >历史关联待确认</small
                        ></template
                      ></ElTableColumn
                    ><!-- @vue-generic {EquipmentChild} -->
                    <ElTableColumn :min-width="150"
                      ><template #header><div class="table-header-label">品牌</div></template
                      ><template #default="{ row: child, $index: index }">{{
                        child.brand
                      }}</template></ElTableColumn
                    ><!-- @vue-generic {EquipmentChild} -->
                    <ElTableColumn :min-width="150"
                      ><template #header><div class="table-header-label">规格型号</div></template
                      ><template #default="{ row: child, $index: index }">{{
                        child.model
                      }}</template></ElTableColumn
                    ><!-- @vue-generic {EquipmentChild} -->
                    <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
                      ><template #header><div class="table-header-label">操作</div></template
                      ><template #default="{ row: child, $index: index }">
                        <ElButton
                          type="danger"
                          link
                          class="relation-delete"
                          @click="editing?.children?.splice(index, 1)"
                        >
                          移除
                        </ElButton>
                      </template></ElTableColumn
                    ><template #empty
                      ><div v-if="!editing.children?.length">暂无子设备</div></template
                    ></AppBusinessTable
                  >
                </div>
              </section>
              <section class="form-section relation-section">
                <div class="relation-title">
                  <h3><i></i>设备BOM</h3>
                  <ElButton class="relation-add" @click="bomPicker = true">
                    <AppIcon name="Plus" /> 新增
                  </ElButton>
                </div>
                <div class="relation-table-wrap">
                  <AppBusinessTable
                    :rows="editing.relatedSpares"
                    :pagination="false"
                    :fill="false"
                    :framed="false"
                    class="business-table relation-table"
                    ><!-- @vue-generic {EquipmentBom} -->
                    <ElTableColumn :min-width="150"
                      ><template #header><div class="table-header-label">料号</div></template
                      ><template #default="{ row: part, $index: index }">{{
                        part.code
                      }}</template></ElTableColumn
                    ><!-- @vue-generic {EquipmentBom} -->
                    <ElTableColumn :min-width="210"
                      ><template #header><div class="table-header-label">物料名称</div></template
                      ><template #default="{ row: part, $index: index }"
                        >{{ part.name
                        }}<small v-if="part.legacy" class="legacy-relation"
                          >历史关联待确认</small
                        ></template
                      ></ElTableColumn
                    ><!-- @vue-generic {EquipmentBom} -->
                    <ElTableColumn :min-width="150"
                      ><template #header><div class="table-header-label">规格型号</div></template
                      ><template #default="{ row: part, $index: index }">{{
                        part.specification
                      }}</template></ElTableColumn
                    ><!-- @vue-generic {EquipmentBom} -->
                    <ElTableColumn :min-width="100"
                      ><template #header><div class="table-header-label">单位</div></template
                      ><template #default="{ row: part }">{{ part.unit }}</template></ElTableColumn
                    ><!-- @vue-generic {EquipmentBom} -->
                    <ElTableColumn :min-width="150"
                      ><template #header><div class="table-header-label">数量</div></template
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
                      ><template #header><div class="table-header-label">操作</div></template
                      ><template #default="{ row: part, $index: index }">
                        <ElButton
                          type="danger"
                          link
                          class="relation-delete"
                          @click="editing?.relatedSpares?.splice(index, 1)"
                        >
                          移除
                        </ElButton>
                      </template></ElTableColumn
                    ><template #empty
                      ><div v-if="!editing.relatedSpares?.length">暂无设备BOM</div></template
                    ></AppBusinessTable
                  >
                </div>
              </section>
            </aside>
          </div>
        </ElForm>
        <AppFormFooter>
          <ElButton :disabled="busy" @click="emit('close')">取消</ElButton
          ><ElButton type="primary" :loading="busy" @click="emit('save')">
            {{ busy ? '保存中…' : '保存' }}
          </ElButton>
        </AppFormFooter>
      </section>
    </div></Transition
  >
  <EquipmentPickerDialog
    v-model="childPicker"
    multiple
    :load-rows="loadChildren"
    :excluded-ids="(editing?.children || []).map((x) => x.equipmentId || '')"
    @select-multiple="selectChildren"
  />
  <MaterialPicker
    v-model="bomPicker"
    :load-rows="equipmentBomMaterials"
    :disabled-ids="(editing?.relatedSpares || []).map((x) => x.materialId || '')"
    @select="selectMaterials"
  />
</template>

<style scoped>
.editor-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
  row-gap: 12px;
}
.editor-form-groups {
  gap: 16px;
}
@media (max-width: 600px) {
  .editor-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
.relation-table :deep(.el-input),
.relation-table :deep(.el-select),
.relation-table :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}
.relation-table :deep(.el-input__inner) {
  border: 0;
  background: transparent;
  padding: 0;
  box-shadow: none;
  height: auto;
}
.editor-head :deep(.el-button),
.relation-title :deep(.el-button) {
  font-size: 13px;
}
.equipment-editor :deep(.el-button + .el-button) {
  margin-left: 0;
}

.legacy-relation {
  display: block;
  color: #b46a18;
  font-size: 12px;
}
.equipment-form :deep(.app-form-field) {
  margin-bottom: 0;
}
.equipment-form :deep(.app-form-field .el-form-item__label) {
  font-size: 13px;
}
.equipment-form :deep(.app-form-field .el-form-item__content) {
  min-width: 0;
}
.image-upload-row {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  font-size: 13px;
}
.image-upload-row > span {
  width: auto;
  line-height: 20px;
  padding-top: 8px;
}
@media (max-width: 600px) {
  .image-upload-row {
    grid-template-columns: 70px minmax(0, 1fr);
  }
}
.owner-field :deep(.el-select__input),
.owner-field :deep(.el-select__input:focus) {
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  outline: none;
  padding: 0;
  height: auto;
  min-height: 0;
  line-height: 20px;
  font: inherit;
}
</style>
