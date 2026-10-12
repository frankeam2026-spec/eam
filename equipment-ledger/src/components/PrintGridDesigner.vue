<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue';
import {
  emptyCell,
  getCell,
  ensureCell,
  columnName,
  cellKey,
  fields,
  resolve,
  type GridLayout,
  type GridCell,
} from '../printing/grid';
const grid = defineModel<GridLayout>({ required: true });
const props = defineProps<{ width: number; height: number; editable: boolean }>();
const emit = defineEmits<{ message: [value: string] }>();
const zoom = ref(1),
  selection = reactiveSelection(),
  selectedField = ref('code'),
  paperSettings = ref(false),
  sampleMode = ref(false);
function reactiveSelection() {
  return ref({ r0: 0, c0: 0, r1: 0, c1: 0 });
}
const rect = computed(() => ({
  r0: Math.min(selection.value.r0, selection.value.r1),
  c0: Math.min(selection.value.c0, selection.value.c1),
  r1: Math.max(selection.value.r0, selection.value.r1),
  c1: Math.max(selection.value.c0, selection.value.c1),
}));
const active = computed(
  () =>
    getCell(grid.value, rect.value.r0, rect.value.c0) || emptyCell(rect.value.r0, rect.value.c0),
);
const scale = computed(() => 3.7795275591 * zoom.value);
const name = computed(
  () =>
    columnName(rect.value.c0) +
    (rect.value.r0 + 1) +
    (rect.value.r1 !== rect.value.r0 || rect.value.c1 !== rect.value.c0
      ? ':' + columnName(rect.value.c1) + (rect.value.r1 + 1)
      : ''),
);
const undo = ref<string[]>([]),
  redo = ref<string[]>([]);
const snapshot = () => JSON.stringify(grid.value);
function remember() {
  undo.value.push(snapshot());
  if (undo.value.length > 60) undo.value.shift();
  redo.value = [];
}
function change(action: () => void) {
  if (!props.editable) return;
  remember();
  action();
}
function doUndo() {
  restore(undo, redo);
}
function doRedo() {
  restore(redo, undo);
}
function restore(from: typeof undo, to: typeof redo) {
  const state = from.value.pop();
  if (!state) return;
  to.value.push(snapshot());
  grid.value = JSON.parse(state);
  selection.value = { r0: 0, c0: 0, r1: 0, c1: 0 };
}
let focusState = '';
function beforeInput() {
  focusState = snapshot();
}
function afterInput() {
  if (focusState && focusState !== snapshot()) {
    undo.value.push(focusState);
    redo.value = [];
  }
  focusState = '';
}
function contentChange(event: Event) {
  change(
    () =>
      (ensureCell(grid.value, rect.value.r0, rect.value.c0).content = (
        event.target as HTMLTextAreaElement
      ).value),
  );
}
function setStyle(key: keyof GridCell, value: unknown) {
  change(() => {
    eachSelected((r, c) => ((ensureCell(grid.value, r, c) as any)[key] = value));
  });
}
type BorderSide = 'top' | 'right' | 'bottom' | 'left';
const borderSides: BorderSide[] = ['top', 'right', 'bottom', 'left'];
function hasBorder(c: GridCell, side: BorderSide) {
  return c.borderSides ? c.borderSides[side] : c.border;
}
function borders(mode: 'all' | 'outer' | 'none' | BorderSide) {
  expand();
  const enabled = borderSides.includes(mode as BorderSide)
    ? !hasBorder(active.value, mode as BorderSide)
    : false;
  change(() => {
    eachSelected((r, c) => {
      const cell = ensureCell(grid.value, r, c);
      const sides = {
        top: hasBorder(cell, 'top'),
        right: hasBorder(cell, 'right'),
        bottom: hasBorder(cell, 'bottom'),
        left: hasBorder(cell, 'left'),
      };
      if (mode === 'all' || mode === 'none')
        borderSides.forEach((side) => (sides[side] = mode === 'all'));
      else if (mode === 'outer') {
        sides.top = cell.row === rect.value.r0;
        sides.bottom = cell.row + cell.rowSpan - 1 === rect.value.r1;
        sides.left = cell.col === rect.value.c0;
        sides.right = cell.col + cell.colSpan - 1 === rect.value.c1;
      } else sides[mode] = enabled;
      cell.borderSides = sides;
      cell.border = false;
    });
  });
}
function borderLine(c: GridCell, side: BorderSide) {
  return hasBorder(c, side)
    ? `${(((c.borderWidth ?? 0.5) * 25.4) / 72) * scale.value}px solid ${c.borderColor || '#000000'}`
    : '1px solid #e7ebf0';
}
function eachSelected(fn: (r: number, c: number) => void) {
  const seen = new Set<string>();
  for (let r = rect.value.r0; r <= rect.value.r1; r++)
    for (let c = rect.value.c0; c <= rect.value.c1; c++) {
      const cell = getCell(grid.value, r, c),
        key = cell ? cellKey(cell.row, cell.col) : cellKey(r, c);
      if (seen.has(key)) continue;
      seen.add(key);
      fn(cell?.row ?? r, cell?.col ?? c);
    }
}
let selecting = false;
function select(event: PointerEvent, r: number, c: number) {
  if (event.button !== 0) return;
  event.preventDefault();
  (event.currentTarget as HTMLElement).closest<HTMLElement>('.print-grid-shell')?.focus();
  paperSettings.value = false;
  if (event.shiftKey) {
    selection.value.r1 = r;
    selection.value.c1 = c;
  } else selection.value = { r0: r, c0: c, r1: r, c1: c };
  selecting = true;
}
function extend(r: number, c: number) {
  if (selecting) {
    selection.value.r1 = r;
    selection.value.c1 = c;
  }
}
function expand() {
  let changed = true;
  while (changed) {
    changed = false;
    for (const cell of grid.value.cells) {
      const a = rect.value;
      if (
        cell.row <= a.r1 &&
        cell.row + cell.rowSpan - 1 >= a.r0 &&
        cell.col <= a.c1 &&
        cell.col + cell.colSpan - 1 >= a.c0
      ) {
        const next = {
          r0: Math.min(a.r0, cell.row),
          c0: Math.min(a.c0, cell.col),
          r1: Math.max(a.r1, cell.row + cell.rowSpan - 1),
          c1: Math.max(a.c1, cell.col + cell.colSpan - 1),
        };
        if (JSON.stringify(a) !== JSON.stringify(next)) {
          selection.value = next;
          changed = true;
        }
      }
    }
  }
}
const isSelected = (r: number, c: number) =>
  r >= rect.value.r0 && r <= rect.value.r1 && c >= rect.value.c0 && c <= rect.value.c1;
