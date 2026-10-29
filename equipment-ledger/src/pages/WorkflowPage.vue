<script setup lang="ts">
import { tr } from '../i18n';
import { computed, onMounted, onBeforeUnmount, nextTick, inject, ref, watch, type Ref } from 'vue';
import {
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElForm,
  ElTableColumn,
  ElTag,
  ElRadioGroup,
  ElRadioButton,
  ElMessage,
  ElAlert,
  ElTabs,
  ElTabPane,
  type FormInstance,
} from 'element-plus';
import AppBusinessTable from '../components/common/AppBusinessTable.vue';
import AppFormField from '../components/common/AppFormField.vue';
import AppIcon from '../components/common/AppIcon.vue';
import AppHelpTip from '../components/common/AppHelpTip.vue';
import {
  inspectWorkflow,
  readableCondition,
  approvalSummary,
  type WorkflowIssue,
} from '../components/workflow/workflowEditing';
import 'element-plus/es/components/radio-group/style/css';
import 'element-plus/es/components/radio-button/style/css';
import 'element-plus/es/components/alert/style/css';
import { formatDateTime } from '../utils/dateTime';
import AppDialog from '../components/common/AppDialog.vue';
import AppFormFooter from '../components/common/AppFormFooter.vue';
import PagedChoiceField from '../components/common/PagedChoiceField.vue';
import WorkflowConditionTree from '../components/workflow/WorkflowConditionTree.vue';
import WorkflowBpmnCanvas from '../components/workflow/WorkflowBpmnCanvas.vue';
import WorkflowScriptEditor from '../components/workflow/WorkflowScriptEditor.vue';
import WorkflowTrialInputs from '../components/workflow/WorkflowTrialInputs.vue';
import 'element-plus/es/components/tabs/style/css';
import { gateway, edgeKey } from '../components/workflow/workflowDiagram';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { useAuthStore } from '../stores/auth';
import {
  workflowApi,
  conditionText,
  type WorkflowDefinition,
  type WorkflowNode,
  type WorkflowVariable,
  type Condition,
} from '../workflow';
import type { SelectorRow } from '../selectors';
const auth = useAuthStore(),
  busy = ref(false),
  loading = ref(false),
  error = ref(''),
  formRef = ref<FormInstance>();
const canvasRef = ref<InstanceType<typeof WorkflowBpmnCanvas>>();
const rows = ref<Awaited<ReturnType<typeof workflowApi.list>>['data']>([]),
  total = ref(0),
  page = ref(1),
  size = ref(20),
  q = ref(''),
  company = ref('');
const companies = ref<SelectorRow[]>([]),
  editCompanies = ref<SelectorRow[]>([]),
  baseVariables = ref<WorkflowVariable[]>([]);
const form = ref<WorkflowDefinition | null>(null),
  selected = ref(''),
  readonly = ref(false),
  options = ref<SelectorRow[]>([]);
const variables = computed<WorkflowVariable[]>(() => [
  ...baseVariables.value,
  ...(form.value?.nodes || [])
    .filter((n) => n.type === 'SCRIPT' && n.script?.outputKey)
    .map((n) => ({
      key: n.script!.outputKey,
      label: n.name + ' · 计算结果',
      type: n.script!.outputType,
      available: true,
      nullable: false,
    })),
]);
const node = computed(() => form.value?.nodes.find((n) => n.key === selected.value));
const conditionLabels = computed(() =>
  Object.fromEntries(
    (form.value?.nodes || [])
      .filter((n) => gateway(n) && n.type !== 'PARALLEL' && n.branches.length > 1)
      .flatMap((n) =>
        n.branches
          .filter((b) => !b.fallback && b.expression)
          .map((b) => [edgeKey(n.key, b.key), readableCondition(b.expression, variables.value)]),
      ),
  ),
);
const workspaceFocus = inject<Ref<boolean>>('workflowFocus', ref(false));
watch(
  () => !!form.value,
  (value) => {
    workspaceFocus.value = value;
  },
);
onBeforeUnmount(() => {
  workspaceFocus.value = false;
});
const basicsOpen = ref(true),
  issuesOpen = ref(false),
  checking = ref(false),
  checked = ref(false);
const issues = ref<WorkflowIssue[]>([]),
  propertiesRef = ref<HTMLElement>();
const actorNames = ref<Record<string, string>>({});
const summaries = computed(() =>
  Object.fromEntries(
    (form.value?.nodes || [])
      .filter((n) => n.type === 'APPROVAL')
      .map((n) => [n.key, approvalSummary(n, actorNames.value)]),
  ),
);
const nodeTypes: Record<string, string> = {
  START: '开始',
  END: '结束',
  APPROVAL: '审批',
  BRANCH: '条件分支',
  PARALLEL: '并行网关',
  INCLUSIVE: '包容网关',
  SUBPROCESS: '子流程',
  SCRIPT: '脚本计算',
};
watch(
  () => form.value?.nodes,
  () => {
    checked.value = false;
    issues.value = [];
  },
  { deep: true },
);
async function locate(issue: WorkflowIssue) {
  selected.value = issue.node;
  await nextTick();
  canvasRef.value?.focus(issue.node);
  const field = propertiesRef.value?.querySelector<HTMLElement>(
    '[data-field="' + issue.field + '"]',
  );
  (field || propertiesRef.value)?.scrollIntoView({ block: 'nearest' });
  field?.querySelector<HTMLElement>('input,button,[tabindex="0"]')?.focus();
}
async function checkFlow() {
  if (!form.value || checking.value) return false;
  error.value = '';
  checking.value = true;
  try {
    await canvasRef.value?.flush();
    const result = inspectWorkflow(form.value);
    for (const n of form.value.nodes.filter((n) => gateway(n) && n.type !== 'PARALLEL')) {
      if (n.branches.length <= 1) continue;
      for (const [i, b] of n.branches.entries())
        if (!b.fallback && b.expression.trim()) {
          try {
            await workflowApi.parse(b.expression, form.value);
          } catch (e) {
            result.push({
              node: n.key,
              field: 'branches',
              branch: i,
              message: e instanceof Error ? e.message : String(e),
            });
          }
        }
    }
    issues.value = result;
    checked.value = true;
    issuesOpen.value = true;
    return !result.length;
  } catch (e) {
    message(e);
    return false;
  } finally {
    checking.value = false;
  }
}
const scriptTab = ref('script'),
  scriptBusy = ref(false),
  scriptResult = ref<string | null>(null),
  scriptError = ref('');
