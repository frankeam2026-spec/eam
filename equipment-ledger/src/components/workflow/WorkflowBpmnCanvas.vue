<script setup lang="ts">
import { tr } from '../../i18n';
import { nextTick, onMounted, onBeforeUnmount, ref, watch, h, render } from 'vue';
import AppIcon from '../common/AppIcon.vue';
import {
  ElButton,
  ElButtonGroup,
  ElAlert,
  ElUpload,
  ElPopover,
  type UploadFile,
} from 'element-plus';
import 'element-plus/es/components/popover/style/css';
import Modeler from 'bpmn-js/lib/Modeler';
import Viewer from 'bpmn-js/lib/NavigatedViewer';
import eamModdle from './eamModdle';
import { attribute, businessElements, readWorkflowNodes, topology } from './workflowModel';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import 'element-plus/es/components/upload/style/css';
import type { WorkflowNode } from '../../workflow';
import { bpmnTypes, layoutDiagram, gateway, edgeKey } from './workflowDiagram';
import {
  canvasShape,
  canvasConnection,
  diagramWaypoints,
  flowLabelBounds,
  type CanvasBounds,
} from './workflowCanvasModel';

const props = defineProps<{
  nodes: WorkflowNode[];
  xml?: string | null;
  readonly?: boolean;
  selected: string;
  summaries?: Record<string, string>;
  invalidNodes?: string[];
  nodeStates?: Record<string, string>;
  conditionLabels?: Record<string, string>;
}>();
const emit = defineEmits<{
  'update:nodes': [WorkflowNode[]];
  'update:xml': [string];
  select: [string];
  append: [string, 'APPROVAL' | 'BRANCH'];
}>();
const host = ref<HTMLElement>(),
  error = ref(''),
  notice = ref(''),
  canUndo = ref(false),
  canRedo = ref(false),
  zoomLabel = ref('100%');
let modeler: Modeler | Viewer,
  syncing = false,
  destroyed = false,
  signature = '',
  generation = 0;
