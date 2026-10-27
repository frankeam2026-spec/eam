<script setup lang="ts">
import { tr } from '../../i18n';
import { formatDateTime } from '../../utils/dateTime';
import { computed, ref } from 'vue';
import {
  ElButton,
  ElInput,
  ElTableColumn,
  ElTag,
  ElMessage,
  ElSelect,
  ElOption,
} from 'element-plus';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import WorkflowBpmnCanvas from './WorkflowBpmnCanvas.vue';
import AppDialog from '../common/AppDialog.vue';
import { useAuthStore } from '../../stores/auth';
import { workflowApi, type WorkflowRound } from '../../workflow';
const props = defineProps<{ rounds: WorkflowRound[]; applicantId?: string }>();
const emit = defineEmits<{ changed: [] }>();
const auth = useAuthStore(),
  busy = ref(false),
  selectedApproval = ref(''),
  opinions = ref<Record<string, string>>({});
const current = computed(() => props.rounds[0]);
const graphRound = ref<WorkflowRound | null>(null);
const graphOpen = ref(false);
const nodeStates = computed(() => {
  const round = graphRound.value,
    states: Record<string, string> = {};
  if (!round) return states;
  for (const n of round.nodes || []) {
    const approvals = round.approvals.filter((a) => a.node_key === n.key);
    if (approvals.some((a) => a.state === 'REJECTED')) states[n.key] = 'REJECTED';
    else if (approvals.some((a) => a.state === 'PENDING')) states[n.key] = 'PENDING';
    else if (approvals.some((a) => a.state === 'APPROVED')) states[n.key] = 'APPROVED';
    else if (approvals.length) states[n.key] = 'CANCELLED';
    if (!n.parentKey && (n.type === 'START' || (n.type === 'END' && round.state === 'APPROVED')))
      states[n.key] = 'APPROVED';
    if (round.events.some((e) => e.nodeKey === n.key && ['BRANCH', 'SCRIPT'].includes(e.type)))
      states[n.key] = 'APPROVED';
  }
  return states;
});
const mine = computed(
  () =>
    current.value?.approvals.filter((a) => a.user_id === auth.user?.id && a.state === 'PENDING') ||
    [],
);
const pending = computed(
  () => mine.value.find((a) => a.id === selectedApproval.value) || mine.value[0],
);
const opinion = computed({
  get: () => (pending.value ? opinions.value[pending.value.id] || '' : ''),
  set: (value) => {
    if (pending.value) opinions.value[pending.value.id] = value;
  },
});
const labels: Record<string, string> = {
  RUNNING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  PENDING: '待审批',
  CANCELLED: '已结束',
};
async function act(approve: boolean) {
  if (!current.value || !pending.value) return;
  busy.value = true;
  try {
    await workflowApi.act(
      current.value.id,
      pending.value.id,
      current.value.rowVersion,
      approve,
      opinion.value,
    );
    opinion.value = '';
    emit('changed');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e));
  } finally {
    busy.value = false;
  }
}
async function withdraw() {
  if (!current.value) return;
  busy.value = true;
  try {
    await workflowApi.withdraw(current.value.id, current.value.rowVersion);
    emit('changed');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e));
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section v-if="rounds.length" class="content requisition-approval">
    <h3>{{ tr('workflow.ui.001') }}</h3>
    <div v-if="pending && current?.state === 'RUNNING'" class="approval-actions">
      <ElSelect
        v-if="mine.length > 1"
        :model-value="pending.id"
        :disabled="busy"
        @update:model-value="selectedApproval = $event"
        ><ElOption v-for="a in mine" :key="a.id" :value="a.id" :label="a.node_name"
      /></ElSelect>
      <ElTag v-else>{{ pending.node_name }}</ElTag>
      <ElInput v-model="opinion" maxlength="1000" :placeholder="tr('workflow.ui.002')" /><ElButton
        type="primary"
        :loading="busy"
        @click="act(true)"
        >{{ tr('workflow.ui.003') }}</ElButton
      ><ElButton type="danger" :loading="busy" @click="act(false)">{{
        tr('workflow.ui.004')
      }}</ElButton>
    </div>
    <ElButton
      v-if="current?.state === 'RUNNING' && applicantId === auth.user?.id"
      :loading="busy"
      @click="withdraw"
      >{{ tr('workflow.ui.005') }}</ElButton
    >
    <div v-for="round in rounds" :key="round.id" class="approval-round">
      <div class="approval-heading">
        <strong
          >{{ tr('workflow.ui.006') }}{{ round.roundNo }}{{ tr('workflow.ui.007')
          }}{{ round.templateName }}</strong
        ><ElTag>{{ labels[round.state] || round.state }}</ElTag>
        <ElButton
          v-if="round.nodes?.length"
          @click="
            graphRound = round;
            graphOpen = true;
          "
          >{{ tr('workflow.editor.progress') }}</ElButton
        >
      </div>
      <AppBusinessTable :rows="round.approvals" :fill="false" :pagination="false"
        ><ElTableColumn
          prop="node_name"
          :label="tr('workflow.advanced.019')"
          min-width="160"
        /><ElTableColumn
          prop="user_name"
          :label="tr('workflow.ui.008')"
          width="140"
        /><ElTableColumn :label="tr('workflow.ui.009')" width="120"
          ><template #default="s">{{ labels[s.row.state] || s.row.state }}</template></ElTableColumn
        ><ElTableColumn
          prop="opinion"
          :label="tr('workflow.ui.010')"
          min-width="240"
        /><ElTableColumn :label="tr('workflow.ui.011')" width="190"
          ><template #default="s">{{ formatDateTime(s.row.acted_at) }}</template></ElTableColumn
        ></AppBusinessTable
      >
      <p v-for="(event, i) in round.events" :key="i">
        {{ formatDateTime(event.createdAt) }} {{ event.description }}
      </p>
    </div>
    <AppDialog v-model="graphOpen" :title="tr('workflow.editor.progress')" width="1200px">
      <template v-if="graphRound">
        <div class="approval-legend">
          <ElTag type="warning">{{ tr('workflow.editor.pending') }}</ElTag
          ><ElTag type="success">{{ tr('workflow.editor.passed') }}</ElTag
          ><ElTag type="danger">{{ tr('workflow.ui.004') }}</ElTag
          ><span>{{ tr('workflow.editor.progressHelp') }}</span>
        </div>
        <div class="approval-graph">
          <WorkflowBpmnCanvas
            :key="graphRound.id"
            :nodes="graphRound.nodes || []"
            readonly
            selected=""
            :node-states="nodeStates"
          />
        </div>
        <AppBusinessTable :rows="graphRound.approvals" :fill="false" :pagination="false">
          <ElTableColumn prop="node_name" :label="tr('workflow.advanced.019')" min-width="160" />
          <ElTableColumn prop="user_name" :label="tr('workflow.ui.008')" min-width="140" />
          <ElTableColumn :label="tr('workflow.ui.009')" min-width="120"
            ><template #default="s">{{
              labels[s.row.state] || s.row.state
            }}</template></ElTableColumn
          >
          <ElTableColumn prop="opinion" :label="tr('workflow.ui.010')" min-width="200" />
          <ElTableColumn :label="tr('workflow.ui.011')" min-width="180"
            ><template #default="s">{{ formatDateTime(s.row.acted_at) }}</template></ElTableColumn
          >
        </AppBusinessTable>
      </template>
    </AppDialog>
  </section>
</template>
<style scoped>
.requisition-approval {
  margin-top: 16px;
}
.approval-graph {
  height: 420px;
  margin: 12px 0;
  border: 1px solid var(--el-border-color-light);
}
.approval-legend {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
}
.requisition-approval h3 {
  font-size: 14px;
  margin-bottom: 12px;
}
.approval-actions,
.approval-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.approval-actions > .el-input {
  max-width: 560px;
}
.approval-round {
  margin-top: 16px;
}
.approval-round p {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 8px 0;
}
</style>
