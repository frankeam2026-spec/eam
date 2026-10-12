<script setup lang="ts">
import { ElDialog } from 'element-plus';
const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    title: string;
    width?: string | number;
    busy?: boolean;
    beforeClose?: () => boolean | Promise<boolean>;
  }>(),
  { width: '520px', busy: false },
);
defineEmits<{ 'update:modelValue': [value: boolean] }>();
async function close(done: () => void) {
  if (props.busy) return;
  if (props.beforeClose && !(await props.beforeClose())) return;
  done();
}
</script>
<template>
  <ElDialog
    :model-value="modelValue"
    :title="title"
    :width="width"
    class="eam-dialog"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="!busy"
    :before-close="close"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
    ><slot /><template v-if="$slots.footer" #footer><slot name="footer" /></template
  ></ElDialog>
</template>
