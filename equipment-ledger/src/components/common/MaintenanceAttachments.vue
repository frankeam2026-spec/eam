<script setup lang="ts">import { tr } from '../../i18n';

import AppAttachments from './AppAttachments.vue';
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
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
async function upload(files: File[]) {
  if (busy.value || props.readonly) return;

  if (!files.length) return;
  if (props.imagesOnly && files.some((file) => !file.type.startsWith('image/'))) {
    ElMessage.error(tr('ui.equipment.text1337'));

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
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0926'));
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <AppAttachments
    :files="modelValue"
    :images-only="imagesOnly"
    :readonly="readonly"
    :uploading="busy"
    @select="upload"
    @remove="
      emit(
        'update:modelValue',
        modelValue.filter((file) => file.id !== $event),
      )
    "
  />
</template>
