<script setup lang="ts">
import DepartmentText from './common/DepartmentText.vue';
import { tr } from '../i18n';

import { ElAlert } from 'element-plus';
import 'element-plus/es/components/alert/style/css';
import AppListToolbar from './common/AppListToolbar.vue';
import { ElTableColumn } from 'element-plus';
import {
  ElButton,
  ElInput,
  ElInputNumber,
  ElSelect,
  ElOption,
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
} from 'element-plus';

import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import AppBackButton from './common/AppBackButton.vue';
import AppPagination from './common/AppPagination.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { confirmAction } from '../services/dialog';
import * as api from '../api';
import TemplateThumbnail from './TemplateThumbnail.vue';
import PrintGridDesigner from './PrintGridDesigner.vue';
import { defaultLayout, legacyToGrid, type GridLayout } from '../printing/grid';
const props = defineProps<{ actions: string[] }>(),
  can = (action: string) => props.actions.includes(action);
const templates = ref<api.LabelTemplate[]>([]),
  keyword = ref(''),
  editor = ref<api.LabelTemplate | null>(null),
  notice = ref(''),
  noticeType = ref<'success' | 'error' | 'info'>('error'),
  busy = ref(false),
  designKey = ref(0);
const focusDesign = ref(false);
const page = ref(1),
  pageSize = ref(20);
const list = computed(() =>
  templates.value.filter((t) => [t.name, t.remark].some((v) => v.includes(keyword.value))),
);
const pages = computed(() => Math.max(1, Math.ceil(list.value.length / pageSize.value)));
const currentPage = computed(() => Math.min(page.value, pages.value));
const pageRows = computed(() =>
  list.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value),
);
async function back() {
  if (!(await guard())) return;
  editor.value = null;
  notice.value = '';
}
function reset() {
  keyword.value = '';
  page.value = 1;
}
async function copyRow(t: api.LabelTemplate) {
  if (await open(t)) await copy();
}
async function removeRow(t: api.LabelTemplate) {
  if (!(await confirmAction(tr('ui.common.dynamic43',{p0:t.name})))) return;
  try {
    await api.deleteLabelTemplate(t.id);
    await load();
    message('模板已删除', 'success');
  } catch (e) {
    message(e);
  }
}
const editable = computed(() => !!editor.value && can(editor.value.id ? '编辑' : '新增'));
const formGuard = useUnsavedChanges(
  () => editor.value,
  () => !!editor.value,
  () => busy.value,
);
const dirty = formGuard.dirty;
function message(e: unknown, type: 'success' | 'error' | 'info' = 'error') {
  noticeType.value = type;
  notice.value = e instanceof Error ? e.message : String(e);
}
async function guard() {
  return !busy.value && (await formGuard.permitLeave());
}
function initial(): api.LabelTemplate {
  return {
    id: '',
    name: '',
    widthMm: 80,
    heightMm: 50,
    remark: '',
    elements: [],
    layout: defaultLayout(),
    rowVersion: 0,
    updatedAt: '',
  };
}
async function open(t: api.LabelTemplate) {
  if (!(await guard())) return false;
  editor.value = JSON.parse(JSON.stringify(t));
  formGuard.markSaved();
  designKey.value++;
  notice.value = '';
  return true;
}
async function create() {
  if (!(await guard())) return;
  editor.value = initial();
  formGuard.markSaved();
  designKey.value++;
  notice.value = '';
}
async function load() {
  try {
    templates.value = (await api.listLabelTemplates()).data;
  } catch (e) {
    message(e);
  }
}
async function save() {
  if (!editor.value || busy.value) return;
  if (!editor.value.name.trim()) {
    message(tr('ui.equipment.text0685'));
    return;
  }
  busy.value = true;
  try {
    editor.value = (await api.saveLabelTemplate(editor.value)).data;
    formGuard.markSaved();
    await load();
    message('模板已保存', 'success');
  } catch (e) {
    message(e);
  } finally {
    busy.value = false;
  }
}
async function copy() {
  if (!editor.value || busy.value) return;
  const t = JSON.parse(JSON.stringify(editor.value));
  t.id = '';
  t.name += '（副本）';
  t.rowVersion = 0;
  t.updatedAt = '';
  editor.value = t;
  formGuard.markSaved(null);
  designKey.value++;
}
async function remove() {
  if (!editor.value?.id || !(await confirmAction(tr('ui.common.dynamic43',{p0:editor.value.name})))) return;
  try {
    await api.deleteLabelTemplate(editor.value.id);
    editor.value = null;
    await load();
    message('模板已删除', 'success');
  } catch (e) {
    message(e);
  }
}
function convert() {
  if (!editor.value || !editable.value) return;
  try {
    editor.value.layout = legacyToGrid(editor.value);
    designKey.value++;
    message('已转换为单元格布局。保存前原模板保持不变。', 'info');
  } catch (e) {
    message(e);
  }
}
function paper(size: string) {
  if (!editor.value?.layout) return;
  const dims =
    size === 'A4' ? [210, 297] : size === 'A5' ? [148, 210] : size === 'A3' ? [297, 420] : [80, 50];
  const old = editor.value.widthMm,
    newWidth = dims[0]!;
  editor.value.widthMm = newWidth;
  editor.value.heightMm = dims[1]!;
  const g = editor.value.layout;
  const oldAvailable = old - g.margin.left - g.margin.right,
    newAvailable = newWidth - g.margin.left - g.margin.right;
  g.columns = g.columns.map((w) => Number(((w * newAvailable) / oldAvailable).toFixed(3)));
  if (size !== 'label' && g.rows.length === 10) {
    g.rows = Array(30).fill(8);
    g.area = null;
  }
}
const dimensionRevision = ref(0);
function setDimension(axis: 'widthMm' | 'heightMm', nextValue: number | undefined) {
  if (!editor.value || !editable.value) return;
  const value = Number(nextValue);
  if (!Number.isFinite(value) || value < 10 || value > 600) {
    dimensionRevision.value++;
    message('尺寸范围为10—600 mm');
    return;
  }
  const g = editor.value.layout;
  if (axis === 'widthMm' && g) {
    const oldAvailable = editor.value.widthMm - g.margin.left - g.margin.right,
      newAvailable = value - g.margin.left - g.margin.right;
    if (newAvailable <= 0) {
      dimensionRevision.value++;
      message('长度必须大于左右边距之和');
      return;
    }
    g.columns = g.columns.map((w) => Number(((w * newAvailable) / oldAvailable).toFixed(3)));
  }
  editor.value[axis] = value;
}
function rotate() {
  if (!editor.value?.layout) return;
  const old = editor.value.widthMm;
  editor.value.widthMm = editor.value.heightMm;
  editor.value.heightMm = old;
  const g = editor.value.layout;
  const total = g.columns.reduce((a, b) => a + b, 0);
  g.columns = g.columns.map((w) =>
    Number(((w * (editor.value!.widthMm - g.margin.left - g.margin.right)) / total).toFixed(3)),
  );
}
const pdfUrl = ref(''),
  pdfTitle = ref(''),
  pdfBusy = ref(false);