function visible(r: number, c: number) {
  const cell = getCell(grid.value, r, c);
  return !cell || (cell.row === r && cell.col === c);
}
function merge() {
  expand();
  if (rect.value.r0 === rect.value.r1 && rect.value.c0 === rect.value.c1) return;
  change(() => {
    const a = rect.value,
      existing = grid.value.cells.filter(
        (c) => c.row >= a.r0 && c.row <= a.r1 && c.col >= a.c0 && c.col <= a.c1,
      ),
      base = {
        ...(getCell(grid.value, a.r0, a.c0) || emptyCell(a.r0, a.c0)),
        row: a.r0,
        col: a.c0,
        rowSpan: a.r1 - a.r0 + 1,
        colSpan: a.c1 - a.c0 + 1,
        content: existing
          .map((c) => c.content)
          .filter(Boolean)
          .join('\n'),
      };
    grid.value.cells = grid.value.cells.filter((c) => !existing.includes(c));
    grid.value.cells.push(base);
  });
}
function split() {
  change(() => {
    const cells: GridCell[] = [];
    eachSelected((r, c) => {
      const e = getCell(grid.value, r, c);
      if (e && (e.rowSpan > 1 || e.colSpan > 1)) {
        for (let rr = r; rr < r + e.rowSpan; rr++)
          for (let cc = c; cc < c + e.colSpan; cc++)
            cells.push({
              ...e,
              row: rr,
              col: cc,
              rowSpan: 1,
              colSpan: 1,
              content: rr === r && cc === c ? e.content : '',
              image: rr === r && cc === c ? e.image : '',
              kind: rr === r && cc === c ? e.kind : 'text',
            });
        grid.value.cells = grid.value.cells.filter((c) => c !== e);
      }
    });
    grid.value.cells.push(...cells);
  });
}
function insertField(key = selectedField.value) {
  change(() => {
    const c = ensureCell(grid.value, rect.value.r0, rect.value.c0);
    if (key === 'equipment_qrcode' || key === 'equipment_barcode') {
      c.kind = key === 'equipment_qrcode' ? 'qrcode' : 'barcode';
      c.content = '{code}';
      c.image = '';
    } else if (key === 'fixed_image') {
      c.kind = 'image';
      c.content = '';
      c.image = '';
    } else if (key === 'fixed_text') {
      c.kind = 'text';
      c.content = '固定文本';
      c.image = '';
    } else {
      c.kind = 'text';
      c.content += `{${key}}`;
    }
  });
}
function drop(event: DragEvent, r: number, c: number) {
  event.preventDefault();
  selection.value = { r0: r, c0: c, r1: r, c1: c };
  const key = event.dataTransfer?.getData('text/print-field');
  if (key) insertField(key);
}
function modifyAxis(axis: 'row' | 'col', insert: boolean) {
  const index = axis === 'row' ? rect.value.r0 : rect.value.c0,
    list = axis === 'row' ? grid.value.rows : grid.value.columns;
  if (!insert && list.length === 1) return;
  change(() => {
    if (insert) {
      if (list.length >= (axis === 'row' ? 500 : 100)) {
        emit('message', '已达到行列数量上限');
        return;
      }
      list.splice(index, 0, axis === 'row' ? 5 : 10);
      for (const c of grid.value.cells) {
        const start = c[axis],
          span = axis === 'row' ? 'rowSpan' : 'colSpan';
        if (start >= index) c[axis]++;
        else if (start + c[span] > index) c[span]++;
      }
    } else {
      list.splice(index, 1);
      grid.value.cells = grid.value.cells.filter((c) => {
        const start = c[axis],
          span = axis === 'row' ? 'rowSpan' : 'colSpan';
        if (start === index && c[span] === 1) return false;
        if (start > index) c[axis]--;
        else if (start <= index && start + c[span] > index) c[span]--;
        return true;
      });
    }
    grid.value.area = null;
    grid.value.repeat = null;
    grid.value.repeatHeader = Math.min(grid.value.repeatHeader, grid.value.rows.length - 1);
    selection.value = {
      r0: Math.min(rect.value.r0, grid.value.rows.length - 1),
      r1: Math.min(rect.value.r0, grid.value.rows.length - 1),
      c0: Math.min(rect.value.c0, grid.value.columns.length - 1),
      c1: Math.min(rect.value.c0, grid.value.columns.length - 1),
    };
  });
}
let resizing: { axis: 'row' | 'col'; index: number; origin: number; size: number } | null = null;
function resize(event: PointerEvent, axis: 'row' | 'col', index: number) {
  if (!props.editable) return;
  event.preventDefault();
  event.stopPropagation();
  remember();
  resizing = {
    axis,
    index,
    origin: axis === 'row' ? event.clientY : event.clientX,
    size: (axis === 'row' ? grid.value.rows : grid.value.columns)[index]!,
  };
}
function move(event: PointerEvent) {
  if (resizing) {
    const delta =
      ((resizing.axis === 'row' ? event.clientY : event.clientX) - resizing.origin) / scale.value;
    (resizing.axis === 'row' ? grid.value.rows : grid.value.columns)[resizing.index] = Math.max(
      0.1,
      Math.round((resizing.size + delta) * 10) / 10,
    );
  }
}
function end() {
  if (selecting) expand();
  selecting = false;
  resizing = null;
}
let copied: { height: number; width: number; cells: GridCell[] } | null = null;
let copiedText = '';
async function copy() {
  expand();
  const a = rect.value;
  copied = {
    height: a.r1 - a.r0 + 1,
    width: a.c1 - a.c0 + 1,
    cells: grid.value.cells
      .filter((c) => c.row >= a.r0 && c.row <= a.r1 && c.col >= a.c0 && c.col <= a.c1)
      .map((c) => ({ ...c, row: c.row - a.r0, col: c.col - a.c0 })),
  };
  const text = Array.from({ length: copied.height }, (_, r) =>
    Array.from(
      { length: copied!.width },
      (_, c) => getCell(grid.value, a.r0 + r, a.c0 + c)?.content || '',
    ).join('\t'),
  ).join('\n');
  copiedText = text;
  try {
    await navigator.clipboard.writeText(text);
  } catch {}
}
function pasteCells() {
  if (!copied || !props.editable) return;
  const a = rect.value;
  if (
    a.r0 + copied.height > grid.value.rows.length ||
    a.c0 + copied.width > grid.value.columns.length
  ) {
    emit('message', '目标区域空间不足，请先插入行列');
    return;
  }
  const er = a.r0 + copied.height - 1,
    ec = a.c0 + copied.width - 1;
  if (
    grid.value.cells.some(
      (c) =>
        c.row <= er &&
        c.row + c.rowSpan - 1 >= a.r0 &&
        c.col <= ec &&
        c.col + c.colSpan - 1 >= a.c0 &&
        (c.row < a.r0 || c.row + c.rowSpan - 1 > er || c.col < a.c0 || c.col + c.colSpan - 1 > ec),
    )
  ) {
    emit('message', '粘贴区域与已有合并单元格相交，请先拆分');
    return;
  }
  change(() => {
    grid.value.cells = grid.value.cells.filter(
      (c) => !(c.row >= a.r0 && c.row <= er && c.col >= a.c0 && c.col <= ec),
    );
    grid.value.cells.push(
      ...copied!.cells.map((c) => ({ ...c, row: c.row + a.r0, col: c.col + a.c0 })),
    );
  });
}
function paste(event: ClipboardEvent) {
  if ((event.target as HTMLElement).closest('input,textarea,select') || !props.editable) return;
  event.preventDefault();
  const text = event.clipboardData?.getData('text/plain').replace(/\r/g, '');
  if (copied && text === copiedText) {
    pasteCells();
    return;
  }
  const rows = text?.split('\n').map((r) => r.split('\t'));
  if (!rows) return;
  change(() =>
    rows.forEach((r, y) =>
      r.forEach((value, x) => {
        const row = rect.value.r0 + y,
          col = rect.value.c0 + x;
        if (row < grid.value.rows.length && col < grid.value.columns.length)
          ensureCell(grid.value, row, col).content = value;
      }),
    ),
  );
}
function clear() {
  change(() =>
    eachSelected((r, c) => {
      const cell = ensureCell(grid.value, r, c);
      cell.content = '';
      cell.image = '';
      cell.kind = 'text';
    }),
  );
}
function keydown(event: KeyboardEvent) {
  if ((event.target as HTMLElement).closest('input,textarea,select')) return;
  const mod = event.metaKey || event.ctrlKey;
  if (mod && event.key.toLowerCase() === 'c') {
    event.preventDefault();
    copy();
  } else if (mod && event.key.toLowerCase() === 'z') {
    event.preventDefault();
    if (props.editable) restore(event.shiftKey ? redo : undo, event.shiftKey ? undo : redo);
  } else if (mod && event.key.toLowerCase() === 'y') {
    event.preventDefault();
    if (props.editable) restore(redo, undo);
  } else if (event.key === 'Delete' || event.key === 'Backspace') {
    event.preventDefault();
    clear();
  } else if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key)) {
    event.preventDefault();
    let r = rect.value.r0,
      c = rect.value.c0;
    if (event.key === 'ArrowUp') r--;
    if (event.key === 'ArrowDown') r++;
    if (event.key === 'ArrowLeft') c--;
    if (event.key === 'ArrowRight') c++;
    r = Math.max(0, Math.min(grid.value.rows.length - 1, r));
    c = Math.max(0, Math.min(grid.value.columns.length - 1, c));
    selection.value = { r0: r, r1: r, c0: c, c1: c };
    expand();
  }
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement,
    file = input.files?.[0];
  if (!file) return;
  if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 1024 * 1024) {
    emit('message', '请选择1MB以内的PNG或JPEG图片');
    return;
  }
  const reader = new FileReader();
  reader.onload = () =>
    change(() => {
      ensureCell(grid.value, rect.value.r0, rect.value.c0).image = String(reader.result);
    });
  reader.readAsDataURL(file);
  input.value = '';
}
function cellStyle(r: number, c: number) {
  const cell = getCell(grid.value, r, c) || emptyCell(r, c);
  return {
    height: grid.value.rows[r]! * scale.value + 'px',
    fontSize: ((cell.fontSize * 25.4) / 72) * scale.value + 'px',
    fontWeight: cell.bold ? '700' : '400',
    fontStyle: cell.italic ? 'italic' : 'normal',
    textDecoration: cell.underline ? 'underline' : 'none',
    textAlign: cell.align,
    verticalAlign: cell.vertical === 'middle' ? 'middle' : cell.vertical,
    color: cell.color,
    backgroundColor: cell.background,
    borderTop: borderLine(cell, 'top'),
    borderRight: borderLine(cell, 'right'),
    borderBottom: borderLine(cell, 'bottom'),
    borderLeft: borderLine(cell, 'left'),
    whiteSpace: cell.overflow === 'wrap' ? 'pre-wrap' : 'pre',
    overflow: 'hidden',
  };
}

