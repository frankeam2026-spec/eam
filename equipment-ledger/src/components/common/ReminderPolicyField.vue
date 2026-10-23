<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElSelect, ElOption } from 'element-plus';
import { tr } from '../../i18n';
import { notificationApi, type ReminderKind } from '../../notifications';
import type { SelectorRow } from '../../selectors';
import PagedChoiceField from './PagedChoiceField.vue';
const props = defineProps<{
  kind: ReminderKind;
  mode?: string;
  id?: string | null;
  name?: string;
  disabled?: boolean;
}>();
const emit = defineEmits<{ 'update:mode': [string]; 'update:id': [string] }>();
const selectedMode = computed({
  get: () => props.mode || 'DEFAULT',
  set: (v) => emit('update:mode', v),
});
const selectedId = computed({ get: () => props.id || '', set: (v) => emit('update:id', v) });
const remembered = ref<SelectorRow[]>([]);
const options = computed(() =>
  props.id && !remembered.value.some((p) => p.id === props.id)
    ? [...remembered.value, { id: props.id, name: props.name || props.id }]
    : remembered.value,
);
</script>
<template>
  <div class="reminder-policy-field">
    <label>{{ tr('notify.modeLabel') }}</label>
    <ElSelect v-model="selectedMode" :disabled="disabled"
      ><ElOption
        v-for="m in ['DEFAULT', 'POLICY', 'OFF']"
        :key="m"
        :value="m"
        :label="tr('notify.mode.' + m)"
    /></ElSelect>
    <PagedChoiceField
      v-if="selectedMode === 'POLICY'"
      v-model="selectedId"
      :options="options"
      :disabled="disabled"
      :load-rows="(q, p, s) => notificationApi.choices(kind, q, p, s)"
      :title="tr('notify.selectPolicy')"
      :columns="[{ key: 'name', label: tr('notify.policyName'), minWidth: 240 }]"
      @select="remembered = $event"
    />
  </div>
</template>
<style scoped>
.reminder-policy-field {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin: 12px 0;
}
.reminder-policy-field label {
  font-size: 13px;
}
.reminder-policy-field > .el-select {
  width: 180px;
}
.reminder-policy-field :deep(.dialog-choice-input) {
  width: 260px;
  max-width: 100%;
}
</style>
