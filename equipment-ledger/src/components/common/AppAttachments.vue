<script setup lang="ts">import { tr } from '../../i18n';

import { ElButton, ElMessage } from 'element-plus';
import AppFilePicker from './AppFilePicker.vue';
import AppIcon from './AppIcon.vue';
import AppAttachmentList from './AppAttachmentList.vue';
import type { AttachmentItem } from './attachment';
const props = withDefaults(
  defineProps<{
    files: AttachmentItem[];
    readonly?: boolean;
    disabled?: boolean;
    uploading?: boolean;
    imagesOnly?: boolean;
    accept?: string;
    title?: string;
    showCover?: boolean;
    showEmpty?: boolean;
    maxSizeMb?: number;
  }>(),
  {
    readonly: false,
    disabled: false,
    uploading: false,
    imagesOnly: false,
    showCover: false,
    showEmpty: true,
    maxSizeMb: 20,
  },
);
const emit = defineEmits<{
  select: [files: File[]];
  remove: [id: string];
  'set-cover': [id: string];
}>();
function select(files: File[]) {
  if (props.readonly || props.disabled || props.uploading) return;
  if (files.some((file) => file.size > props.maxSizeMb * 1024 * 1024)) {
    ElMessage.error(tr('ui.common.dynamic54',{p0:props.maxSizeMb}));
    return;
  }
  if (
    props.imagesOnly &&
    files.some(
      (file) =>
        !file.type.startsWith('image/') && !/\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(file.name),
    )
  ) {
    ElMessage.error(tr('ui.equipment.text1337'));
    return;
  }
  if (files.length) emit('select', files);
}
</script>
<template>
  <div class="app-attachments">
    <div v-if="title || !readonly" class="attachment-toolbar">
      <strong v-if="title">{{ title }}</strong>
      <AppFilePicker
        v-if="!readonly"
        multiple
        :accept="
          accept || (imagesOnly ? 'image/*' : 'image/*,.pdf,.doc,.docx,.xls,.xlsx,.txt,.zip,.csv')
        "
        :disabled="disabled || uploading"
        @select="select"
      >
        <ElButton native-type="button" :loading="uploading" :disabled="disabled || uploading"
          ><AppIcon v-if="!uploading" name="Upload" />{{
            uploading ? tr('ui.equipment.text0134') : imagesOnly ? tr('ui.equipment.text0995') : tr('ui.equipment.text1338')
          }}</ElButton
        >
      </AppFilePicker>
    </div>
    <!-- <p v-if="!readonly" class="attachment-hint">
      支持多选，单个文件不超过{{ maxSizeMb }}MB{{
        imagesOnly ? '；点击缩略图可放大查看。' : '；图片可点击放大，文档可下载查看。'
      }}
    </p> -->
    <AppAttachmentList
      :files="files"
      :removable="!readonly"
      :disabled="disabled || uploading"
      :show-cover="showCover"
      :show-empty="showEmpty"
      @remove="emit('remove', $event)"
      @set-cover="emit('set-cover', $event)"
    />
  </div>
</template>
<style scoped>
.app-attachments {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  min-width: 0;
  color: var(--text);
  font-size: 13px;
}
.attachment-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.attachment-toolbar strong {
  margin-right: auto;
  font-size: 13px;
}
.attachment-hint {
  margin: 0;
  line-height: 20px;
  font-size: 12px;
}
</style>
