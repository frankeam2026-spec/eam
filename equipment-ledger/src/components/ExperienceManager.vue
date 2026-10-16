<script setup lang="ts">
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
  size = ref(10),
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
  faultType = ref(''),
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
    body,
  });
  const payload = await res.json();
  if (!res.ok) throw new Error(payload.message || '上传失败');
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
    });
    if (!r.ok) {
      const e = await r.json();
      throw new Error(e.message || '导出失败');
    }
    const url = URL.createObjectURL(await r.blob());
    const a = document.createElement('a');
    a.href = url;
    a.download = resource.value + '.csv';
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导出失败');
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
        { prop: 'materialCode', label: '料号', minWidth: 150 },
        { prop: 'materialName', label: '物料名称', minWidth: 160 },
        { prop: 'specification', label: '规格型号', minWidth: 160 },
        {
          prop: 'remaining',
          label: '剩余未用',
          minWidth: 115,
          formatter: (v: unknown) => formatNumber(v),
        },
        { prop: 'holderName', label: '保管人', minWidth: 110 },
        { prop: 'warehouse', label: '来源仓库', minWidth: 140 },
        { prop: 'taskCode', label: '来源任务', minWidth: 160 },
        { prop: 'remark', label: '暂存说明', minWidth: 180 },
      ]
    : [
        {
          prop: 'code',
          label: resource.value === 'anomalies' ? '异常编号' : '计划编号',
          minWidth: 170,
        },
        { prop: 'equipmentName', label: '设备名称', minWidth: 150 },
        { prop: 'description', label: '问题／工作说明', minWidth: 260 },
        { prop: 'status', label: '状态', minWidth: 110 },
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
          size.value = [10, 20, 50, 100].includes(Number(p.size)) ? Number(p.size) : 10;
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
        faultType: r.data.fault_type,
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
        faultType: '',
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
    if (seq === revision) error.value = e instanceof Error ? e.message : '加载失败';
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
    ElMessage.warning('请选择一名计划负责人');
    return;
  }
  form.value.ownerId = ids[0];
  userOpen.value = false;
}
async function save() {
  if (!form.value.equipmentId || !form.value.description?.trim()) {
    ElMessage.warning('请选择设备并填写说明');
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
    ElMessage.success('已保存');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败');
  } finally {
    busy.value = false;
  }
}
async function action(name: string) {
  dialog.value = name;
  reason.value = '';
  evidence.value = [];
  faultType.value = '';
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
            faultType: faultType.value,
            severity: severity.value,
            repairId: linkId.value,
            attachments: evidence.value,
          }),
        },
      );
      detail.value = r.data;
    }
    dialog.value = '';
    ElMessage.success('操作成功');
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败');
  } finally {
    busy.value = false;
  }
}
async function backfill() {
  busy.value = true;
  try {
    await request('/anomalies/backfill', { method: 'POST', body: '{}' });
    await load();
    ElMessage.success('已同步可确认的历史异常');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '同步失败');
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
    class="experience-page"
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
            ><AppIcon name="Plus" />新增</ElButton
          >

          <ElSelect
            v-if="resource === 'material-custody' && auth.can(menu, '查看')"
            v-model="personal"
            ><ElOption :value="true" label="我的余料" /><ElOption
              :value="false"
              label="全部授权余料"
          /></ElSelect>
        </div>
        <form @submit.prevent="searchList">
          <ListSearchInput v-model="search" placeholder="搜索编号、名称、说明" /><ElButton
            class=""
            @click="showFilter($event.currentTarget as HTMLElement)"
            native-type="button"
          >
            添加筛选</ElButton
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
            重置
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
            row.status
          }}</span></template
        ><template #actions="{ row }"
          ><template v-if="resource === 'material-custody'"
            ><ElButton class="text-action" @click="openCustody(row)" type="primary" link
              >暂存说明</ElButton
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
              申请退料
            </ElButton></template
          ><template v-else
            ><ElButton
              class="text-action"
              @click="router.push('/' + resource + '/' + row.id)"
              type="primary"
              link
            >
              查看</ElButton
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
              编辑
            </ElButton></template
          ></template
        ></AppBusinessTable
      >
    </template>
    <template v-else
      ><header class="experience-heading">
        <AppBackButton
          :label="'返回' + (resource === 'anomalies' ? '异常库' : '维修计划')"
          @click="back"
        />
        <div>
          <h1>
            {{
              editing
                ? (detail ? '编辑' : '新增') + (resource === 'anomalies' ? '异常' : '维修计划')
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
              直接消除</ElButton
            ><ElButton
              v-if="canAction('报修') && !detail.repair_id && !detail.plan_id"
              class=""
              @click="action('report')"
              type="primary"
            >
              报修</ElButton
            ><ElButton
              v-if="canAction('报修') && !detail.repair_id && !detail.plan_id"
              class=""
              @click="repairOpen = true"
            >
              关联工单</ElButton
            ><ElButton
              v-if="canAction('安排维修') && !detail.repair_id && !detail.plan_id"
              class=""
              @click="router.push('/repair-plans/new?anomalyId=' + detail.id)"
            >
              安排维修</ElButton
            ><ElButton
              v-if="canAction('作废') && !detail.repair_id && !detail.plan_id"
              class=""
              @click="action('void')"
            >
              作废
            </ElButton></template
          ><template
            v-if="resource === 'repair-plans' && ['草稿', '待生成'].includes(detail.status)"
            ><ElButton
              v-if="canAction('启用') && detail.status === '草稿'"
              class=""
              @click="action('enable')"
              type="primary"
            >
              启用</ElButton
            ><ElButton v-if="canAction('生成工单')" class="" @click="action('generate')">
              生成工单</ElButton
            ><ElButton v-if="canAction('取消')" class="" @click="action('cancel')">
              取消计划
            </ElButton></template
          >
        </div>
      </header>
      <p v-if="error" class="experience-error" role="alert">{{ error }}</p>
      <div v-else class="experience-detail-layout">
        <main>
          <form v-if="editing" class="experience-form" @submit.prevent="save">
            <h3>基本信息</h3>
            <label
              ><span>设备 *</span
              ><ElButton class="object-input" @click="equipmentOpen = true" native-type="button"
                >{{
                  form.equipmentCode ? form.equipmentCode + ' · ' + form.equipmentName : '选择设备'
                }}<AppIcon name="Search" /></ElButton></label
            ><label v-if="resource === 'anomalies'"
              ><span>故障等级</span
              ><ElSelect v-model="form.severity" clearable
                ><ElOption
                  v-for="s in ['一般', '紧急', '重大']"
                  :key="s"
                  :value="s"
                  :label="s" /></ElSelect></label
            ><label v-if="resource === 'anomalies'" class="full"
              ><span>附件</span
              ><MaintenanceAttachments
                v-model="form.attachments"
                :upload-file="uploadEvidence" /></label
            ><label class="full"
              ><span>说明 *</span
              ><ElInput
                v-model="form.description"
                type="textarea"
                :rows="4"
                maxlength="4000" /></label
            ><template v-if="resource === 'repair-plans'"
              ><label
                ><span>计划开始 *</span
                ><ElDatePicker
                  v-model="form.plannedAt"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="选择时间" /></label
              ><label
                ><span>计划负责人 *</span
                ><ElButton class="object-input" @click="userOpen = true" native-type="button"
                  >{{ form.ownerId ? '已选负责人：' + form.ownerId : '选择负责人'
                  }}<AppIcon name="Search" /></ElButton></label
              ><label
                ><span>故障类型 *</span
                ><TreeSelect
                  v-model="form.faultType"
                  :nodes="base.faultTypeData.map((x) => ({ ...x, value: x.name }))" /></label
              ><label
                ><span>故障等级 *</span
                ><ElSelect v-model="form.severity"
                  ><ElOption
                    v-for="s in ['一般', '紧急', '重大']"
                    :key="s"
                    :value="s"
                    :label="s" /></ElSelect
              ></label>
              <p v-if="form.anomalyIds?.length" class="full">
                关联 {{ form.anomalyIds.length }} 项异常；启用后到期自动生成维修工单。
              </p></template
            >
          </form>
          <section v-else-if="detail" class="experience-summary">
            <h3>问题与处理</h3>
            <p class="description">{{ detail.description }}</p>
            <dl>
              <div v-if="detail.source_kind">
                <dt>发现来源</dt>
                <dd>{{ detail.source_kind }}</dd>
              </div>
              <div v-if="detail.discovered_name">
                <dt>发现人</dt>
                <dd>{{ detail.discovered_name }}</dd>
              </div>
              <div v-if="detail.planned_at">
                <dt>计划开始</dt>
                <dd>{{ formatDateTime(detail.planned_at) }}</dd>
              </div>
              <div>
                <dt>故障等级</dt>
                <dd>{{ detail.severity }}</dd>
              </div>
              <div v-if="detail.resolution">
                <dt>消除措施</dt>
                <dd>{{ detail.resolution }}</dd>
              </div>
            </dl>
            <div class="related-links">
              <ElButton
                v-if="detail.repair_id"
                class=""
                @click="router.push('/repairs/' + detail.repair_id)"
              >
                查看关联维修工单</ElButton
              ><ElButton
                v-if="detail.plan_id"
                class=""
                @click="router.push('/repair-plans/' + detail.plan_id)"
              >
                查看维修计划
              </ElButton>
            </div>
            <p v-if="detail.generation_error" class="experience-error">
              {{ detail.generation_error }}
            </p>
            <div v-if="detail.attachments?.length" class="anomaly-file-list">
              <h3>来源附件</h3>
              <AppAttachmentList :files="detail.attachments || []" />
            </div>
          </section>
        </main>
        <aside v-if="detail && !editing" class="experience-timeline">
          <h3>处理时间轴</h3>
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
        ><ElButton class="" :disabled="busy" @click="back">取消</ElButton
        ><ElButton class="" :disabled="busy" @click="save" type="primary"
          >保存</ElButton
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
      title="选择计划负责人（单人）"
      :load-users="ownerChoices"
      :selected="form.ownerId ? [form.ownerId] : []"
      @confirm="chooseUser"
    />
    <AppDialog
      :model-value="!!dialog && dialog !== 'link'"
      :title="
        (
          {
            resolve: '直接消除异常',
            report: '异常报修',
            void: '作废异常',
            enable: '启用维修计划',
            generate: '提前生成维修工单',
            cancel: '取消维修计划',
            custody: '余料暂存说明',
            'return-request': '申请退料',
          } as Record<string, string>
        )[dialog] || '确认操作'
      "
      :busy="busy"
      @update:model-value="
        (value) => {
          if (!value) dialog = '';
        }
      "
      ><div class="experience-action-form">
        <label v-if="dialog === 'return-request'"
          >退料数量 *<ElInput v-model="returnQuantity" inputmode="decimal"
        /></label>
        <template v-if="dialog === 'report'"
          ><label
            >故障类型 *<TreeSelect
              v-model="faultType"
              :nodes="base.faultTypeData.map((x) => ({ ...x, value: x.name }))" /></label
          ><label
            >故障等级 *<ElSelect v-model="severity" placeholder="请选择"
              ><ElOption
                v-for="s in ['一般', '紧急', '重大']"
                :key="s"
                :value="s"
                :label="s" /></ElSelect></label></template
        ><label v-else-if="!['enable', 'generate'].includes(dialog)"
          >{{ dialog === 'resolve' ? '消除措施' : '说明' }} *<ElInput
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
              ? '本次立即生成一张维修工单，继续按派工、接单、验收流程处理。'
              : '启用后到达计划时间自动生成维修工单。'
          }}
        </p>
      </div>
      <template #footer
        ><ElButton class="" :disabled="busy" @click="dialog = ''">取消</ElButton
        ><ElButton class="" :disabled="busy" @click="submitAction" type="primary"
          >提交</ElButton
        ></template
      ></AppDialog
    >
  </section>
</template>
<style scoped>
.experience-page {
  display: flex;
  flex-direction: column;
  padding: 0px 8px;
  gap: 12px;
  height: calc(100vh - 56px);
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
.object-input {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface);
  min-height: 34px;
  padding: 6px 10px;
  text-align: left;
  cursor: pointer;
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
