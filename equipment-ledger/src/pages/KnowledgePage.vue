<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import {
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElForm,
  ElFormItem,
  ElMessage,
  ElTableColumn,
  ElAlert,
  ElUpload,
  ElLoading,
} from 'element-plus';
import { request } from '../api';
import { tr } from '../i18n';
import { useAuthStore } from '../stores/auth';
import AppBusinessTable from '../components/common/AppBusinessTable.vue';
import AppPagination from '../components/common/AppPagination.vue';
import AppDialog from '../components/common/AppDialog.vue';
import RowMoreActions from '../components/common/RowMoreActions.vue';
import KnowledgeVersionHistory from '../components/knowledge/KnowledgeVersionHistory.vue';
import KnowledgeCaseCreator from '../components/knowledge/KnowledgeCaseCreator.vue';
import PagedChoiceField from '../components/common/PagedChoiceField.vue';
import { ElCheckbox } from 'element-plus';
import { listEquipment } from '../api';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import type { SelectorRow } from '../selectors';
type Row = {
  id: string;
  title: string;
  companyId: string;
  status: string;
  parseStatus: string;
  rowVersion: number;
  indexStatus?: string;
};
type Page = { pageNo: number; needsOcr: boolean; textContent: string; chunkCount: number };
const vLoading = ElLoading.directive;
const auth = useAuthStore();
const versionDocument = ref<Row>();
const replacement = ref<Row>();
function replaceDialog(row: Row) {
  replacement.value = row;
  title.value = row.title;
  company.value = row.companyId;
  file.value = undefined;
  dialog.value = true;
}
const binding = ref(false),
  bindingRow = ref<Row>(),
  bindingAll = ref(false),
  bindingIds = ref<string[]>([]),
  bindingOptions = ref<SelectorRow[]>([]);