function closePdf() {
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value);
  pdfUrl.value = '';
}
function showPdf(blob: Blob, title: string) {
  closePdf();
  pdfUrl.value = URL.createObjectURL(blob);
  pdfTitle.value = title;
}
async function preview() {
  if (!editor.value || pdfBusy.value) return;
  pdfBusy.value = true;
  try {
    showPdf(
      await api.labelPreview({ ...editor.value, name: editor.value.name.trim() || '未命名模板' }),
      '打印预览 · 示例设备',
    );
  } catch (e) {
    message(e);
  } finally {
    pdfBusy.value = false;
  }
}
const printTarget = ref<api.LabelTemplate | null>(null);
const printOpen = ref(false),
  equipmentRows = ref<api.EquipmentRecord[]>([]),
  equipmentKeyword = ref(''),
  equipmentPage = ref(1),
  equipmentSize = ref(20),
  equipmentTotal = ref(0),
  ids = ref<string[]>([]),
  copies = ref(1);
async function openPrint(t: api.LabelTemplate | null = editor.value) {
  if (!t?.id) {
    message(tr('ui.validation.message20'));
    return;
  }
  if (t === editor.value && dirty.value) {
    message(tr('ui.validation.message21'));
    return;
  }
  printTarget.value = t;
  printOpen.value = true;
  ids.value = [];
  copies.value = 1;
  equipmentKeyword.value = '';
  equipmentPage.value = 1;
  await searchEquipment();
}
async function searchEquipment() {
  try {
    const r = await api.listEquipment({
      keyword: equipmentKeyword.value,
      page: equipmentPage.value,
      size: equipmentSize.value,
    });
    equipmentRows.value = r.data;
    equipmentTotal.value = r.page.total;
  } catch (e) {
    message(e);
  }
}
async function generate() {
  if (!printTarget.value?.id || !ids.value.length || busy.value) return;
  if (!Number.isInteger(copies.value) || copies.value < 1 || copies.value > 20) {
    message('份数需为1至20');
    return;
  }
  busy.value = true;
  try {
    const blob = await api.labelPdf(printTarget.value.id, ids.value, copies.value);
    printOpen.value = false;
    showPdf(blob, '设备标签 · ' + printTarget.value.name);
  } catch (e) {
    message(e);
  } finally {
    busy.value = false;
  }
}
function printPdf() {
  const frame = document.getElementById('print-pdf-frame') as HTMLIFrameElement | null;
  try {
    frame?.contentWindow?.focus();
    frame?.contentWindow?.print();
  } catch {
    if (pdfUrl.value) window.open(pdfUrl.value, '_blank');
  }
}
onMounted(load);
onBeforeRouteLeave(() => !busy.value);
onBeforeRouteUpdate(() => !busy.value);
onBeforeUnmount(closePdf);
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => equipmentRows.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];
</script>
<template>
  <section class="content print-template-page" :class="{ 'is-focused': editor && focusDesign }">
    <template v-if="!editor"
      ><AppListToolbar class="template-list-toolbar">
        <ElButton v-if="can('新增')" class="" @click="create" type="primary"
          ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0149') }}
        </ElButton>
        <form @submit.prevent="page = 1">
          <ListSearchInput v-model="keyword" :placeholder="tr('ui.equipment.text0668')" /><ElButton
            class="list-reset"
            @click="reset"
            native-type="button"
            text
            >{{ tr('ui.equipment.text0204') }}</ElButton
          >
        </form>
      </AppListToolbar>
      <div class="template-gallery">
        <div class="template-cards">
          <article v-for="t in pageRows" :key="t.id" class="template-card">
            <ElButton
              :title="t.remark || t.name"
              @click="open(t)"
              class="template-card-open app-content-button"
              ><div class="template-preview"><TemplateThumbnail :template="t" /></div>
              <div class="template-card-info">
                <strong :title="t.name">{{ t.name }}</strong
                ><span class="template-dimensions">{{ t.widthMm }} × {{ t.heightMm }} mm</span>
                <p class="template-remark" :title="t.remark">
                  {{ t.remark || tr('ui.equipment.text0669') }}
                </p>
              </div></ElButton
            >
            <div class="template-card-actions">
              <ElButton type="primary" link @click="open(t)">{{
                can('编辑') ? tr('ui.equipment.text0670') : tr('ui.equipment.text0671')
              }}</ElButton>
              <div class="template-card-secondary-actions">
                <ElButton v-if="can('打印')" @click="openPrint(t)">{{ tr('ui.equipment.text0549') }}</ElButton>
                <ElDropdown v-if="can('新增') || can('删除')" trigger="click">
                  <ElButton :aria-label="tr('ui.equipment.text0672')" :title="tr('ui.equipment.text0673')"
                    ><AppIcon name="More"
                  /></ElButton>
                  <template #dropdown
                    ><ElDropdownMenu>
                      <ElDropdownItem v-if="can('新增')" @click="copyRow(t)"
                        >{{ tr('ui.equipment.text0674') }}</ElDropdownItem
                      >
                      <ElDropdownItem v-if="can('删除')" @click="removeRow(t)" class="danger-text"
                        >{{ tr('ui.equipment.text0675') }}</ElDropdownItem
                      >
                    </ElDropdownMenu></template
                  >
                </ElDropdown>
              </div>
            </div>
          </article>
        </div>
        <div v-if="!pageRows.length" class="gallery-empty">
          {{ keyword ? tr('ui.equipment.text0676') : tr('ui.equipment.text0677') }}
        </div>
      </div>
      <footer class="template-pagination">
        <span>{{ tr('ui.equipment.text0309') }} {{ list.length }} {{ tr('ui.equipment.text0678') }}</span
        ><AppPagination
          v-model:page="page"
          v-model:page-size="pageSize"
          :total="list.length"
        /></footer
    ></template>
    <template v-else
      ><header class="print-page-head">
        <AppBackButton :label="tr('ui.equipment.text0679')" :disabled="busy" @click="back" /><strong>{{
          editor.id ? tr('ui.equipment.text0670') : tr('ui.equipment.text0680')
        }}</strong
        ><span v-if="dirty" class="unsaved">{{ tr('ui.equipment.text0681') }}</span>
        <div>
          <ElButton @click="focusDesign = !focusDesign">{{
            focusDesign ? tr('ui.equipment.text0682') : tr('ui.equipment.text0683')
          }}</ElButton>
          <ElButton v-if="can('新增')" class="" :disabled="busy" @click="copy"> {{ tr('ui.equipment.text0270') }}</ElButton
          ><ElButton class="" :disabled="busy || pdfBusy" @click="preview">{{
            pdfBusy ? tr('ui.equipment.text0684') : tr('ui.equipment.text0269')
          }}</ElButton
          ><ElButton
            v-if="can('打印')"
            class=""
            :disabled="busy || !editor.id"
            @click="openPrint()"
          >
            {{ tr('ui.equipment.text0549') }}</ElButton
          ><ElButton v-if="editable" class="" :disabled="busy" @click="save" type="primary">{{
            busy ? tr('ui.equipment.text0158') : tr('ui.equipment.text0159')
          }}</ElButton>
        </div>
      </header>
      <div class="print-three-columns" :inert="busy || undefined">
        <div class="template-center" v-if="editor">
          <div class="template-meta">
            <label
              >{{ tr('ui.equipment.text0404') }}<ElInput
                v-model="editor.name"
                :disabled="!editable"
                maxlength="100"
                :placeholder="tr('ui.equipment.text0685')"
            /></label>
            <span class="template-dimensions-summary"
              >{{ tr('ui.equipment.text0686') }} {{ editor.widthMm }} × {{ editor.heightMm }} mm</span
            >
            <label class="remark"
              >{{ tr('ui.equipment.text0130') }}<ElInput
                v-model="editor.remark"
                :disabled="!editable"
                maxlength="500"
                :placeholder="tr('ui.equipment.text0687')"
            /></label>
          </div>
          <PrintGridDesigner
            v-if="editor.layout"
            :key="designKey"
            v-model="editor.layout"
            :width="editor.widthMm"
            :height="editor.heightMm"
            :editable="editable"
            @message="message"
            ><template #paper>
              <div class="paper-settings-form">
                <label
                  >{{ tr('ui.equipment.text0688') }}<ElSelect
                    :placeholder="`自定义 ${editor.widthMm} × ${editor.heightMm} mm`"
                    :disabled="!editable"
                    @change="paper($event)"
                    :model-value="''"
                    ><ElOption
                      value=""
                      :label="
                        tr('ui.equipment.text0689') + String(editor.widthMm) + ' ' + String(editor.heightMm)
                      " /><ElOption value="label" :label="tr('ui.equipment.text0690')" /><ElOption
                      value="A5"
                      :label="'A5 148 × 210'" /><ElOption
                      value="A4"
                      :label="'A4 210 × 297'" /><ElOption
                      value="A3"
                      :label="'A3 297 × 420'" /></ElSelect
                ></label>
                <div class="paper-dimensions">
                  <label
                    >{{ tr('ui.equipment.text0691') }}<ElInputNumber
                      :key="'widthMm' + dimensionRevision"
                      :model-value="editor.widthMm"
                      @change="setDimension('widthMm', $event)"
                      :disabled="!editable"
                      :min="10"
                      :max="600"
                      :step="0.1"
                      controls-position="right" /></label
                  ><label
                    >{{ tr('ui.equipment.text0692') }}<ElInputNumber
                      :key="'heightMm' + dimensionRevision"
                      :model-value="editor.heightMm"
                      @change="setDimension('heightMm', $event)"
                      :disabled="!editable"
                      :min="10"
                      :max="600"
                      :step="0.1"
                      controls-position="right"
                  /></label>
                </div>
                <ElButton :disabled="!editable" @click="rotate">{{ tr('ui.equipment.text0693') }}</ElButton>
              </div></template
            ></PrintGridDesigner
          >
          <div v-else class="legacy-template">
            <h3>{{ tr('ui.equipment.text0694') }}</h3>
            <p>{{ tr('ui.equipment.text0695') }}</p>
            <ElButton v-if="editable" class="" @click="convert" type="primary"
              >{{ tr('ui.equipment.text0696') }}</ElButton
            ><ElButton v-if="can('新增')" class="" @click="create">{{ tr('ui.equipment.text0697') }}</ElButton>
          </div>
        </div>
      </div></template
    >
    <ElAlert v-if="notice" :title="notice" :type="noticeType" show-icon @close="notice = ''" />
    <div v-if="printOpen" class="print-modal-mask">
      <section class="print-picker">
        <header>
          <strong>{{ tr('ui.equipment.text0698') }}</strong
          ><ElButton @click="printOpen = false" :aria-label="tr('ui.equipment.text0076')" :title="tr('ui.equipment.text0076')"
            ><AppIcon name="Close"
          /></ElButton>
        </header>
        <div class="print-search">
          <ElInput
            v-model="equipmentKeyword"
            :placeholder="tr('ui.equipment.text0699')"
            @keyup.enter="
              equipmentPage = 1;
              searchEquipment();
            "
          /><ElButton
            class=""
            @click="
              equipmentPage = 1;
              searchEquipment();
            "
            type="primary"
          >
            {{ tr('ui.equipment.text0700') }}
          </ElButton>
        </div>
        <div class="print-picker-scroll">
          <AppBusinessTable
            v-model:selection-ids="ids"
            :selection-width="48"
            selection-fixed="left"
            selection-align="center"
            selectable
            :rows="equipmentRows"
            :pagination="false"
            :fill="false"
            :framed="false"
            class="business-table"
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0078') }}</div></template
              ><template #default="{ row: e, $index: tableRowIndex }">{{
                e.code
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn :min-width="210"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0081') }}</div></template
              ><template #default="{ row: e, $index: tableRowIndex }">{{
                e.name
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0098') }}</div></template
              ><template #default="{ row: e, $index: tableRowIndex }"><DepartmentText :value="e.departmentPath || e.department" :department-id="e.departmentOrgId" /></template></ElTableColumn
            ><template #empty
              ><div v-if="!equipmentRows.length">{{ tr('ui.equipment.text0701') }}</div></template
            ></AppBusinessTable
          >
        </div>
        <div class="print-picker-pages">
          <span>{{ tr('ui.equipment.text0295') }} {{ ids.length }} {{ tr('ui.equipment.text0702') }} {{ equipmentTotal }} {{ tr('ui.equipment.text0370') }}</span
          ><AppPagination
            :page="equipmentPage"
            v-model:page-size="equipmentSize"
            :total="equipmentTotal"
            @update:page="equipmentPage = $event; searchEquipment()"
          />
        </div>
        <footer>
          <label
            >{{ tr('ui.equipment.text0704') }}<ElInputNumber
              v-model="copies"
              :min="1"
              :max="20"
              controls-position="right" /></label
          ><ElButton class="" @click="printOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
          ><ElButton class="" :disabled="busy || !ids.length" @click="generate" type="primary">{{
            busy ? tr('ui.equipment.text0684') : tr('ui.equipment.text0705')
          }}</ElButton>
        </footer>
      </section>
    </div>
    <div v-if="pdfUrl" class="print-modal-mask">
      <section class="pdf-dialog">
        <header>
          <strong>{{ pdfTitle }}</strong
          ><a :href="pdfUrl" download="设备标签.pdf">{{ tr('ui.equipment.text0706') }}</a
          ><ElButton class="" @click="printPdf" type="primary">{{ tr('ui.equipment.text0549') }}</ElButton
          ><ElButton class="pdf-close" @click="closePdf" :aria-label="tr('ui.equipment.text0076')" :title="tr('ui.equipment.text0076')" text
            ><AppIcon name="Close"
          /></ElButton>
        </header>
        <iframe id="print-pdf-frame" :src="pdfUrl" :title="tr('ui.equipment.text0707')"></iframe>
        <footer>{{ tr('ui.equipment.text0708') }}</footer>
      </section>
    </div>
  </section>
