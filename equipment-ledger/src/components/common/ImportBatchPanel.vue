<script setup lang="ts">import { tr } from '../../i18n';

import 'element-plus/es/components/steps/style/css';
import 'element-plus/es/components/step/style/css';
import 'element-plus/es/components/alert/style/css';
import 'element-plus/es/components/radio-group/style/css';
import 'element-plus/es/components/radio-button/style/css';
import 'element-plus/es/components/link/style/css';
import { computed, ref, watch, onBeforeUnmount } from 'vue';
import {
  ElUpload,
  ElButton,
  ElAlert,
  ElSteps,
  ElStep,
  ElTag,
  ElRadioGroup,
  ElRadioButton,
  ElCheckbox,
  ElTableColumn,
  ElLink,
  type UploadFile,
  type UploadInstance,
} from 'element-plus';
import AppBusinessTable from './AppBusinessTable.vue';
import AppPagination from './AppPagination.vue';
import AppFormFooter from './AppFormFooter.vue';
import AppBackButton from './AppBackButton.vue';
import AppIcon from './AppIcon.vue';
import AppFilePicker from './AppFilePicker.vue';
import { formatDateTime } from '../../utils/dateTime';
import type {
  ImportApi,
  ImportBatch,
  ImportPreviewRow,
  ImportResultRow,
  ImportPreview,
  ImportState,
} from '../../imports';
const props = withDefaults(
  defineProps<{
    api: ImportApi;
    objectLabel: string;
    batchId?: string;
    maxRows?: number;
    maxMb?: number;
    canViewResults?: boolean;
    presentation?: 'page' | 'dialog';
  }>(),
  { batchId: '', maxRows: 10000, maxMb: 10, canViewResults: false, presentation: 'page' },
);
const emit = defineEmits<{
  'batch-change': [id: string];
  'busy-change': [busy: boolean];
  back: [];
  'open-record': [row: ImportResultRow];
}>();
const batch = ref<ImportBatch>();
const error = ref('');
const downloading = ref(false);
const uploading = ref(false);
const submitting = ref(false);
watch([uploading, submitting], ([upload, submit]) => emit('busy-change', upload || submit));
const loading = ref(false);
const preview = ref<ImportPreview>();
const results = ref<ImportResultRow[]>([]);
const resultTotal = ref(0);
const showResults = ref(false);
const onlyErrors = ref(true);
const allColumns = ref(false);
const page = ref(1);
const size = ref(10);
const uploader = ref<UploadInstance>();
const stateNames: Record<ImportState, string> = {
  VALIDATING: '正在校验',
  INVALID: '需要修改',
  READY: '校验通过',
  IMPORTING: '正在导入',
  SUCCEEDED: '导入成功',
  FAILED: tr('ui.validation.message35'),
  EXPIRED: '已过期',
};
const processing = computed(
  () =>
    uploading.value || batch.value?.state === 'VALIDATING' || batch.value?.state === 'IMPORTING',
);
const step = computed(() =>
  !batch.value
    ? 0
    : batch.value.state === 'SUCCEEDED'
      ? 3
      : batch.value.state === 'IMPORTING'
        ? 2
        : 1,
);
const statusType = computed(() =>
  batch.value?.state === 'SUCCEEDED' || batch.value?.state === 'READY'
    ? 'success'
    : batch.value?.state === 'INVALID' || batch.value?.state === 'FAILED'
      ? 'danger'
      : 'info',
);
const columns = computed(() => {
  const cols = preview.value?.columns || [];
  if (allColumns.value) return cols;
  const wrong = new Set(preview.value?.data.flatMap((r) => r.issues.map((e) => e.field)) || []);
  return cols.filter((c, i) => i < 8 || wrong.has(c.key));
});
const previewAvailable = computed(
  () =>
    batch.value?.totalRows &&
    batch.value.reportAvailable &&
    !['VALIDATING', 'EXPIRED'].includes(batch.value.state),
);
let timer: ReturnType<typeof setTimeout> | undefined;
let revision = 0;
let stateRevision = 0;
let listRevision = 0;
let alive = true;
const keys = new Map<string, string>();
function message(e: unknown) {
  return e instanceof Error ? e.message : tr('ui.equipment.text1428');
}
function save(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
async function download(report = false) {
  downloading.value = true;
  error.value = '';
  try {
    save(
      await (report && batch.value ? props.api.report(batch.value.id) : props.api.template()),
      report ? '设备导入校验结果.xlsx' : `${props.objectLabel}导入模板.xlsx`,
    );
  } catch (e) {
    error.value = message(e);
  } finally {
    downloading.value = false;
  }
}
function schedule() {
  clearTimeout(timer);
  if (alive && processing.value && batch.value) timer = setTimeout(() => void refresh(), 1500);
}
async function refresh() {
  const id = batch.value?.id || props.batchId;
  if (!id) return;
  const seq = revision;
  const stateSeq = ++stateRevision;
  try {
    const value = await props.api.state(id);
    if (!alive || seq !== revision || stateSeq !== stateRevision) return;
    const changed = batch.value?.rowVersion !== value.rowVersion;
    batch.value = value;
    if (changed && !['VALIDATING', 'IMPORTING'].includes(value.state)) {
      onlyErrors.value = value.errorRows > 0;
      page.value = 1;
      await loadRows();
    }
    schedule();
  } catch (e) {
    if (seq === revision) {
      error.value = message(e);
      clearTimeout(timer);
    }
  }
}
async function selectFile(file: UploadFile) {
  await selectRawFile(file.raw);
}
async function selectRawFile(raw?: File) {
  uploader.value?.clearFiles();
  if (!raw || uploading.value || processing.value) return;
  if (!/\.xlsx$/i.test(raw.name) || raw.size === 0 || raw.size > props.maxMb * 1024 * 1024) {
    error.value = tr('ui.common.dynamic56',{p0:props.maxMb});
    return;
  }
  const seq = ++revision;
  ++listRevision;
  clearTimeout(timer);
  uploading.value = true;
  error.value = '';
  preview.value = undefined;
  showResults.value = false;
  results.value = [];
  try {
    const value = await props.api.upload(raw);
    if (!alive || seq !== revision) return;
    batch.value = value;
    emit('batch-change', value.id);
    await refresh();
  } catch (e) {
    if (seq === revision) error.value = message(e);
  } finally {
    if (seq === revision) {
      uploading.value = false;
      schedule();
    }
  }
}
async function loadRows() {
  if (!batch.value) return;
  const seq = ++listRevision;
  const id = batch.value.id;
  loading.value = true;
  try {
    if (showResults.value) {
      const value = await props.api.results(id, page.value, size.value);
      if (seq !== listRevision || id !== batch.value?.id) return;
      results.value = value.data;
      resultTotal.value = value.page.total;
    } else if (previewAvailable.value) {
      const value = await props.api.preview(id, page.value, size.value, onlyErrors.value);
      if (seq !== listRevision || id !== batch.value?.id) return;
      preview.value = value;
    }
  } catch (e) {
    if (seq === listRevision) error.value = message(e);
  } finally {
    if (seq === listRevision) loading.value = false;
  }
}
watch([page, size, onlyErrors, showResults], () => void loadRows());
watch([onlyErrors, size, showResults], () => (page.value = 1));
watch(
  () => props.batchId,
  async (id) => {
    if (id === batch.value?.id) return;
    ++revision;
    ++listRevision;
    clearTimeout(timer);
    batch.value = undefined;
    preview.value = undefined;
    if (id) await refresh();
  },
  { immediate: true },
);
function confirmKey(id: string) {
  if (keys.has(id)) return keys.get(id)!;
  let key: string | undefined;
  try {
    key = sessionStorage.getItem(`eam:import:${id}`) || undefined;
  } catch {}
  key ||= crypto.randomUUID();
  keys.set(id, key);
  try {
    sessionStorage.setItem(`eam:import:${id}`, key);
  } catch {}
  return key;
}
async function confirm() {
  if (!batch.value || batch.value.state !== 'READY' || submitting.value) return;
  submitting.value = true;
  error.value = '';
  try {
    batch.value = await props.api.confirm(
      batch.value.id,
      batch.value.rowVersion,
      confirmKey(batch.value.id),
    );
    schedule();
  } catch (e) {
    error.value = message(e);
    await refresh();
  } finally {
    submitting.value = false;
  }
}
function cellErrors(row: ImportPreviewRow, key: string) {
  return row.issues.filter((e) => e.field === key);
}
onBeforeUnmount(() => {
  alive = false;
  ++revision;
  ++listRevision;
  clearTimeout(timer);
});
</script>
<template>
  <section class="import-page" :class="{ 'import-dialog-panel': presentation === 'dialog' }">
    <header class="import-heading">
      <div>
        <AppBackButton
          v-if="presentation === 'page'"
          :label="`返回${objectLabel}`"
          :disabled="uploading"
          @click="emit('back')"
        />
        <h1 v-if="presentation === 'page'">{{ objectLabel }}{{ tr('ui.equipment.text1196') }}</h1>
        <ElTag>{{ tr('ui.equipment.text1429') }}</ElTag>
      </div>
      <ElButton :loading="downloading" @click="download()"
        ><AppIcon name="Download" />{{ tr('ui.equipment.text1430') }}</ElButton
      >
    </header>
    <main class="import-body">
      <ElSteps :active="step" simple
        ><ElStep :title="tr('ui.equipment.text1431')" /><ElStep :title="tr('ui.equipment.text1432')" /><ElStep
          :title="tr('ui.equipment.text1433')" /><ElStep :title="tr('ui.equipment.text1434')"
      /></ElSteps>
      <ElAlert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
        class="import-alert"
      />
      <section v-if="!batch" class="import-panel">
        <h2>{{ tr('ui.equipment.text0135') }}{{ objectLabel }}{{ tr('ui.equipment.text1435') }}</h2>
        <p class="import-description">
          {{ tr('ui.equipment.text1436') }}
        </p>
        <ElUpload
          ref="uploader"
          drag
          accept=".xlsx"
          :auto-upload="false"
          :show-file-list="false"
          :multiple="false"
          :disabled="processing"
          :on-change="selectFile"
          class="import-upload"
          ><AppIcon name="Upload" />
          <div>{{ uploading ? tr('ui.equipment.text1437') : tr('ui.equipment.text1438') }}</div>
          <small
            >{{ tr('ui.equipment.text1439') }}{{ maxMb }}{{ tr('ui.equipment.text1440') }}{{
              maxRows
            }}{{ tr('ui.equipment.text1441') }}</small
          ></ElUpload
        >
        <p class="import-description">
          {{ tr('ui.equipment.text1442') }}
        </p>
      </section>
      <template v-if="batch">
        <section class="import-panel">
          <div class="import-batch-heading">
            <div>
              <strong>{{ batch.fileName }}</strong
              ><ElTag :type="statusType">{{ stateNames[batch.state] }}</ElTag>
            </div>
            <div>
              <AppFilePicker
                accept=".xlsx"
                :disabled="processing"
                @select="(files) => selectRawFile(files[0])"
                ><ElButton :disabled="processing" :loading="uploading"
                  >{{ tr('ui.equipment.text1443') }}</ElButton
                ></AppFilePicker
              >
              <ElButton text :disabled="loading" @click="refresh">{{ tr('ui.equipment.text1444') }}</ElButton>
            </div>
          </div>
          <p v-if="!['INVALID', 'READY', 'SUCCEEDED'].includes(batch.state)">{{ batch.message }}</p>
          <p class="import-batch-id">
            {{ tr('ui.equipment.text1445') }}{{ batch.id }} {{ tr('ui.equipment.text1446') }}{{ formatDateTime(batch.createdAt) }} {{ tr('ui.equipment.text1447') }}
          </p>
          <div class="import-counts">
            <div>
              <span>{{ tr('ui.equipment.text1448') }}</span><strong>{{ batch.totalRows }}</strong>
            </div>
            <div>
              <span>{{ tr('ui.equipment.text1449') }}</span><strong>{{ batch.validRows }}</strong>
            </div>
            <div :class="{ 'has-errors': batch.errorRows > 0 }">
              <span>{{ tr('ui.equipment.text1450') }}</span><strong>{{ batch.errorRows }}</strong>
            </div>
            <div :class="{ 'has-errors': batch.errorCount > 0 }">
              <span>{{ tr('ui.equipment.text1451') }}</span><strong>{{ batch.errorCount }}</strong>
            </div>
          </div>
          <ElAlert
            v-if="batch.state === 'READY'"
            type="info"
            :closable="false"
            show-icon
            :title="`将新增${batch.totalRows}条${objectLabel}，其中${batch.autoNumberRows}条自动生成编号。当前尚未写入。`"
          />
          <ElAlert
            v-if="batch.state === 'INVALID'"
            type="error"
            :closable="false"
            show-icon
            :title="tr('ui.equipment.text1452')"
          />
          <ElAlert
            v-if="batch.state === 'SUCCEEDED'"
            type="success"
            :closable="false"
            show-icon
            :title="`成功新增${batch.successRows}条${objectLabel}。重复确认不会再次新增。`"
          />
          <p v-if="processing" role="status">
            {{
              batch.state === 'IMPORTING'
                ? tr('ui.equipment.text1453')
                : tr('ui.equipment.text1454')
            }}{{ tr('ui.equipment.text1455') }}
          </p>
        </section>
        <section
          v-if="previewAvailable || (batch.state === 'SUCCEEDED' && canViewResults)"
          class="import-panel import-preview"
        >
          <div class="import-preview-toolbar">
            <div>
              <ElButton
                v-if="batch.state === 'SUCCEEDED' && canViewResults"
                :type="showResults ? 'primary' : 'default'"
                @click="showResults = !showResults"
                >{{ showResults ? tr('ui.equipment.text1456') : tr('ui.equipment.text1457') }}</ElButton
              ><ElRadioGroup v-if="!showResults" v-model="onlyErrors"
                ><ElRadioButton :value="true">{{ tr('ui.equipment.text1458') }}</ElRadioButton
                ><ElRadioButton :value="false">{{ tr('ui.equipment.text1459') }}</ElRadioButton></ElRadioGroup
              ><ElCheckbox v-if="!showResults" v-model="allColumns">{{ tr('ui.equipment.text1460') }}</ElCheckbox>
            </div>
            <ElButton v-if="batch.reportAvailable" :loading="downloading" @click="download(true)"
              >{{ tr('ui.equipment.text1461') }}</ElButton
            >
          </div>
          <AppBusinessTable
            v-if="!showResults"
            :rows="preview?.data || []"
            :loading="loading"
            :pagination="false"
            :fill="false"
            :max-height="440"
            row-key="row"
            ><!-- @vue-generic {ImportPreviewRow} -->
            <ElTableColumn prop="row" :label="tr('ui.equipment.text1462')" width="100" fixed="left" />
            <!-- @vue-generic {ImportPreviewRow} -->
            <ElTableColumn v-for="col in columns" :key="col.key" :label="col.label" min-width="180"
              ><template #default="{ row }"
                ><div
                  :class="{ 'import-cell-error': cellErrors(row, col.key).length }"
                  :title="
                    cellErrors(row, col.key)
                      .map((e) => e.reason + '；' + e.suggestion)
                      .join('\n')
                  "
                >
                  {{ row.values[col.key]
                  }}<small v-for="issue in cellErrors(row, col.key)" :key="issue.reason">{{
                    issue.reason
                  }}</small>
                </div></template
              ></ElTableColumn
            >
            <!-- @vue-generic {ImportPreviewRow} -->
            <ElTableColumn :label="tr('ui.equipment.text1463')" min-width="360" fixed="right"
              ><template #default="{ row }"
                ><div v-for="(issue, index) in row.issues" :key="index" class="import-issue">
                  <strong>{{ issue.label }}：{{ issue.reason }}</strong
                  ><span>{{ issue.suggestion }}</span>
                </div>
                <ElTag v-if="!row.issues.length" type="success" size="small"
                  >{{ tr('ui.equipment.text1449') }}</ElTag
                ></template
              ></ElTableColumn
            ><template #empty>{{
              onlyErrors ? tr('ui.equipment.text1464') : tr('ui.equipment.text1465')
            }}</template></AppBusinessTable
          >
          <AppBusinessTable
            v-else
            :rows="results"
            :loading="loading"
            :pagination="false"
            :fill="false"
            :max-height="440"
            row-key="id"
            ><!-- @vue-generic {ImportResultRow} -->
            <ElTableColumn :label="tr('ui.equipment.text0078')" min-width="190"
              ><template #default="{ row }"
                ><ElLink type="primary" :underline="false" @click="emit('open-record', row)">{{
                  row.code
                }}</ElLink></template
              ></ElTableColumn
            ><!-- @vue-generic {ImportResultRow} -->
            <ElTableColumn
              prop="name"
              :label="tr('ui.equipment.text0081')"
              min-width="200" /><!-- @vue-generic {ImportResultRow} -->
            <ElTableColumn prop="department" :label="tr('ui.equipment.text0098')" min-width="280"
          /></AppBusinessTable>
          <div class="import-pagination">
            <span>{{ tr('ui.equipment.text0309') }} {{ showResults ? resultTotal : preview?.page.total || 0 }} {{ tr('ui.equipment.text0296') }}</span
            ><AppPagination
              v-model:page="page"
              v-model:page-size="size"
              :total="showResults ? resultTotal : preview?.page.total || 0"
            />
          </div>
        </section>
      </template>
    </main>
    <component
      :is="presentation === 'dialog' ? 'footer' : AppFormFooter"
      :class="{ 'import-dialog-footer': presentation === 'dialog' }"
      ><ElButton :disabled="uploading || submitting" @click="emit('back')">{{
        batch?.state === 'SUCCEEDED' ? (presentation === 'dialog' ? tr('ui.equipment.text0076') : tr('ui.equipment.text1466')) : tr('ui.equipment.text0157')
      }}</ElButton
      ><ElButton
        v-if="batch?.state === 'READY'"
        type="primary"
        :loading="submitting"
        @click="confirm"
        >{{ tr('ui.equipment.text1433') }}</ElButton
      ><ElButton v-else-if="batch?.state === 'INVALID'" disabled>{{ tr('ui.equipment.text1433') }}</ElButton></component
    >
  </section>
