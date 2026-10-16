<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
import {
  ElButton,
  ElForm,
  ElInput,
  ElInputNumber,
  ElSelect,
  ElOption,
  ElSwitch,
  ElTableColumn,
  ElAlert,
  ElTabs,
  ElTabPane,
  ElTag,
  ElMessage,
  type FormInstance,
  type FormRules,
} from 'element-plus';
import AppListToolbar from '../components/common/AppListToolbar.vue';
import AppBusinessTable from '../components/common/AppBusinessTable.vue';
import AppFormField from '../components/common/AppFormField.vue';
import AppFormFooter from '../components/common/AppFormFooter.vue';
import AppBackButton from '../components/common/AppBackButton.vue';
import AppFilePicker from '../components/common/AppFilePicker.vue';
import RowMoreActions from '../components/common/RowMoreActions.vue';
import AppPdfPreview from '../components/common/AppPdfPreview.vue';
import AppIcon from '../components/common/AppIcon.vue';
import ListSearchInput from '../components/common/ListSearchInput.vue';
import { useAuthStore } from '../stores/auth';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { confirmAction } from '../services/dialog';
import {
  printing,
  printPdf,
  type DocumentPrintTemplate,
  type PrintCatalog,
} from '../printing/documents';
const auth = useAuthStore(),
  can = (action: string) => auth.can('printTemplate', action);
const catalog = ref<PrintCatalog>(),
  rows = ref<DocumentPrintTemplate[]>([]),
  editing = ref<DocumentPrintTemplate>();
const search = ref(''),
  loading = ref(false),
  saving = ref(false),
  previewing = ref(false),
  error = ref(''),
  previewError = ref(''),
  previewUrl = ref(''),
  tab = ref('style');
