<script setup lang="ts">import { tr, enumLabel } from '../i18n';

import { ElTableColumn } from 'element-plus';
import AppBusinessTable from './common/AppBusinessTable.vue';
import AppDecimalInput from './common/AppDecimalInput.vue';
import { remainingMaterialQuantity } from '../utils/taskMaterialQuantities';
import { formatNumber } from '../utils/number';
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElButton, ElInput, ElSelect, ElOption, ElMessage } from 'element-plus';
import AppDialog from './common/AppDialog.vue';
import { request } from '../api';
import { useAuthStore } from '../stores/auth';
const props = defineProps<{
  kind: '维修' | '保养' | '点巡检';
  taskId: string;
  canWork?: boolean;
  canRequest?: boolean;
}>();
const emit = defineEmits<{ changed: [] }>();
const router = useRouter(),
  auth = useAuthStore();
type Row = Record<string, string>;
const data = ref<{
  equipment: Row[];
  requests: Row[];
  allocations: Row[];
  installed: Row[];
  usage: Row[];
}>({ equipment: [], requests: [], allocations: [], installed: [], usage: [] });
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  dialog = ref(''),
  selected = ref<Row>(),
  quantity = ref('1'),
  equipmentId = ref(''),
  destination = ref('');
let confirmationKey = '';
let revision = 0;
const path = () => `/inventory/task-materials/${encodeURIComponent(props.kind)}/${props.taskId}`;
async function load() {
  const current = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const response = await request<{ data: typeof data.value }>(path());
    if (current === revision) data.value = response.data;
  } catch (e) {
    if (current === revision) error.value = e instanceof Error ? e.message : tr('ui.equipment.text1225');
  } finally {
    if (current === revision) loading.value = false;
  }
}
watch(() => [props.kind, props.taskId], load, { immediate: true });
function open(row: Row, type: string) {
  selected.value = row;
  quantity.value = type === 'remove' ? '1' : remainingMaterialQuantity(row);
  equipmentId.value = data.value.equipment.length === 1 ? data.value.equipment[0]!.id! : '';
  destination.value = '';
  confirmationKey = crypto.randomUUID();
  dialog.value = type;
}
async function submit() {
  if (busy.value || !selected.value) return;
  busy.value = true;
  try {
    data.value = (
      await request<{ data: typeof data.value }>(
        path() + '/' + (dialog.value === 'remove' ? 'remove' : 'use'),
        {
          method: 'POST',
          body: JSON.stringify({
            allocationId: selected.value.id,
            usageId: selected.value.id,
            quantity: quantity.value,
            equipmentId: equipmentId.value === '__shared__' ? '' : equipmentId.value,
            attribution: equipmentId.value === '__shared__' ? 'shared' : 'equipment',
            destination: destination.value,
            confirmationKey,
          }),
        },
      )
    ).data;
    dialog.value = '';
    ElMessage.success(tr('ui.equipment.text1226'));
    emit('changed');
    window.dispatchEvent(new Event('task-materials-changed'));
    window.dispatchEvent(new Event('work-inbox-changed'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0197'));
  } finally {
    busy.value = false;
  }
}
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => data.value.allocations;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows2 = () => data.value.installed;
type TableColumnRow2 = NonNullable<ReturnType<typeof tableColumnRows2>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows3 = () => data.value.usage;
type TableColumnRow3 = NonNullable<ReturnType<typeof tableColumnRows3>>[number];
</script>
<template>
  <section class="task-materials">
    <header>
      <strong>{{ tr('ui.equipment.text1227') }}</strong>
      <div>
        <ElButton text :loading="loading" @click="load">{{ tr('ui.equipment.text0424') }}</ElButton
        ><ElButton
          v-if="canRequest || canWork"
          type="primary"
          plain
          @click="
            router.push({
              name: 'inventory-requisition-detail',
              params: { id: 'new' },
              query: { taskKind: kind, taskId },
            })
          "
          >{{ tr('ui.equipment.text1094') }}</ElButton
        >
      </div>
    </header>
    <p v-if="error" role="alert">{{ error }}</p>
    <div v-if="data.requests.length" class="requests">
      <span v-for="r in data.requests" :key="r.id"
        ><ElButton
          v-if="auth.can('inventoryRequisition', '查看')"
          link
          type="primary"
          @click="router.push({ name: 'inventory-requisition-detail', params: { id: r.id } })"
          >{{ r.code }}</ElButton
        ><span v-else>{{ r.code }}</span> · {{ enumLabel('status', r.status) }}</span
      >
    </div>
    <div class="scroll">
      <AppBusinessTable
        :rows="data.allocations"
        :pagination="false"
        :fill="false"
        :framed="false"
        class="allocation-table"
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0515') }}</div></template
          ><template #default="{ row: a, $index: tableRowIndex }">{{
            a.materialName
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text1228') }}</div></template
          ><template #default="{ row: a, $index: tableRowIndex }">{{
            a.issueCode
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" class-name="numeric"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0652') }}</div></template
          ><template #default="{ row: a, $index: tableRowIndex }"
            >{{ formatNumber(a.quantity) }} {{ a.unit }}</template
          ></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" class-name="numeric"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text1229') }}</div></template
          ><template #default="{ row: a, $index: tableRowIndex }">
            {{ formatNumber(a.usageType === '可安装' ? '0' : a.usedQuantity) }}
          </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" class-name="numeric"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text1230') }}</div></template
          ><template #default="{ row: a, $index: tableRowIndex }">
            {{ formatNumber(a.usageType === '可安装' ? a.usedQuantity : '0') }}
          </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" class-name="numeric"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text1231') }}</div></template
          ><template #default="{ row: a, $index: tableRowIndex }">{{
            formatNumber(a.returnedQuantity)
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150" class-name="numeric remaining"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0186') }}</div></template
          ><template #default="{ row: a, $index: tableRowIndex }">{{
            formatNumber(remainingMaterialQuantity(a))
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn
          class-name="actions"
          fixed="right"
          :width="170"
          :min-width="156"
          :resizable="false"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
          ><template #default="{ row: a, $index: tableRowIndex }">
            <ElButton
              v-if="canWork && Number(remainingMaterialQuantity(a)) > 0"
              link
              type="primary"
              @click="open(a, 'use')"
              >{{ a.usageType === '可安装' ? tr('ui.equipment.text1232') : tr('ui.equipment.text1233') }}</ElButton
            >
          </template></ElTableColumn
        ><template #empty
          ><div v-if="!data.allocations.length">
            {{ loading ? tr('ui.equipment.text0162') : tr('ui.equipment.text1234') }}
          </div></template
        ></AppBusinessTable
      >
    </div>
    <p v-if="data.allocations.length" class="quantity-hint">
      {{ tr('ui.equipment.text1235') }}
    </p>
    <template v-if="canWork && data.installed.length"
      ><h4>{{ tr('ui.equipment.text1236') }}</h4>
      <div class="scroll">
        <AppBusinessTable :rows="data.installed" :pagination="false" :fill="false" :framed="false"
          ><!-- @vue-generic {TableColumnRow2} -->
          <ElTableColumn :min-width="210"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0168') }}</div></template
            ><template #default="{ row: u, $index: tableRowIndex }">{{
              u.name
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow2} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0091') }}</div></template
            ><template #default="{ row: u, $index: tableRowIndex }">{{
              u.model
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow2} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0144') }}</div></template
            ><template #default="{ row: u, $index: tableRowIndex }">{{
              formatNumber(u.quantity)
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow2} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0169') }}</div></template
            ><template #default="{ row: u, $index: tableRowIndex }">{{
              u.createdAt?.replace('T', ' ').slice(0, 19)
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow2} -->
          <ElTableColumn
            class-name="actions"
            fixed="right"
            :width="170"
            :min-width="156"
            :resizable="false"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
            ><template #default="{ row: u, $index: tableRowIndex }">
              <ElButton v-if="canWork" link type="primary" @click="open(u, 'remove')"
                >{{ tr('ui.equipment.text1114') }}</ElButton
              >
            </template></ElTableColumn
          ></AppBusinessTable
        >
      </div></template
    >
    <template v-if="data.usage.length"
      ><h4>{{ tr('ui.equipment.text1237') }}</h4>
      <div class="scroll">
        <AppBusinessTable :rows="data.usage" :pagination="false" :fill="false" :framed="false"
          ><!-- @vue-generic {TableColumnRow3} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0515') }}</div></template
            ><template #default="{ row: u, $index: tableRowIndex }">{{
              u.materialName
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow3} -->
          <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
            ><template #default="{ row: u, $index: tableRowIndex }">{{
              u.kind
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow3} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0144') }}</div></template
            ><template #default="{ row: u, $index: tableRowIndex }">{{
              formatNumber(u.quantity)
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow3} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text1238') }}</div></template
            ><template #default="{ row: u, $index: tableRowIndex }"
              >{{ enumLabel('status', u.status) }} {{ u.destination }}</template
            ></ElTableColumn
          ><!-- @vue-generic {TableColumnRow3} -->
          <ElTableColumn :min-width="156" :width="240" :resizable="false"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text1115') }}</div></template
            ><template #default="{ row: u, $index: tableRowIndex }">{{
              u.actorName
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow3} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0174') }}</div></template
            ><template #default="{ row: u, $index: tableRowIndex }">{{
              u.createdAt?.replace('T', ' ').slice(0, 19)
            }}</template></ElTableColumn
          ></AppBusinessTable
        >
      </div></template
    >
    <AppDialog
      :model-value="!!dialog"
      :title="
        dialog === 'remove'
          ? tr('ui.equipment.text1127')
          : selected?.usageType === '可安装'
            ? tr('ui.equipment.text1239')
            : tr('ui.equipment.text1233')
      "
      :busy="busy"
      @update:model-value="
        (v) => {
          if (!v) dialog = '';
        }
      "
      ><div class="fields">
        <label v-if="dialog === 'remove'"
          >{{ tr('ui.equipment.text1240') }}<ElInput
            v-model="destination"
            :placeholder="tr('ui.equipment.text1241')"
            maxlength="500" /></label
        ><template v-else
          ><label>{{ tr('ui.equipment.text0144') }}<AppDecimalInput v-model="quantity" inputmode="decimal" /></label
          ><label
            >{{ tr('ui.equipment.text1242') }}<ElSelect v-model="equipmentId" :placeholder="tr('ui.equipment.text1243')"
              ><ElOption
                v-if="selected?.usageType !== '可安装' && data.equipment.length > 1"
                value="__shared__"
                :label="tr('ui.equipment.text1244')" /><ElOption
                v-for="eq in data.equipment"
                :key="eq.id"
                :value="eq.id"
                :label="eq.name" /></ElSelect
          ></label>
          <p>{{ tr('ui.equipment.text1245') }}</p></template
        >
      </div>
      <template #footer
        ><div class="buttons">
          <ElButton :disabled="busy" @click="dialog = ''">{{ tr('ui.equipment.text0157') }}</ElButton
          ><ElButton type="primary" :loading="busy" @click="submit">{{ tr('ui.equipment.text0253') }}</ElButton>
        </div></template
      ></AppDialog
    >
  </section>
