<script setup lang="ts">
import AppDecimalInput from './common/AppDecimalInput.vue';
import AppBackButton from './common/AppBackButton.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import { computed, ref, onMounted } from 'vue';
import { storeToRefs } from 'pinia';
import { ElInput, ElSelect, ElOption, ElDatePicker, ElMessage } from 'element-plus';
import {
  uploadEquipmentFile,
  equipmentBatchOptions,
  type EquipmentRecord,
  type OrganizationRecord,
} from '../api';
import { useBaseDataStore } from '../stores/baseData';
import { EQUIPMENT_STATES } from '../config/equipment';
import TreeSelect from './TreeSelect.vue';
import BooleanSwitch from './BooleanSwitch.vue';
const editing = defineModel<EquipmentRecord | null>({ required: true });
const drawer = defineModel<boolean>('open', { required: true });
const props = defineProps<{ isNew: boolean; busy: boolean }>();
const emit = defineEmits<{ save: []; close: []; uploading: [boolean] }>();
const { categoryData, locationData, unitData, useStateData, sourceData } =
  storeToRefs(useBaseDataStore());
const categoryTreeOptions = computed(() => categoryData.value.filter((x) => x.status === '启用'));
const locationTreeOptions = computed(() => locationData.value.filter((x) => x.status === '启用'));
const organizations = ref<OrganizationRecord[]>([]);
const organizationError = ref('');
async function loadOrganizations() {
  try {
    organizations.value = (await equipmentBatchOptions()).departments;
    organizationError.value = '';
  } catch (e) {
    organizationError.value = e instanceof Error ? e.message : '部门加载失败';
  }
}
onMounted(loadOrganizations);
function departmentCompany(id: string) {
  const visited = new Set<string>();
  let node = organizations.value.find((x) => x.id === id);
  while (node && !visited.has(node.id)) {
    visited.add(node.id);
    if (node.type === '公司') return node;
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
    disabled: x.type !== '部门',
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
  },
});
function addChild() {
  if (!editing.value) return;
  editing.value.children = [
    ...(editing.value.children || []),
    { code: '', name: '', brand: '', model: '' },
  ];
}
function addSpare() {
  if (!editing.value) return;
  editing.value.relatedSpares = [
    ...(editing.value.relatedSpares || []),
    { code: '', name: '', specification: '', quantity: '1' },
  ];
}
function openDocumentPicker() {
  documentPicker.value?.click();
}
const uploading = ref(false);
async function handleDocumentUpload(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  if (!editing.value || props.busy || !files.length) return;
  uploading.value = true;
  emit('uploading', true);
  try {
    for (const file of files) {
      const stored = await uploadEquipmentFile(file);
      editing.value.documents = [
        ...(editing.value.documents || []),
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
    }
    ElMessage.success('附件上传成功，保存设备后生效');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '附件上传失败');
  } finally {
    input.value = '';
    uploading.value = false;
    emit('uploading', false);
  }
}
async function selectEquipmentImage(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!editing.value || props.busy || !file) return;
  if (!/\.(png|jpe?g|gif)$/i.test(file.name) || file.size > 20 * 1024 * 1024) {
    ElMessage.warning('请选择不超过 20MB 的 PNG、JPG 或 GIF 图片');
    input.value = '';
    return;
  }
  uploading.value = true;
  emit('uploading', true);
  try {
    const stored = await uploadEquipmentFile(file);
    editing.value.imageFileId = stored.id;
    editing.value.imageName = stored.name;
    ElMessage.success('图片上传成功，保存设备后生效');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图片上传失败');
  } finally {
    input.value = '';
    uploading.value = false;
    emit('uploading', false);
  }
}
const documentPicker = ref<HTMLInputElement | null>(null);
</script>
<template>
  <Transition name="fade"
    ><div v-if="drawer" class="drawer-mask equipment-editor-mask">
      <section
        class="drawer equipment-editor"
        role="dialog"
        aria-modal="true"
        :aria-label="isNew ? '新增设备' : '编辑设备'"
      >
        <header class="drawer-head editor-head">
          <div class="editor-title">
            <AppBackButton label="返回设备台账" :disabled="busy" @click="emit('close')" />
            <div class="editor-heading-line">
              <span>设备台账</span><i>/</i>
              <h2>{{ isNew ? '新增设备' : '编辑设备' }}</h2>
            </div>
            <span v-if="!isNew" class="drawer-code">{{ editing?.code }}</span>
          </div>
          <button class="close-btn" aria-label="关闭" :disabled="busy" @click="emit('close')">
            ×
          </button>
        </header>
        <div class="drawer-body editor-body" v-if="editing">
          <div class="editor-layout">
            <div class="editor-main-column">
              <section class="form-section">
                <div class="editor-form-groups">
                  <section class="editor-field-group">
                    <h4>设备标识</h4>
                    <div class="editor-grid">
                      <label class="editor-field"
                        ><span>设备编号</span
                        ><ElInput
                          v-model="editing.code"
                          :placeholder="isNew ? '留空则自动生成' : '请输入设备编号'"
                          maxlength="64" /></label
                      ><label class="editor-field required"
                        ><span>设备名称</span
                        ><ElInput
                          v-model="editing.name"
                          placeholder="请输入设备名称"
                          maxlength="100" /></label
                      ><label class="editor-field required"
                        ><span>类别</span
                        ><TreeSelect
                          v-model="editing.category"
                          :nodes="categoryTreeOptions"
                          placeholder="请选择设备类别"
                      /></label>
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
                      ><label class="editor-field required"
                        ><span>单位</span
                        ><ElSelect v-model="editing.unit" placeholder="请选择" clearable
                          ><ElOption
                            v-for="item in unitData.filter((x) => x.status === '启用')"
                            :key="item.id"
                            :label="item.name"
                            :value="item.name" /></ElSelect
                      ></label>
                    </div>
                  </section>
                  <section class="editor-field-group">
                    <h4>归属与位置</h4>
                    <div class="editor-grid">
                      <label class="editor-field required editor-span-2"
                        ><span>所属部门</span
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
                        </small></label
                      ><button
                        v-if="organizationError"
                        class="text-action"
                        type="button"
                        @click="loadOrganizations"
                      >
                        部门加载失败，点击重试</button
                      ><label class="editor-field"
                        ><span>负责人</span
                        ><ElInput v-model="editing.owner" placeholder="请选择负责人"
                      /></label>
                      <label class="editor-field required editor-span-2"
                        ><span>功能位置</span
                        ><TreeSelect
                          v-model="editing.functionLocation"
                          :nodes="locationTreeOptions"
                          placeholder="请选择功能位置"
                          path-value
                      /></label>
                    </div>
                  </section>
                  <section class="editor-field-group">
                    <h4>使用与生命周期</h4>
                    <div class="editor-grid">
                      <label class="editor-field"
                        ><span>设备状态</span
                        ><ElSelect v-model="editing.state" placeholder="请选择" clearable
                          ><ElOption
                            v-for="state in EQUIPMENT_STATES"
                            :key="state"
                            :label="state"
                            :value="state" /></ElSelect></label
                      ><label class="editor-field required"
                        ><span>使用状态</span
                        ><ElSelect v-model="editing.useState" placeholder="请选择" clearable
                          ><ElOption
                            v-for="item in useStateData.filter((x) => x.status === '启用')"
                            :key="item.id"
                            :label="item.name"
                            :value="item.name" /></ElSelect></label
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
                        ><ElSelect v-model="editing.source" placeholder="请选择" clearable
                          ><ElOption
                            v-for="item in sourceData.filter((x) => x.status === '启用')"
                            :key="item.id"
                            :label="item.name"
                            :value="item.name" /></ElSelect></label
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
                      <div class="boolean-group">
                        <label class="editor-field"
                          ><span>特种设备</span
                          ><BooleanSwitch v-model="editing.specialEquipment" /></label
                        ><label class="editor-field"
                          ><span>计量设备</span
                          ><BooleanSwitch v-model="editing.metrologyEquipment" /></label
                        ><label class="editor-field"
                          ><span>开启折旧</span
                          ><BooleanSwitch v-model="editing.depreciationEnabled"
                        /></label>
                      </div>
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
                      <span>设备图片</span
                      ><label class="image-picker"
                        ><input
                          type="file"
                          accept=".png,.jpg,.jpeg,.gif"
                          :disabled="busy"
                          @change="selectEquipmentImage"
                        />
                        <img
                          v-if="editing.imageFileId"
                          :src="`/api/v1/equipment/files/${editing.imageFileId}/preview`"
                          alt="设备图片"
                        />
                        <span v-else-if="!editing.imageName">＋<small>上传图片</small></span
                        ><span v-else class="image-name">{{ editing.imageName }}</span></label
                      >
                      <div class="equipment-image-actions">
                        <span>{{
                          uploading
                            ? '正在上传…'
                            : editing.imageName || '支持 PNG、JPG、GIF，最大 20MB'
                        }}</span>
                        <span v-if="editing.imageName && !editing.imageFileId"
                          >原记录仅保存名称，请重新上传</span
                        >
                        <button
                          v-if="editing.imageName || editing.imageFileId"
                          type="button"
                          :disabled="busy"
                          @click="
                            editing.imageName = '';
                            editing.imageFileId = '';
                          "
                        >
                          移除图片
                        </button>
                      </div>
                    </div>
                  </section>
                </div>
              </section>
            </div>
            <aside class="editor-side-column">
              <section class="form-section relation-section">
                <div class="relation-title">
                  <h3><i></i>设备相关文件</h3>
                  <button
                    class="btn secondary relation-add"
                    :disabled="busy"
                    @click="openDocumentPicker"
                  >
                    {{ uploading ? '上传中…' : '＋ 上传' }}
                  </button>
                </div>
                <div class="relation-table-wrap">
                  <table class="business-table relation-table">
                    <thead>
                      <tr>
                        <th>资料编号</th>
                        <th>资料名称</th>
                        <th>资料类型</th>
                        <th>文档密级</th>
                        <th>数量</th>
                        <th></th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(doc, index) in editing.documents" :key="index">
                        <td><input v-model="doc.code" placeholder="编号" /></td>
                        <td>
                          <input v-model="doc.name" placeholder="资料名称" /><a
                            v-if="doc.id"
                            :href="`/api/v1/equipment/files/${doc.id}`"
                            target="_blank"
                            rel="noopener"
                            >下载</a
                          ><span v-else>未上传文件</span>
                        </td>
                        <td><input v-model="doc.type" placeholder="类型" /></td>
                        <td>
                          <select v-model="doc.security">
                            <option>普通</option>
                            <option>内部</option>
                            <option>机密</option>
                          </select>
                        </td>
                        <td><input v-model.number="doc.quantity" type="number" min="1" /></td>
                        <td>
                          <button
                            class="relation-delete"
                            @click="editing?.documents?.splice(index, 1)"
                          >
                            删除
                          </button>
                        </td>
                      </tr>
                      <tr v-if="!editing.documents?.length">
                        <td colspan="6" class="relation-empty">暂无相关文件</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <input
                  ref="documentPicker"
                  class="hidden-file"
                  type="file"
                  accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx,.txt,.zip,.csv"
                  multiple
                  @change="handleDocumentUpload"
                />
              </section>
              <section class="form-section relation-section">
                <div class="relation-title">
                  <h3><i></i>子设备</h3>
                  <button class="btn secondary relation-add" @click="addChild">＋ 新增</button>
                </div>
                <div class="relation-table-wrap">
                  <table class="business-table relation-table">
                    <thead>
                      <tr>
                        <th>设备编号</th>
                        <th>设备名称</th>
                        <th>品牌</th>
                        <th>规格型号</th>
                        <th></th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(child, index) in editing.children" :key="index">
                        <td><input v-model="child.code" placeholder="编号" /></td>
                        <td><input v-model="child.name" placeholder="名称" /></td>
                        <td><input v-model="child.brand" placeholder="品牌" /></td>
                        <td><input v-model="child.model" placeholder="型号" /></td>
                        <td>
                          <button
                            class="relation-delete"
                            @click="editing?.children?.splice(index, 1)"
                          >
                            删除
                          </button>
                        </td>
                      </tr>
                      <tr v-if="!editing.children?.length">
                        <td colspan="5" class="relation-empty">暂无子设备</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>
              <section class="form-section relation-section">
                <div class="relation-title">
                  <h3><i></i>设备关联备件</h3>
                  <button class="btn secondary relation-add" @click="addSpare">＋ 新增</button>
                </div>
                <div class="relation-table-wrap">
                  <table class="business-table relation-table">
                    <thead>
                      <tr>
                        <th>备件编码</th>
                        <th>备件名称</th>
                        <th>规格型号</th>
                        <th>数量</th>
                        <th></th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(part, index) in editing.relatedSpares" :key="index">
                        <td><input v-model="part.code" placeholder="编码" /></td>
                        <td><input v-model="part.name" placeholder="名称" /></td>
                        <td><input v-model="part.specification" placeholder="规格型号" /></td>
                        <td><AppDecimalInput v-model="part.quantity" /></td>
                        <td>
                          <button
                            class="relation-delete"
                            @click="editing?.relatedSpares?.splice(index, 1)"
                          >
                            删除
                          </button>
                        </td>
                      </tr>
                      <tr v-if="!editing.relatedSpares?.length">
                        <td colspan="5" class="relation-empty">暂无关联备件</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>
            </aside>
          </div>
        </div>
        <AppFormFooter>
          <button class="btn secondary" :disabled="busy" @click="emit('close')">取消</button
          ><button class="btn primary" :disabled="busy" @click="emit('save')">
            {{ busy ? '保存中…' : '保存' }}
          </button>
        </AppFormFooter>
      </section>
    </div></Transition
  >
</template>