const scriptOpen = ref(false),
  scriptEditor = ref<InstanceType<typeof WorkflowScriptEditor>>();
const guard = useUnsavedChanges(
  () => form.value,
  () => !!form.value && !readonly.value,
  () => busy.value,
);
const expressionOpen = ref(false),
  expression = ref(''),
  condition = ref<Condition | null>(null),
  conditionMode = ref('visual'),
  expressionError = ref(''),
  branchIndex = ref(0);
const versionsOpen = ref(false),
  versions = ref<Awaited<ReturnType<typeof workflowApi.versions>>['data']>([]);
const trialOpen = ref(false),
  trialBusy = ref(false),
  trialValues = ref<Record<string, string>>({}),
  trialLines = ref<{ code: string; materialId: string; quantity: string }[]>([]),
  trace = ref<Record<string, unknown>[]>([]);
const trialTab = ref('inputs'),
  trialError = ref('');
const trialResults = computed(() => trace.value.filter((t) => t.type === 'SCRIPT_RESULT'));
const traceEntries = computed<(Record<string, unknown> & { nodeKey: string })[]>(() => {
  let current = '';
  return trace.value.map((t) => {
    if (t.node) current = String(t.node);
    return { ...t, nodeKey: current };
  });
});
async function locateTrace(key: string) {
  trialOpen.value = false;
  selected.value = key;
  await nextTick();
  canvasRef.value?.focus(key);
}
const trialLabel = computed(() => Object.fromEntries(variables.value.map((v) => [v.key, v.label])));
function message(e: unknown) {
  error.value = e instanceof Error ? e.message : String(e);
}
let loadSequence = 0;
watch([page, size], () => {
  if (!form.value) void load();
});
async function load() {
  const sequence = ++loadSequence;
  rows.value = [];
  loading.value = true;
  error.value = '';
  try {
    const r = await workflowApi.list(q.value, company.value, page.value, size.value);
    if (sequence !== loadSequence) return;
    rows.value = r.data;
    total.value = r.total;
  } catch (e) {
    message(e);
  } finally {
    loading.value = false;
  }
}
async function edit(id?: string, version?: string) {
  if (!(await guard.permitLeave())) return;
  error.value = '';
  try {
    if (id) {
      form.value = (await workflowApi.detail(id, version)).data;
      readonly.value = !!version || !auth.can('workflow', '编辑');
    } else {
      form.value = {
        id: null,
        rowVersion: 0,
        name: '',
        businessType: 'requisition',
        companyId: editCompanies.value.length === 1 ? editCompanies.value[0]!.id : '',
        description: '',
        versionId: null,
        state: 'DRAFT',
        nodes: [
          {
            key: 'start',
            type: 'START',
            name: '申请人提交',
            next: 'approval',
            approvalMode: 'ANY',
            users: [],
            roles: [],
            branches: [],
          },
          {
            key: 'approval',
            type: 'APPROVAL',
            name: '领料审批',
            next: 'end',
            approvalMode: 'ANY',
            users: [],
            roles: [],
            branches: [],
          },
          {
            key: 'end',
            type: 'END',
            name: '审批结束',
            next: null,
            approvalMode: 'ANY',
            users: [],
            roles: [],
            branches: [],
          },
        ],
      };
      readonly.value = false;
    }
    selected.value = form.value.nodes.find((n) => n.type === 'APPROVAL')?.key || 'start';
    basicsOpen.value = !id;
    issuesOpen.value = false;
    issues.value = [];
    checked.value = false;
    guard.markSaved();
  } catch (e) {
    message(e);
  }
}
async function back() {
  if (await guard.permitLeave()) {
    form.value = null;
    await load();
  }
}
async function save() {
  if (!form.value) return false;
  if (!(await formRef.value?.validate().catch(() => false))) {
    basicsOpen.value = true;
    await nextTick();
    formRef.value?.scrollToField(!form.value.name.trim() ? 'name' : 'companyId');
    return false;
  }
  busy.value = true;
  error.value = '';
  try {
    form.value.diagramXml = (await canvasRef.value?.flush()) || form.value.diagramXml;
    form.value = (await workflowApi.save(form.value)).data;
    guard.markSaved();
    ElMessage.success('流程草稿已保存');
    return true;
  } catch (e) {
    message(e);
    return false;
  } finally {
    busy.value = false;
  }
}
async function publish() {
  if (!form.value) return;
  if (!(await formRef.value?.validate().catch(() => false))) {
    basicsOpen.value = true;
    return;
  }
  if (!(await checkFlow())) return;
  if (guard.dirty.value || !form.value.id) {
    if (!(await save())) return;
  }
  busy.value = true;
  try {
    form.value = (await workflowApi.publish(form.value!)).data;
    guard.markSaved();
    ElMessage.success('流程已发布，新提交将使用此版本');
  } catch (e) {
    message(e);
  } finally {
    busy.value = false;
  }
}
function add(type: WorkflowNode['type'], branchIndex?: number) {
  if (!form.value || !node.value) return;
  const branch = branchIndex === undefined ? undefined : node.value.branches[branchIndex];
  if (gateway(node.value) && !branch) return;
  const key = 'n' + crypto.randomUUID().replaceAll('-', '').slice(0, 16);
  const next =
    branch?.target ||
    (node.value.type === 'END'
      ? node.value.key
      : node.value.next ||
        form.value.nodes.find(
          (n) => n.type === 'END' && (n.parentKey || '') === (node.value!.parentKey || ''),
        )?.key ||
        '');
  const n: WorkflowNode = {
    key,
    type,
    name: type === 'BRANCH' ? '条件分支' : '审批节点',
    next: type === 'BRANCH' ? null : next,
    parentKey: node.value.parentKey || null,
    approvalMode: 'ANY',
    users: [],
    roles: [],
    branches:
      type === 'BRANCH'
        ? [
            {
              key: 'match',
              name: '条件一',
              target: next,
              fallback: false,
              expression: 'SUM(lines.quantity) > 0',
            },
            { key: 'otherwise', name: '其他情况', target: next, fallback: true, expression: '' },
          ]
        : [],
  };
  if (branch) branch.target = key;
  else if (node.value.type === 'END') {
    for (const old of form.value.nodes) {
      if (old.next === next) old.next = key;
      for (const b of old.branches) if (b.target === next) b.target = key;
    }
  } else if (!gateway(node.value)) node.value.next = key;
  form.value.nodes.splice(
    form.value.nodes.findIndex((n) => n.key === next),
    0,
    n,
  );
  selected.value = key;
}
function appendNode(key: string, type: 'APPROVAL' | 'BRANCH') {
  selected.value = key;
  add(type);
}
function addBranch() {
  if (!form.value || !node.value) return;
  const parallel = node.value.type === 'PARALLEL';
  node.value.branches.splice(
    parallel ? node.value.branches.length : Math.max(0, node.value.branches.length - 1),
    0,
    {
      key: 'b' + Date.now(),
      name: parallel ? '新路径' : '新条件',
      target: targetOptions(node.value.key).find((n) => n.type === 'END')?.key || '',
      fallback: false,
      expression: parallel ? '' : 'SUM(lines.quantity) > 0',
    },
  );
}
function remove() {
  if (!form.value || !node.value || ['START', 'END'].includes(node.value.type)) return;
  const key = node.value.key,
    next =
      node.value.next ||
      node.value.branches.find((b) => b.fallback)?.target ||
      node.value.branches[0]?.target ||
      form.value.nodes.find(
        (n) => n.type === 'END' && (n.parentKey || '') === (node.value!.parentKey || ''),
      )?.key ||
      '';
  for (const n of form.value.nodes) {
    if (n.next === key) n.next = next;
    for (const b of n.branches) if (b.target === key) b.target = next;
  }
  const removed = new Set([key]);
  for (let pass = 0; pass < 8; pass++)
    form.value.nodes.forEach((n) => {
      if (n.parentKey && removed.has(n.parentKey)) removed.add(n.key);
    });
  form.value.nodes = form.value.nodes.filter((n) => !removed.has(n.key));
  selected.value = 'start';
}
function targetOptions(key: string) {
  const current = form.value?.nodes.find((n) => n.key === key);
  return (
    form.value?.nodes.filter(
      (n) =>
        n.key !== key && n.type !== 'START' && (n.parentKey || '') === (current?.parentKey || ''),
    ) || []
  );
}
async function editCondition(index: number) {
  branchIndex.value = index;
  expression.value = node.value!.branches[index]!.expression;
  expressionError.value = '';
  try {
    condition.value = (
      await workflowApi.parse(expression.value, form.value?.companyId ? form.value : undefined)
    ).data;
    conditionMode.value = 'visual';
  } catch {
    condition.value = null;
    conditionMode.value = 'expression';
  }
  expressionOpen.value = true;
}
async function switchCondition(mode: string | number | boolean | undefined) {
  expressionError.value = '';
  try {
    if (mode === 'expression' && condition.value) expression.value = conditionText(condition.value);
    else if (mode === 'visual')
      condition.value = (
        await workflowApi.parse(expression.value, form.value?.companyId ? form.value : undefined)
      ).data;
    conditionMode.value = String(mode);
  } catch (e) {
    expressionError.value = e instanceof Error ? e.message : String(e);
  }
}
async function saveCondition() {
  try {
    const text =
      conditionMode.value === 'visual' && condition.value
        ? conditionText(condition.value)
        : expression.value;
    await workflowApi.parse(text, form.value?.companyId ? form.value : undefined);
    node.value!.branches[branchIndex.value]!.expression = text;
    expressionOpen.value = false;
  } catch (e) {
    expressionError.value = e instanceof Error ? e.message : String(e);
  }
}
async function showVersions() {
  if (!form.value?.id) return;
  versions.value = (await workflowApi.versions(form.value.id)).data;
  versionsOpen.value = true;
}
async function scriptTrial() {
  if (!node.value?.script || !form.value || scriptBusy.value) return;
  scriptBusy.value = true;
  scriptResult.value = null;
  scriptError.value = '';
  try {
    scriptResult.value = String(
      (
        await workflowApi.scriptTrial(form.value.companyId, node.value.script, {
          ...trialValues.value,
          lines: trialLines.value,
        })
      ).data.value,
    );
  } catch (e) {
    scriptError.value = e instanceof Error ? e.message : String(e);
  } finally {
    scriptBusy.value = false;
  }
}
async function trial() {
  if (trialBusy.value) return;
  trialBusy.value = true;
  trace.value = [];
  trialError.value = '';
  try {
    const values: Record<string, unknown> = { ...trialValues.value, lines: trialLines.value };
    if (!values.applicantDepartmentId) values.applicantDepartmentId = null;
    trace.value = (await workflowApi.simulate(form.value!, values)).data;
    trialTab.value = 'path';
  } catch (e) {
    trialError.value = e instanceof Error ? e.message : String(e);
  } finally {
    trialBusy.value = false;
  }
}
function choices(kind: string) {
  return (q: string, p: number, s: number) =>
    workflowApi.choices(form.value!.companyId, kind, q, p, s);
}
function mergeOptions(rows: SelectorRow[]) {
  options.value = [...new Map([...options.value, ...rows].map((r) => [r.id, r])).values()];
}
let actorSequence = 0;
watch(
  () =>
    JSON.stringify([
      form.value?.companyId,
      ...(form.value?.nodes || []).map((n) => [n.users, n.roles]),
    ]),
  async () => {
    const sequence = ++actorSequence,
      c = form.value?.companyId;
    actorNames.value = {};
    options.value = [];
    if (!c || !form.value) return;
    try {
      for (const kind of ['users', 'roles'] as const) {
        const ids = [...new Set(form.value.nodes.flatMap((n) => n[kind]))];
        for (let i = 0; i < ids.length; i += 100) {
          const result = await workflowApi.resolve(c, kind, ids.slice(i, i + 100));
          if (sequence !== actorSequence) return;
          mergeOptions(result.data);
          result.data.forEach((r) => {
            actorNames.value[kind + ':' + r.id] = r.name;
          });
        }
      }
    } catch (e) {
      if (sequence === actorSequence) message(e);
    }
  },
);
watch(
  () => [form.value?.versionId, form.value?.companyId, selected.value],
  async () => {
    const n = node.value,
      c = form.value?.companyId;
    if (!n || !c) return;
    const key = n.key;
    try {
      for (const [kind, ids] of [
        ['users', n.users],
        ['roles', n.roles],
      ] as const) {
        if (ids.length) {
          const r = await workflowApi.resolve(c, kind, ids);
          if (node.value?.key === key && form.value?.companyId === c) mergeOptions(r.data);
        }
      }
    } catch (e) {
      message(e);
    }
  },
);
onMounted(async () => {
  try {
    companies.value = (await workflowApi.companies()).data;
    if (auth.can('workflow', '编辑'))
      editCompanies.value = (await workflowApi.companies('编辑')).data;
    baseVariables.value = (await workflowApi.variables()).data;
    await load();
  } catch (e) {
    message(e);
  }
});
</script>
<template>
  <section class="content workflow-page" :class="{ 'workflow-editing': !!form }">
    <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <template v-if="!form">
      <div class="workflow-toolbar">
        <h2>{{ tr('workflow.ui.024') }}</h2>
        <ElSelect
          v-model="company"
          clearable
          :placeholder="tr('workflow.ui.025')"
          @change="
            page = 1;
            load();
          "
          ><ElOption v-for="c in companies" :key="c.id" :value="c.id" :label="c.name" /></ElSelect
        ><ElInput
          v-model="q"
          clearable
          :placeholder="tr('workflow.ui.026')"
          @keyup.enter="
            page = 1;
            load();
          "
        /><ElButton
          @click="
            page = 1;
            load();
          "
          >{{ tr('workflow.ui.027') }}</ElButton
        ><ElButton v-if="auth.can('workflow', '编辑')" type="primary" @click="edit()">{{
          tr('workflow.ui.028')
        }}</ElButton>
      </div>
      <AppBusinessTable
        v-model:page="page"
        v-model:page-size="size"
        :total="total"
        :rows="rows"
        :loading="loading"
        row-key="id"
        fill
        ><ElTableColumn :label="tr('workflow.ui.029')" min-width="220"
          ><template #default="s"
            ><ElButton link type="primary" @click="edit(s.row.id)">{{
              s.row.name
            }}</ElButton></template
          ></ElTableColumn
        ><ElTableColumn
          prop="companyName"
          :label="tr('workflow.ui.030')"
          min-width="180"
        /><ElTableColumn :label="tr('workflow.ui.031')" width="140"
          ><template #default>{{ tr('workflow.ui.032') }}</template></ElTableColumn
        ><ElTableColumn :label="tr('workflow.ui.033')" width="120"
          ><template #default="s"
            ><ElTag :type="s.row.active ? 'success' : 'info'">{{
              s.row.active ? '生效中' : '未生效'
            }}</ElTag></template
          ></ElTableColumn
        ><ElTableColumn :label="tr('workflow.ui.034')" fixed="right" width="156"
          ><template #default="s"
            ><ElButton link @click="edit(s.row.id)">{{ tr('workflow.ui.035') }}</ElButton
            ><ElButton
              v-if="s.row.active && auth.can('workflow', '停用')"
              link
              type="danger"
              @click="workflowApi.disable(s.row.id, s.row.rowVersion).then(load).catch(message)"
              >{{ tr('workflow.ui.036') }}</ElButton
            ></template
          ></ElTableColumn
        ></AppBusinessTable
      >
    </template>
    <template v-else>
      <div class="workflow-toolbar">
        <ElButton
          class="workflow-back"
          :aria-label="tr('workflow.ui.037')"
          :disabled="busy || checking"
          @click="back"
          ><AppIcon name="Left"
        /></ElButton>
        <h2>{{ readonly ? '查看流程' : '流程设计' }}</h2>
        <ElTag v-if="form.state === 'PUBLISHED'">{{ tr('workflow.ui.038') }}</ElTag
        ><ElTag type="info">{{ form.id ? form.name : '新建流程' }}</ElTag
        ><span class="workflow-company">{{
          companies.find((c) => c.id === form?.companyId)?.name
        }}</span
        ><span class="workflow-spacer" />
        <ElButton :aria-expanded="basicsOpen" @click="basicsOpen = !basicsOpen">{{
          tr('workflow.editor.basics')
        }}</ElButton>
        <ElButton :loading="checking" :disabled="busy" @click="checkFlow">{{
          tr('workflow.editor.validate')
        }}</ElButton>
        <ElButton v-if="form.id" @click="showVersions">{{ tr('workflow.ui.039') }}</ElButton
        ><ElButton
          @click="
            trialOpen = true;
            trace = [];
          "
          >{{ tr('workflow.ui.040') }}</ElButton
        >
      </div>
      <ElForm
        v-show="basicsOpen"
        ref="formRef"
        :model="form"
        :disabled="readonly || busy || checking"
        label-position="left"
        label-width="96px"
        class="workflow-basics"
        ><AppFormField
          :label="tr('workflow.ui.029')"
          prop="name"
          :rules="[{ required: true, message: '请输入流程名称' }]"
          ><ElInput v-model="form.name" maxlength="100" /></AppFormField
        ><AppFormField
          :label="tr('workflow.ui.030')"
          prop="companyId"
          :rules="[{ required: true, message: '请选择适用公司' }]"
          ><ElSelect v-model="form.companyId" :disabled="!!form.id"
            ><ElOption
              v-for="c in readonly ? companies : editCompanies"
              :key="c.id"
              :value="c.id"
              :label="c.name" /></ElSelect></AppFormField
        ><AppFormField :label="tr('workflow.ui.031')">{{ tr('workflow.ui.032') }}</AppFormField
        ><AppFormField class="workflow-description" :label="tr('workflow.ui.041')"
          ><ElInput v-model="form.description" maxlength="1000" /></AppFormField
      ></ElForm>
      <div v-if="issuesOpen" class="workflow-validation" aria-live="polite">
        <div class="workflow-validation-heading">
          <strong>{{
            issues.length
              ? tr('workflow.editor.problems') + '（' + issues.length + '）'
              : tr('workflow.editor.valid')
          }}</strong>
          <AppHelpTip :content="tr('workflow.editor.validationHelp')" /><span
            class="workflow-spacer"
          /><ElButton text @click="issuesOpen = false">{{
            tr('workflow.editor.collapse')
          }}</ElButton>
        </div>
        <div v-if="issues.length" class="workflow-issue-list">
          <ElButton v-for="(issue, i) in issues" :key="i" text type="danger" @click="locate(issue)"
            >{{ form.nodes.find((n) => n.key === issue.node)?.name || issue.node
            }}{{ issue.branch === undefined ? '' : ' / ' + (issue.branch + 1) }}：{{
              issue.message
            }}</ElButton
          >
        </div>
      </div>
      <div class="workflow-designer" :class="{ 'workflow-locked': busy || checking }">
        <WorkflowBpmnCanvas
          ref="canvasRef"
          :key="String(form.id) + ':' + readonly"
          v-model:nodes="form.nodes"
          v-model:xml="form.diagramXml"
          :readonly="readonly"
          :selected="selected"
          :summaries="summaries"
          :condition-labels="conditionLabels"
          :invalid-nodes="issues.map((i) => i.node)"
          @select="selected = $event"
          @append="appendNode"
        />
        <div v-if="node" ref="propertiesRef" class="workflow-properties" :key="node.key">
          <div class="workflow-property-heading">
            <ElTag>{{ nodeTypes[node.type] }}</ElTag
            ><strong>{{ node.name || tr('workflow.ui.042') }}</strong>
          </div>
          <h3>{{ tr('workflow.editor.nodeBasics') }}</h3>
          <ElForm label-position="left" label-width="96px" :disabled="readonly || busy"
            ><AppFormField
              :label="tr('workflow.ui.043')"
              data-field="name"
              :error="checked && !node.name.trim() ? '请输入节点名称' : ''"
              ><ElInput v-model="node.name" maxlength="100"
            /></AppFormField>
            <template v-if="node.type === 'SCRIPT' && node.script">
              <h3 data-field="script">
                {{ tr('workflow.editor.calculation')
                }}<AppHelpTip :content="tr('workflow.advanced.012')" />
              </h3>
              <AppFormField :label="tr('workflow.advanced.001')">JavaScript</AppFormField>
              <AppFormField :label="tr('workflow.advanced.002')"
                ><ElInput
                  v-model="node.script.outputKey"
                  :disabled="!auth.can('workflow', '脚本配置')"
                  maxlength="80"
              /></AppFormField>
              <AppFormField :label="tr('workflow.advanced.003')"
                ><ElSelect
                  v-model="node.script.outputType"
                  :disabled="!auth.can('workflow', '脚本配置')"
                  ><ElOption :label="tr('workflow.advanced.004')" value="NUMBER" /><ElOption
                    :label="tr('workflow.advanced.005')"
                    value="TEXT" /><ElOption
                    :label="tr('workflow.advanced.006')"
                    value="BOOLEAN" /></ElSelect
              ></AppFormField>
              <AppFormField :label="tr('workflow.advanced.007')" class="workflow-script-field"
                ><ElButton @click="scriptOpen = true">{{ tr('workflow.advanced.008') }}</ElButton>
                <span class="expression-preview"
                  >{{ node.script.source.split('\n').length
                  }}{{ tr('workflow.advanced.009') }}</span
                ></AppFormField
              >
              <ElButton
                :disabled="!auth.can('workflow', '脚本试算')"
                @click="
                  trialOpen = true;
                  trace = [];
                "
                >{{ tr('workflow.advanced.010') }}</ElButton
              >
            </template>
            <template v-if="node.type === 'APPROVAL'"
              ><h3 data-field="actors">
                {{ tr('workflow.editor.approvers')
                }}<AppHelpTip :content="tr('workflow.editor.actorHelp')" />
              </h3>
              <AppFormField :label="tr('workflow.ui.044')"
                ><ElRadioGroup v-model="node.approvalMode"
                  ><ElRadioButton value="ANY">{{ tr('workflow.ui.045') }}</ElRadioButton
                  ><ElRadioButton value="ALL">{{
                    tr('workflow.ui.046')
                  }}</ElRadioButton></ElRadioGroup
                ></AppFormField
              ><AppFormField :label="tr('workflow.ui.047')"
                ><PagedChoiceField
                  :model-value="node.users"
                  multiple
                  :options="options"
                  :disabled="!form.companyId || readonly"
                  :context="form.companyId + node.key"
                  :columns="[{ key: 'name', label: '姓名' }]"
                  :load-rows="choices('users')"
                  @update:model-value="node.users = $event as string[]"
                  @select="mergeOptions" /></AppFormField
              ><AppFormField :label="tr('workflow.ui.048')"
                ><PagedChoiceField
                  :model-value="node.roles"
                  multiple
                  :options="options"
                  :disabled="!form.companyId || readonly"
                  :context="form.companyId + node.key"
                  :columns="[{ key: 'name', label: '角色' }]"
                  :load-rows="choices('roles')"
                  @update:model-value="node.roles = $event as string[]"
                  @select="mergeOptions" /></AppFormField
            ></template>
            <template v-if="gateway(node)"
              ><h3 data-field="branches">
                {{ tr('workflow.editor.paths')
                }}<AppHelpTip
                  :content="
                    node.type === 'PARALLEL'
                      ? tr('workflow.editor.parallelHelp')
                      : tr('workflow.editor.branchHelp')
                  "
                />
              </h3>
              <div v-for="(b, i) in node.branches" :key="b.key" class="workflow-branch">
                <AppFormField
                  :label="
                    node.type === 'PARALLEL'
                      ? '并行路径 ' + (i + 1)
                      : b.fallback
                        ? '其他情况'
                        : '优先级 ' + (i + 1)
                  "
                  ><ElInput v-model="b.name" :disabled="b.fallback" maxlength="100" /></AppFormField
                ><AppFormField
                  v-if="node.type !== 'PARALLEL' && !b.fallback && node.branches.length > 1"
                  :label="tr('workflow.ui.049')"
                  ><ElButton @click="editCondition(i)">{{ tr('workflow.ui.050') }}</ElButton
                  ><span class="expression-preview" :title="b.expression">{{
                    readableCondition(b.expression, variables)
                  }}</span></AppFormField
                ><AppFormField :label="tr('workflow.ui.051')"
                  ><ElSelect v-model="b.target"
                    ><ElOption
                      v-for="t in targetOptions(node.key)"
                      :key="t.key"
                      :value="t.key"
                      :label="t.name" /></ElSelect
                ></AppFormField>
                <div v-if="!readonly" class="workflow-node-tools">
                  <ElButton @click="add('APPROVAL', i)">{{ tr('workflow.ui.056') }}</ElButton>
                  <ElButton @click="add('BRANCH', i)">{{ tr('workflow.ui.057') }}</ElButton>
                </div>
                <div v-if="node.type !== 'PARALLEL' && !b.fallback">
                  <ElButton
                    text
                    @click="
                      node.branches = [
                        ...node.branches
                          .filter((x) => x !== b)
                          .map((x) => ({ ...x, fallback: false })),
                        { ...b, fallback: true },
                      ]
                    "
                    >{{ tr('workflow.canvas.012') }}</ElButton
                  >
                  <ElButton
                    v-if="i > 0"
                    text
                    @click="
                      [node.branches[i - 1], node.branches[i]] = [
                        node.branches[i]!,
                        node.branches[i - 1]!,
                      ]
                    "
                    >{{ tr('workflow.ui.052') }}</ElButton
                  ><ElButton
                    v-if="node.branches.length > 2"
                    text
                    type="danger"
                    @click="node.branches.splice(i, 1)"
                    >{{ tr('workflow.ui.053') }}</ElButton
                  >
                </div>
              </div>
              <ElButton @click="addBranch">{{ tr('workflow.ui.054') }}</ElButton>
            </template>
            <AppFormField
              v-else-if="node.type !== 'END'"
              data-field="next"
              :label="tr('workflow.ui.055')"
              ><ElSelect v-model="node.next"
                ><ElOption
                  v-for="t in targetOptions(node.key)"
                  :key="t.key"
                  :value="t.key"
                  :label="t.name" /></ElSelect
            ></AppFormField>
            <div v-if="!readonly" class="workflow-node-tools">
              <ElButton v-if="!gateway(node)" @click="add('APPROVAL')">{{
                tr('workflow.ui.056')
              }}</ElButton
              ><ElButton v-if="!gateway(node)" @click="add('BRANCH')">{{
                tr('workflow.ui.057')
              }}</ElButton
              ><ElButton
                v-if="!['START', 'END'].includes(node.type)"
                text
                type="danger"
                @click="remove"
                >{{ tr('workflow.ui.058') }}</ElButton
              >
            </div>
          </ElForm>
        </div>
      </div>
      <AppFormFooter
        ><ElButton @click="back">{{ tr('workflow.ui.037') }}</ElButton
        ><ElButton v-if="!readonly" :loading="busy" @click="save">{{
          tr('workflow.ui.059')
        }}</ElButton
        ><ElButton
          v-if="!readonly && auth.can('workflow', '发布')"
          type="primary"
          :loading="busy"
          @click="publish"
          >{{ tr('workflow.ui.060') }}</ElButton
        ></AppFormFooter
      >
    </template>
    <AppDialog v-model="expressionOpen" :title="tr('workflow.ui.061')" width="900px"
      ><ElRadioGroup :model-value="conditionMode" @update:model-value="switchCondition"
        ><ElRadioButton value="visual">{{ tr('workflow.ui.062') }}</ElRadioButton
        ><ElRadioButton value="expression">{{ tr('workflow.ui.063') }}</ElRadioButton></ElRadioGroup
      ><ElAlert v-if="expressionError" :title="expressionError" type="error" :closable="false" />
      <div class="workflow-condition">
        <WorkflowConditionTree
          v-if="conditionMode === 'visual' && condition"
          v-model="condition"
          :variables="variables"
          :company-id="form!.companyId"
        /><template v-else
          ><ElInput v-model="expression" type="textarea" :rows="6" />
          <div class="workflow-variable-list">
            <ElButton
              v-for="v in variables"
              :key="v.key"
              text
              :disabled="!v.available"
              @click="expression += ' ' + v.key"
              >{{ v.label }}</ElButton
            >
          </div>
          <p>{{ tr('workflow.ui.064') }}</p></template
        >
      </div>
      <template #footer
        ><ElButton @click="expressionOpen = false">{{ tr('workflow.ui.065') }}</ElButton
        ><ElButton type="primary" @click="saveCondition">{{
          tr('workflow.ui.066')
        }}</ElButton></template
      ></AppDialog
    >
    <AppDialog v-model="versionsOpen" :title="tr('workflow.ui.039')" width="680px"
      ><AppBusinessTable :rows="versions" :fill="false" :pagination="false"
        ><ElTableColumn prop="versionNo" :label="tr('workflow.ui.067')" /><ElTableColumn
          prop="state"
          :label="tr('workflow.ui.068')"
        /><ElTableColumn :label="tr('workflow.ui.069')"
          ><template #default="s">{{ formatDateTime(s.row.publishedAt) }}</template></ElTableColumn
        ><ElTableColumn :label="tr('workflow.ui.034')"
          ><template #default="s"
            ><ElButton
              link
              @click="
                edit(form!.id!, s.row.id);
                versionsOpen = false;
              "
              >{{ tr('workflow.ui.070') }}</ElButton
            ></template
          ></ElTableColumn
        ></AppBusinessTable
      ></AppDialog
    >
    <AppDialog
      v-if="node?.script"
      v-model="scriptOpen"
      :busy="scriptBusy"
      :title="tr('workflow.advanced.011')"
      width="1000px"
    >
      <ElTabs v-model="scriptTab">
        <ElTabPane :label="tr('workflow.advanced.020')" name="script">
          <div class="workflow-variable-list">
            <ElButton
              v-for="v in baseVariables.filter((v) => v.available)"
              :key="v.key"
              text
              :disabled="scriptBusy || readonly || !auth.can('workflow', '脚本配置')"
              @click="
                scriptEditor?.insert(
                  v.key.startsWith('lines.')
                    ? 'input.lines.map(line => line.' + v.key.slice(6) + ')'
                    : 'input.' + v.key,
                )
              "
              >{{ v.label }}</ElButton
            >
          </div>
          <WorkflowScriptEditor
            ref="scriptEditor"
            v-model="node.script.source"
            :readonly="scriptBusy || readonly || !auth.can('workflow', '脚本配置')"
          />
          <p class="expression-preview">{{ tr('workflow.advanced.012') }}</p>
        </ElTabPane>
        <ElTabPane :label="tr('workflow.advanced.021')" name="inputs"
          ><WorkflowTrialInputs
            v-model="trialValues"
            v-model:lines="trialLines"
            :variables="baseVariables"
            :company-id="form?.companyId || ''"
            :disabled="scriptBusy"
        /></ElTabPane>
      </ElTabs>
      <ElAlert v-if="scriptError" :title="scriptError" type="error" :closable="false" />
      <div v-if="scriptResult !== null" class="workflow-trace">
        {{ tr('workflow.advanced.022') }}{{ scriptResult }}
      </div>
      <template #footer
        ><ElButton
          :disabled="!form?.companyId || !auth.can('workflow', '脚本试算')"
          :loading="scriptBusy"
          @click="scriptTrial"
          >{{ tr('workflow.advanced.023') }}</ElButton
        ><ElButton type="primary" :disabled="scriptBusy" @click="scriptOpen = false">{{
          tr('workflow.advanced.013')
        }}</ElButton></template
      >
    </AppDialog>
    <AppDialog
      v-model="trialOpen"
      :title="tr('workflow.editor.trial')"
      width="1000px"
      :busy="trialBusy"
      ><ElAlert :title="tr('workflow.editor.trialHelp')" type="info" :closable="false" show-icon />
      <ElAlert v-if="trialError" :title="trialError" type="error" :closable="false" show-icon />
      <ElTabs v-model="trialTab">
        <ElTabPane :label="tr('workflow.advanced.021')" name="inputs"
          ><WorkflowTrialInputs
            v-model="trialValues"
            v-model:lines="trialLines"
            :variables="baseVariables"
            :company-id="form?.companyId || ''"
            :disabled="trialBusy"
        /></ElTabPane>
        <ElTabPane :label="tr('workflow.editor.executionPath')" name="path">
          <p v-if="!trace.length" class="expression-preview">
            {{ tr('workflow.editor.trialEmpty') }}
          </p>
          <ol v-else class="workflow-trace-list">
            <li
              v-for="(t, i) in traceEntries"
              :key="i"
              :class="{ 'trace-unmatched': t.matched === false }"
            >
              <ElTag size="small" :type="t.matched === false ? 'info' : 'success'">{{
                t.type === 'APPROVAL'
                  ? tr('workflow.editor.simulated')
                  : 'matched' in t
                    ? t.matched
                      ? '命中'
                      : '未命中'
                    : nodeTypes[String(t.type)] || tr('workflow.editor.calculation')
              }}</ElTag>
              <ElButton text @click="locateTrace(t.nodeKey)">{{ t.name }}</ElButton>
              <span v-if="t.type === 'SCRIPT_RESULT'">{{ t.value }}</span>
            </li>
          </ol>
        </ElTabPane>
        <ElTabPane :label="tr('workflow.editor.results')" name="results">
          <AppBusinessTable :rows="trialResults" :fill="false" :pagination="false">
            <ElTableColumn prop="name" :label="tr('workflow.ui.043')" min-width="180" />
            <ElTableColumn
              prop="value"
              :label="tr('workflow.editor.resultValue')"
              min-width="200"
            />
          </AppBusinessTable>
        </ElTabPane>
      </ElTabs>
      <template #footer
        ><ElButton :disabled="trialBusy" @click="trialOpen = false">{{
          tr('workflow.ui.076')
        }}</ElButton
        ><ElButton type="primary" :loading="trialBusy" @click="trial">{{
          tr('workflow.ui.077')
        }}</ElButton></template
      ></AppDialog
    >
  </section>