</template>
<style scoped>
.template-gallery {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px;
  /* background: #e9eef5; */
  border-radius: 8px;
}
.template-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  align-content: start;
}
.template-card {
  min-width: 0;
  background: #fff;
  border: 1px solid #d5deea;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgb(36 57 87 / 7%);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.template-card:hover,
.template-card:focus-within {
  border-color: var(--brand);
  box-shadow: 0 6px 18px rgb(36 57 87 / 12%);
}
.template-card-open.el-button {
  display: block;
  width: 100%;
  height: auto;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  text-align: left;
  white-space: normal;
  color: var(--text);
  background: transparent;
}
.template-card-open :deep(> span) {
  display: block;
  width: 100%;
  min-width: 0;
}
.template-preview {
  height: 208px;
  padding: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #fafcff, #f0f5fc);
  border-bottom: 1px solid var(--line);
}
.template-card-info {
  display: grid;
  gap: 8px;
  padding: 16px;
  color: var(--text);
}
.template-card-info strong {
  display: block;
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.template-dimensions {
  font-size: 13px;
  color: var(--text);
}
.template-remark {
  margin: 0;
  line-height: 20px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.template-card-actions,
.template-card-secondary-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
.template-card-actions {
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid var(--line);
}
.template-card-actions .el-button + .el-button {
  margin-left: 0;
}
.gallery-empty {
  text-align: center;
  color: var(--text);
  padding: 70px 0;
}
.list-reset {
  border: 0;
  background: none;
  color: #758196;
  font: inherit;
  cursor: pointer;
}
.template-pagination {
  background: #fff;
  height: 40px;
  flex-shrink: 0;
  border-top: 1px solid #e2e7ee;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 14px;
  color: var(--text);
}
.print-template-page.is-focused {
  position: fixed;
  inset: 54px 0 0;
  height: auto;
  z-index: 30;
}
.print-template-page {
  height: calc(100vh - 54px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: var(--text);
  font-size: 13px;
  background: var(--canvas);
  /* padding: 0px 8px; */
}
.print-page-head {
  height: 46px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 14px;
  border-bottom: 1px solid #e2e8ef;
}
.print-page-head > div {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.print-page-head .btn {
  height: 30px;
  padding: 0 14px;
}
.unsaved {
  color: #b78942;
  font-size: 11px;
}
.print-three-columns {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.template-center {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.template-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 20px;
  padding: 12px 16px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  font-size: 13px;
  color: var(--text);
}
.template-meta label {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  white-space: nowrap;
}
.template-meta label:first-child {
  flex: 0 1 280px;
}
.template-meta .remark {
  flex: 1 1 220px;
}
.template-meta .el-input {
  flex: 1;
  min-width: 0;
}
.template-dimensions-summary {
  white-space: nowrap;
}
.print-page-head {
  background: var(--surface);
  min-height: 52px;
}
.print-page-head :deep(.el-button + .el-button) {
  margin-left: 0;
}
.template-list-toolbar form {
  display: flex;
  gap: 12px;
  align-items: center;
}
.paper-settings-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}
.paper-settings-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.paper-dimensions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.legacy-template {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8d9caf;
  background: #f3f5f8;
  gap: 12px;
}
.legacy-template h3 {
  font-size: 16px;
  font-weight: 500;
  color: #54647c;
}
.legacy-template p {
  max-width: 450px;
  line-height: 1.8;
  text-align: center;
}

.print-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 180;
  background: #18273c77;
  display: grid;
  place-items: center;
}
.print-picker {
  width: min(800px, 90vw);
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 7px;
  padding: 18px 22px;
}
.print-picker header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.print-picker header button,
.pdf-close {
  border: 0;
  background: none;
  color: #95a1b2;
  font-size: 24px;
  cursor: pointer;
}
.print-search {
  display: flex;
  gap: 10px;
  margin: 16px 0 12px;
}
.print-picker-scroll {
  overflow: auto;
}
.print-picker table {
  width: 100%;
  border-collapse: collapse;
}
.print-picker-pages {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid #e8edf3;
}
.print-picker-pages > span:first-child {
  margin-right: auto;
  color: #8c99ac;
}
.print-picker-pages button {
  border: 1px solid #e1e7ef;
  background: white;
  color: #748398;
  border-radius: 4px;
  width: 27px;
  height: 27px;
  font-size: 17px;
  cursor: pointer;
}
.print-picker-pages button:disabled {
  opacity: 0.4;
}
.print-picker footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 16px;
}
.print-picker footer label {
  margin-right: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.pdf-dialog {
  width: 90vw;
  height: 90vh;
  display: flex;
  flex-direction: column;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}
.pdf-dialog header {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 14px;
  flex-shrink: 0;
}
.pdf-dialog header strong {
  margin-right: auto;
}
.pdf-dialog header a {
  color: var(--brand);
  text-decoration: none;
}
.pdf-dialog iframe {
  flex: 1;
  border: 0;
  width: 100%;
  background: #56616e;
}
.pdf-dialog footer {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #93a0b2;
  font-size: 11px;
}
@media (max-width: 1100px) {
  .template-meta {
    gap: 8px;
  }
  .template-meta > span {
    display: none;
  }
}
</style>
