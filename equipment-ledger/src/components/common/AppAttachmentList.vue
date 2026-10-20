<script setup lang="ts">import { tr } from '../../i18n';

import { computed } from 'vue';
import { ElImage, ElButton } from 'element-plus';
import 'element-plus/es/components/image/style/css';
import AppIcon from './AppIcon.vue';
import { isAttachmentImage, attachmentUrl, type AttachmentItem } from './attachment';
const props = withDefaults(
  defineProps<{
    files: AttachmentItem[];
    removable?: boolean;
    disabled?: boolean;
    showCover?: boolean;
    showEmpty?: boolean;
  }>(),
  { removable: false, disabled: false, showCover: false, showEmpty: true },
);
const emit = defineEmits<{ remove: [id: string]; 'set-cover': [id: string] }>();
const images = computed(() => props.files.filter(isAttachmentImage));
const documents = computed(() => props.files.filter((file) => !isAttachmentImage(file)));
const previewUrls = computed(() => images.value.map(attachmentUrl));
function sizeLabel(size?: number) {
  if (size == null) return '';
  return size >= 1024 * 1024
    ? `${(size / 1024 / 1024).toFixed(1)} MB`
    : `${Math.ceil(size / 1024)} KB`;
}
</script>
<template>
  <div class="app-attachment-list">
    <div v-if="images.length" class="attachment-images" @click.prevent>
      <figure v-for="(file, index) in images" :key="file.id" class="attachment-image-card">
        <ElImage
          :src="attachmentUrl(file)"
          :alt="`图片${index + 1}`"
          fit="cover"
          :preview-src-list="previewUrls"
          :initial-index="index"
          preview-teleported
        >
          <template #error
            ><span class="attachment-image-error"
              ><AppIcon name="Picture" />{{ tr('ui.equipment.text1332') }}</span
            ></template
          >
        </ElImage>
        <figcaption v-if="showCover || removable" class="attachment-image-actions">
          <span v-if="showCover && index === 0" class="cover-label">{{ tr('ui.equipment.text1333') }}</span>
          <ElButton
            v-else-if="showCover && removable"
            link
            type="primary"
            :disabled="disabled"
            @click="emit('set-cover', file.id)"
            >{{ tr('ui.equipment.text1334') }}</ElButton
          >
          <ElButton
            v-if="removable"
            link
            type="danger"
            :disabled="disabled"
            :aria-label="`移除图片${index + 1}`"
            @click="emit('remove', file.id)"
            >{{ tr('ui.equipment.text0151') }}</ElButton
          >
        </figcaption>
      </figure>
    </div>
    <div v-if="documents.length" class="attachment-documents">
      <div v-for="file in documents" :key="file.id" class="attachment-document">
        <AppIcon name="Document" />
        <a :href="file.url || file.dataUrl" :title="file.name" target="_blank" rel="noopener">{{
          file.name || tr('ui.equipment.text1335')
        }}</a>
        <span v-if="file.size != null" class="attachment-size">{{ sizeLabel(file.size) }}</span>
        <ElButton
          v-if="removable"
          link
          type="danger"
          :disabled="disabled"
          :aria-label="tr('ui.equipment.text1336')"
          @click="emit('remove', file.id)"
          >{{ tr('ui.equipment.text0151') }}</ElButton
        >
      </div>
    </div>
    <!-- <span v-if="!files.length && showEmpty" class="attachment-empty">暂无附件</span> -->
  </div>
</template>
<style scoped>
.app-attachment-list {
  min-width: 0;
  width: 100%;
  color: var(--text);
  font-size: 13px;
}
.attachment-images {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.attachment-image-card {
  width: 112px;
  margin: 0;
  border: 1px solid var(--line);
  padding: 6px;
  border-radius: 6px;
  background: var(--surface);
  flex: none;
}
.attachment-image-card :deep(.el-image) {
  display: block;
  width: 100%;
  height: 88px;
  border-radius: 4px;
  cursor: pointer;
}
.attachment-image-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 26px;
  margin-top: 4px;
}
.attachment-image-actions .el-button + .el-button {
  margin-left: 0;
}
.cover-label {
  color: var(--brand);
}
.attachment-image-error {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
}
.attachment-documents {
  display: grid;
  gap: 8px;
}
.attachment-images + .attachment-documents {
  margin-top: 12px;
}
.attachment-document {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 4px;
  min-width: 0;
}
.attachment-document > .app-icon {
  color: var(--brand);
  flex: none;
}
.attachment-document a {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--brand);
  text-decoration: none;
}
.attachment-size {
  white-space: nowrap;
  font-size: 12px;
}
.attachment-empty {
  display: block;
  padding: 8px 0;
}
</style>
