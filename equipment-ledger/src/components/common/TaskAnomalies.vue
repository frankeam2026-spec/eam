<script setup lang="ts">import { tr } from '../../i18n';

import { ElButton } from 'element-plus';

import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElInput, ElSelect, ElOption, ElMessage } from 'element-plus';
import AppDialog from './AppDialog.vue';
import AppBusinessTable from './AppBusinessTable.vue';
import { request } from '../../api';
const props = defineProps<{
  kind: '保养' | '点巡检' | '维修';
  taskId: string;
  canRecord?: boolean;
}>();
const router = useRouter();
const data = ref<Record<string, any>[]>([]),
  items = ref<Record<string, any>[]>([]),
  open = ref(false),
  item = ref(''),
  description = ref(''),
  error = ref(''),
  busy = ref(false);
let revision = 0;
async function load() {
  const seq = ++revision;
  try {
    const r = await request<{ data: Record<string, any>[]; items?: Record<string, any>[] }>(
      `/anomalies/source?kind=${encodeURIComponent(props.kind)}&taskId=${props.taskId}`,
    );
    if (seq === revision) {
      data.value = r.data;
      items.value = r.items || [];
      error.value = '';
    }
  } catch (e) {
    if (seq === revision) error.value = e instanceof Error ? e.message : tr('ui.equipment.text1585');
  }
}
watch(() => [props.kind, props.taskId], load, { immediate: true });
async function record() {
  const chosen = items.value.find((i) => i.id === item.value);
  if (!chosen) {
    ElMessage.warning(tr('ui.equipment.text1586'));
    return;
  }
  busy.value = true;
  try {
    const r = await request<{ data: Record<string, any> }>('/anomalies', {
      method: 'POST',
      body: JSON.stringify({
        sourceKind: props.kind,
        sourceTaskId: props.taskId,
        sourceItemId: item.value,
        equipmentId: chosen.equipmentId,
        description: description.value,
      }),
    });
    open.value = false;
    await load();
    router.push('/anomalies/' + r.data.id);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text1587'));
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="task-anomalies">
    <header>
      <h3>{{ tr('ui.equipment.text1588') }}</h3>
      <ElButton
        v-if="canRecord && kind !== '维修'"
        class=""
        @click="
          open = true;
          item = '';
          description = '';
        "
      >
        {{ tr('ui.equipment.text1589') }}
      </ElButton>
    </header>
    <p v-if="error" role="alert">{{ error }}</p>
    <AppBusinessTable
      v-else
      :rows="data"
      :pagination="false"
      :fill="false"
      :columns="[
        { prop: 'code', label: '异常编号', minWidth: 165 },
        { prop: 'description', label: '异常说明', minWidth: 220 },
        { prop: 'status', label: '状态', minWidth: 110 },
      ]"
      ><template #actions="{ row }"
        ><ElButton link type="primary" class="" @click="router.push('/anomalies/' + row.id)">
          {{ tr('ui.equipment.text1590') }}
        </ElButton></template
      ></AppBusinessTable
    ><AppDialog v-model="open" :title="tr('ui.equipment.text1591')" :busy="busy"
      ><div class="anomaly-fields">
        <label
          >{{ tr('ui.equipment.text1592') }}<ElSelect v-model="item" filterable
            ><ElOption
              v-for="i in items"
              :key="i.id"
              :value="i.id"
              :label="i.name" /></ElSelect></label
        ><label
          >{{ tr('ui.equipment.text1593') }}<ElInput v-model="description" type="textarea" :rows="4" maxlength="4000"
        /></label>
        <p>
          {{ tr('ui.equipment.text1594') }}
        </p>
      </div>
      <template #footer
        ><ElButton class="" :disabled="busy" @click="open = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton class="" :disabled="busy" @click="record" type="primary"
          >{{ tr('ui.equipment.text1595') }}</ElButton
        ></template
      ></AppDialog
    >
  </section>
</template>
<style scoped>
.task-anomalies {
  padding: 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 6px;
  margin: 16px 0;
}
.task-anomalies header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.task-anomalies h3 {
  font-size: 14px;
  margin: 0;
}
.anomaly-fields {
  display: grid;
  gap: 16px;
}
.anomaly-fields label {
  display: grid;
  gap: 8px;
}
</style>