onMounted(() => {
  window.addEventListener('pointermove', move);
  window.addEventListener('pointerup', end);
});
onBeforeUnmount(() => {
  window.removeEventListener('pointermove', move);
  window.removeEventListener('pointerup', end);
});
</script>
<template>
  <div class="print-grid-shell" tabindex="0" @keydown="keydown" @paste="paste">
    <aside class="grid-fields">
      <h3>设备字段</h3>
      <div class="field-list">
        <button
          v-for="f in fields"
          :key="f.key"
          :disabled="!editable"
          draggable="true"
          @click="insertField(f.key)"
          @dragstart="$event.dataTransfer?.setData('text/print-field', f.key)"
        >
          {{ f.label }}
        </button>
      </div>
      <p>选中单元格后点击字段，或拖入画布。</p>
    </aside>
    <div class="print-grid-main">
      <div class="grid-toolbar">
        <button :disabled="!editable || !undo.length" title="撤销" @click="doUndo">↶</button
        ><button :disabled="!editable || !redo.length" title="重做" @click="doRedo">↷</button><i></i
        ><button :disabled="!editable" @click="merge">合并</button
        ><button :disabled="!editable" @click="split">拆分</button
        ><button
          :disabled="!editable"
          @click="setStyle('bold', !active.bold)"
          :class="{ active: active.bold }"
          title="加粗"
        >
          <b>B</b></button
        ><select
          :disabled="!editable"
          aria-label="边框"
          @change="
            borders(($event.target as HTMLSelectElement).value as any);
            ($event.target as HTMLSelectElement).value = '';
          "
        >
          <option value="">▦ 边框</option>
          <option value="all">全部边框</option>
          <option value="outer">外框</option>
          <option value="top">上边框</option>
          <option value="bottom">下边框</option>
          <option value="left">左边框</option>
          <option value="right">右边框</option>
          <option value="none">清除边框</option></select
        ><i></i
        ><select
          :disabled="!editable"
          @change="
            modifyAxis(
              ($event.target as HTMLSelectElement).value.includes('Row') ? 'row' : 'col',
              ($event.target as HTMLSelectElement).value.startsWith('insert'),
            );
            ($event.target as HTMLSelectElement).value = '';
          "
        >
          <option value="">行列操作</option>
          <option value="insertRow">插入行</option>
          <option value="deleteRow">删除行</option>
          <option value="insertCol">插入列</option>
          <option value="deleteCol">删除列</option></select
        ><button @click="copy" title="复制区域">复制</button
        ><button :disabled="!editable" @click="pasteCells" title="粘贴区域">粘贴</button
        ><button @click="paperSettings = !paperSettings" :class="{ active: paperSettings }">
          纸张</button
        ><select v-model.number="zoom">
          <option :value="0.75">75%</option>
          <option :value="1">100%</option>
          <option :value="1.5">150%</option>
          <option :value="2">200%</option>
        </select>
      </div>
      <div class="grid-formula">
        <span>{{ name }}</span
        ><b>fx</b
        ><input
          :disabled="!editable"
          :value="active.content"
          @change="contentChange"
          placeholder="输入文字，或在右侧插入字段"
        /><label><input type="checkbox" v-model="sampleMode" />示例值</label>
      </div>
      <div class="grid-scroll">
        <div
          class="grid-paper"
          :style="{
            minWidth: width * scale + 32 + 'px',
            minHeight: height * scale + 28 + 'px',
            paddingLeft: grid.margin.left * scale + 'px',
            paddingTop: grid.margin.top * scale + 'px',
          }"
        >
          <table
            class="design-grid"
            :style="{ width: grid.columns.reduce((a, b) => a + b, 0) * scale + 32 + 'px' }"
          >
            <colgroup>
              <col style="width: 32px" />
              <col v-for="(w, c) in grid.columns" :key="c" :style="{ width: w * scale + 'px' }" />
            </colgroup>
            <thead>
              <tr>
                <th
                  class="corner"
                  @click="
                    selection = {
                      r0: 0,
                      c0: 0,
                      r1: grid.rows.length - 1,
                      c1: grid.columns.length - 1,
                    }
                  "
                ></th>
                <th
                  v-for="(_, c) in grid.columns"
                  @click="selection = { r0: 0, c0: c, r1: grid.rows.length - 1, c1: c }"
                  :class="{ chosen: c >= rect.c0 && c <= rect.c1 }"
                >
                  {{ columnName(c)
                  }}<i class="col-handle" @pointerdown="resize($event, 'col', c)"></i>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(_, r) in grid.rows" :key="r">
                <th
                  class="row-header"
                  @click="selection = { r0: r, c0: 0, r1: r, c1: grid.columns.length - 1 }"
                  :style="{ height: grid.rows[r]! * scale + 'px' }"
                  :class="{ chosen: r >= rect.r0 && r <= rect.r1 }"
                >
                  {{ r + 1 }}<i class="row-handle" @pointerdown="resize($event, 'row', r)"></i>
                </th>
                <template v-for="(_, c) in grid.columns" :key="c"
                  ><td
                    v-if="visible(r, c)"
                    :rowspan="getCell(grid, r, c)?.rowSpan || 1"
                    :colspan="getCell(grid, r, c)?.colSpan || 1"
                    :style="cellStyle(r, c)"
                    :class="{
                      selected: isSelected(r, c),
                      outside:
                        grid.area &&
                        (r < grid.area.startRow ||
                          r > grid.area.endRow ||
                          c < grid.area.startCol ||
                          c > grid.area.endCol),
                    }"
                    @pointerdown="select($event, r, c)"
                    @pointerenter="extend(r, c)"
                    @dragover.prevent
                    @drop="drop($event, r, c)"
                  >
                    <div
                      class="grid-cell-content"
                      :style="{
                        maxHeight:
                          grid.rows
                            .slice(r, r + (getCell(grid, r, c)?.rowSpan || 1))
                            .reduce((a, b) => a + b, 0) *
                            scale -
                          4 +
                          'px',
                      }"
                    >
                      <img
                        v-if="getCell(grid, r, c)?.kind === 'image' && getCell(grid, r, c)?.image"
                        :src="getCell(grid, r, c)?.image"
                      /><span
                        v-else-if="['qrcode', 'barcode'].includes(getCell(grid, r, c)?.kind || '')"
                        class="code-placeholder"
                        >{{ getCell(grid, r, c)?.kind === 'qrcode' ? '▦ 二维码' : '▥ 条形码'
                        }}<small>{{ getCell(grid, r, c)?.content }}</small></span
                      ><template v-else>{{
                        sampleMode
                          ? resolve(getCell(grid, r, c)?.content || '')
                          : getCell(grid, r, c)?.content
                      }}</template>
                    </div>
                  </td></template
                >
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="grid-status">
        <span>{{ grid.rows.length }}行 × {{ grid.columns.length }}列</span
        ><span>拖动行列边界调整大小 · 拖动框选单元格</span
        ><span
          :style="{
            color:
              grid.columns.reduce((a, b) => a + b, 0) + grid.margin.left + grid.margin.right >
              width + 0.01
                ? '#cf535f'
                : undefined,
          }"
          >列宽 {{ grid.columns.reduce((a, b) => a + b, 0).toFixed(1) }}mm · 纸张 {{ width }} ×
          {{ height }}mm</span
        >
      </div>
    </div>
    <aside class="grid-properties">
      <template v-if="paperSettings"
        ><h3>纸张与打印</h3>
        <slot name="paper" />
        <div class="property-pair">
          <label
            >上边距<input
              v-model.number="grid.margin.top"
              :disabled="!editable"
              type="number"
              min="0"
              step=".5"
              @focus="beforeInput"
              @change="afterInput" /></label
          ><label
            >下边距<input
              v-model.number="grid.margin.bottom"
              :disabled="!editable"
              type="number"
              min="0"
              step=".5"
              @focus="beforeInput"
              @change="afterInput" /></label
          ><label
            >左边距<input
              v-model.number="grid.margin.left"
              :disabled="!editable"
              type="number"
              min="0"
              step=".5"
              @focus="beforeInput"
              @change="afterInput" /></label
          ><label
            >右边距<input
              v-model.number="grid.margin.right"
              :disabled="!editable"
              type="number"
              min="0"
              step=".5"
              @focus="beforeInput"
              @change="afterInput"
          /></label>
        </div>
        <label
          >重复表头行数<input
            v-model.number="grid.repeatHeader"
            :disabled="!editable"
            type="number"
            min="0"
            :max="grid.rows.length - 1"
            @focus="beforeInput"
            @change="afterInput" /></label
        ><label class="check-label"
          ><input
            v-model="grid.pageNumbers"
            :disabled="!editable"
            type="checkbox"
            @focus="beforeInput"
            @change="afterInput"
          />显示页码</label
        ><button
          :disabled="!editable"
          @click="
            change(() => {
              expand();
              grid.repeat = null;
              grid.area = {
                startRow: rect.r0,
                endRow: rect.r1,
                startCol: rect.c0,
                endCol: rect.c1,
              };
            })
          "
        >
          将所选区域设为打印区域</button
        ><button v-if="grid.area" :disabled="!editable" @click="change(() => (grid.area = null))">
          清除打印区域
        </button></template
      >
      <template v-else
        ><h3>单元格 {{ name }}</h3>
        <div class="property-pair">
          <label
            >行高（mm）<input
              :value="grid.rows[rect.r0]"
              :disabled="!editable"
              type="number"
              min=".1"
              step=".5"
              @change="
                change(
                  () =>
                    (grid.rows[rect.r0] = Math.max(
                      0.1,
                      Number(($event.target as HTMLInputElement).value),
                    )),
                )
              " /></label
          ><label
            >列宽（mm）<input
              :value="grid.columns[rect.c0]"
              :disabled="!editable"
              type="number"
              min=".1"
              step=".5"
              @change="
                change(
                  () =>
                    (grid.columns[rect.c0] = Math.max(
                      0.1,
                      Number(($event.target as HTMLInputElement).value),
                    )),
                )
              "
          /></label>
        </div>
        <label
          >内容类型<select
            :value="active.kind"
            :disabled="!editable"
            @change="setStyle('kind', ($event.target as HTMLSelectElement).value)"
          >
            <option value="text">文本 / 字段</option>
            <option value="image">图片</option>
            <option value="qrcode">二维码</option>
            <option value="barcode">条形码（Code128）</option>
          </select></label
        ><template v-if="active.kind === 'image'"
          ><label
            >上传图片<input
              :disabled="!editable"
              type="file"
              accept="image/png,image/jpeg"
              @change="upload" /></label></template
        ><template v-else
          ><label
            >内容<textarea
              :value="active.content"
              :disabled="!editable"
              maxlength="4000"
              placeholder="设备编码：{code}"
              @change="contentChange"
            />
          </label>
          <div class="property-pair">
            <label
              >字号（pt）<input
                :value="active.fontSize"
                :disabled="!editable"
                type="number"
                min="6"
                max="72"
                @change="
                  setStyle('fontSize', Number(($event.target as HTMLInputElement).value))
                " /></label
            ><label
              >文字颜色<input
                :value="active.color"
                :disabled="!editable"
                type="color"
                @change="setStyle('color', ($event.target as HTMLInputElement).value)"
            /></label>
          </div>
          <div class="font-toggles">
            <button
              v-for="key in ['bold', 'italic', 'underline'] as const"
              :class="{ active: active[key] }"
              :disabled="!editable"
              @click="setStyle(key, !active[key])"
            >
              {{ key === 'bold' ? '加粗' : key === 'italic' ? '斜体' : '下划线' }}
            </button>
          </div>
          <div class="property-pair">
            <label
              >水平对齐<select
                :value="active.align"
                :disabled="!editable"
                @change="setStyle('align', ($event.target as HTMLSelectElement).value)"
              >
                <option value="left">左对齐</option>
                <option value="center">居中</option>
                <option value="right">右对齐</option>
              </select></label
            ><label
              >垂直对齐<select
                :value="active.vertical"
                :disabled="!editable"
                @change="setStyle('vertical', ($event.target as HTMLSelectElement).value)"
              >
                <option value="top">顶部</option>
                <option value="middle">居中</option>
                <option value="bottom">底部</option>
              </select></label
            >
          </div>
          <label
            >长文本处理<select
              :value="active.overflow"
              :disabled="!editable"
              @change="setStyle('overflow', ($event.target as HTMLSelectElement).value)"
            >
              <option value="wrap">自动换行</option>
              <option value="shrink">缩小字号</option>
              <option value="clip">截断</option>
            </select></label
          ></template
        >
        <div class="property-pair">
          <label
            >背景色<input
              :value="active.background"
              :disabled="!editable"
              type="color"
              @change="setStyle('background', ($event.target as HTMLInputElement).value)"
          /></label>
        </div>
        <h3 class="border-title">边框</h3>
        <div class="border-presets">
          <button :disabled="!editable" @click="borders('all')">全部</button
          ><button :disabled="!editable" @click="borders('outer')">外框</button
          ><button :disabled="!editable" @click="borders('none')">清除</button>
        </div>
        <div class="border-sides">
          <button
            v-for="side in borderSides"
            :key="side"
            :disabled="!editable"
            :class="{ active: hasBorder(active, side) }"
            @click="borders(side)"
          >
            {{ { top: '上', right: '右', bottom: '下', left: '左' }[side] }}
          </button>
        </div>
        <div class="property-pair">
          <label
            >线条颜色<input
              :value="active.borderColor || '#000000'"
              :disabled="!editable"
              type="color"
              @change="setStyle('borderColor', ($event.target as HTMLInputElement).value)" /></label
          ><label
            >粗细（pt）<select
              :value="active.borderWidth ?? 0.5"
              :disabled="!editable"
              @change="setStyle('borderWidth', Number(($event.target as HTMLSelectElement).value))"
            >
              <option :value="0.5">0.5</option>
              <option :value="1">1</option>
              <option :value="1.5">1.5</option>
              <option :value="2">2</option>
              <option :value="3">3</option>
            </select></label
          >
        </div>
        <button :disabled="!editable" @click="clear">清空内容</button>
        <p class="property-note">
          浅色网格线仅辅助排版；设置的边框会显示在预览和打印中。
        </p></template
      >
    </aside>
  </div>