let importing = false;
let resizeObserver: ResizeObserver | undefined;
const metadata = new Map<string, WorkflowNode>();
const overlayRoots: HTMLElement[] = [];
let overlayQueued = false;
function refreshOverlays() {
  if (!modeler || destroyed || overlayQueued) return;
  overlayQueued = true;
  queueMicrotask(() => {
    overlayQueued = false;
    if (destroyed) return;
    overlayRoots.splice(0).forEach((root) => render(null, root));
    const overlays = service('overlays'),
      canvas = service('canvas'),
      registry = service('elementRegistry');
    overlays.remove({ type: 'eam-summary' });
    for (const element of registry.getAll()) {
      if (!element.labelTarget || !element.businessObject) continue;
      const text = props.conditionLabels?.[element.businessObject.id];
      if (!text) continue;
      const originalName = element.businessObject.name;
      try {
        element.businessObject.name = text;
        service('graphicsFactory').update('shape', element, registry.getGraphics(element));
      } finally {
        element.businessObject.name = originalName;
      }
    }
    for (const n of props.nodes) {
      const shape = registry.get(n.key);
      if (!shape) continue;
      for (const state of ['invalid', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'])
        canvas.removeMarker(n.key, 'wf-' + state);
      if (props.invalidNodes?.includes(n.key)) canvas.addMarker(n.key, 'wf-invalid');
      const state = props.nodeStates?.[n.key];
      if (state) canvas.addMarker(n.key, 'wf-' + state);
      canvas.removeMarker(n.key, 'wf-captioned');
      if (props.summaries?.[n.key]) {
        canvas.addMarker(n.key, 'wf-captioned');
        const caption = document.createElement('div');
        caption.className = 'wf-node-caption';
        caption.style.width = Math.max(70, shape.width - 8) + 'px';
        caption.textContent = props.summaries[n.key]!;
        caption.title = props.summaries[n.key]!;
        overlays.add(n.key, 'eam-summary', {
          position: { top: shape.height - 34, left: 4 },
          html: caption,
        });
      }
      if (
        !props.readonly &&
        n.key === props.selected &&
        ['START', 'APPROVAL', 'SCRIPT', 'SUBPROCESS'].includes(n.type)
      ) {
        const root = document.createElement('div');
        render(
          h(
            ElPopover,
            { trigger: 'click', placement: 'right', width: 180 },
            {
              reference: () =>
                h(
                  ElButton,
                  {
                    circle: true,
                    size: 'small',
                    type: 'primary',
                    'aria-label': tr('workflow.editor.append'),
                  },
                  { default: () => h(AppIcon, { name: 'Plus' }) },
                ),
              default: () =>
                h('div', { style: 'display:flex;flex-direction:column;gap:8px' }, [
                  h(
                    ElButton,
                    { onClick: () => emit('append', n.key, 'APPROVAL') },
                    { default: () => tr('workflow.advanced.019') },
                  ),
                  h(
                    ElButton,
                    { style: 'margin:0', onClick: () => emit('append', n.key, 'BRANCH') },
                    { default: () => tr('workflow.canvas.009') },
                  ),
                ]),
            },
          ),
          root,
        );
        overlayRoots.push(root);
        overlays.add(n.key, 'eam-summary', {
          position: { top: shape.height / 2 - 14, left: shape.width + 8 },
          html: root,
        });
      }
    }
  });
}
function focus(key: string) {
  const element = service('elementRegistry').get(key);
  if (!element) return;
  if (!props.readonly) service('selection').select(element);
  service('canvas').scrollToElement(element);
}
// bpmn-js services are dynamically registered; this boundary keeps them inside the diagram adapter.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const service = (name: string): any => modeler.get(name);
const supported = new Set(Object.values(bpmnTypes));

async function load(xml?: string | null) {
  syncing = true;
  try {
    await modeler.importXML(xml || (await layoutDiagram(props.nodes)));
    let matches = false;
    try {
      matches =
        topology(readWorkflowNodes(modeler.getDefinitions(), props.nodes)) ===
        topology(props.nodes);
    } catch {}
    if (!matches) {
      await modeler.importXML(await layoutDiagram(props.nodes));
      notice.value = '已按流程配置重新生成画布';
    }
    canonicalMetadata();
    service('canvas').zoom('fit-viewport', 'auto');
    signature = JSON.stringify(props.nodes);
    error.value = '';
    refreshOverlays();
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    syncing = false;
    if (!props.readonly) {
      const commands = service('commandStack');
      canUndo.value = commands.canUndo();
      canRedo.value = commands.canRedo();
    }
  }
}
function canonicalMetadata() {
  const elements = businessElements(modeler.getDefinitions()),
    registry = service('elementRegistry');
  const render = (bo: any) => {
    const element = registry.get(bo.id);
    if (!element) return;
    const graphics = service('graphicsFactory');
    graphics.update('shape', element, registry.getGraphics(element));
    for (const label of element.labels || [])
      graphics.update('shape', label, registry.getGraphics(label));
  };
  for (const node of props.nodes) {
    const bo = elements.find((e) => e.id === node.key);
    if (!bo) continue;
    bo.name = node.name;
    if (node.type === 'APPROVAL') {
      bo.set('eam:users', JSON.stringify(node.users));
      bo.set('eam:roles', JSON.stringify(node.roles));
      bo.set('eam:approvalMode', node.approvalMode);
    }
    if (node.script) {
      bo.set('eam:outputKey', node.script.outputKey);
      bo.set('eam:outputType', node.script.outputType);
      bo.script = node.script.source;
      bo.scriptFormat = 'javascript';
    }
    if (gateway(node)) {
      const outgoing = elements.filter(
        (e) => e.$type === 'bpmn:SequenceFlow' && e.sourceRef?.id === node.key,
      );
      bo.default = undefined;
      for (const branch of node.branches) {
        const flow = outgoing.find(
          (e) =>
            e.id === edgeKey(node.key, branch.key) ||
            e.id === branch.key ||
            attribute(e, 'branchKey') === branch.key,
        );
        if (!flow) continue;
        flow.name = branch.name;
        flow.set('eam:branchKey', branch.key);
        flow.conditionExpression =
          node.type !== 'PARALLEL' && !branch.fallback && branch.expression
            ? service('moddle').create('bpmn:FormalExpression', { body: branch.expression })
            : undefined;
        if (branch.fallback) bo.default = flow;
      }
    }
    render(bo);
  }
}
function snapshot() {
  if (syncing || destroyed || props.readonly) return;
  const commands = service('commandStack');
  canUndo.value = commands.canUndo();
  canRedo.value = commands.canRedo();
  if (!importing) props.nodes.forEach((n) => metadata.set(n.key, n));
  let nodes: WorkflowNode[];
  try {
    nodes = readWorkflowNodes(modeler.getDefinitions(), [...metadata.values()]);
    if (
      nodes.some(
        (n) =>
          !['BRANCH', 'PARALLEL', 'INCLUSIVE'].includes(n.type) &&
          businessElements(modeler.getDefinitions()).filter(
            (e) => e.$type === 'bpmn:SequenceFlow' && e.sourceRef?.id === n.key,
          ).length > 1,
      )
    )
      throw Error('多条执行路径需要通过网关连接');
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
    return;
  }
  signature = JSON.stringify(nodes);
  if (!nodes.some((n) => n.key === props.selected))
    emit('select', nodes.find((n) => n.type === 'APPROVAL')?.key || nodes[0]?.key || '');
  emit('update:nodes', nodes);
  refreshOverlays();
  error.value = '';
  // v-model updates reach this child on Vue's next render. Serializing immediately can
  // reconcile the old props back into a newly imported or edited graph.
  void nextTick()
    .then(() => flush())
    .catch((e) => {
      if (!destroyed) error.value = e instanceof Error ? e.message : String(e);
    });
}
async function flush() {
  if (error.value) throw new Error(error.value);
  if (!syncing && JSON.stringify(props.nodes) !== signature) await reconcile();
  const request = ++generation;
  const { xml } = await modeler.saveXML({ format: true });
  if (!destroyed && request === generation && xml) emit('update:xml', xml);
  return xml || '';
}
async function reconcile() {
  if (!modeler || syncing || JSON.stringify(props.nodes) === signature) return;
  if (!importing) props.nodes.forEach((n) => metadata.set(n.key, n));
  syncing = true;
  try {
    const registry = service('elementRegistry'),
      modeling = service('modeling');
    const semantic = new Map(businessElements(modeler.getDefinitions()).map((e) => [e.id, e]));
    const currentIds = new Set(props.nodes.map((n) => n.key));
    for (const e of registry.getAll())
      if (supported.has(e.type) && !e.labelTarget && !currentIds.has(e.id))
        modeling.removeElements([e]);
    let added = false;
    for (const [index, node] of props.nodes.entries()) {
      let element = registry.get(node.key);
      if (!element && semantic.has(node.key)) continue;
      if (!element) {
        added = true;
        element = modeling.createShape(
          service('elementFactory').createShape(canvasShape(service('bpmnFactory'), node)),
          { x: 200 + index * 200, y: 250 },
          node.parentKey ? registry.get(node.parentKey) : service('canvas').getRootElement(),
        );
      }
      const attributes: Record<string, string> = {};
      if (node.type === 'APPROVAL')
        Object.assign(attributes, {
          users: JSON.stringify(node.users),
          roles: JSON.stringify(node.roles),
          approvalMode: node.approvalMode,
        });
      if (node.script)
        Object.assign(attributes, {
          outputKey: node.script.outputKey,
          outputType: node.script.outputType,
        });
      for (const [key, value] of Object.entries(attributes))
        if (attribute(element.businessObject, key) !== value)
          modeling.updateProperties(element, { ['eam:' + key]: value });
      if (
        node.script &&
        (element.businessObject.script !== node.script.source ||
          element.businessObject.scriptFormat !== 'javascript')
      )
        modeling.updateProperties(element, {
          script: node.script.source,
          scriptFormat: 'javascript',
        });
      if (element.businessObject.name !== node.name)
        modeling.updateProperties(element, { name: node.name });
    }
    const expected = props.nodes.flatMap((n) =>
      gateway(n)
        ? n.branches.map((b) => ({
            source: n.key,
            target: b.target,
            key: edgeKey(n.key, b.key),
            name: b.name,
            fallback: b.fallback,
            branchKey: b.key,
            expression: b.expression,
          }))
        : n.next
          ? [
              {
                source: n.key,
                target: n.next,
                key: edgeKey(n.key, 'next'),
                name: '',
                fallback: false,
                branchKey: '',
                expression: '',
              },
            ]
          : [],
    );
    for (const e of registry.getAll())
      if (e.type === 'bpmn:SequenceFlow' && !expected.some((x) => x.key === e.id))
        modeling.removeConnection(e);
    for (const edge of expected) {
      const source = registry.get(edge.source),
        target = registry.get(edge.target);
      if (!source || !target) continue;
      let flow = registry.get(edge.key);
      if (flow && (flow.source !== source || flow.target !== target)) {
        modeling.removeConnection(flow);
        flow = null;
      }
      if (!flow)
        flow = modeling.connect(source, target, canvasConnection(service('bpmnFactory'), edge.key));
      if (edge.branchKey && attribute(flow.businessObject, 'branchKey') !== edge.branchKey)
        modeling.updateProperties(flow, { 'eam:branchKey': edge.branchKey });
      const expression = edge.fallback ? '' : edge.expression;
      if ((flow.businessObject.conditionExpression?.body || '') !== expression)
        modeling.updateProperties(flow, {
          conditionExpression: expression
            ? service('bpmnFactory').create('bpmn:FormalExpression', { body: expression })
            : undefined,
        });
      if (flow.businessObject.name !== edge.name)
        modeling.updateProperties(flow, { name: edge.name });
      if (edge.fallback && source.businessObject.default !== flow.businessObject)
        modeling.updateProperties(source, { default: flow.businessObject });
    }
    signature = JSON.stringify(props.nodes);
    if (added) await arrange();
    await flush();
  } finally {
    syncing = false;
    if (!props.readonly) {
      const commands = service('commandStack');
      canUndo.value = commands.canUndo();
      canRedo.value = commands.canRedo();
    }
    if (JSON.stringify(props.nodes) !== signature) void reconcile();
    refreshOverlays();
  }
}
async function arrange() {
  const wasSyncing = syncing;
  syncing = true;
  try {
    const document = new DOMParser().parseFromString(
      await layoutDiagram(props.nodes),
      'application/xml',
    );
    const registry = service('elementRegistry'),
      modeling = service('modeling');
    for (const shape of document.getElementsByTagNameNS(
      'http://www.omg.org/spec/BPMN/20100524/DI',
      'BPMNShape',
    )) {
      const element = registry.get(shape.getAttribute('bpmnElement')),
        bounds = shape.firstElementChild;
      if (element && bounds) {
        if (['bpmn:UserTask', 'bpmn:ScriptTask', 'bpmn:SubProcess'].includes(element.type))
          modeling.resizeShape(element, {
            x: element.x,
            y: element.y,
            width: Number(bounds.getAttribute('width')),
            height: Number(bounds.getAttribute('height')),
          });
        modeling.moveShape(element, {
          x: Number(bounds.getAttribute('x')) - element.x,
          y: Number(bounds.getAttribute('y')) - element.y,
        });
        if (element.label && element.businessObject.name) {
          const label = element.label,
            bounds = {
              x: element.x + element.width / 2 - 60,
              y: element.y + element.height + 8,
              width: 120,
              height: 28,
            };
          modeling.resizeShape(
            label,
            { ...bounds, x: label.x, y: label.y },
            { width: 0, height: 0 },
            { autoResize: true, layout: false },
          );
          modeling.moveShape(
            label,
            { x: bounds.x - label.x, y: bounds.y - label.y },
            label.parent,
            { layout: false },
          );
        }
      }
    }
    for (const edge of document.getElementsByTagNameNS(
      'http://www.omg.org/spec/BPMN/20100524/DI',
      'BPMNEdge',
    )) {
      const element = registry.get(edge.getAttribute('bpmnElement'));
      if (element) modeling.updateWaypoints(element, diagramWaypoints(edge));
    }
    const occupied: CanvasBounds[] = [];
    for (const flow of registry
      .getAll()
      .filter(
        (e: any) => e.type === 'bpmn:SequenceFlow' && !e.labelTarget && e.businessObject.name,
      )) {
      const obstacles = registry
        .getAll()
        .filter(
          (e: any) => supported.has(e.type) && !e.labelTarget && e.type !== 'bpmn:SubProcess',
        );
      const text = props.conditionLabels?.[flow.id] || flow.businessObject.name;
      const bounds = flowLabelBounds(flow.waypoints, text, obstacles, occupied);
      if (!flow.label) modeling.updateLabel(flow, flow.businessObject.name);
      const label = flow.label;
      modeling.resizeShape(
        label,
        { ...bounds, x: label.x, y: label.y },
        { width: 0, height: 0 },
        { autoResize: true, layout: false },
      );
      modeling.moveShape(label, { x: bounds.x - label.x, y: bounds.y - label.y }, label.parent, {
        layout: false,
      });
      occupied.push(bounds);
    }
    service('canvas').zoom('fit-viewport', 'auto');
    const saved = await modeler.saveXML({ format: true });
    if (saved.xml) emit('update:xml', saved.xml);
  } finally {
    syncing = wasSyncing;
    refreshOverlays();
  }
}
function zoom(delta: number) {
  const canvas = service('canvas');
  const amount = Math.min(2, Math.max(0.25, canvas.zoom() + delta));
  canvas.zoom(amount);
  zoomLabel.value = Math.round(amount * 100) + '%';
}
function create(type: WorkflowNode['type'], event: MouseEvent) {
  service('create').start(event, shape(type));
}
function shape(type: WorkflowNode['type']) {
  const name = {
    START: '开始',
    END: '结束',
    APPROVAL: '审批节点',
    BRANCH: '条件分支',
    PARALLEL: '并行网关',
    INCLUSIVE: '包容网关',
    SUBPROCESS: '子流程',
    SCRIPT: '脚本计算',
  }[type];
  return service('elementFactory').createShape({
    type: bpmnTypes[type],
    ...(type === 'SUBPROCESS' ? { isExpanded: true } : {}),
    businessObject: service('bpmnFactory').create(bpmnTypes[type], { name }),
  });
}
async function importDiagram(file: UploadFile) {
  if (!file.raw) return;
  if (file.raw.size > 500000) {
    notice.value = 'BPMN文件不能超过500KB';
    return;
  }
  const xml = await file.raw.text();
  if (xml.includes('<!DOCTYPE') || xml.includes('<!ENTITY')) {
    notice.value = 'BPMN文件不支持DTD或实体声明';
    return;
  }
  const backup = (await modeler.saveXML({ format: true })).xml,
    backupError = error.value;
  const previous = props.nodes;
  syncing = true;
  importing = true;
  try {
    await modeler.importXML(xml);
    const elements = businessElements(modeler.getDefinitions());
    const nodes = readWorkflowNodes(modeler.getDefinitions(), []);
    if (
      nodes.some(
        (n) =>
          !gateway(n) &&
          elements.filter((e) => e.$type === 'bpmn:SequenceFlow' && e.sourceRef?.id === n.key)
            .length > 1,
      )
    )
      throw Error('多条执行路径需要通过网关连接');
    metadata.clear();
    nodes.forEach((n) => metadata.set(n.key, n));
    signature = JSON.stringify(nodes);
    emit('update:nodes', nodes);
    emit('select', nodes.find((n) => n.type === 'APPROVAL')?.key || nodes[0]?.key || '');
    await nextTick();
    const saved = await modeler.saveXML({ format: true });
    if (saved.xml) emit('update:xml', saved.xml);
    error.value = '';
    notice.value = '';
    service('canvas').zoom('fit-viewport', 'auto');
  } catch (e) {
    if (backup) await modeler.importXML(backup);
    signature = JSON.stringify(previous);
    emit('update:nodes', previous);
    metadata.clear();
    previous.forEach((n) => metadata.set(n.key, n));
    notice.value = e instanceof Error ? e.message : String(e);
    error.value = backupError;
  } finally {
    syncing = false;
    importing = false;
    refreshOverlays();
  }
}
function undo() {
  service('commandStack').undo();
}
function redo() {
  service('commandStack').redo();
}
async function exportXml() {
  const xml = await flush(),
    url = URL.createObjectURL(new Blob([xml], { type: 'application/xml' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = 'workflow.bpmn';
  link.click();
  URL.revokeObjectURL(url);
}
onMounted(async () => {
  await nextTick();
  modeler = props.readonly
    ? new Viewer({ container: host.value, moddleExtensions: { eam: eamModdle } })
    : new Modeler({ container: host.value, moddleExtensions: { eam: eamModdle } });
  if (!props.readonly) {
    const provider = service('contextPadProvider'),
      original = provider.getContextPadEntries.bind(provider);
    provider.getContextPadEntries = (element: any) => {
      const entries = original(element);
      for (const key of [
        'append.intermediate-event',
        'append.text-annotation',
        'replace',
        'append.end-event',
        'append.gateway',
        'append.append-task',
      ])
        delete entries[key];
      const names: Record<string, string> = {
        'append.end-event': '添加结束节点',
        'append.gateway': '添加条件分支',
        'append.append-task': '添加审批节点',
        delete: '删除',
        connect: '连接下一步',
      };
      for (const [key, value] of Object.entries(entries))
        if (names[key]) (value as any).title = names[key];
      for (const [entry, type] of [
        ['append.append-task', 'APPROVAL'],
        ['append.gateway', 'BRANCH'],
        ['append.end-event', 'END'],
      ] as const) {
        if (entries[entry])
          entries[entry].action = {
            click: () => service('autoPlace').append(element, shape(type)),
            dragstart: (event: MouseEvent) => create(type, event),
          };
      }
      return entries;
    };
  }
  modeler.on('selection.changed', (event: any) => {
    const selected = event.newSelection?.[0];
    const element = selected?.labelTarget || selected;
    if (element && supported.has(element.type)) emit('select', element.id);
  });
  modeler.on('commandStack.changed', snapshot);
  modeler.on('canvas.viewbox.changed', (event: any) => {
    zoomLabel.value = Math.round(event.viewbox.scale * 100) + '%';
  });
  await load(props.xml);
  if (host.value) {
    resizeObserver = new ResizeObserver(() => service('canvas').resized());
    resizeObserver.observe(host.value);
  }
  if (!props.readonly) service('selection').select(service('elementRegistry').get(props.selected));
});
watch(() => props.nodes, reconcile, { deep: true });
watch(
  () => props.selected,
  (key) => {
    if (modeler && !props.readonly) {
      const e = service('elementRegistry').get(key);
      if (e) service('selection').select(e);
    }
  },
);
onBeforeUnmount(() => {
  overlayRoots.splice(0).forEach((root) => render(null, root));
  resizeObserver?.disconnect();
  destroyed = true;
  generation++;
  modeler?.destroy();
});
watch(
  () => [
    props.summaries,
    props.selected,
    props.invalidNodes,
    props.nodeStates,
    props.conditionLabels,
  ],
  refreshOverlays,
  { deep: true },
);
defineExpose({ flush, focus });
</script>
<template>
  <div class="bpmn-workspace">
    <div class="bpmn-toolbar">
      <ElButtonGroup v-if="!readonly"
        ><ElButton :disabled="!canUndo" @click="undo">{{ tr('workflow.canvas.001') }}</ElButton
        ><ElButton :disabled="!canRedo" @click="redo">{{
          tr('workflow.canvas.002')
        }}</ElButton></ElButtonGroup
      >
      <span class="bpmn-toolbar-title">{{ tr('workflow.canvas.003') }}</span
      ><span class="bpmn-toolbar-spacer" />
      <ElButton text :aria-label="tr('workflow.editor.zoomOut')" @click="zoom(-0.1)"
        ><AppIcon name="Minus" /></ElButton
      ><span class="bpmn-zoom">{{ zoomLabel }}</span
      ><ElButton text :aria-label="tr('workflow.editor.zoomIn')" @click="zoom(0.1)"
        ><AppIcon name="Plus"
      /></ElButton>
      <ElButton text @click="service('canvas').zoom('fit-viewport', 'auto')">{{
        tr('workflow.canvas.004')
      }}</ElButton
      ><ElButton text @click="exportXml">{{ tr('workflow.canvas.005') }}</ElButton>
      <ElUpload
        v-if="!readonly"
        :auto-upload="false"
        :show-file-list="false"
        accept=".bpmn,.xml"
        :on-change="importDiagram"
        ><ElButton text>{{ tr('workflow.advanced.014') }}</ElButton></ElUpload
      >
      <ElButton v-if="!readonly" text @click="arrange">{{ tr('workflow.canvas.013') }}</ElButton>
    </div>
    <ElAlert
      v-if="error || notice"
      :title="error || notice"
      type="error"
      :closable="false"
      show-icon
    />
    <div class="bpmn-stage" :class="{ 'bpmn-readonly': readonly }">
      <div v-if="!readonly" class="bpmn-stencil">
        <span class="bpmn-stencil-title">{{ tr('workflow.canvas.006') }}</span>
        <ElButton class="bpmn-stencil-item" @mousedown="create('START', $event)"
          ><i class="bpmn-icon-start-event-none" />{{ tr('workflow.canvas.007') }}</ElButton
        >
        <ElButton class="bpmn-stencil-item" @mousedown="create('APPROVAL', $event)"
          ><i class="bpmn-icon-user-task" />{{ tr('workflow.canvas.008') }}</ElButton
        >
        <ElButton class="bpmn-stencil-item" @mousedown="create('BRANCH', $event)"
          ><i class="bpmn-icon-gateway-xor" />{{ tr('workflow.canvas.009') }}</ElButton
        >
        <ElButton class="bpmn-stencil-item" @mousedown="create('END', $event)"
          ><i class="bpmn-icon-end-event-none" />{{ tr('workflow.canvas.010') }}</ElButton
        >
        <ElButton class="bpmn-stencil-item" @mousedown="create('PARALLEL', $event)"
          ><i class="bpmn-icon-gateway-parallel" />{{ tr('workflow.advanced.015') }}</ElButton
        >
        <ElButton class="bpmn-stencil-item" @mousedown="create('INCLUSIVE', $event)"
          ><i class="bpmn-icon-gateway-or" />{{ tr('workflow.advanced.016') }}</ElButton
        >
        <ElButton class="bpmn-stencil-item" @mousedown="create('SUBPROCESS', $event)"
          ><i class="bpmn-icon-subprocess-expanded" />{{ tr('workflow.advanced.017') }}</ElButton
        >
        <ElButton class="bpmn-stencil-item" @mousedown="create('SCRIPT', $event)"
          ><i class="bpmn-icon-script-task" />{{ tr('workflow.advanced.018') }}</ElButton
        >
      </div>
      <div ref="host" class="bpmn-host" />
    </div>
    <div class="bpmn-status">{{ tr('workflow.canvas.011') }}</div>
  </div>
</template>
<style scoped>
.bpmn-workspace {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  height: 100%;
  background: var(--el-bg-color);
  overflow: hidden;
}
.bpmn-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
  flex-wrap: wrap;
}
.bpmn-toolbar-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.bpmn-toolbar-spacer {
  flex: 1;
}
.bpmn-zoom {
  font-size: 12px;
  min-width: 36px;
  text-align: center;
}
.bpmn-stage {
  display: flex;
  flex: 1;
  min-height: 0;
  position: relative;
}
.bpmn-host {
  flex: 1;
  min-width: 0;
  min-height: 200px;
  background: radial-gradient(var(--el-border-color) 0.7px, transparent 0.7px);
  background-size: 18px 18px;
}
.bpmn-readonly :deep(.djs-context-pad) {
  display: none;
}
.bpmn-stencil {
  width: 96px;
  padding: 12px 8px;
  border-right: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-extra-light);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
  overflow-x: hidden;
}
.bpmn-stencil-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.bpmn-stencil-item.el-button {
  height: 48px;
  width: 100%;
  min-width: 0;
  margin: 0;
  padding: 8px;
  cursor: grab;
}
.bpmn-stencil-item :deep(span) {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.bpmn-stencil-item i {
  font-size: 22px;
}
.bpmn-status {
  padding: 8px 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border-top: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}
.bpmn-host :deep(.djs-palette) {
  display: none;
}
.bpmn-host :deep(.djs-container) {
  font-family: inherit;
}
.bpmn-host :deep(.djs-label) {
  font-family: inherit !important;
  font-size: 13px !important;
}
.bpmn-host :deep(.djs-context-pad) {
  margin-top: -32px;
}
.bpmn-host :deep(.wf-node-caption) {
  font-size: 12px;
  line-height: 15px;
  max-height: 30px;
  overflow: hidden;
  text-align: center;
  color: var(--el-text-color-regular);
  background: var(--el-bg-color);
  border-radius: 2px;
  pointer-events: auto;
}
.bpmn-host :deep(.wf-captioned .djs-visual > text) {
  transform: translateY(-13px);
}
.bpmn-host :deep(.wf-invalid .djs-visual > :first-child),
.bpmn-host :deep(.wf-REJECTED .djs-visual > :first-child) {
  stroke: var(--el-color-danger) !important;
}
.bpmn-host :deep(.wf-PENDING .djs-visual > :first-child) {
  stroke: #b77900 !important;
  fill: #fff8df !important;
}
.bpmn-host :deep(.wf-APPROVED .djs-visual > :first-child) {
  stroke: var(--el-color-success) !important;
}
.bpmn-host :deep(.wf-CANCELLED .djs-visual > :first-child) {
  stroke: var(--el-text-color-secondary) !important;
}
@media (max-width: 1280px) {
  .bpmn-stencil {
    width: 80px;
  }
}
</style>
