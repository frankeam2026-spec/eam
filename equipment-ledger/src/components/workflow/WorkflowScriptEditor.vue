<script setup lang="ts">
import { onMounted, onBeforeUnmount, watch, ref } from 'vue';
import { EditorView, basicSetup } from 'codemirror';
import { EditorState, Compartment } from '@codemirror/state';
import { javascript } from '@codemirror/lang-javascript';
const props = defineProps<{ modelValue: string; readonly?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [string] }>();
const host = ref<HTMLElement>();
let view: EditorView | undefined;
const permissions = new Compartment();
const access = () => [
  EditorState.readOnly.of(!!props.readonly),
  EditorView.editable.of(!props.readonly),
];
onMounted(() => {
  view = new EditorView({
    parent: host.value,
    doc: props.modelValue,
    extensions: [
      basicSetup,
      javascript(),
      permissions.of(access()),
      EditorView.updateListener.of((update) => {
        if (update.docChanged) emit('update:modelValue', update.state.doc.toString());
      }),
      EditorView.theme({
        '&': {
          height: '420px',
          border: '1px solid var(--el-border-color)',
          borderRadius: 'var(--el-border-radius-base)',
        },
        '.cm-scroller': {
          fontFamily: 'Consolas, Menlo, monospace',
          fontSize: '13px',
          lineHeight: '1.7',
        },
        '.cm-gutters': {
          backgroundColor: 'var(--el-fill-color-light)',
          borderRight: '1px solid var(--el-border-color-light)',
          color: 'var(--el-text-color-secondary)',
        },
        '.cm-content': { padding: '12px 0' },
        '&.cm-focused': { outline: '2px solid var(--el-color-primary-light-7)' },
      }),
    ],
  });
});
watch(
  () => props.modelValue,
  (value) => {
    if (view && view.state.doc.toString() !== value)
      view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } });
  },
);
watch(
  () => props.readonly,
  () => view?.dispatch({ effects: permissions.reconfigure(access()) }),
);
function insert(value: string) {
  if (!view || props.readonly) return;
  view.dispatch(view.state.replaceSelection(value));
  view.focus();
}
onBeforeUnmount(() => view?.destroy());
defineExpose({ insert });
</script>
<template><div ref="host" class="workflow-code-editor" /></template>