</template>
<style scoped>
.print-grid-shell {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  outline: 0;
  background: #fff;
  font-size: 13px;
}
.grid-fields {
  width: 170px;
  flex-shrink: 0;
  overflow: auto;
  border-right: 1px solid #e4e9ef;
  padding: 14px;
  background: #fafbfe;
  box-sizing: border-box;
}
.grid-fields h3 {
  font-size: 13px;
  margin: 0 0 12px;
  color: #354258;
}
.grid-fields .field-list {
  grid-template-columns: 1fr;
  margin: 0;
}
.grid-fields button:hover {
  color: var(--brand);
  border-color: var(--brand);
}
.grid-fields p {
  color: #94a0b0;
  line-height: 1.7;
  font-size: 12px;
}
.print-grid-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.grid-toolbar {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 10px;
  border-bottom: 1px solid #e5eaf0;
  flex-wrap: wrap;
}
.grid-toolbar button,
.grid-toolbar select,
.grid-properties button {
  height: 29px;
  border: 1px solid #dce3eb;
  background: #fff;
  border-radius: 4px;
  padding: 0 9px;
  color: #526076;
  font: inherit;
  cursor: pointer;
}
.grid-toolbar button.active,
.grid-properties button.active {
  color: var(--brand);
  background: var(--brand-soft);
  border-color: var(--brand);
}
.grid-toolbar button:disabled,
.grid-properties button:disabled {
  opacity: 0.4;
  cursor: default;
}
.grid-toolbar i {
  height: 18px;
  border-left: 1px solid #e4e9ef;
  margin: 0 4px;
}
.grid-toolbar > select:last-child {
  margin-left: auto;
}
.grid-formula {
  display: flex;
  align-items: center;
  height: 36px;
  border-bottom: 1px solid #e3e9ef;
  gap: 10px;
  flex-shrink: 0;
}
.grid-formula > span {
  width: 90px;
  text-align: center;
  border-right: 1px solid #e3e9ef;
  color: #64748b;
}
.grid-formula > b {
  color: #8b98a9;
  font-style: italic;
}
.grid-formula > input {
  border: 0;
  outline: 0;
  flex: 1;
  min-width: 0;
  font: inherit;
  background: #fff;
}
.grid-formula > label {
  display: flex;
  gap: 5px;
  align-items: center;
  margin-right: 12px;
  color: #8a98ac;
  white-space: nowrap;
}
.grid-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 24px;
  background: #eff2f6;
}
.grid-paper {
  display: inline-block;
  background: #fff;
  box-shadow: 0 2px 15px #2d405817;
  box-sizing: content-box;
}
.design-grid {
  table-layout: fixed;
  border-collapse: collapse;
  font-family: Arial, 'Microsoft YaHei', sans-serif;
}
.design-grid th {
  height: 28px;
  font-size: 11px;
  font-weight: 400;
  position: relative;
  text-align: center;
  color: #8391a6;
  background: #f4f6f9;
  border: 1px solid #dfe5ed;
  user-select: none;
}
.design-grid th.chosen {
  background: var(--brand-soft);
  color: var(--brand);
}
.design-grid td {
  border: 1px solid #e7ebf0;
  padding: 2px;
  box-sizing: border-box;
  position: relative;
  user-select: none;
  cursor: cell;
  overflow: hidden;
  line-height: 1.2;
}
.design-grid td.selected {
  outline: 1.5px solid var(--brand);
  outline-offset: -1.5px;
  background-image: linear-gradient(#245bfa0a, #245bfa0a);
}
.design-grid td.outside {
  opacity: 0.35;
}
.design-grid .row-header {
  width: 32px;
}
.grid-cell-content {
  overflow: hidden;
  overflow-wrap: anywhere;
}
.grid-cell-content img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
  max-height: inherit;
}
.col-handle {
  position: absolute;
  right: -3px;
  top: 0;
  width: 6px;
  height: 100%;
  cursor: col-resize;
  z-index: 3;
}
.row-handle {
  position: absolute;
  bottom: -3px;
  left: 0;
  height: 6px;
  width: 100%;
  cursor: row-resize;
  z-index: 3;
}
.grid-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 30px;
  flex-shrink: 0;
  padding: 0 12px;
  border-top: 1px solid #e4e9ef;
  color: #8e9bad;
  font-size: 11px;
}
.grid-properties {
  width: 250px;
  flex-shrink: 0;
  padding: 16px;
  overflow: auto;
  border-left: 1px solid #e4e9ef;
  background: white;
}
.grid-properties h3 {
  font-size: 13px;
  color: #354258;
  margin: 0 0 16px;
}
.grid-properties label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
  color: #748196;
}
.grid-properties input:not([type='checkbox']),
.grid-properties select,
.grid-properties textarea {
  width: 100%;
  height: 32px;
  box-sizing: border-box;
  border: 1px solid #dce3ec;
  border-radius: 4px;
  padding: 0 8px;
  min-width: 0;
  color: #3d4b60;
  background: #fff;
  font: inherit;
}
.grid-properties textarea {
  height: 80px;
  padding: 7px;
  resize: vertical;
}
.property-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.grid-properties .check-label {
  flex-direction: row;
  align-items: center;
}
.grid-properties input[type='color'] {
  padding: 3px;
}
.field-insert {
  display: flex;
  gap: 5px;
  margin: 0 0 14px;
}
.field-insert select {
  flex: 1;
}
.font-toggles {
  display: flex;
  gap: 6px;
  margin-bottom: 14px;
}
.font-toggles button {
  flex: 1;
  padding: 0 4px;
  font-size: 12px;
}
.border-title {
  border-top: 1px solid #e7ebf0;
  padding-top: 14px;
}
.border-presets,
.border-sides {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}
.border-presets button,
.border-sides button {
  flex: 1;
  padding: 0 5px;
}
.property-note {
  font-size: 11px;
  color: #9ba7b8;
  line-height: 1.6;
}
.field-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-top: 16px;
}
.field-list button {
  border: 1px solid #e0e6ef;
  font: inherit;
  text-align: left;
  padding: 7px;
  background: #f4f7fc;
  border-radius: 4px;
  color: #64758d;
  cursor: grab;
  font-size: 12px;
}
.code-placeholder {
  font-size: 12px;
  color: #64748b;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.code-placeholder small {
  font-size: 9px;
}
.print-grid-shell input[type='checkbox'] {
  accent-color: var(--brand);
}
@media (max-width: 1100px) {
  .grid-properties {
    width: 215px;
  }
  .grid-status > span:nth-child(2) {
    display: none;
  }
  .grid-scroll {
    padding: 16px;
  }
}
</style>
