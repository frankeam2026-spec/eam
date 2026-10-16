<script setup lang="ts">
import { computed } from 'vue';
import type { LabelTemplate } from '../api';
import { resolve } from '../printing/grid';
const props = defineProps<{ template: LabelTemplate }>();
const cells = computed(() => {
  const g = props.template.layout;
  if (!g)
    return props.template.elements.map((e) => ({
      x: e.x,
      y: e.y,
      w: e.width,
      h: e.height,
      text: e.type === 'text' ? e.prefix + e.text : e.prefix + resolve(`{${e.type}}`),
      image: e.image,
      kind: e.type === 'image' ? 'image' : 'text',
      size: e.fontSize,
      bold: e.bold,
      align: e.align,
      color: '#000000',
      bg: '#ffffff',
      sides: { top: false, right: false, bottom: false, left: false },
      borderColor: '#000000',
      borderWidth: 0.5,
    }));
  const sum = (a: number[], start: number, end: number) =>
    a.slice(start, end).reduce((n, v) => n + v, 0);
  return g.cells.map((c) => ({
    x: g.margin.left + sum(g.columns, 0, c.col),
    y: g.margin.top + sum(g.rows, 0, c.row),
    w: sum(g.columns, c.col, c.col + c.colSpan),
    h: sum(g.rows, c.row, c.row + c.rowSpan),
    text: resolve(c.content),
    image: c.image,
    kind: c.kind,
    size: c.fontSize,
    bold: c.bold,
    align: c.align,
    color: c.color,
    bg: c.background,
    sides: c.borderSides || { top: c.border, right: c.border, bottom: c.border, left: c.border },
    borderColor: c.borderColor || '#000000',
    borderWidth: c.borderWidth ?? 0.5,
  }));
});
</script>
<template>
  <svg
    class="label-thumbnail"
    :viewBox="`0 0 ${template.widthMm} ${template.heightMm}`"
    role="img"
    :aria-label="`${template.name}排版缩略图`"
  >
    <rect width="100%" height="100%" fill="white" />
    <svg
      v-for="(c, index) in cells"
      :key="index"
      :x="c.x"
      :y="c.y"
      :width="c.w"
      :height="c.h"
      :viewBox="`0 0 ${c.w} ${c.h}`"
      overflow="hidden"
    >
      <rect :width="c.w" :height="c.h" :fill="c.bg" />
      <image
        v-if="c.kind === 'image' && c.image"
        :href="c.image"
        width="100%"
        height="100%"
        preserveAspectRatio="xMidYMid meet"
      />
      <template v-else-if="c.kind === 'qrcode' || c.kind === 'barcode'">
        <rect
          x=".5"
          y=".5"
          :width="Math.max(0, c.w - 1)"
          :height="Math.max(0, c.h - 1)"
          fill="#f0f3f8"
          stroke="#a9b5c7"
          stroke-width=".2"
        />
        <text
          :x="c.w / 2"
          :y="c.h / 2"
          text-anchor="middle"
          dominant-baseline="middle"
          :font-size="Math.min(3, c.h / 3)"
          fill="#687b96"
        >
          {{ c.kind === 'qrcode' ? '二维码' : '条形码' }}
        </text>
      </template>
      <text
        v-else
        :x="c.align === 'center' ? c.w / 2 : c.align === 'right' ? c.w - 0.8 : 0.8"
        :y="c.h / 2"
        dominant-baseline="middle"
        :text-anchor="c.align === 'center' ? 'middle' : c.align === 'right' ? 'end' : 'start'"
        :font-size="(c.size * 25.4) / 72"
        :font-weight="c.bold ? 700 : 400"
        :fill="c.color"
      >
        {{ c.text.split('\n')[0] }}
      </text>
      <g :stroke="c.borderColor" :stroke-width="(c.borderWidth * 25.4) / 72">
        <line v-if="c.sides.top" x1="0" y1="0" :x2="c.w" y2="0" />
        <line v-if="c.sides.bottom" x1="0" :y1="c.h" :x2="c.w" :y2="c.h" />
        <line v-if="c.sides.left" x1="0" y1="0" x2="0" :y2="c.h" />
        <line v-if="c.sides.right" :x1="c.w" y1="0" :x2="c.w" :y2="c.h" />
      </g>
    </svg>
  </svg>
</template>
<style scoped>
.label-thumbnail {
  display: block;
  max-width: 100%;
  max-height: 100%;
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 2px 5px #24395715);
  border: 1px solid #e2e7ef;
  font-family: Arial, 'Microsoft YaHei', sans-serif;
}
</style>
