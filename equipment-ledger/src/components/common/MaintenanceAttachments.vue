<script setup lang="ts">
import { ref } from 'vue';
import { ElButton, ElMessage } from 'element-plus';
import type { RepairAttachment } from '../../api';
import { maintenanceUpload } from '../../maintenance';
const props = withDefaults(
  defineProps<{
    modelValue: RepairAttachment[];
    imagesOnly?: boolean;
    readonly?: boolean;
    uploadFile?: (file: File) => Promise<RepairAttachment>;
  }>(),
  { imagesOnly: false, readonly: false },
);
const emit = defineEmits<{ 'update:modelValue': [RepairAttachment[]] }>();
const busy = ref(false);
const input = ref<HTMLInputElement>();
async function upload(e: Event) {
  if (busy.value || props.readonly) return;
  const files = Array.from((e.target as HTMLInputElement).files || []);
  if (!files.length) return;
  if (props.imagesOnly && files.some((file) => !file.type.startsWith('image/'))) {
    ElMessage.error('请选择图片');
    if (input.value) input.value.value = '';
    return;
  }
  busy.value = true;
  try {
    const next = [...props.modelValue];
    for (const f of files) {
      next.push(await (props.uploadFile || maintenanceUpload)(f));
      // Keep completed uploads attached even if a later file fails.
      emit('update:modelValue', [...next]);
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败');
  } finally {
    busy.value = false;
    if (input.value) input.value.value = '';
  }
}
</script>
<template>
  <div class="maintenance-attachments">
    <ElButton v-if="!readonly" size="small" :loading="busy" @click="input?.click()"
      >＋ {{ imagesOnly ? '添加图片' : '添加附件' }}</ElButton
    ><input
      ref="input"
      type="file"
      :accept="imagesOnly ? 'image/*' : undefined"
      multiple
      hidden
      @change="upload"
    /><span v-for="(a, i) in modelValue" :key="a.id" class="maintenance-file"
      ><a :href="a.url" target="_blank" rel="noopener">{{ a.name }}</a
      ><button
        v-if="!readonly"
        type="button"
        :disabled="busy"
        aria-label="移除附件"
        @click="
          emit(
            'update:modelValue',
            modelValue.filter((_, n) => n !== i),
          )
        "
      >
        ×
      </button></span
    >
  </div>
</template>
<style scoped>
.maintenance-attachments {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.maintenance-file {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 4px 8px;
  background: var(--brand-soft, #eff4ff);
  border-radius: 4px;
  font-size: 13px;
}
.maintenance-file a {
  color: var(--brand);
  text-decoration: none;
}
.maintenance-file button {
  border: 0;
  background: none;
  color: var(--text-secondary);
  cursor: pointer;
}
</style>