const form = ref<FormInstance>();
const busy = computed(() => saving.value || previewing.value);
const guard = useUnsavedChanges(
  () => editing.value,
  () => !!editing.value,
  () => busy.value,
);
const displayed = computed(() => {
  if (!catalog.value) return rows.value;
  const preset: DocumentPrintTemplate = {
    id: 'system',
    name: '标准出库单（系统）',
    companyId: '',
    companyName: '系统预置',
    enabled: true,
    isDefault: false,
    rowVersion: 0,
    config: catalog.value.defaults,
  };
  return [...rows.value, preset].filter((t) =>
    (t.name + ' ' + t.companyName).includes(search.value.trim()),
  );
});
const rules: FormRules = {
  name: [{ required: true, whitespace: true, message: '请输入模板名称', trigger: 'blur' }],
  'config.title': [
    { required: true, whitespace: true, message: '请输入单据标题', trigger: 'blur' },
  ],
};
const columnKeys = computed({
  get: () => editing.value?.config.columns.map((c) => c.key) || [],
  set: (keys: string[]) => {
    if (!editing.value || !catalog.value) return;
    const old = new Map(editing.value.config.columns.map((c) => [c.key, c]));
    editing.value.config.columns = keys.map(
      (key) => old.get(key) || { key, label: catalog.value!.columns[key]!, width: 12 },
    );
  },
});
function clearPreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = '';
  previewError.value = '';
}
onBeforeUnmount(clearPreview);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [a, b] = await Promise.all([printing.catalog(), printing.templates()]);
    catalog.value = a.data;
    rows.value = b.data;
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    loading.value = false;
  }
}
onMounted(load);
async function open(item?: DocumentPrintTemplate, copy = false) {
  if (!catalog.value) return;
  clearPreview();
  tab.value = 'style';
  error.value = '';
  const value: DocumentPrintTemplate = item
    ? JSON.parse(JSON.stringify(item))
    : {
        id: '',
        name: '标准出库单',
        companyId: catalog.value.canManageCommon ? '' : catalog.value.companies[0]?.id || '',
        enabled: true,
        isDefault: false,
        rowVersion: 0,
        config: JSON.parse(JSON.stringify(catalog.value.defaults)),
      };
  if (copy || value.id === 'system') {
    value.id = '';
    value.name = item?.id === 'system' ? '标准出库单' : value.name + '（副本）';
    value.isDefault = false;
    value.rowVersion = 0;
    if (!catalog.value.canManageCommon && !value.companyId)
      value.companyId = catalog.value.companies[0]?.id || '';
  }
  editing.value = value;
  guard.markSaved();
  await nextTick();
  form.value?.clearValidate();
  await preview();
}
async function back() {
  if (!(await guard.permitLeave())) return;
  editing.value = undefined;
  clearPreview();
  error.value = '';
}
async function preview() {
  if (!editing.value) return;
  previewing.value = true;
  previewError.value = '';
  try {
    const pdf = await printPdf('templates/preview', {
      method: 'POST',
      body: JSON.stringify({ ...editing.value, companyId: editing.value.companyId || null }),
    });
    clearPreview();
    previewUrl.value = URL.createObjectURL(pdf);
  } catch (e) {
    previewError.value = e instanceof Error ? e.message : '预览失败';
  } finally {
    previewing.value = false;
  }
}
async function save() {
  if (editing.value && (!editing.value.name.trim() || !editing.value.config.title.trim())) {
    tab.value = 'style';
    await nextTick();
  }
  if (!editing.value || saving.value || !(await form.value?.validate().catch(() => false))) return;
  saving.value = true;
  error.value = '';
  try {
    await printing.save(editing.value);
    guard.markSaved();
    editing.value = undefined;
    clearPreview();
    await load();
    ElMessage.success('打印模板已保存');
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败';
  } finally {
    saving.value = false;
  }
}
async function remove(item: DocumentPrintTemplate) {
  if (!(await confirmAction(`删除打印模板“${item.name}”？已留底PDF将继续保留。`))) return;
  try {
    await printing.remove(item);
    await load();
    ElMessage.success('已删除');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败');
  }
}
function move(index: number, offset: number) {
  if (!editing.value) return;
  const cols = editing.value.config.columns;
  const next = index + offset;
  if (next < 0 || next >= cols.length) return;
  [cols[index], cols[next]] = [cols[next]!, cols[index]!];
}
async function logo(files: File[]) {
  const file = files[0];
  if (!file || !editing.value) return;
  if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 512000) {
    ElMessage.error('请选择500KB以内的PNG或JPG图片');
    return;
  }
  try {
    const result = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(new Error('图片读取失败'));
      reader.readAsDataURL(file);
    });
    if (editing.value) editing.value.config.logo = result;
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '图片读取失败');
  }
}
</script>
<template>
  <section class="print-template-management">
    <ElAlert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <template v-if="!editing">
      <AppListToolbar
        ><ElButton v-if="can('新增')" type="primary" :disabled="loading || !catalog" @click="open()"
          ><AppIcon name="Plus" />新增</ElButton
        >
        <form @submit.prevent>
          <ListSearchInput v-model="search" placeholder="搜索模板名称、适用公司" /><ElButton
            text
            @click="search = ''"
            >重置</ElButton
          >
        </form></AppListToolbar
      >
      <AppBusinessTable
        :rows="displayed"
        :loading="loading"
        :error="error"
        :pagination="false"
        row-key="id"
        @retry="load"
      >
        <ElTableColumn prop="name" label="模板名称" min-width="200" show-overflow-tooltip />
        <ElTableColumn label="单据类型" width="110"
          ><template #default>出库单</template></ElTableColumn
        >
        <ElTableColumn label="适用公司" min-width="190" show-overflow-tooltip
          ><template #default="{ row }">{{
            row.companyName ||
            (row.companyId
              ? catalog?.companies.find((c) => c.id === row.companyId)?.name
              : '公共模板')
          }}</template></ElTableColumn
        >
        <ElTableColumn label="纸张" width="100"
          ><template #default="{ row }"
            >{{ row.config.paper === 'CUSTOM' ? '自定义' : row.config.paper }}
            {{ row.config.landscape ? '横向' : '纵向' }}</template
          ></ElTableColumn
        >
        <ElTableColumn label="状态" width="90" align="center"
          ><template #default="{ row }"
            ><ElTag :type="row.enabled ? 'success' : 'info'">{{
              row.enabled ? '启用' : '停用'
            }}</ElTag></template
          ></ElTableColumn
        >
        <ElTableColumn label="默认" width="110"
          ><template #default="{ row }">{{
            row.id === 'system' ? '系统兜底' : row.isDefault ? '默认模板' : ''
          }}</template></ElTableColumn
        >
        <ElTableColumn label="操作" fixed="right" width="200"
          ><template #default="{ row }"
            ><ElButton
              v-if="row.id !== 'system' && row.canEdit"
              link
              type="primary"
              @click="open(row as DocumentPrintTemplate)"
              >编辑</ElButton
            ><ElButton
              v-if="can('新增')"
              link
              type="primary"
              @click="open(row as DocumentPrintTemplate, true)"
              >{{ row.id === 'system' ? '复制为新模板' : '复制' }}</ElButton
            ><RowMoreActions
              v-if="row.id !== 'system' && row.canDelete"
              :actions="[
                { label: '删除', danger: true, run: () => remove(row as DocumentPrintTemplate) },
              ]" /></template
        ></ElTableColumn>
      </AppBusinessTable>
    </template>
    <template v-else-if="catalog">
      <header class="print-edit-header">
        <AppBackButton label="返回打印模板" :disabled="busy" @click="back" /><strong>{{
          editing.id ? '编辑打印模板' : '新增打印模板'
        }}</strong
        ><ElButton :loading="previewing" :disabled="saving" @click="preview">预览样板</ElButton>
      </header>
      <div class="print-editor-body">
        <section class="print-config-panel">
          <ElForm
            ref="form"
            :model="editing"
            :rules="rules"
            label-width="96px"
            :disabled="busy"
            @submit.prevent="save"
          >
            <ElTabs v-model="tab"
              ><ElTabPane label="基本样式" name="style" /><ElTabPane
                label="明细配置"
                name="columns" /><ElTabPane label="签字与说明" name="footer"
            /></ElTabs>
            <div v-show="tab === 'style'">
              <AppFormField label="模板名称" prop="name"
                ><ElInput v-model="editing.name" maxlength="100"
              /></AppFormField>
              <AppFormField label="适用公司" prop="companyId"
                ><ElSelect
                  :model-value="editing.companyId || '__common__'"
                  @update:model-value="
                    (value) => (editing!.companyId = value === '__common__' ? '' : String(value))
                  "
                  filterable
                  ><ElOption
                    v-if="catalog.canManageCommon"
                    label="公共模板"
                    value="__common__" /><ElOption
                    v-for="c in catalog.companies"
                    :key="c.id"
                    :value="c.id"
                    :label="c.name" /></ElSelect
              ></AppFormField>
              <AppFormField label="启用"
                ><ElSwitch
                  v-model="editing.enabled"
                  @change="
                    (value) => {
                      if (!value) editing!.isDefault = false;
                    }
                  "
              /></AppFormField>
              <AppFormField label="设为默认"
                ><ElSwitch v-model="editing.isDefault" :disabled="!editing.enabled"
              /></AppFormField>
              <AppFormField label="单据标题" prop="config.title"
                ><ElInput v-model="editing.config.title" maxlength="80"
              /></AppFormField>
              <AppFormField label="公司Logo"
                ><div class="print-logo-controls">
                  <img
                    v-if="editing.config.logo"
                    :src="editing.config.logo"
                    alt="打印Logo"
                  /><AppFilePicker accept="image/png,image/jpeg" :disabled="busy" @select="logo"
                    ><ElButton>{{ editing.config.logo ? '替换' : '上传' }}</ElButton></AppFilePicker
                  ><ElButton
                    v-if="editing.config.logo"
                    text
                    type="danger"
                    @click="editing.config.logo = ''"
                    >移除</ElButton
                  >
                </div></AppFormField
              >
              <AppFormField label="纸张"
                ><ElSelect v-model="editing.config.paper"
                  ><ElOption label="A4" value="A4" /><ElOption label="A5" value="A5" /><ElOption
                    label="自定义／空白多联纸"
                    value="CUSTOM" /></ElSelect
              ></AppFormField>
              <template v-if="editing.config.paper === 'CUSTOM'"
                ><AppFormField label="纸宽（mm）"
                  ><ElInputNumber
                    v-model="editing.config.widthMm"
                    :min="80"
                    :max="600"
                    :precision="2"
                    controls-position="right" /></AppFormField
                ><AppFormField label="纸高（mm）"
                  ><ElInputNumber
                    v-model="editing.config.heightMm"
                    :min="100"
                    :max="600"
                    :precision="2"
                    controls-position="right" /></AppFormField
              ></template>
              <AppFormField label="方向"
                ><ElSelect v-model="editing.config.landscape"
                  ><ElOption label="纵向" :value="false" /><ElOption
                    label="横向"
                    :value="true" /></ElSelect
              ></AppFormField>
              <AppFormField label="正文字号"
                ><ElInputNumber
                  v-model="editing.config.fontSize"
                  :min="8"
                  :max="14"
                  controls-position="right"
              /></AppFormField>
              <AppFormField label="基本信息"
                ><ElSelect
                  v-model="editing.config.headers"
                  multiple
                  filterable
                  collapse-tags
                  collapse-tags-tooltip
                  :max-collapse-tags="3"
                  ><ElOption
                    v-for="(label, key) in catalog.headers"
                    :key="key"
                    :label="label"
                    :value="String(key)" /></ElSelect
              ></AppFormField>
            </div>
            <div v-show="tab === 'columns'">
              <AppFormField label="显示字段"
                ><ElSelect
                  v-model="columnKeys"
                  multiple
                  filterable
                  collapse-tags
                  collapse-tags-tooltip
                  ><ElOption
                    v-for="(label, key) in catalog.columns"
                    :key="key"
                    :label="label"
                    :value="String(key)"
                    :disabled="['code', 'name', 'quantity'].includes(String(key))" /></ElSelect
              ></AppFormField>
              <p class="print-note">
                按比例分配纸张宽度；调整后点击预览。包含价格、金额或币种的模板仅供有金额权限的人员使用。
              </p>
              <AppBusinessTable
                :rows="editing.config.columns"
                :fill="false"
                :pagination="false"
                row-key="key"
              >
                <ElTableColumn label="字段" min-width="90"
                  ><template #default="{ row }">{{
                    catalog.columns[row.key]
                  }}</template></ElTableColumn
                >
                <ElTableColumn label="打印标题" min-width="125"
                  ><template #default="{ row }"
                    ><ElInput v-model="row.label" maxlength="24" /></template
                ></ElTableColumn>
                <ElTableColumn label="宽度比例" min-width="125"
                  ><template #default="{ row }"
                    ><ElInputNumber
                      v-model="row.width"
                      :min="3"
                      :max="100"
                      controls-position="right" /></template
                ></ElTableColumn>
                <ElTableColumn label="顺序" fixed="right" width="156"
                  ><template #default="{ $index }"
                    ><ElButton
                      link
                      type="primary"
                      :disabled="$index === 0"
                      @click="move($index, -1)"
                      >上移</ElButton
                    ><ElButton
                      link
                      type="primary"
                      :disabled="$index === editing!.config.columns.length - 1"
                      @click="move($index, 1)"
                      >下移</ElButton
                    ></template
                  ></ElTableColumn
                >
              </AppBusinessTable>
            </div>
            <div v-show="tab === 'footer'">
              <AppFormField label="签字栏"
                ><ElSelect
                  v-model="editing.config.signatures"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  :multiple-limit="4"
                  placeholder="输入名称并回车，最多4项"
                  ><ElOption
                    v-for="label in ['发料人', '领用人', '仓管员', '经办人']"
                    :key="label"
                    :label="label"
                    :value="label" /></ElSelect
              ></AppFormField>
              <AppFormField label="页尾说明"
                ><ElInput
                  v-model="editing.config.footer"
                  type="textarea"
                  :rows="5"
                  maxlength="500"
                  show-word-limit
              /></AppFormField>
              <p class="print-note">
                签字栏预留手写位置，页码自动生成；单号、所属公司和单据状态始终展示。
              </p>
            </div>
          </ElForm>
        </section>
        <section class="print-preview-panel">
          <div class="print-preview-heading">
            <strong>样板预览</strong><span>使用演示数据</span>
          </div>
          <ElAlert
            v-if="previewError"
            :title="previewError"
            type="error"
            :closable="false"
          /><AppPdfPreview v-if="previewUrl" :src="previewUrl" />
          <p v-else>{{ previewing ? '正在生成预览…' : '点击预览样板查看排版效果' }}</p>
        </section>
      </div>
      <AppFormFooter
        ><ElButton :disabled="busy" @click="back">取消</ElButton
        ><ElButton type="primary" :loading="saving" :disabled="previewing" @click="save"
          >保存</ElButton
        ></AppFormFooter
      >
    </template>
  </section>
