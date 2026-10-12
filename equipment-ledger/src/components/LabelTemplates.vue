<script setup lang="ts">
import AppBackButton from './common/AppBackButton.vue';
import AppPagination from './common/AppPagination.vue';
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
  busy = ref(false),
  designKey = ref(0);
const page = ref(1),
  pageSize = ref(24);
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
  if (!(await confirmAction(`确认删除模板“${t.name}”？`))) return;
  try {
    await api.deleteLabelTemplate(t.id);
    await load();
    message('模板已删除');
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
function message(e: unknown) {
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
    message('请输入模板名称');
    return;
  }
  busy.value = true;
  try {
    editor.value = (await api.saveLabelTemplate(editor.value)).data;
    formGuard.markSaved();
    await load();
    message('模板已保存');
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
  if (!editor.value?.id || !(await confirmAction(`确认删除模板“${editor.value.name}”？`))) return;
  try {
    await api.deleteLabelTemplate(editor.value.id);
    editor.value = null;
    await load();
    message('模板已删除');
  } catch (e) {
    message(e);
  }
}
function convert() {
  if (!editor.value || !editable.value) return;
  try {
    editor.value.layout = legacyToGrid(editor.value);
    designKey.value++;
    message('已转换为单元格布局。保存前原模板保持不变。');
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
function setDimension(axis: 'widthMm' | 'heightMm', event: Event) {
  if (!editor.value || !editable.value) return;
  const input = event.target as HTMLInputElement,
    value = Number(input.value);
  if (!Number.isFinite(value) || value < 10 || value > 600) {
    input.value = String(editor.value[axis]);
    message('尺寸范围为10—600 mm');
    return;
  }
  const g = editor.value.layout;
  if (axis === 'widthMm' && g) {
    const oldAvailable = editor.value.widthMm - g.margin.left - g.margin.right,
      newAvailable = value - g.margin.left - g.margin.right;
    if (newAvailable <= 0) {
      input.value = String(editor.value.widthMm);
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
  equipmentTotal = ref(0),
  ids = ref<string[]>([]),
  copies = ref(1);
async function openPrint(t: api.LabelTemplate | null = editor.value) {
  if (!t?.id) {
    message('请先保存模板');
    return;
  }
  if (t === editor.value && dirty.value) {
    message('请先保存修改后再打印');
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
      size: 10,
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
</script>
<template>
  <section class="print-template-page">
    <template v-if="!editor"
      ><div class="template-list-toolbar">
        <button v-if="can('新增')" class="btn primary" @click="create">＋ 新增</button>
        <form @submit.prevent="page = 1">
          <input v-model="keyword" placeholder="搜索模板名称、备注" /><button class="btn primary">
            查询</button
          ><button type="button" class="list-reset" @click="reset">重置</button>
        </form>
      </div>
      <div class="template-gallery">
        <div class="template-cards">
          <article v-for="t in pageRows" :key="t.id" class="template-card">
            <button class="template-card-open" :title="t.remark || t.name" @click="open(t)">
              <div class="template-preview"><TemplateThumbnail :template="t" /></div>
              <div class="template-card-info">
                <strong>{{ t.name }}</strong
                ><span>{{ t.widthMm }} × {{ t.heightMm }} mm</span>
              </div>
            </button>
            <div class="template-card-actions">
              <button v-if="can('打印')" class="card-print" @click="openPrint(t)">打印</button>
              <details v-if="can('新增') || can('删除')" class="card-more">
                <summary title="更多操作" aria-label="更多操作">•••</summary>
                <div>
                  <button v-if="can('新增')" @click="copyRow(t)">复制模板</button
                  ><button v-if="can('删除')" class="danger-text" @click="removeRow(t)">
                    删除模板
                  </button>
                </div>
              </details>
            </div>
          </article>
        </div>
        <div v-if="!pageRows.length" class="gallery-empty">
          {{ keyword ? '没有匹配的模板' : '暂无模板，点击新增开始设计' }}
        </div>
      </div>
      <footer class="template-pagination">
        <span>共 {{ list.length }} 个模板</span
        ><AppPagination
          v-model:page="page"
          v-model:page-size="pageSize"
          :total="list.length"
          :show-sizes="false"
        /></footer
    ></template>
    <template v-else
      ><header class="print-page-head">
        <AppBackButton label="返回模板列表" :disabled="busy" @click="back" /><strong>{{
          editor.id ? '编辑模板' : '新增模板'
        }}</strong
        ><span v-if="dirty" class="unsaved">未保存</span>
        <div>
          <button v-if="can('新增')" class="btn secondary" :disabled="busy" @click="copy">
            复制</button
          ><button class="btn secondary" :disabled="busy || pdfBusy" @click="preview">
            {{ pdfBusy ? '生成中…' : '预览' }}</button
          ><button
            v-if="can('打印')"
            class="btn secondary"
            :disabled="busy || !editor.id"
            @click="openPrint()"
          >
            打印</button
          ><button v-if="editable" class="btn primary" :disabled="busy" @click="save">
            {{ busy ? '保存中…' : '保存' }}
          </button>
        </div>
      </header>
      <div class="print-three-columns" :inert="busy || undefined">
        <div class="template-center" v-if="editor">
          <div class="template-meta">
            <label
              >名称<input
                v-model="editor.name"
                :disabled="!editable"
                maxlength="100"
                placeholder="请输入模板名称"
            /></label>
            <div class="template-size">
              <label
                >长度<input
                  :value="editor.widthMm"
                  :disabled="!editable"
                  type="number"
                  min="10"
                  max="600"
                  step=".1"
                  @change="setDimension('widthMm', $event)" /></label
              ><span>×</span
              ><label
                >宽度<input
                  :value="editor.heightMm"
                  :disabled="!editable"
                  type="number"
                  min="10"
                  max="600"
                  step=".1"
                  @change="setDimension('heightMm', $event)" /></label
              ><span>mm</span>
            </div>
            <label class="remark"
              >备注<input
                v-model="editor.remark"
                :disabled="!editable"
                maxlength="500"
                placeholder="模板用途"
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
            ><template #paper
              ><label
                >纸张<select
                  :disabled="!editable"
                  @change="
                    paper(($event.target as HTMLSelectElement).value);
                    ($event.target as HTMLSelectElement).value = '';
                  "
                >
                  <option value="">自定义 {{ editor.widthMm }} × {{ editor.heightMm }}</option>
                  <option value="label">标签 80 × 50</option>
                  <option value="A5">A5 148 × 210</option>
                  <option value="A4">A4 210 × 297</option>
                  <option value="A3">A3 297 × 420</option>
                </select></label
              >
              <div class="paper-dimensions">
                <label
                  >长度（mm）<input
                    :value="editor.widthMm"
                    @change="setDimension('widthMm', $event)"
                    :disabled="!editable"
                    type="number"
                    min="10"
                    max="600"
                    step=".1" /></label
                ><label
                  >宽度（mm）<input
                    :value="editor.heightMm"
                    @change="setDimension('heightMm', $event)"
                    :disabled="!editable"
                    type="number"
                    min="10"
                    max="600"
                    step=".1"
                /></label>
              </div>
              <button :disabled="!editable" @click="rotate">切换横纵方向</button></template
            ></PrintGridDesigner
          >
          <div v-else class="legacy-template">
            <h3>原自由布局模板</h3>
            <p>原模板可继续预览和打印。转换后可使用单元格排版，保存前不改变已有模板。</p>
            <button v-if="editable" class="btn primary" @click="convert">转换为单元格模板</button
            ><button v-if="can('新增')" class="btn secondary" @click="create">新建表格模板</button>
          </div>
        </div>
      </div></template
    >
    <div v-if="notice" class="print-notice" @click="notice = ''">{{ notice }} <span>×</span></div>
    <div v-if="printOpen" class="print-modal-mask">
      <section class="print-picker">
        <header><strong>选择打印设备</strong><button @click="printOpen = false">×</button></header>
        <div class="print-search">
          <input
            v-model="equipmentKeyword"
            placeholder="设备名称、编码、型号"
            @keyup.enter="
              equipmentPage = 1;
              searchEquipment();
            "
          /><button
            class="btn primary"
            @click="
              equipmentPage = 1;
              searchEquipment();
            "
          >
            查询
          </button>
        </div>
        <div class="print-picker-scroll">
          <table class="business-table">
            <thead>
              <tr>
                <th></th>
                <th>设备编码</th>
                <th>设备名称</th>
                <th>所属部门</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="e in equipmentRows">
                <td><input v-model="ids" type="checkbox" :value="e.id" /></td>
                <td>{{ e.code }}</td>
                <td>{{ e.name }}</td>
                <td>{{ e.department }}</td>
              </tr>
              <tr v-if="!equipmentRows.length">
                <td colspan="4">暂无设备</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="print-picker-pages">
          <span>已选 {{ ids.length }} 台 · 共 {{ equipmentTotal }} 台</span
          ><button
            :disabled="equipmentPage === 1"
            @click="
              equipmentPage--;
              searchEquipment();
            "
          >
            ‹</button
          ><span>{{ equipmentPage }}</span
          ><button
            :disabled="equipmentPage * 10 >= equipmentTotal"
            @click="
              equipmentPage++;
              searchEquipment();
            "
          >
            ›
          </button>
        </div>
        <footer>
          <label>每台份数<input v-model.number="copies" type="number" min="1" max="20" /></label
          ><button class="btn secondary" @click="printOpen = false">取消</button
          ><button class="btn primary" :disabled="busy || !ids.length" @click="generate">
            {{ busy ? '生成中…' : '生成打印文件' }}
          </button>
        </footer>
      </section>
    </div>
    <div v-if="pdfUrl" class="print-modal-mask">
      <section class="pdf-dialog">
        <header>
          <strong>{{ pdfTitle }}</strong
          ><a :href="pdfUrl" download="设备标签.pdf">下载PDF</a
          ><button class="btn primary" @click="printPdf">打印</button
          ><button class="pdf-close" @click="closePdf">×</button>
        </header>
        <iframe id="print-pdf-frame" :src="pdfUrl" title="打印PDF预览"></iframe>
        <footer>打印缩放设为100%，使用匹配的纸张尺寸。</footer>
      </section>
    </div>
  </section>
</template>
<style scoped>
.template-gallery {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 4px 14px 20px;
  background: #f4f6fa;
}
.template-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(245px, 1fr));
  gap: 16px;
  align-content: start;
}
.template-card {
  position: relative;
  min-width: 0;
  background: #fff;
  border: 1px solid #e1e7f0;
  border-radius: 7px;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.template-card:hover,
.template-card:focus-within {
  border-color: var(--brand);
  box-shadow: 0 4px 16px #2538560d;
}
.template-card-open {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  font: inherit;
  color: inherit;
  cursor: pointer;
  border-radius: 7px;
  overflow: hidden;
}
.template-preview {
  height: 170px;
  background: #edf1f6;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  box-sizing: border-box;
}
.template-card-info {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.template-card-info strong {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 90px;
}
.template-card-info > span {
  font-size: 12px;
  color: #8c99ac;
}
.template-card-actions {
  position: absolute;
  right: 12px;
  bottom: 37px;
  display: flex;
  gap: 6px;
  align-items: center;
  opacity: 0;
  transition: opacity 0.15s;
}
.template-card:hover .template-card-actions,
.template-card:focus-within .template-card-actions,
.template-card:has(details[open]) .template-card-actions {
  opacity: 1;
}
.card-print,
.card-more summary {
  height: 27px;
  min-width: 27px;
  border: 1px solid #dfe6ef;
  border-radius: 4px;
  background: #fff;
  color: var(--brand);
  font: inherit;
  cursor: pointer;
  padding: 0 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.card-more {
  position: relative;
}
.card-more summary {
  list-style: none;
  color: #7a899f;
}
.card-more summary::-webkit-details-marker {
  display: none;
}
.card-more > div {
  position: absolute;
  right: 0;
  top: 32px;
  z-index: 10;
  width: 115px;
  padding: 5px;
  background: #fff;
  border: 1px solid #e0e6ef;
  border-radius: 5px;
  box-shadow: 0 6px 20px #17263b20;
}
.card-more button {
  width: 100%;
  border: 0;
  background: none;
  color: #52637b;
  font: inherit;
  text-align: left;
  padding: 9px;
  cursor: pointer;
  border-radius: 3px;
}
.card-more button:hover {
  background: var(--brand-soft);
}
.card-more button.danger-text {
  color: #cb5963;
}
.gallery-empty {
  text-align: center;
  color: #93a1b5;
  padding: 70px 0;
}
@media (hover: none) {
  .template-card-actions {
    opacity: 1;
  }
}

.template-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  gap: 12px;
  flex-shrink: 0;
  background: #f4f6fa;
}
.template-list-toolbar form {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.template-list-toolbar input,
.template-filter-row input {
  height: 34px;
  border: 1px solid #dfe5ed;
  border-radius: 4px;
  padding: 0 10px;
  font: inherit;
}
.template-list-toolbar form > input {
  width: 270px;
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
  color: #78859a;
}
.template-pagination > span:first-child {
  margin-right: auto;
}
.template-pagination button,
.template-pagination select {
  height: 27px;
  border: 1px solid #e0e5ed;
  border-radius: 4px;
  background: #fff;
  color: #66758c;
  font: inherit;
}
.template-pagination button:disabled {
  opacity: 0.4;
}

.print-template-page {
  height: calc(100vh - 54px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  color: #3e4c60;
  font-size: 13px;
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
.template-sidebar {
  width: 200px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e3e9f0;
  background: #f9fafc;
}
.template-list-tools {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
}
.template-list-tools button {
  border: 0;
  background: var(--brand-soft);
  color: var(--brand);
  width: 27px;
  height: 27px;
  border-radius: 4px;
  font-size: 20px;
  cursor: pointer;
}
.template-search {
  margin: 0 12px 12px;
  height: 32px;
  width: calc(100% - 24px);
  box-sizing: border-box;
  border: 1px solid #dfe5ed;
  border-radius: 4px;
  background: white;
  padding: 0 10px;
  font: inherit;
}
.template-list {
  flex: 1;
  overflow: auto;
  padding: 0 7px;
}
.template-list button {
  display: flex;
  flex-direction: column;
  gap: 7px;
  text-align: left;
  width: 100%;
  border: 1px solid transparent;
  background: none;
  padding: 12px 10px;
  border-radius: 5px;
  margin-bottom: 4px;
  color: #52627b;
  font: inherit;
  cursor: pointer;
}
.template-list button b {
  font-weight: 500;
  overflow-wrap: anywhere;
}
.template-list button small {
  font-size: 11px;
  color: #94a0b2;
}
.template-list button.selected {
  background: var(--brand-soft);
  color: var(--brand);
  border-color: #e0e7ff;
}
.template-list button:hover {
  background: #eef3fa;
}
.template-list p {
  padding: 14px;
  color: #9da8b8;
}
.template-list-bottom {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  border-top: 1px solid #e2e8ef;
}
.template-list-bottom button {
  border: 0;
  background: none;
  color: #6c7b90;
  font: inherit;
  cursor: pointer;
}
.template-list-bottom button:disabled {
  opacity: 0.4;
}
.template-list-bottom .danger-text {
  color: #cd5661;
}
.template-center {
  flex: 1;
  min-width: 850px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.template-meta {
  height: 46px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 14px;
  border-bottom: 1px solid #e3e9f0;
  font-size: 12px;
  color: #8491a3;
}
.template-meta label {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
.template-meta input {
  border: 1px solid #dfe5ec;
  border-radius: 4px;
  height: 30px;
  width: 170px;
  background: #fff;
  font: inherit;
  padding: 0 9px;
  color: #46566e;
}
.template-size {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-shrink: 0;
}
.template-meta .template-size input {
  width: 65px;
  padding: 0 5px;
}
.template-meta .remark {
  flex: 1;
}
.template-meta .remark input {
  width: 100%;
  min-width: 80px;
}
.paper-dimensions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.no-template,
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
.print-notice {
  position: fixed;
  left: 50%;
  bottom: 35px;
  transform: translateX(-50%);
  z-index: 300;
  padding: 12px 18px;
  background: #263952;
  color: #fff;
  border-radius: 5px;
  box-shadow: 0 5px 22px #26395233;
  cursor: pointer;
  max-width: 75vw;
}
.print-notice span {
  margin-left: 15px;
  opacity: 0.7;
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
.print-search > input {
  flex: 1;
  min-width: 0;
  height: 34px;
  border: 1px solid #dce4ee;
  border-radius: 4px;
  padding: 0 10px;
  font: inherit;
}
.print-picker-scroll {
  overflow: auto;
}
.print-picker table {
  width: 100%;
  border-collapse: collapse;
}
.print-picker th {
  text-align: left;
  background: #f6f8fb;
  color: #7d8ba0;
  font-weight: 500;
}
.print-picker th,
.print-picker td {
  padding: 10px 12px;
  border-bottom: 1px solid #e9edf3;
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
.print-picker footer input {
  width: 60px;
  height: 30px;
  border: 1px solid #dce4ed;
  border-radius: 4px;
  padding: 0 8px;
  font: inherit;
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
.print-template-page input[type='checkbox'] {
  accent-color: var(--brand);
}
.print-template-page input:focus {
  outline: 2px solid var(--brand-soft);
  border-color: var(--brand);
}
@media (max-width: 1100px) {
  .template-sidebar {
    width: 170px;
  }
  .template-meta {
    gap: 8px;
  }
  .template-meta input {
    width: 130px;
  }
  .template-meta > span {
    display: none;
  }
}
</style>