</template>
<style scoped>
.import-page {
  height: calc(100dvh - 54px);
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--page-bg, #f3f5f9);
  font-size: 13px;
  color: var(--text, #263246);
}
.import-dialog-panel {
  height: min(680px, calc(100dvh - 140px));
}
.import-dialog-panel .import-heading {
  padding: 0 0 12px;
}
.import-dialog-panel .import-body {
  padding: 12px;
}
.import-dialog-footer {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
  gap: 16px;
  padding: 12px 0 0;
  background: var(--surface, #fff);
  border-top: 1px solid var(--line);
}
.import-dialog-footer :deep(.el-button + .el-button) {
  margin-left: 0;
}
.import-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid var(--line);
  background: var(--surface, #fff);
  gap: 12px;
}
.import-heading > div,
.import-batch-heading > div {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.import-heading h1 {
  font-size: 16px;
  margin: 0;
}
.import-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.import-panel {
  padding: 16px;
  background: var(--surface, #fff);
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 6px;
  min-width: 0;
}
.import-panel h2 {
  font-size: 14px;
  margin: 0 0 10px;
}
.import-description {
  margin: 10px 0;
  line-height: 1.6;
}
.import-upload :deep(.el-upload) {
  width: 100%;
}
.import-upload :deep(.el-upload-dragger) {
  padding: 20px;
}
.import-upload .app-icon {
  font-size: 28px;
  color: var(--brand);
}
.import-upload small {
  display: block;
  margin-top: 8px;
}
.import-alert {
  flex: none;
}
.import-batch-heading,
.import-preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.import-batch-id {
  font-size: 12px;
  overflow-wrap: anywhere;
}
.import-counts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0;
}
.import-counts > div {
  background: var(--el-color-primary-light-9);
  padding: 12px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.import-counts strong {
  font-size: 22px;
  color: var(--brand);
}
.import-counts .has-errors {
  background: var(--el-color-danger-light-9);
}
.import-counts .has-errors strong {
  color: var(--el-color-danger);
}
.import-preview-toolbar {
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.import-preview-toolbar > div {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.import-cell-error {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  padding: 4px 6px;
  border-radius: 3px;
  overflow-wrap: anywhere;
}
.import-cell-error small {
  display: block;
  margin-top: 4px;
  font-size: 12px;
}
.import-issue {
  margin: 4px 0 8px;
  line-height: 1.5;
}
.import-issue strong {
  color: var(--el-color-danger);
  font-weight: 500;
  display: block;
}
.import-issue span {
  display: block;
  margin-top: 3px;
}
.import-pagination {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
}
.import-batch-heading strong {
  overflow-wrap: anywhere;
}
@media (max-width: 800px) {
  .import-counts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .import-pagination {
    flex-wrap: wrap;
  }
}
</style>

<style scoped>
.import-preview :deep(td.el-table__cell) {
  vertical-align: top;
}
</style>
