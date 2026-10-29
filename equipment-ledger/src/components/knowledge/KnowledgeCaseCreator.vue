<script setup lang="ts">
import { ref } from 'vue';
import { ElButton, ElInput, ElAlert } from 'element-plus';
import AppDialog from '../common/AppDialog.vue';
import PagedChoiceField from '../common/PagedChoiceField.vue';
import { request, listRepairs } from '../../api';
import { tr } from '../../i18n';
import type { SelectorRow } from '../../selectors';
import { useUnsavedChanges } from '../../composables/useUnsavedChanges';
const emit = defineEmits<{ saved: [] }>();
const open = ref(false),
  busy = ref(false),
  error = ref(''),
  order = ref(''),
  options = ref<SelectorRow[]>([]),
  title = ref(''),
  text = ref(''),
  version = ref(0);
const guard = useUnsavedChanges(
  () => ({ order: order.value, title: title.value, text: text.value }),
  () => open.value,
  () => busy.value,
);
async function cancel() {
  if (await guard.permitLeave()) open.value = false;
}
async function rows(q: string, page: number, size: number) {
  const result = await listRepairs({ q, page, size, status: '已完成' });
  return {
    data: result.data.map((r) => ({
      id: r.id,
      name: r.orderNo + ' ' + r.equipmentName,
      code: r.orderNo,
    })),
    page: result.page,
  };
}
async function draft() {
  if (!order.value) return;
  busy.value = true;
  error.value = '';
  try {
    const result = await request<{ text: string; title: string; rowVersion: number }>(
      `/knowledge/repair-cases/${order.value}/draft`,
    );
    text.value = result.text;
    title.value = result.title;
    version.value = result.rowVersion;
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  } finally {
    busy.value = false;
  }
}
async function save() {
  busy.value = true;
  try {
    await request('/knowledge/repair-cases', {
      method: 'POST',
      body: JSON.stringify({
        repairOrderId: order.value,
        repairRowVersion: version.value,
        title: title.value,
        text: text.value,
      }),
    });
    open.value = false;
    emit('saved');
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  } finally {
    busy.value = false;
  }
}
function start() {
  open.value = true;
  order.value = '';
  title.value = '';
  text.value = '';
  error.value = '';
  guard.markSaved();
}
</script>
<template>
  <ElButton @click="start">{{ tr('knowledge.createCase') }}</ElButton
  ><AppDialog
    v-model="open"
    :busy="busy"
    :title="tr('knowledge.createCase')"
    width="760px"
    :before-close="guard.permitLeave"
    ><ElAlert v-if="error" :title="error" type="error" :closable="false" /><PagedChoiceField
      :model-value="order"
      :options="options"
      :disabled="busy"
      :load-rows="rows"
      @update:model-value="
        order = String($event);
        text = '';
      "
      @select="options = $event"
    /><ElButton :disabled="busy || !order" @click="draft">{{ tr('knowledge.caseDraft') }}</ElButton>
    <p>{{ tr('knowledge.caseNotice') }}</p>
    <ElInput v-model="title" :placeholder="tr('knowledge.title')" maxlength="200" /><ElInput
      v-model="text"
      type="textarea"
      :rows="10"
      maxlength="20000"
    /><template #footer
      ><ElButton :disabled="busy" @click="cancel">{{ tr('knowledge.cancel') }}</ElButton
      ><ElButton
        type="primary"
        :loading="busy"
        :disabled="!text.trim() || !title.trim()"
        @click="save"
        >{{ tr('knowledge.saveCase') }}</ElButton
      ></template
    ></AppDialog
  >
</template>