</template>
<style scoped>
.task-materials {
  color: var(--text);
  font-size: 13px;
  background: white;
  margin-top: 20px;
  padding: 16px;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
header div,
.buttons {
  display: flex;
  gap: 16px;
}
.requests {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
}
.scroll {
  overflow: auto;
}
table {
  width: 100%;
  min-width: 660px;
  table-layout: fixed;
  border-collapse: collapse;
}
.actions {
  position: sticky;
  right: 0;
  background: white;
  width: 156px;
}
.app-business-table :deep(thead .actions) {
  background: #f5f7fb;
}
.app-business-table :deep(tbody tr:hover .actions) {
  background: var(--table-row-hover, #f5f7fb);
}
.empty {
  text-align: center;
  padding: 28px;
}
h4 {
  margin: 20px 0 10px;
  font-size: 13px;
}
.fields {
  display: grid;
  gap: 16px;
}
.fields label {
  display: grid;
  grid-template-columns: 80px 1fr;
  align-items: center;
  gap: 10px;
}
.buttons {
  justify-content: center;
}
.buttons :deep(.el-button) {
  margin-left: 0;
  min-width: 100px;
}
.allocation-table :deep(th:first-child) {
  width: 170px;
}
.allocation-table :deep(th:nth-child(2)) {
  width: 160px;
}
.numeric {
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.remaining {
  color: var(--primary, #205cf5);
  font-weight: 600;
}
.quantity-hint {
  margin: 12px 0 0;
  font-size: 13px;
  line-height: 1.6;
}
</style>
