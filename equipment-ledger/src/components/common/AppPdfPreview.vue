<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { ElAlert, ElButton, ElSelect, ElOption } from 'element-plus';
import {
  getDocument,
  GlobalWorkerOptions,
  type PDFDocumentProxy,
  type PDFDocumentLoadingTask,
  type RenderTask,
} from 'pdfjs-dist';
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
GlobalWorkerOptions.workerSrc = workerUrl;
const props = defineProps<{ src: string }>();
const area = ref<HTMLDivElement>(),
  mode = ref('width'),
  areaWidth = ref(400),
  areaHeight = ref(400),
  ratio = ref(210 / 297);
const fitWidth = computed(() =>
  Math.max(80, Math.min(areaWidth.value - 24, (areaHeight.value - 24) * ratio.value)),
);
const sizeObserver = new ResizeObserver((entries) => {
  const box = entries[0]?.contentRect;
  if (box) {
    areaWidth.value = box.width;
    areaHeight.value = box.height;
  }
});
onMounted(() => {
  if (area.value) sizeObserver.observe(area.value);
});
const canvas = ref<HTMLCanvasElement>(),
  page = ref(1),
  count = ref(0),
  busy = ref(false),
  error = ref('');
let loading: PDFDocumentLoadingTask | undefined,
  document: PDFDocumentProxy | undefined,
  render: RenderTask | undefined;
let version = 0,
  ready: Promise<void> = Promise.resolve();
async function paint() {
  const revision = version,
    doc = document;
  if (!doc || !canvas.value) return;
  render?.cancel();
  error.value = '';
  busy.value = true;
  try {
    const p = await doc.getPage(page.value);
    if (revision !== version || !canvas.value) return;
    const viewport = p.getViewport({ scale: 1.5 });
    ratio.value = viewport.width / viewport.height;
    const target = canvas.value;
    target.width = viewport.width;
    target.height = viewport.height;
    render = p.render({ canvas: target, viewport });
    await render.promise;
  } catch (e) {
    if (revision === version && (e as Error).name !== 'RenderingCancelledException')
      error.value = 'PDF页面显示失败，请重新预览';
  } finally {
    if (revision === version) busy.value = false;
  }
}
async function load() {
  const revision = ++version;
  render?.cancel();
  void loading?.destroy();
  document = undefined;
  count.value = 0;
  page.value = 1;
  error.value = '';
  if (!props.src) return;
  busy.value = true;
  try {
    loading = getDocument({ url: props.src, isEvalSupported: false });
    const doc = await loading.promise;
    if (revision !== version) {
      void doc.destroy();
      return;
    }
    document = doc;
    count.value = doc.numPages;
    await nextTick();
    await paint();
  } catch (e) {
    if (revision === version) error.value = 'PDF加载失败，请重新预览或下载原文件';
  } finally {
    if (revision === version) busy.value = false;
  }
}
watch(
  () => props.src,
  () => {
    ready = load();
  },
  { immediate: true },
);
watch(page, () => {
  void paint();
});
onBeforeUnmount(() => {
  sizeObserver.disconnect();
  version++;
  render?.cancel();
  void loading?.destroy();
});
async function print() {
  await ready;
  const doc = document;
  if (!doc) throw new Error('PDF尚未加载，请稍后再试');
  const pages: string[] = [];
  for (let n = 1; n <= doc.numPages; n++) {
    const p = await doc.getPage(n),
      view = p.getViewport({ scale: 1.5 }),
      surface = window.document.createElement('canvas');
    surface.width = view.width;
    surface.height = view.height;
    await p.render({ canvas: surface, viewport: view }).promise;
    pages.push(surface.toDataURL('image/png'));
    surface.width = surface.height = 0;
  }
  const first = await doc.getPage(1),
    paper = first.getViewport({ scale: 1 });
  const width = (paper.width / 72) * 25.4,
    height = (paper.height / 72) * 25.4;
  const frame = window.document.createElement('iframe');
  frame.title = '打印出库单';
  frame.style.cssText = 'position:fixed;left:-10000px;width:1px;height:1px;border:0';
  frame.srcdoc = `<!doctype html><html><head><meta charset="utf-8"><title>出库单</title><style>@page{size:${width}mm ${height}mm;margin:0}*{box-sizing:border-box}html,body{margin:0;padding:0}.sheet{display:block;width:${width}mm;height:${height}mm;break-after:page}.sheet:last-child{break-after:auto}img{width:100%;height:100%;display:block}</style></head><body>${pages.map((src) => `<div class="sheet"><img src="${src}"></div>`).join('')}</body></html>`;
  await new Promise<void>((resolve, reject) => {
    frame.onload = () => resolve();
    frame.onerror = () => reject(new Error('打印窗口加载失败'));
    window.document.body.appendChild(frame);
  });
  const target = frame.contentWindow;
  if (!target) {
    frame.remove();
    throw new Error('无法打开打印窗口，请下载PDF打印');
  }
  const cleanup = () => frame.remove();
  target.addEventListener('afterprint', cleanup, { once: true });
  try {
    await Promise.all(
      Array.from(frame.contentDocument?.images || []).map((image) => image.decode()),
    );
    target.focus();
    target.print();
  } catch (e) {
    cleanup();
    throw e;
  }
  // Browsers that omit afterprint must not retain hidden print frames indefinitely.
  window.setTimeout(cleanup, 300000);
}
defineExpose({ print });
</script>
<template>
  <div class="app-pdf-preview">
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <div v-if="count" class="pdf-navigation">
      <ElSelect v-model="mode" size="small" aria-label="预览缩放" style="width: 100px"
        ><ElOption label="整页显示" value="page" /><ElOption label="适合页宽" value="width"
      /></ElSelect>
      <ElButton :disabled="busy || page <= 1" size="small" @click="page--">上一页</ElButton
      ><span>{{ page }} / {{ count }}</span
      ><ElButton :disabled="busy || page >= count" size="small" @click="page++">下一页</ElButton>
    </div>
    <div ref="area" class="pdf-canvas-area">
      <p v-if="busy && !count">正在加载PDF…</p>
      <canvas
        v-show="count"
        ref="canvas"
        :style="{ width: mode === 'page' ? fitWidth + 'px' : '100%' }"
        :aria-label="`PDF第${page}页`"
      />
    </div>
  </div>
</template>
<style scoped>
.app-pdf-preview {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  background: #e9edf3;
}
.pdf-navigation {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 8px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color);
  font-size: 13px;
  flex: none;
}
.pdf-canvas-area {
  overflow: auto;
  min-height: 0;
  flex: 1;
  padding: 12px;
  text-align: center;
}
canvas {
  display: block;
  width: 100%;
  max-width: 680px;
  height: auto;
  margin: 0 auto;
  box-shadow: 0 2px 9px #1b2d461f;
  background: white;
}
</style>
