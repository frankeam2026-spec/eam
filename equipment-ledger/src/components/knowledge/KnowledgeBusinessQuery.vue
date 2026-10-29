<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { ElAlert, ElButton, ElProgress, ElTableColumn } from 'element-plus';
import { useRouter } from 'vue-router';
import { request } from '../../api';
import { tr } from '../../i18n';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import AppPagination from '../common/AppPagination.vue';
import type { BusinessPlan, BusinessQuery } from './businessQueries';
const props = defineProps<{ result: BusinessQuery }>();
const emit = defineEmits<{ navigate: [] }>();
const router = useRouter();
const data = ref(props.result),
  page = ref(1),
  size = ref(20),
  busy = ref(false),
  error = ref(''),
  detail = ref(false);
const activePlan = ref(props.result.plan);
let revision = 0;
onBeforeUnmount(() => revision++);
watch(
  () => props.result,
  (value) => {
    revision++;
    data.value = value;
    activePlan.value = value.plan;
    page.value = 1;
    busy.value = false;
    error.value = '';
    detail.value = false;
  },
);
async function load(plan: BusinessPlan, targetPage = 1) {
  const current = ++revision;
  busy.value = true;
  error.value = '';
  try {
    const result = await request<BusinessQuery>('/knowledge/assistant/business-query', {
      method: 'POST',
      body: JSON.stringify({
        plan,
        equipmentId: props.result.equipmentId || null,
        page: targetPage,
        size: size.value,
      }),
    });
    if (revision === current) data.value = result;
  } catch (e) {
    if (revision === current) {
      data.value = { ...data.value, data: [], total: 0 };
      error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
    }
  } finally {
    if (revision === current) busy.value = false;
  }
}
watch([page, size], () => void load(activePlan.value, page.value));
async function drill(row: Record<string, unknown>) {
  const plan = JSON.parse(JSON.stringify(props.result.plan)) as BusinessPlan,
    conditions: unknown[] = [];
  if (plan.where) conditions.push(plan.where);
  const leaf = (field: string, value: unknown) => ({
    field,
    op: value == null || value === '' ? 'empty' : 'eq',
    values: value == null || value === '' ? [] : [String(value)],
  });
  for (const field of plan.groupBy) {
    conditions.push(leaf(field, row[field]));
    const identity = props.result.identities[field];
    if (identity) conditions.push(leaf(identity, row[identity]));
  }
  if (plan.operation === 'TREND') {
    const period = String(row.period || '');
    if (!period) conditions.push({ field: plan.timeField, op: 'empty', values: [] });
    else {
      const start =
        period.length === 4 ? period + '-01-01' : period.length === 7 ? period + '-01' : period;
      const next = new Date(start + 'T00:00:00Z');
      if (plan.bucket === 'day') next.setUTCDate(next.getUTCDate() + 1);
      else if (plan.bucket === 'year') next.setUTCFullYear(next.getUTCFullYear() + 1);
      else next.setUTCMonth(next.getUTCMonth() + 1);
      conditions.push(
        { field: plan.timeField, op: 'gte', values: [start] },
        { field: plan.timeField, op: 'lt', values: [next.toISOString().slice(0, 10)] },
      );
    }
  }
  plan.where = conditions.length ? { op: 'AND', children: conditions } : null;
  plan.operation = 'LIST';
  plan.groupBy = [];
  plan.aggregate = 'count';
  plan.measure = '';
  plan.sortBy = '';
  plan.topN = 0;
  detail.value = true;
  activePlan.value = plan;
  if (page.value !== 1) page.value = 1;
  else await load(plan);
}
function back() {
  detail.value = false;
  activePlan.value = props.result.plan;
  page.value = 1;
  data.value = props.result;
  error.value = '';
  revision++;
  busy.value = false;
}
function open(row: Record<string, unknown>) {
  const url = String(row.url || '');
  if (!/^\/(equipment|repairs|maintenance|inspection)\//.test(url)) return;
  void router.push(url);
  emit('navigate');
}
function display(value: unknown) {
  return value == null ? '' : String(value);
}
</script>
<template>
  <section class="business-result">
    <header>
      <strong>{{ result.title }}</strong
      ><ElButton v-if="detail" link type="primary" @click="back">{{
        tr('ui.equipment.text0208')
      }}</ElButton>
    </header>
    <small
      >{{ data.range }}<template v-if="data.timeField"> · {{ data.timeField }}</template> ·
      {{ data.asOf }}</small
    >
    <small v-if="data.conditions">{{ data.conditions }}</small>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="Object.hasOwn(data, 'value')" class="business-value">
      <ElButton
        v-if="data.plan.aggregate === 'count'"
        link
        type="primary"
        :disabled="busy"
        @click="drill({})"
        >{{ data.value }}</ElButton
      >
      <template v-else>{{ data.value == null ? '—' : data.value }}</template>
      <small>{{ data.valueLabel }}</small>
    </p>
    <template v-else>
      <AppBusinessTable
        :rows="data.data || []"
        :loading="busy"
        :fill="false"
        :pagination="false"
        :max-height="300"
      >
        <ElTableColumn
          v-for="(column, index) in data.columns"
          :key="column.key"
          :label="column.label"
          :min-width="column.key === 'percent' ? 140 : 115"
          :align="
            ['count', 'value', 'percent', 'quantity', 'available', 'reserved'].includes(column.key)
              ? 'right'
              : 'left'
          "
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <ElButton
              v-if="index === 0 && data.plan.operation !== 'LIST'"
              link
              type="primary"
              :disabled="busy"
              @click="drill(row)"
              >{{ display(row[column.key]) || tr('ui.equipment.text1382') }}</ElButton
            >
            <ElButton
              v-else-if="column.key === 'code' && row.url"
              link
              type="primary"
              @click="open(row)"
              >{{ row.code }}</ElButton
            >
            <ElProgress
              v-else-if="column.key === 'percent'"
              :percentage="Number(row.percent)"
              :stroke-width="5"
            />
            <span v-else>{{ display(row[column.key]) }}</span>
          </template>
        </ElTableColumn>
      </AppBusinessTable>
      <div class="business-pagination">
        <AppPagination v-model:page="page" v-model:page-size="size" :total="data.total" />
      </div>
    </template>
  </section>
</template>
<style scoped>
.business-result {
  margin-top: 8px;
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 8px;
}
.business-result header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.business-result small {
  display: block;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin: 6px 0 8px;
}
.business-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--brand);
  margin: 8px 0;
}
.business-pagination {
  overflow-x: auto;
  padding-top: 8px;
}
</style>
