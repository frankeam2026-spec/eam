<script setup lang="ts">
import { ref, watch, onBeforeUnmount, nextTick } from 'vue';
import {
  ElButton,
  ElSelect,
  ElOption,
  ElAlert,
  ElTableColumn,
  ElTabs,
  ElTabPane,
  ElTag,
  ElLoading,
} from 'element-plus';
import 'element-plus/es/components/alert/style/css';
import AppPdfPreview from './AppPdfPreview.vue';
import AppDialog from './AppDialog.vue';
import AppBusinessTable from './AppBusinessTable.vue';
import {
  printing,
  printPdf,
  pdfFromBase64,
  downloadPrint,
  type PrintChoice,
  type PrintJob,
} from '../../printing/documents';
const props = defineProps<{ modelValue: boolean; documentId: string; documentCode?: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();
const vLoading = ElLoading.directive;
const templates = ref<PrintChoice[]>([]),
  templateId = ref(''),
  url = ref(''),
  fingerprint = ref(''),
  error = ref(''),
  tab = ref('print');
const busy = ref(false),
  records = ref<PrintJob[]>([]),
  selectedJob = ref<PrintJob>();
let key = crypto.randomUUID(),
  revision = 0;
const pdfPreview = ref<InstanceType<typeof AppPdfPreview>>();
function replaceUrl(blob?: Blob) {
  if (url.value) URL.revokeObjectURL(url.value);
  url.value = blob ? URL.createObjectURL(blob) : '';
}
onBeforeUnmount(() => {
  revision++;
  replaceUrl();
});
async function preview() {
  if (!templateId.value) return;
  const version = ++revision;
  busy.value = true;
  error.value = '';
  fingerprint.value = '';
  selectedJob.value = undefined;
  key = crypto.randomUUID();
  replaceUrl();
  try {
    const { data } = await printing.preview(props.documentId, templateId.value);
    if (version !== revision) return;
    replaceUrl(pdfFromBase64(data.pdf));
    fingerprint.value = data.fingerprint;
  } catch (e) {
    if (version === revision) error.value = e instanceof Error ? e.message : '预览失败';
  } finally {
    if (version === revision) busy.value = false;
  }
}
async function loadRecords() {
  records.value = (await printing.jobs(props.documentId)).data;
}
async function load() {
  revision++;
  tab.value = 'print';
  error.value = '';
  templates.value = [];
  templateId.value = '';
  fingerprint.value = '';
  selectedJob.value = undefined;
  replaceUrl();
  busy.value = true;
  try {
    const { data } = await printing.options(props.documentId);
    templates.value = data.templates;
    templateId.value = data.defaultId;
    await loadRecords();
  } catch (e) {
    error.value = e instanceof Error ? e.message : '打印配置读取失败';
  } finally {
    busy.value = false;
  }
  if (templateId.value) await preview();
}
watch(
  () => props.modelValue,
  (open) => {
    if (open) void load();
    else {
      revision++;
      replaceUrl();
    }
  },
  { immediate: true },
);
async function generate() {
  if (selectedJob.value) return selectedJob.value;
  if (!fingerprint.value) throw new Error('请先预览单据');
  const { data } = await printing.generate(
    props.documentId,
    templateId.value,
    fingerprint.value,
    key,
  );
  replaceUrl(await printPdf('jobs/' + data.id + '/file'));
  selectedJob.value = data;
  await loadRecords();
  return data;
}
async function output(kind: 'print' | 'download') {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    const job = await generate();
    if (kind === 'download') downloadPrint(url.value, job.documentCode);
    else {
      await nextTick();
      if (!pdfPreview.value) throw new Error('请先加载PDF预览');
      await pdfPreview.value.print();
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '生成失败';
  } finally {
    busy.value = false;
  }
}
async function openRecord(row: PrintJob) {
  busy.value = true;
  error.value = '';
  try {
    replaceUrl(await printPdf('jobs/' + row.id + '/file'));
    selectedJob.value = row;
    tab.value = 'print';
  } catch (e) {
    error.value = e instanceof Error ? e.message : '读取失败';
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <AppDialog
    :model-value="modelValue"
    :title="`出库单打印${documentCode ? ' · ' + documentCode : ''}`"
    width="1100px"
    :busy="busy"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="document-print-content" v-loading="busy">
      <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <ElTabs v-model="tab"
        ><ElTabPane label="打印预览" name="print" /><ElTabPane
          :label="`留底记录（最近${records.length}条）`"
          name="history"
      /></ElTabs>
      <template v-if="tab === 'print'">
        <div class="document-print-options">
          <span>打印模板</span
          ><ElSelect v-model="templateId" :disabled="busy" @change="preview"
            ><ElOption v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" /></ElSelect
          ><ElButton :disabled="busy || !templateId" @click="preview">重新预览</ElButton
          ><ElTag v-if="selectedJob" type="success">已留底 · {{ selectedJob.createdAt }}</ElTag>
        </div>
        <p class="document-print-help">
          {{
            selectedJob
              ? `当前查看生成时的PDF，单据状态：${selectedJob.documentStatus}。重新预览可使用当前单据及模板。`
              : '预览确认后打印或下载，系统保存正式PDF留底。针式多联纸请在打印机中选择实际纸张，按实际尺寸打印。'
          }}
        </p>
        <AppPdfPreview v-if="url" ref="pdfPreview" :src="url" class="document-print-frame" />
        <div v-else class="document-print-empty">
          {{ busy ? '正在生成预览…' : '请选择模板生成预览' }}
        </div>
      </template>
      <AppBusinessTable
        v-else
        :rows="records"
        :fill="false"
        :pagination="false"
        row-key="id"
        :max-height="430"
      >
        <ElTableColumn prop="createdAt" label="生成时间" min-width="170" /><ElTableColumn
          prop="templateName"
          label="模板"
          min-width="190"
          show-overflow-tooltip
        /><ElTableColumn prop="actorName" label="生成人" width="110" /><ElTableColumn
          prop="documentStatus"
          label="生成时状态"
          width="110"
        /><ElTableColumn prop="pageCount" label="页数" width="70" /><ElTableColumn
          label="操作"
          fixed="right"
          width="156"
          ><template #default="{ row }"
            ><ElButton link type="primary" @click="openRecord(row as PrintJob)"
              >查看原PDF</ElButton
            ></template
          ></ElTableColumn
        >
      </AppBusinessTable>
    </div>
    <template #footer
      ><ElButton :disabled="busy" @click="emit('update:modelValue', false)">关闭</ElButton
      ><ElButton v-if="tab === 'print'" :disabled="busy || !url" @click="output('download')"
        >下载PDF</ElButton
      ><ElButton
        v-if="tab === 'print'"
        type="primary"
        :loading="busy"
        :disabled="!url"
        @click="output('print')"
        >打印</ElButton
      ></template
    >
  </AppDialog>
</template>
<style scoped>
.document-print-content {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.document-print-options {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 13px;
}
.document-print-options .el-select {
  width: 260px;
}
.document-print-help {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
}
.document-print-frame {
  height: min(52dvh, 560px);
  min-height: 220px;
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: #f2f4f7;
}
.document-print-empty {
  padding: 60px 0;
  text-align: center;
}
</style>
