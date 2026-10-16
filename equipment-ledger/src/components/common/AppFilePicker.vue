<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue';
import { ElUpload, ElButton, type UploadInstance, type UploadFile } from 'element-plus';
import 'element-plus/es/components/upload/style/css';
withDefaults(defineProps<{ accept?: string; multiple?: boolean; disabled?: boolean }>(), {
  multiple: false,
  disabled: false,
});
const emit = defineEmits<{ select: [files: File[]] }>();
const upload = ref<UploadInstance>();
let pending: File[] = [];
let scheduled = false;
let active = true;
function changed(file: UploadFile) {
  if (file.raw) pending.push(file.raw);
  if (scheduled) return;
  scheduled = true;
  queueMicrotask(() => {
    scheduled = false;
    const files = pending;
    pending = [];
    upload.value?.clearFiles();
    if (active && files.length) emit('select', files);
  });
}
onBeforeUnmount(() => {
  active = false;
  pending = [];
});
// ElUpload has no public method for opening its picker; use its own internal file input.
defineExpose({
  open: () =>
    (upload.value?.$el.querySelector('input[type="file"]') as HTMLInputElement | null)?.click(),
});
</script>
<template>
  <ElUpload
    ref="upload"
    :accept="accept"
    :multiple="multiple"
    :disabled="disabled"
    :auto-upload="false"
    :show-file-list="false"
    :on-change="changed"
  >
    <slot><ElButton :disabled="disabled">选择文件</ElButton></slot>
  </ElUpload>
</template>
