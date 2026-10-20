<script setup lang="ts">import { tr } from '../../i18n';

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
    if (version === revision) error.value = e instanceof Error ? e.message : tr('ui.equipment.text1516');
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
    error.value = e instanceof Error ? e.message : tr('ui.equipment.text1517');
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
  if (!fingerprint.value) throw new Error(tr('ui.equipment.text1518'));
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
      if (!pdfPreview.value) throw new Error(tr('ui.equipment.text1519'));
      await pdfPreview.value.print();
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('ui.equipment.text1520');
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
    error.value = e instanceof Error ? e.message : tr('ui.equipment.text1521');
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
        ><ElTabPane :label="tr('ui.equipment.text1522')" name="print" /><ElTabPane
          :label="`留底记录（最近${records.length}条）`"
          name="history"
      /></ElTabs>
      <template v-if="tab === 'print'">
        <div class="document-print-options">
          <span>{{ tr('ui.equipment.text0035') }}</span
          ><ElSelect v-model="templateId" :disabled="busy" @change="preview"
            ><ElOption v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" /></ElSelect
          ><ElButton :disabled="busy || !templateId" @click="preview">{{ tr('ui.equipment.text1523') }}</ElButton
          ><ElTag v-if="selectedJob" type="success">{{ tr('ui.equipment.text1524') }} {{ selectedJob.createdAt }}</ElTag>
        </div>
        <p class="document-print-help">
          {{
            selectedJob
              ? `当前查看生成时的PDF，单据状态：${selectedJob.documentStatus}。重新预览可使用当前单据及模板。`
              : tr('ui.equipment.text1525')
          }}
        </p>
        <AppPdfPreview v-if="url" ref="pdfPreview" :src="url" class="document-print-frame" />
        <div v-else class="document-print-empty">
          {{ busy ? tr('ui.equipment.text1526') : tr('ui.equipment.text1527') }}
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
        <ElTableColumn prop="createdAt" :label="tr('ui.equipment.text1528')" min-width="170" /><ElTableColumn
          prop="templateName"
          :label="tr('ui.equipment.text1529')"
          min-width="190"
          show-overflow-tooltip
        /><ElTableColumn prop="actorName" :label="tr('ui.equipment.text1530')" width="110" /><ElTableColumn
          prop="documentStatus"
          :label="tr('ui.equipment.text1531')"
          width="110"
        /><ElTableColumn prop="pageCount" :label="tr('ui.equipment.text1532')" width="70" /><ElTableColumn
          :label="tr('ui.equipment.text0145')"
          fixed="right"
          width="156"
          ><template #default="{ row }"
            ><ElButton link type="primary" @click="openRecord(row as PrintJob)"
              >{{ tr('ui.equipment.text1533') }}</ElButton
            ></template
          ></ElTableColumn
        >
      </AppBusinessTable>
    </div>
    <template #footer
      ><ElButton :disabled="busy" @click="emit('update:modelValue', false)">{{ tr('ui.equipment.text0076') }}</ElButton
      ><ElButton v-if="tab === 'print'" :disabled="busy || !url" @click="output('download')"
        >{{ tr('ui.equipment.text0706') }}</ElButton
      ><ElButton
        v-if="tab === 'print'"
        type="primary"
        :loading="busy"
        :disabled="!url"
        @click="output('print')"
        >{{ tr('ui.equipment.text0549') }}</ElButton
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