</template>
<style scoped>
.workflow-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  /* background: var(--el-bg-color); */
  overflow: auto;
}
.workflow-editing {
  overflow: hidden;
}
.workflow-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}
.workflow-toolbar h2 {
  font-size: 16px;
  margin: 0 8px 0 0;
}
.workflow-toolbar > .el-select {
  width: 220px;
}
.workflow-toolbar > .el-input {
  width: 240px;
}
.workflow-spacer {
  flex: 1;
}
.workflow-company {
  font-size: 13px;
  color: var(--el-text-color-regular);
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.workflow-back.el-button {
  width: 32px;
  height: 32px;
  padding: 4px;
  border: 0;
  background: transparent;
}
.workflow-back :deep(.app-icon) {
  --app-icon-size: 24px;
}
.workflow-basics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 24px;
  padding: 12px 0 0;
  border-top: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}
.workflow-basics :deep(.el-form-item) {
  margin-bottom: 12px;
}
.workflow-description {
  grid-column: 1/-1;
}
.workflow-designer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  flex: 1;
  min-height: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  overflow: hidden;
}
.workflow-locked {
  pointer-events: none;
}
.workflow-properties {
  padding: 16px;
  background: var(--el-bg-color);
  border-left: 1px solid var(--el-border-color-light);
  overflow: auto;
  min-height: 0;
}
.workflow-property-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 16px;
  overflow-wrap: anywhere;
}
.workflow-properties h3 {
  display: flex;
  align-items: center;
  font-size: 14px;
  margin: 0 0 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-light);
}
.workflow-properties :deep(.el-form-item) {
  margin-bottom: 16px;
}
.workflow-properties :deep(.el-radio-group) {
  flex-wrap: nowrap;
}
.workflow-properties :deep(.el-radio-button__inner) {
  padding: 8px 10px;
}
.workflow-node-tools {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  padding-top: 12px;
}
.workflow-node-tools .el-button + .el-button {
  margin-left: 0;
}
.workflow-branch {
  padding: 12px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  margin-bottom: 12px;
}
.expression-preview {
  font-size: 13px;
  color: var(--el-text-color-regular);
  overflow-wrap: anywhere;
  line-height: 1.6;
}
.workflow-condition {
  margin-top: 16px;
}
.workflow-trace {
  padding: 8px 0;
}
.workflow-variable-list {
  display: flex;
  flex-wrap: wrap;
  margin-top: 12px;
}
.workflow-validation {
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  padding: 8px 12px;
}
.workflow-validation-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.workflow-issue-list {
  display: flex;
  flex-wrap: wrap;
  max-height: 120px;
  overflow: auto;
  gap: 4px 12px;
}
.workflow-issue-list .el-button {
  margin: 0;
  white-space: normal;
  height: auto;
  text-align: left;
}
.workflow-trace-list {
  padding: 0 0 0 20px;
  margin: 16px 0;
}
.workflow-trace-list li {
  padding: 8px;
  border-left: 2px solid var(--el-border-color-light);
  line-height: 1.6;
}
.workflow-trace-list li::marker {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.workflow-trace-list .el-button {
  margin: 0 8px;
}
.trace-unmatched {
  color: var(--el-text-color-secondary);
}
@media (max-width: 1280px) {
  .workflow-designer {
    grid-template-columns: minmax(0, 1fr) 300px;
  }
  .workflow-basics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 900px) {
  .workflow-editing {
    overflow: auto;
  }
  .workflow-designer {
    grid-template-columns: 1fr;
    min-height: 900px;
    flex: none;
  }
  .workflow-properties {
    max-height: 380px;
    border-left: 0;
    border-top: 1px solid var(--el-border-color-light);
  }
  .workflow-designer > :first-child {
    min-height: 500px;
  }
  .workflow-basics {
    grid-template-columns: 1fr;
  }
}
</style>