async function equipmentRows(q: string, page: number, size: number) {
  return listEquipment({ q, page, size });
}
async function openBinding(row: Row) {
  try {
    const result = await request<{
      all: boolean;
      equipment: { equipmentId: string }[];
      rowVersion: number;
    }>(`/knowledge/documents/${row.id}/applicability`);
    bindingRow.value = { ...row, rowVersion: result.rowVersion };
    bindingAll.value = result.all;
    bindingIds.value = result.equipment.map((e) => e.equipmentId);
    bindingOptions.value = bindingIds.value.map((id) => ({ id, name: id }));
    binding.value = true;
  } catch (e) {
    ElMessage.error(message(e));
  }
}
async function saveBinding() {
  if (!bindingRow.value) return;
  busy.value = true;
  try {
    await request(`/knowledge/documents/${bindingRow.value.id}/applicability`, {
      method: 'PUT',
      body: JSON.stringify({
        rowVersion: bindingRow.value.rowVersion,
        all: bindingAll.value,
        equipmentIds: bindingAll.value ? [] : bindingIds.value,
      }),
    });
    binding.value = false;
    await load();
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
const rows = ref<Row[]>([]),
  total = ref(0),
  page = ref(1),
  size = ref(20),
  query = ref(''),
  applied = ref('');
const loading = ref(false),
  error = ref(''),
  busy = ref(false),
  dialog = ref(false),
  preview = ref(false);
const companies = ref<{ id: string; name: string }[]>([]),
  company = ref(''),
  title = ref(''),
  documentType = ref('MANUAL'),
  file = ref<File>();
const pages = ref<Page[]>([]),
  previewTitle = ref(''),
  previewBusy = ref(false),
  previewError = ref('');
const form = ref<InstanceType<typeof ElForm>>();
const uploadGuard = useUnsavedChanges(
  () => ({
    company: company.value,
    title: title.value,
    documentType: documentType.value,
    file: file.value?.name,
  }),
  () => dialog.value,
  () => busy.value,
);
const bindingGuard = useUnsavedChanges(
  () => ({ all: bindingAll.value, ids: bindingIds.value }),
  () => binding.value,
  () => busy.value,
);
watch(
  dialog,
  (value) => {
    if (value) uploadGuard.markSaved();
  },
  { flush: 'sync' },
);
watch(
  binding,
  (value) => {
    if (value) bindingGuard.markSaved();
  },
  { flush: 'sync' },
);
async function cancelUpload() {
  if (await uploadGuard.permitLeave()) dialog.value = false;
}
async function cancelBinding() {
  if (await bindingGuard.permitLeave()) binding.value = false;
}
let revision = 0,
  previewRevision = 0;
function message(e: unknown) {
  return e instanceof Error ? e.message : tr('common.error.requestFailed');
}
async function load() {
  const current = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const result = await request<{ data: Row[]; total: number }>(
      `/knowledge/documents?q=${encodeURIComponent(applied.value)}&page=${page.value}&size=${size.value}`,
    );
    if (current === revision) {
      rows.value = result.data;
      total.value = result.total;
    }
  } catch (e) {
    if (current === revision) {
      rows.value = [];
      error.value = message(e);
    }
  } finally {
    if (current === revision) loading.value = false;
  }
}
function search() {
  applied.value = query.value;
  if (page.value === 1) void load();
  else page.value = 1;
}
async function uploadDialog() {
  replacement.value = undefined;
  try {
    const result = await request<{ data: { id: string; name: string }[] }>(
      '/knowledge/documents/companies',
    );
    companies.value = result.data;
    company.value = result.data.length === 1 ? result.data[0]!.id : '';
    title.value = '';
    documentType.value = 'MANUAL';
    file.value = undefined;
    dialog.value = true;
  } catch (e) {
    ElMessage.error(message(e));
  }
}
function choose(event: { raw?: File }) {
  file.value = event.raw;
}
async function upload() {
  if (!(await form.value?.validate().catch(() => false))) return;
  if (!file.value) {
    ElMessage.error(tr('knowledge.choosePdf'));
    return;
  }
  if (file.value.size > 50 * 1024 * 1024) {
    ElMessage.error(tr('knowledge.fileLimit'));
    return;
  }
  busy.value = true;
  try {
    const body = new FormData();
    body.append('file', file.value);
    body.append('companyId', company.value);
    body.append('title', title.value);
    body.append('documentType', documentType.value);
    if (replacement.value) {
      body.append('rowVersion', String(replacement.value.rowVersion));
      await request(`/knowledge/documents/${replacement.value.id}/versions`, {
        method: 'POST',
        body,
      });
    } else await request('/knowledge/documents', { method: 'POST', body });
    dialog.value = false;
    ElMessage.success(tr('knowledge.uploaded'));
    await load();
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function inspect(value: unknown) {
  const row = value as Row;
  const current = ++previewRevision;
  pages.value = [];
  previewTitle.value = String(row.title);
  preview.value = true;
  previewBusy.value = true;
  previewError.value = '';
  try {
    const result = await request<{ data: Page[] }>(`/knowledge/documents/${row.id}/pages`);
    if (current === previewRevision) pages.value = result.data;
  } catch (e) {
    if (current === previewRevision) previewError.value = message(e);
  } finally {
    if (current === previewRevision) previewBusy.value = false;
  }
}
function state(value: string) {
  return tr(`knowledge.state.${value}`);
}
const operating = ref('');
async function operation(row: Row, action: 'index' | 'publish' | 'disable' | 'retry-parse') {
  if (operating.value) return;
  operating.value = row.id;
  try {
    await request(`/knowledge/documents/${row.id}/${action}`, {
      method: 'POST',
      body: JSON.stringify({ rowVersion: row.rowVersion }),
    });
    ElMessage.success(
      tr(
        action === 'retry-parse'
          ? 'knowledge.retryQueued'
          : action === 'index'
            ? 'knowledge.indexed'
            : action === 'disable'
              ? 'knowledge.disabled'
              : 'knowledge.published',
      ),
    );
    await load();
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    operating.value = '';
  }
}
function actions(value: unknown) {
  const row = value as Row;
  if (operating.value) return [];
  const result = [];
  if (auth.can('knowledge', '编辑')) {
    result.push({
      label: tr('knowledge.versions'),
      run: () => {
        versionDocument.value = row;
      },
    });
    if (row.parseStatus === 'FAILED')
      result.push({ label: tr('knowledge.retryParse'), run: () => operation(row, 'retry-parse') });
  }
  if (auth.can('knowledge', '编辑'))
    result.push({ label: tr('knowledge.applicability'), run: () => openBinding(row) });
  if (auth.can('knowledge', '编辑'))
    result.push({ label: tr('knowledge.replace'), run: () => replaceDialog(row) });
  if (auth.can('knowledge', '编辑') && row.parseStatus === 'READY')
    result.push({ label: tr('knowledge.index'), run: () => operation(row, 'index') });
  if (auth.can('knowledge', '发布') && row.parseStatus === 'READY')
    result.push({ label: tr('knowledge.publish'), run: () => operation(row, 'publish') });
  if (auth.can('knowledge', '停用') && row.status === 'PUBLISHED')
    result.push({ label: tr('knowledge.disable'), run: () => operation(row, 'disable') });
  return result;
}
watch([page, size], load);
onMounted(load);
const timer = setInterval(() => {
  if (
    rows.value.some(
      (r) =>
        ['PENDING', 'RUNNING'].includes(r.parseStatus) ||
        ['PENDING', 'RUNNING'].includes(r.indexStatus || ''),
    )
  )
    void load();
}, 5000);
onUnmounted(() => clearInterval(timer));
</script>
<template>
  <section class="knowledge-page">
    <KnowledgeVersionHistory :document="versionDocument" @close="versionDocument = undefined" />
    <AppDialog
      v-model="binding"
      :title="tr('knowledge.applicability')"
      :busy="busy"
      :before-close="bindingGuard.permitLeave"
      ><ElAlert :title="tr('knowledge.scopeNotice')" type="info" :closable="false" /><ElCheckbox
        v-model="bindingAll"
        >{{ tr('knowledge.generic') }}</ElCheckbox
      ><PagedChoiceField
        v-if="!bindingAll"
        :model-value="bindingIds"
        :options="bindingOptions"
        multiple
        :load-rows="equipmentRows"
        :columns="[
          { key: 'code', label: tr('xiaozhu.equipmentCode') },
          { key: 'name', label: tr('xiaozhu.equipmentName') },
          { key: 'model', label: tr('xiaozhu.equipmentModel') },
        ]"
        @update:model-value="bindingIds = Array.isArray($event) ? $event : [$event]"
        @select="bindingOptions = $event"
      /><template #footer
        ><ElButton :disabled="busy" @click="cancelBinding">{{ tr('knowledge.cancel') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="saveBinding">{{
          tr('ai.save')
        }}</ElButton></template
      ></AppDialog
    >
    <div class="knowledge-toolbar">
      <KnowledgeCaseCreator
        v-if="auth.can('knowledge', '编辑') && auth.can('repair', '查看')"
        @saved="load"
      />
      <ElButton v-if="auth.can('knowledge', '编辑')" type="primary" @click="uploadDialog">{{
        tr('knowledge.upload')
      }}</ElButton>
      <div class="knowledge-search">
        <ElInput
          v-model="query"
          clearable
          :placeholder="tr('knowledge.search')"
          @keyup.enter="search"
        /><ElButton @click="search">{{ tr('knowledge.searchAction') }}</ElButton>
      </div>
    </div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <AppBusinessTable
      :loading="loading"
      :rows="rows"
      :pagination="false"
      row-key="id"
      class="knowledge-table"
    >
      <ElTableColumn
        prop="title"
        :label="tr('knowledge.title')"
        min-width="260"
        show-overflow-tooltip
      />
      <ElTableColumn :label="tr('knowledge.status')" width="120"
        ><template #default="scope">{{ state(scope.row.status) }}</template></ElTableColumn
      >
      <ElTableColumn :label="tr('knowledge.parseStatus')" width="150"
        ><template #default="scope">{{ state(scope.row.parseStatus) }}</template></ElTableColumn
      >
      <ElTableColumn :label="tr('knowledge.indexStatus')" width="140"
        ><template #default="scope">{{
          scope.row.indexStatus ? state(scope.row.indexStatus) : ''
        }}</template></ElTableColumn
      >
      <ElTableColumn
        v-if="
          auth.can('knowledge', '编辑') ||
          auth.can('knowledge', '发布') ||
          auth.can('knowledge', '停用')
        "
        :label="tr('knowledge.actions')"
        fixed="right"
        width="170"
        ><template #default="scope"
          ><ElButton
            v-if="auth.can('knowledge', '编辑')"
            link
            type="primary"
            :disabled="!!operating"
            @click="inspect(scope.row)"
            >{{ tr('knowledge.inspect') }}</ElButton
          ><RowMoreActions v-if="actions(scope.row).length" :actions="actions(scope.row)" /><span
            v-if="operating === scope.row.id"
            >{{ tr('knowledge.processing') }}</span
          ></template
        ></ElTableColumn
      >
    </AppBusinessTable>
    <footer class="knowledge-footer">
      <AppPagination v-model:page="page" v-model:page-size="size" :total="total" />
    </footer>
    <AppDialog
      v-model="dialog"
      :title="tr('knowledge.upload')"
      :busy="busy"
      :before-close="uploadGuard.permitLeave"
    >
      <ElForm ref="form" :model="{ company, title }" label-width="90px" label-position="left">
        <ElFormItem
          v-if="!replacement"
          prop="company"
          :label="tr('knowledge.company')"
          :rules="[{ required: true, message: tr('knowledge.required'), trigger: 'change' }]"
          ><ElSelect v-model="company" filterable
            ><ElOption v-for="c in companies" :key="c.id" :value="c.id" :label="c.name" /></ElSelect
        ></ElFormItem>
        <ElFormItem
          prop="title"
          :label="tr('knowledge.title')"
          :rules="[
            {
              required: true,
              whitespace: true,
              message: tr('knowledge.required'),
              trigger: 'blur',
            },
          ]"
          ><ElInput v-model="title" maxlength="200" :readonly="!!replacement"
        /></ElFormItem>
        <ElFormItem v-if="!replacement" :label="tr('knowledge.type')"
          ><ElSelect v-model="documentType"
            ><ElOption value="MANUAL" :label="tr('knowledge.manual')" /><ElOption
              value="SOP"
              label="SOP" /></ElSelect
        ></ElFormItem>
        <ElFormItem :label="tr('knowledge.file')"
          ><ElUpload
            :auto-upload="false"
            :limit="1"
            accept=".pdf,application/pdf"
            :disabled="busy"
            @change="choose"
            @remove="file = undefined"
            ><ElButton :disabled="busy">{{ tr('assist.chooseFile') }}</ElButton></ElUpload
          ></ElFormItem
        >
      </ElForm>
      <p class="knowledge-note">{{ tr('knowledge.draftNotice') }}</p>
      <template #footer
        ><ElButton :disabled="busy" @click="cancelUpload">{{ tr('knowledge.cancel') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="upload">{{
          tr('knowledge.upload')
        }}</ElButton></template
      >
    </AppDialog>
    <AppDialog v-model="preview" :title="previewTitle" width="800px">
      <div v-loading="previewBusy" class="knowledge-preview">
        <ElAlert v-if="previewError" :title="previewError" type="error" :closable="false" />
        <article v-for="p in pages" :key="p.pageNo">
          <header>
            {{ tr('knowledge.page', { page: p.pageNo }) }} ·
            {{ tr('knowledge.chunks', { count: p.chunkCount }) }}
          </header>
          <ElAlert
            v-if="p.needsOcr"
            :title="tr('knowledge.ocrRequired')"
            type="warning"
            :closable="false"
          />
          <pre>{{ p.textContent }}</pre>
        </article>
      </div>
    </AppDialog>
  </section>
</template>
<style scoped>
.knowledge-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface, #fff);
  border: 1px solid var(--line);
  border-radius: 6px;
  overflow: hidden;
}
.knowledge-toolbar {
  padding: 8px;
  display: flex;
  gap: 8px;
  align-items: center;
}
.knowledge-search {
  display: flex;
  gap: 8px;
  margin-left: auto;
  width: 340px;
}
.knowledge-table {
  flex: 1;
  min-height: 0;
}
.knowledge-footer {
  min-height: 42px;
  border-top: 1px solid var(--line);
  display: flex;
  align-items: center;
  padding: 0 8px;
}
.knowledge-note {
  color: var(--text-secondary);
  line-height: 1.6;
}
.knowledge-preview {
  min-height: 100px;
  max-height: 65vh;
  overflow: auto;
}
.knowledge-preview article {
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
}
.knowledge-preview header {
  font-weight: 700;
  margin-bottom: 8px;
}
.knowledge-preview pre {
  white-space: pre-wrap;
  font: inherit;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.el-select {
  width: 100%;
}
@media (max-width: 600px) {
  .knowledge-search {
    width: auto;
    min-width: 0;
  }
}
</style>
