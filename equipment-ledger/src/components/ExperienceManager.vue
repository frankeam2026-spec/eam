<script setup lang="ts">
import { normalizePageSize } from '../utils/pagination';
import { currentLocale } from '../i18n';import { tr, enumLabel } from '../i18n';

import DialogChoiceInput from './common/DialogChoiceInput.vue';
import AppAttachmentList from './common/AppAttachmentList.vue';
import AppListToolbar from './common/AppListToolbar.vue';
import { ElButton, ElCheckbox } from 'element-plus';

import { computed, ref, watch, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElInput, ElSelect, ElOption, ElDatePicker, ElMessage, ElMessageBox } from 'element-plus';
import AppBusinessTable from './common/AppBusinessTable.vue';
import AppBackButton from './common/AppBackButton.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import AppDialog from './common/AppDialog.vue';
import AppIcon from './common/AppIcon.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import EquipmentPickerDialog from './common/EquipmentPickerDialog.vue';
import RepairUserPicker from './common/RepairUserPicker.vue';
import RepairOrderPicker from './common/RepairOrderPicker.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import FilterConditionPopover from './common/FilterConditionPopover.vue';
import { useConditionPopover } from '../composables/useConditionPopover';
import AppliedFilterChips from './common/AppliedFilterChips.vue';
import type { FilterCondition, FilterField } from '../query/advancedFilters';
import TreeSelect from './TreeSelect.vue';
import MaintenanceAttachments from './common/MaintenanceAttachments.vue';
import type { RepairAttachment } from '../api';
import { request, type DispatchUser, type EquipmentRecord } from '../api';
import { useAuthStore } from '../stores/auth';
import { useBaseDataStore } from '../stores/baseData';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { formatDateTime } from '../utils/dateTime';
import { formatNumber } from '../utils/number';
type Row = Record<string, any>;
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore(),
  base = useBaseDataStore();
const kind = computed(() => String(route.meta.page));
const resource = computed(() =>
  kind.value === 'repair-plans'
    ? 'repair-plans'
    : kind.value === 'material-custody'
      ? 'material-custody'
      : 'anomalies',
);
const menu = computed(() =>
  resource.value === 'repair-plans'
    ? 'repairPlan'
    : resource.value === 'material-custody'
      ? 'materialCustody'
      : 'anomaly',
);
const rows = ref<Row[]>([]),
  total = ref(0),
  page = ref(1),
  size = ref(20),
  search = ref(''),
  query = ref(''),
  detail = ref<Row>(),
  form = ref<Row>({}),
  busy = ref(false),
  loading = ref(false),
  error = ref(''),
  equipmentOpen = ref(false),
  userOpen = ref(false),
  dialog = ref(''),
  reason = ref(''),
  returnQuantity = ref(''),
  faultTypeId = ref(''),
  severity = ref(''),
  linkId = ref(''),
  personal = ref(true);
const evidence = ref<RepairAttachment[]>([]);
async function uploadEvidence(file: File): Promise<RepairAttachment> {
  const body = new FormData();
  body.append('file', file);
  const res = await fetch('/api/v1/anomalies/files', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Accept-Language': currentLocale.value },
    body,
  });
  const payload = await res.json();
  if (!res.ok) throw new Error(payload.message || tr('ui.equipment.text0926'));
  return payload.data;
}
const ownerChoices = () => request<{ data: DispatchUser[] }>('/repair-plans/owner-choices');
const editing = computed(() => route.params.id === 'new' || route.query.edit === '1');
const repairOpen = ref(false),
  filtersOpen = ref(false),
  columnOpen = ref(false),
  conditions = ref<FilterCondition[]>([]),
  hidden = ref<string[]>([]),
  exportBusy = ref(false);