</template>
<style scoped>
.print-template-management {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - 54px);
  min-height: 0;
  padding: 8px 16px 0;
  gap: 8px;
  color: var(--el-text-color-primary);
  font-size: 13px;
}
.print-edit-header {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  flex: none;
}
.print-edit-header > .el-button:last-child {
  margin-left: auto;
}
.print-editor-body {
  display: grid;
  grid-template-columns: minmax(400px, 1fr) minmax(320px, 1fr);
  gap: 12px;
  min-height: 0;
  flex: 1;
}
.print-config-panel {
  padding: 12px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  min-height: 0;
  min-width: 0;
  overflow: auto;
}
.print-config-panel :deep(.el-select) {
  width: 100%;
}
.print-config-panel :deep(.el-input-number) {
  max-width: 100%;
}
.print-preview-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #e9edf3;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  overflow: hidden;
}
.print-preview-panel :deep(.app-pdf-preview) {
  border: 0;
  flex: 1;
  min-height: 0;
  width: 100%;
}
.print-preview-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  background: var(--el-bg-color);
}
.print-note {
  line-height: 1.7;
  margin: 8px 0 14px;
}
.print-logo-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.print-logo-controls img {
  width: 76px;
  height: 42px;
  object-fit: contain;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
}
@media (max-width: 900px) {
  .print-editor-body {
    grid-template-columns: minmax(0, 1fr);
    overflow: auto;
  }
  .print-preview-panel {
    min-height: 420px;
  }
  .print-config-panel {
    overflow: visible;
  }
}
</style>