const fields = computed<FilterField[]>(() =>
  columns.value.map((c) => ({
    key: c.prop,
    label: c.label,
    type:
      c.prop === 'status'
        ? 'choice'
        : c.prop.includes('_at')
          ? 'date'
          : c.prop === 'remaining'
            ? 'number'
            : 'text',
    options:
      c.prop === 'status'
        ? resource.value === 'anomalies'
          ? ['待处理', '已安排', '处理中', '已消除', '已作废']
          : ['草稿', '待生成', '已生成', '已取消']
        : undefined,
  })),
);
const shownColumns = computed(() => columns.value.filter((c) => !hidden.value.includes(c.prop)));
const effectiveFilters = () => conditions.value;
function searchList() {
  query.value = search.value;
  page.value = 1;
  load();
}
function applyFilters(value: FilterCondition[]) {
  conditions.value = value;
  page.value = 1;
  load();
}
async function linkRepair(r: { id: string }) {
  linkId.value = r.id;
  dialog.value = 'link';
  await submitAction();
}
async function exportRows() {
  exportBusy.value = true;
  try {
    const params = new URLSearchParams({
      q: query.value,
      filters: JSON.stringify(effectiveFilters()),
      personal: String(personal.value),
    });
    const r = await fetch('/api/v1/' + resource.value + '/export?' + params, {
      credentials: 'same-origin',
      headers: { 'Accept-Language': currentLocale.value },
    });
    if (!r.ok) {
      const e = await r.json();
      throw new Error(e.message || tr('ui.equipment.text0185'));
    }
    const url = URL.createObjectURL(await r.blob());
    const a = document.createElement('a');
    a.href = url;
    a.download = resource.value + '.csv';
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0185'));
  } finally {
    exportBusy.value = false;
  }
}
const { markSaved, permitLeave } = useUnsavedChanges(
  () => form.value,
  () => editing.value,
  () => busy.value,
);
const columns = computed(() =>
  resource.value === 'material-custody'
    ? [
        { prop: 'materialCode', get label() { return tr('ui.equipment.text0154'); }, minWidth: 150 },
        { prop: 'materialName', get label() { return tr('ui.equipment.text0155'); }, minWidth: 160 },
        { prop: 'specification', get label() { return tr('ui.equipment.text0091'); }, minWidth: 160 },
        {
          prop: 'remaining',
          get label() { return tr('ui.equipment.text0186'); },
          minWidth: 115,
          formatter: (v: unknown) => formatNumber(v),
        },
        { prop: 'holderName', get label() { return tr('ui.equipment.text0187'); }, minWidth: 110 },
        { prop: 'warehouse', get label() { return tr('ui.equipment.text0188'); }, minWidth: 140 },
        { prop: 'taskCode', get label() { return tr('ui.equipment.text0189'); }, minWidth: 160 },
        { prop: 'remark', get label() { return tr('ui.equipment.text0190'); }, minWidth: 180 },
      ]
    : [
        {
          prop: 'code',
          label: resource.value === 'anomalies' ? '异常编号' : '计划编号',
          minWidth: 170,
        },
        { prop: 'equipmentName', get label() { return tr('ui.equipment.text0081'); }, minWidth: 150 },
        { prop: 'description', get label() { return tr('ui.equipment.text0191'); }, minWidth: 260 },
        { prop: 'status', get label() { return tr('ui.equipment.text0170'); }, minWidth: 110 },
        {
          prop: resource.value === 'anomalies' ? 'discovered_at' : 'planned_at',
          label: resource.value === 'anomalies' ? '发现时间' : '计划开始',
          minWidth: 165,
          formatter: (v: unknown) => formatDateTime(String(v || '')),
        },
      ],
);
let revision = 0;
const preferenceLoads = new Map<string, Promise<void>>();
function preparePreferences(key: string) {
  if (!preferenceLoads.has(key))
    preferenceLoads.set(
      key,
      request<{ data: Row }>('/home/page-preferences/' + key)
        .then(({ data: p }) => {
          if (resource.value !== key) return;
          query.value = search.value = typeof p.query === 'string' ? p.query : '';
          conditions.value = Array.isArray(p.conditions) ? p.conditions : [];
          const legacy: Record<string, string> =
            p.columnFilters && typeof p.columnFilters === 'object' ? p.columnFilters : {};
          const appliedFields = new Set(conditions.value.map((c) => c.field));
          conditions.value.push(
            ...Object.entries(legacy)
              .filter(([field, value]) => value && !appliedFields.has(field))
              .map(([field, value]) => ({ field, operator: 'contains' as const, values: [value] })),
          );
          hidden.value = Array.isArray(p.hiddenColumns) ? p.hiddenColumns : [];
          page.value = Math.max(1, Number(p.page) || 1);
          size.value = normalizePageSize(p.size);
          personal.value = p.personal !== false;
        })
        .catch(() => {}),
    );
  return preferenceLoads.get(key)!;
}
let preferenceTimer: ReturnType<typeof setTimeout> | undefined;
watch(
  () =>
    JSON.stringify({
      query: query.value,
      conditions: conditions.value,
      hiddenColumns: hidden.value,
      page: page.value,
      size: size.value,
      personal: personal.value,
    }),
  (value) => {
    if (route.params.id || !preferenceLoads.has(resource.value)) return;
    clearTimeout(preferenceTimer);
    const key = resource.value;
    preferenceTimer = setTimeout(() => {
      void request('/home/page-preferences/' + key, { method: 'PUT', body: value }).catch(() => {});
    }, 350);
  },
);
async function load() {
  const seq = ++revision;
  error.value = '';
  loading.value = true;
  try {
    if (route.params.id && route.params.id !== 'new') {
      const r = await request<{ data: Row }>('/' + resource.value + '/' + route.params.id);
      if (seq !== revision) return;
      detail.value = r.data;
      form.value = {
        ...r.data,
        equipmentId: r.data.equipment_id,
        ownerId: r.data.owner_id,
        plannedAt: formatDateTime(r.data.planned_at).replace(' ', 'T'),
        faultTypeId: r.data.fault_type_id || r.data.faultTypeId || '',
        severity: r.data.severity,
        anomalyIds: r.data.anomalyIds || [],
        rowVersion: r.data.rowVersion,
      };
    } else if (route.params.id === 'new') {
      detail.value = undefined;
      form.value = {
        equipmentId: '',
        equipmentCode: '',
        equipmentName: '',
        description: '',
        faultTypeId: '',
        severity: '',
        attachments: [],
        plannedAt: '',
        ownerId: '',
        anomalyIds: [],
      };
      if (route.query.anomalyId) {
        const a = (await request<{ data: Row }>('/anomalies/' + route.query.anomalyId)).data;
        form.value = {
          ...form.value,
          equipmentId: a.equipment_id,
          equipmentCode: a.equipmentCode,
          equipmentName: a.equipmentName,
          description: a.description,
          anomalyIds: [String(a.id)],
        };
      }
    } else {
      detail.value = undefined;
      await preparePreferences(resource.value);
      if (seq !== revision) return;
      const r = await request<{ data: Row[]; total: number }>(
        '/' +
          resource.value +
          '?' +
          new URLSearchParams({
            q: query.value,
            page: String(page.value),
            size: String(size.value),
            personal: String(personal.value),
            filters: JSON.stringify(effectiveFilters()),
          }),
      );
      if (seq !== revision) return;
      rows.value = r.data;
      total.value = r.total;
    }
    markSaved();
  } catch (e) {
    if (seq === revision) error.value = e instanceof Error ? e.message : tr('ui.equipment.text0160');
  } finally {
    if (seq === revision) loading.value = false;
  }
}
onMounted(() => base.load('故障类型'));
watch(() => [route.fullPath, page.value, size.value, personal.value], load, { immediate: true });
async function back() {
  if (await permitLeave()) router.push('/' + resource.value);
}
function chooseEquipment(e: EquipmentRecord) {
  equipmentOpen.value = false;
  form.value.equipmentId = e.id;
  form.value.equipmentCode = e.code;
  form.value.equipmentName = e.name;
}
function chooseUser(ids: string[]) {
  if (ids.length !== 1) {
    ElMessage.warning(tr('ui.equipment.text0192'));
    return;
  }
  form.value.ownerId = ids[0];
  userOpen.value = false;
}
async function save() {
  if (!form.value.equipmentId || !form.value.description?.trim()) {
    ElMessage.warning(tr('ui.equipment.text0193'));
    return;
  }
  busy.value = true;
  try {
    const r = await request<{ data: Row }>(
      '/' + resource.value + (detail.value ? '/' + detail.value.id : ''),
      { method: detail.value ? 'PUT' : 'POST', body: JSON.stringify(form.value) },
    );
    markSaved();
    await router.replace('/' + resource.value + '/' + r.data.id);
    ElMessage.success(tr('ui.equipment.text0194'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0195'));
  } finally {
    busy.value = false;
  }
}
async function action(name: string) {
  dialog.value = name;
  reason.value = '';
  evidence.value = [];
  faultTypeId.value = '';
  severity.value = '';
  linkId.value = '';
  if (name === 'report') await base.load('故障类型');
}
async function submitAction() {
  if (!detail.value) return;
  busy.value = true;
  try {
    if (resource.value === 'material-custody') {
      await request(
        '/material-custody/' +
          detail.value.id +
          (dialog.value === 'return-request' ? '/return-request' : ''),
        {
          method: dialog.value === 'return-request' ? 'POST' : 'PUT',
          body: JSON.stringify({
            rowVersion: detail.value.rowVersion,
            remark: reason.value,
            quantity: returnQuantity.value,
          }),
        },
      );
    } else {
      const r = await request<{ data: Row }>(
        '/' + resource.value + '/' + detail.value.id + '/actions',
        {
          method: 'POST',
          body: JSON.stringify({
            action: dialog.value,
            rowVersion: detail.value.rowVersion,
            reason: reason.value,
            faultTypeId: faultTypeId.value,
            severity: severity.value,
            repairId: linkId.value,
            attachments: evidence.value,
          }),
        },
      );
      detail.value = r.data;
    }
    dialog.value = '';
    ElMessage.success(tr('ui.equipment.text0196'));
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0197'));
  } finally {
    busy.value = false;
  }
}
async function backfill() {
  busy.value = true;
  try {
    await request('/anomalies/backfill', { method: 'POST', body: '{}' });
    await load();
    ElMessage.success(tr('ui.equipment.text0198'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0199'));
  } finally {
    busy.value = false;
  }
}
function canAction(action: string) {
  return (
    auth.can(menu.value, action) &&
    (resource.value !== 'anomalies' || (detail.value?.actions || []).includes(action))
  );
}
function openCustody(r: Row) {
  detail.value = r;
  dialog.value = 'custody';
  reason.value = r.remark || '';
}
function statusClass(status: string) {
  return status === '已消除'
    ? 'success'
    : status === '待处理'
      ? 'warning'
      : status === '已作废' || status === '已取消'
        ? 'neutral'
        : 'progress';
}
const { filterAnchor, editingFilter, showFilter, replaceFilters, confirmFilter } =
  useConditionPopover(
    filtersOpen,
    () => conditions.value,
    (value) => {
      applyFilters(value);
    },
  );
</script>
<template>
  <section
    class="content experience-page"
    :class="{ 'anomaly-list-page': resource === 'anomalies' && !route.params.id }"
  >
    <template v-if="!route.params.id"
      ><AppListToolbar class="experience-toolbar">
        <div>
          <ElButton
            v-if="resource !== 'material-custody' && auth.can(menu, '新增')"
            class=""
            @click="router.push('/' + resource + '/new')"
            type="primary"
            ><AppIcon name="Plus" />{{ tr('ui.equipment.text0149') }}</ElButton
          >

          <ElSelect
            v-if="resource === 'material-custody' && auth.can(menu, '查看')"
            v-model="personal"
            ><ElOption :value="true" :label="tr('ui.equipment.text0200')" /><ElOption
              :value="false"
              :label="tr('ui.equipment.text0201')"
          /></ElSelect>
        </div>
        <form @submit.prevent="searchList">
          <ListSearchInput v-model="search" :placeholder="tr('ui.equipment.text0202')" /><ElButton
            class=""
            @click="showFilter($event.currentTarget as HTMLElement)"
            native-type="button"
          >
            {{ tr('ui.equipment.text0203') }}</ElButton
          ><ElButton
            class=""
            @click="
              search = '';
              query = '';
              conditions = [];
              page = 1;
              load();
            "
            native-type="button"
          >
            {{ tr('ui.equipment.text0204') }}
          </ElButton>
        </form>
      </AppListToolbar>
      <AppliedFilterChips
        editable
        @edit="(condition, anchor) => showFilter(anchor, condition.field)"
        :conditions="effectiveFilters()"
        :fields="fields"
        @change="replaceFilters"
      />

      <AppBusinessTable
        v-model:page="page"
        v-model:page-size="size"
        :rows="rows"
        :columns="shownColumns"
        :total="total"
        :error="error"
        :loading="loading"
        @retry="load"
        ><template #footer-actions
          ><ListToolbarIcons
            mode="footer"
            :filter-open="false"
            :columns-open="columnOpen"
            :can-export="auth.can(menu, '导出')"
            :export-busy="exportBusy"
            @toggle-columns="columnOpen = !columnOpen"
            @export="exportRows"
            ><template #columns
              ><ElCheckbox
                v-for="c in columns"
                :key="c.prop"
                class="column-choice"
                :model-value="!hidden.includes(c.prop)"
                @change="
                  hidden = hidden.includes(c.prop)
                    ? hidden.filter((k) => k !== c.prop)
                    : [...hidden, c.prop]
                "
                >{{ c.label }}</ElCheckbox
              ></template
            ></ListToolbarIcons
          ></template
        ><template #code="{ row }"
          ><ElButton
            class="text-action"
            @click="router.push('/' + resource + '/' + row.id)"
            type="primary"
            link
            >{{ row.code }}</ElButton
          ></template
        ><template #status="{ row }"
          ><span class="experience-status" :class="statusClass(row.status)">{{
            enumLabel('status',row.status)
          }}</span></template
        ><template #actions="{ row }"
          ><template v-if="resource === 'material-custody'"
            ><ElButton class="text-action" @click="openCustody(row)" type="primary" link
              >{{ tr('ui.equipment.text0190') }}</ElButton
            ><ElButton
              v-if="String(row.holderId) === String(auth.user?.id)"
              class="text-action"
              @click="
                detail = row;
                reason = '';
                returnQuantity = String(row.remaining);
                dialog = 'return-request';
              "
              type="primary"
              link
            >
              {{ tr('ui.equipment.text0205') }}
            </ElButton></template
          ><template v-else
            ><ElButton
              class="text-action"
              @click="router.push('/' + resource + '/' + row.id)"
              type="primary"
              link
            >
              {{ tr('ui.equipment.text0206') }}</ElButton
            ><ElButton
              v-if="
                resource === 'repair-plans' &&
                auth.can(menu, '编辑') &&
                ['草稿', '待生成'].includes(row.status)
              "
              class="text-action"
              @click="router.push('/' + resource + '/' + row.id + '?edit=1')"
              type="primary"
              link
            >
              {{ tr('ui.equipment.text0207') }}
            </ElButton></template
          ></template
        ></AppBusinessTable
      >
    </template>
    <template v-else
      ><header class="experience-heading">
        <AppBackButton
          :label="tr('ui.equipment.text0208') + (resource === 'anomalies' ? tr('ui.equipment.text0005') : tr('ui.equipment.text0209'))"
          @click="back"
        />
        <div>
          <h1>
            {{
              editing
                ? (detail ? tr('ui.equipment.text0207') : tr('ui.equipment.text0149')) + (resource === 'anomalies' ? tr('ui.equipment.text0210') : tr('ui.equipment.text0209'))
                : detail?.code
            }}
          </h1>
          <p>
            {{ detail?.equipmentCode || form.equipmentCode }} ·
            {{ detail?.equipmentName || form.equipmentName }}
          </p>
        </div>
        <span v-if="detail" class="experience-status" :class="statusClass(detail.status)">{{
          detail.status
        }}</span>
        <div class="heading-actions" v-if="detail && !editing">
          <template
            v-if="
              resource === 'anomalies' && ['待处理', '已安排', '处理中'].includes(detail.status)
            "
            ><ElButton
              v-if="canAction('消除') && !detail.repair_id && !detail.plan_id"
              class=""
              @click="action('resolve')"
            >
              {{ tr('ui.equipment.text0211') }}</ElButton
            ><ElButton
              v-if="canAction('报修') && !detail.repair_id && !detail.plan_id"
              class=""
              @click="action('report')"
              type="primary"
            >
              {{ tr('ui.equipment.text0212') }}</ElButton
            ><ElButton
              v-if="canAction('报修') && !detail.repair_id && !detail.plan_id"
              class=""
              @click="repairOpen = true"
            >
              {{ tr('ui.equipment.text0213') }}</ElButton
            ><ElButton
              v-if="canAction('安排维修') && !detail.repair_id && !detail.plan_id"
              class=""
              @click="router.push('/repair-plans/new?anomalyId=' + detail.id)"
            >
              {{ tr('ui.equipment.text0214') }}</ElButton
            ><ElButton
              v-if="canAction('作废') && !detail.repair_id && !detail.plan_id"
              class=""
              @click="action('void')"
            >
              {{ tr('ui.equipment.text0215') }}
            </ElButton></template
          ><template
            v-if="resource === 'repair-plans' && ['草稿', '待生成'].includes(detail.status)"
            ><ElButton
              v-if="canAction('启用') && detail.status === '草稿'"
              class=""
              @click="action('enable')"
              type="primary"
            >
              {{ tr('ui.equipment.text0216') }}</ElButton
            ><ElButton v-if="canAction('生成工单')" class="" @click="action('generate')">
              {{ tr('ui.equipment.text0217') }}</ElButton
            ><ElButton v-if="canAction('取消')" class="" @click="action('cancel')">
              {{ tr('ui.equipment.text0218') }}
            </ElButton></template
          >
        </div>
      </header>
      <p v-if="error" class="experience-error" role="alert">{{ error }}</p>
      <div v-else class="experience-detail-layout">
        <main>
          <form v-if="editing" class="experience-form" @submit.prevent="save">
            <h3>{{ tr('ui.equipment.text0077') }}</h3>
            <label
              ><span>{{ tr('ui.equipment.text0219') }}</span
              ><DialogChoiceInput
                :model-value="
                  form.equipmentCode ? form.equipmentCode + ' · ' + form.equipmentName : ''
                "
                :placeholder="tr('ui.equipment.text0220')"
                :title="tr('ui.equipment.text0220')"
                @open="equipmentOpen = true" /></label
            ><label v-if="resource === 'anomalies'"
              ><span>{{ tr('ui.equipment.text0221') }}</span
              ><ElSelect v-model="form.severity" clearable
                ><ElOption
                  v-for="s in ['一般', '紧急', '重大']"
                  :key="s"
                  :value="s"
                  :label="enumLabel('systemOption', s)" /></ElSelect></label
            ><label v-if="resource === 'anomalies'" class="full"
              ><span>{{ tr('ui.equipment.text0222') }}</span
              ><MaintenanceAttachments
                v-model="form.attachments"
                :upload-file="uploadEvidence" /></label
            ><label class="full"
              ><span>{{ tr('ui.equipment.text0223') }}</span
              ><ElInput
                v-model="form.description"
                type="textarea"
                :rows="4"
                maxlength="4000" /></label
            ><template v-if="resource === 'repair-plans'"
              ><label
                ><span>{{ tr('ui.equipment.text0224') }}</span
                ><ElDatePicker
                  v-model="form.plannedAt"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  :placeholder="tr('ui.equipment.text0225')" /></label
              ><label
                ><span>{{ tr('ui.equipment.text0226') }}</span
                ><DialogChoiceInput
                  :model-value="form.ownerId ? '已选负责人：' + form.ownerId : ''"
                  :placeholder="tr('ui.equipment.text0227')"
                  :title="tr('ui.equipment.text0227')"
                  @open="userOpen = true" /></label
              ><label
                ><span>{{ tr('ui.equipment.text0228') }}</span
                ><TreeSelect
                  v-model="form.faultTypeId"
                  :nodes="base.faultTypeData.map((x) => ({ ...x, value: x.id }))" /></label
              ><label
                ><span>{{ tr('ui.equipment.text0229') }}</span
                ><ElSelect v-model="form.severity"
                  ><ElOption
                    v-for="s in ['一般', '紧急', '重大']"
                    :key="s"
                    :value="s"
                    :label="enumLabel('systemOption', s)" /></ElSelect
              ></label>
              <p v-if="form.anomalyIds?.length" class="full">
                {{ tr('ui.equipment.text0230') }} {{ form.anomalyIds.length }} {{ tr('ui.equipment.text0231') }}
              </p></template
            >
          </form>
          <section v-else-if="detail" class="experience-summary">
            <h3>{{ tr('ui.equipment.text0232') }}</h3>
            <p class="description">{{ detail.description }}</p>
            <dl>
              <div v-if="detail.source_kind">
                <dt>{{ tr('ui.equipment.text0233') }}</dt>
                <dd>{{ detail.source_kind }}</dd>
              </div>
              <div v-if="detail.discovered_name">
                <dt>{{ tr('ui.equipment.text0234') }}</dt>
                <dd>{{ detail.discovered_name }}</dd>
              </div>
              <div v-if="detail.planned_at">
                <dt>{{ tr('ui.equipment.text0235') }}</dt>
                <dd>{{ formatDateTime(detail.planned_at) }}</dd>
              </div>
              <div>
                <dt>{{ tr('ui.equipment.text0221') }}</dt>
                <dd>{{ detail.severity }}</dd>
              </div>
              <div v-if="detail.resolution">
                <dt>{{ tr('ui.equipment.text0236') }}</dt>
                <dd>{{ detail.resolution }}</dd>
              </div>
            </dl>
            <div class="related-links">
              <ElButton
                v-if="detail.repair_id"
                class=""
                @click="router.push('/repairs/' + detail.repair_id)"
              >
                {{ tr('ui.equipment.text0237') }}</ElButton
              ><ElButton
                v-if="detail.plan_id"
                class=""
                @click="router.push('/repair-plans/' + detail.plan_id)"
              >
                {{ tr('ui.equipment.text0238') }}
              </ElButton>
            </div>
            <p v-if="detail.generation_error" class="experience-error">
              {{ detail.generation_error }}
            </p>
            <div v-if="detail.attachments?.length" class="anomaly-file-list">
              <h3>{{ tr('ui.equipment.text0239') }}</h3>
              <AppAttachmentList :files="detail.attachments || []" />
            </div>
          </section>
        </main>
        <aside v-if="detail && !editing" class="experience-timeline">
          <h3>{{ tr('ui.equipment.text0240') }}</h3>
          <ol>
            <li v-for="(event, index) in detail.events" :key="index">
              <strong>{{ event.action }}</strong>
              <p>{{ event.actor }} · {{ formatDateTime(event.time) }}</p>
              <p v-for="(value, key) in event.details" :key="key">{{ key }}：{{ value }}</p>
            </li>
          </ol>
        </aside>
      </div>
      <AppFormFooter v-if="editing"
        ><ElButton class="" :disabled="busy" @click="back">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton class="" :disabled="busy" @click="save" type="primary"
          >{{ tr('ui.equipment.text0159') }}</ElButton
        ></AppFormFooter
      >
    </template>
    <FilterConditionPopover
      :anchor="filterAnchor"
      :field-key="editingFilter"
      :placement="editingFilter ? 'bottom-start' : 'bottom-end'"
      v-model="filtersOpen"
      :fields="fields"
      :conditions="conditions"
      @confirm="confirmFilter"
    /><RepairOrderPicker
      v-model="repairOpen"
      :equipment-code="detail?.equipmentCode || ''"
      @select="linkRepair"
    /><EquipmentPickerDialog v-model="equipmentOpen" @select="chooseEquipment" /><RepairUserPicker
      v-model="userOpen"
      :title="tr('ui.equipment.text0241')"
      :load-users="ownerChoices"
      :selected="form.ownerId ? [form.ownerId] : []"
      @confirm="chooseUser"
    />
    <AppDialog
      :model-value="!!dialog && dialog !== 'link'"
      :title="
        (
          {
            resolve: tr('ui.equipment.text0242'),
            report: tr('ui.equipment.text0243'),
            void: tr('ui.equipment.text0244'),
            enable: tr('ui.equipment.text0245'),
            generate: tr('ui.equipment.text0246'),
            cancel: tr('ui.equipment.text0247'),
            custody: tr('ui.equipment.text0248'),
            'return-request': tr('ui.equipment.text0205'),
          } as Record<string, string>
        )[dialog] || tr('ui.equipment.text0249')
      "
      :busy="busy"
      @update:model-value="
        (value) => {
          if (!value) dialog = '';
        }
      "
      ><div class="experience-action-form">
        <label v-if="dialog === 'return-request'"
          >{{ tr('ui.equipment.text0250') }}<ElInput v-model="returnQuantity" inputmode="decimal"
        /></label>
        <template v-if="dialog === 'report'"
          ><label
            >{{ tr('ui.equipment.text0228') }}<TreeSelect
              v-model="faultTypeId"
              :nodes="base.faultTypeData.map((x) => ({ ...x, value: x.id }))" /></label
          ><label
            >{{ tr('ui.equipment.text0229') }}<ElSelect v-model="severity" :placeholder="tr('ui.equipment.text0096')"
              ><ElOption
                v-for="s in ['一般', '紧急', '重大']"
                :key="s"
                :value="s"
                :label="enumLabel('systemOption', s)" /></ElSelect></label></template
        ><label v-else-if="!['enable', 'generate'].includes(dialog)"
          >{{ dialog === 'resolve' ? tr('ui.equipment.text0236') : tr('ui.equipment.text0177') }} *<ElInput
            v-model="reason"
            type="textarea"
            :rows="4"
        /></label>
        <MaintenanceAttachments
          v-if="dialog === 'resolve'"
          v-model="evidence"
          :upload-file="uploadEvidence"
        />
        <p v-if="['enable', 'generate'].includes(dialog)">
          {{
            dialog === 'generate'
              ? tr('ui.equipment.text0251')
              : tr('ui.equipment.text0252')
          }}
        </p>
      </div>
      <template #footer
        ><ElButton class="" :disabled="busy" @click="dialog = ''">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton class="" :disabled="busy" @click="submitAction" type="primary"
          >{{ tr('ui.equipment.text0253') }}</ElButton
        ></template
      ></AppDialog
    >
  </section>
</template>
<style scoped>
.experience-page {
  display: flex;
  flex-direction: column;
  /* padding: 0px 8px; */
  /* gap: 12px; */
  height: 100%;
  min-height: 0;
  background: var(--canvas);
}
.experience-page.anomaly-list-page {
  gap: 0;
}
.experience-toolbar > div {
  display: flex;
  align-items: center;
  gap: 8px;
}
.experience-toolbar .el-select {
  width: 170px;
}
.experience-toolbar .list-search-input {
  width: 280px;
}
.text-action {
  border: 0;
  background: transparent;
  color: var(--brand);
  cursor: pointer;
  padding: 4px;
}
.experience-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.experience-heading h1 {
  font-size: 18px;
  margin: 0;
}
.experience-heading p {
  margin: 5px 0 0;
}
.heading-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.experience-detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  overflow: auto;
  min-height: 0;
  flex: 1;
}
.experience-detail-layout > main {
  min-width: 0;
}
.experience-form,
.experience-summary,
.experience-timeline {
  padding: 20px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  align-self: start;
}
.experience-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
}
.experience-form h3,
.full {
  grid-column: 1/-1;
}
h3 {
  font-size: 14px;
  margin: 0 0 12px;
}
.experience-form label {
  display: grid;
  gap: 8px;
}
.experience-form :deep(.el-date-editor) {
  width: 100%;
}
.description {
  white-space: pre-wrap;
  line-height: 1.8;
}
.experience-summary dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.experience-summary dt {
  color: var(--text-secondary);
}
.experience-summary dd {
  margin: 8px 0 0;
}
.related-links {
  display: flex;
  gap: 8px;
}
.experience-timeline ol {
  list-style: none;
  padding: 0 0 0 14px;
}
.experience-timeline li {
  border-left: 2px solid var(--line);
  padding: 0 0 18px 14px;
  position: relative;
}
.experience-timeline li:before {
  content: '';
  position: absolute;
  left: -5px;
  top: 3px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--brand);
}
.experience-timeline p {
  font-size: 13px;
  overflow-wrap: anywhere;
}
.experience-error {
  color: var(--danger);
}
.experience-action-form {
  display: grid;
  gap: 16px;
}
.experience-action-form label {
  display: grid;
  gap: 8px;
}
.experience-status {
  display: inline-flex;
  padding: 4px 9px;
  border-radius: 4px;
  white-space: nowrap;
}
.success {
  background: var(--success-bg);
  color: var(--success);
}
.warning {
  background: var(--warning-bg);
  color: var(--warning);
}
.progress {
  background: var(--brand-soft);
  color: var(--brand);
}
.neutral {
  background: var(--canvas);
  color: var(--text-secondary);
}
@media (max-width: 1300px) {
  .experience-detail-layout {
    grid-template-columns: minmax(0, 1fr) 250px;
  }
  .experience-heading {
    flex-wrap: wrap;
  }
  .heading-actions {
    width: 100%;
    margin-left: 44px;
  }
}
.anomaly-file-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.anomaly-file-list h3 {
  width: 100%;
}
.column-choice {
  display: flex;
  gap: 8px;
  padding: 6px;
}
</style>
