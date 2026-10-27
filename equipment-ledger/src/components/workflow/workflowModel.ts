import type { WorkflowNode } from '../../workflow';
import { bpmnTypes, edgeKey } from './workflowDiagram';

// The untyped boundary belongs to bpmn-moddle; application code consumes WorkflowNode records.
export const attribute = (object: any, key: string) =>
  object.get?.('eam:' + key) ?? object.$attrs?.['eam:' + key];
export function businessElements(definitions: any): any[] {
  const processes = (definitions.rootElements || []).filter((e: any) => e.$type === 'bpmn:Process');
  if (processes.length !== 1) throw Error('每份模板只能包含一个业务流程');
  const elements: any[] = [];
  function walk(parent: any, depth: number) {
    if (depth > 8) throw Error('子流程不能超过8层');
    for (const e of parent.flowElements || []) {
      elements.push(e);
      if (e.$type === 'bpmn:SubProcess') walk(e, depth + 1);
    }
  }
  walk(processes[0], 0);
  return elements;
}
function references(value?: string): string[] {
  if (!value) return [];
  try {
    const rows = JSON.parse(value);
    if (
      Array.isArray(rows) &&
      rows.length <= 100 &&
      rows.every((id) => typeof id === 'string' && /^[1-9][0-9]*$/.test(id))
    )
      return rows;
  } catch {}
  throw Error('BPMN审批人员或角色配置无效');
}
export function readWorkflowNodes(definitions: any, previous: WorkflowNode[] = []): WorkflowNode[] {
  const elements = businessElements(definitions),
    oldNodes = new Map(previous.map((n) => [n.key, n]));
  const supported = new Set(Object.values(bpmnTypes));
  const flows = elements.filter((e) => e.$type === 'bpmn:SequenceFlow');
  if (flows.some((flow) => !flow.sourceRef || !flow.targetRef)) throw Error('BPMN存在无效连接');
  const nodes = elements.filter((e) => supported.has(e.$type));
  if (nodes.length > 100) throw Error('流程最多100个节点');
  if (
    elements.some(
      (e) =>
        !supported.has(e.$type) &&
        !['bpmn:SequenceFlow', 'bpmn:TextAnnotation', 'bpmn:Association'].includes(e.$type),
    ) ||
    nodes.some((e) => e.eventDefinitions?.length || e.loopCharacteristics || e.triggeredByEvent)
  )
    throw Error('此BPMN包含尚未接入的节点或执行方式');
  return nodes.map((e) => {
    const old = oldNodes.get(e.id),
      type = Object.entries(bpmnTypes).find(
        ([, value]) => value === e.$type,
      )![0] as WorkflowNode['type'];
    if (type === 'SCRIPT' && e.scriptFormat && !['javascript', 'js'].includes(e.scriptFormat))
      throw Error('脚本节点仅支持JavaScript');
    const outgoing = flows.filter((flow) => flow.sourceRef.id === e.id),
      isGateway = ['BRANCH', 'PARALLEL', 'INCLUSIVE'].includes(type);
    const branches = isGateway
      ? outgoing
          .map((flow) => {
            const branch = old?.branches.find(
              (b) => edgeKey(e.id, b.key) === flow.id || b.key === flow.id,
            );
            return {
              key: attribute(flow, 'branchKey') || branch?.key || flow.id,
              name: flow.name || branch?.name || '新条件',
              target: flow.targetRef.id,
              fallback: type !== 'PARALLEL' && e.default?.id === flow.id,
              expression:
                type === 'PARALLEL'
                  ? ''
                  : flow.conditionExpression?.body || branch?.expression || '',
            };
          })
          .sort(
            (a, b) =>
              Number(a.fallback) - Number(b.fallback) ||
              ((old?.branches.findIndex((branch) => branch.key === a.key) ?? -1) + 1 || 999) -
                ((old?.branches.findIndex((branch) => branch.key === b.key) ?? -1) + 1 || 999),
          )
      : [];
    return {
      key: e.id,
      type,
      name:
        e.name ||
        {
          START: '开始',
          END: '结束',
          APPROVAL: '审批节点',
          BRANCH: '条件分支',
          PARALLEL: '并行网关',
          INCLUSIVE: '包容网关',
          SUBPROCESS: '子流程',
          SCRIPT: '脚本计算',
        }[type],
      next: type === 'END' || isGateway ? null : outgoing[0]?.targetRef.id || null,
      parentKey: e.$parent?.$type === 'bpmn:SubProcess' ? e.$parent.id : null,
      approvalMode: attribute(e, 'approvalMode') || old?.approvalMode || 'ANY',
      users:
        type === 'APPROVAL'
          ? attribute(e, 'users') !== undefined
            ? references(attribute(e, 'users'))
            : old?.users || []
          : [],
      roles:
        type === 'APPROVAL'
          ? attribute(e, 'roles') !== undefined
            ? references(attribute(e, 'roles'))
            : old?.roles || []
          : [],
      branches,
      script:
        type === 'SCRIPT'
          ? {
              language: 'javascript',
              source:
                e.script ??
                old?.script?.source ??
                'return decimal.sum(input.lines.map(line => line.quantity));',
              outputKey: attribute(e, 'outputKey') || old?.script?.outputKey || 'computed_' + e.id,
              outputType: attribute(e, 'outputType') || old?.script?.outputType || 'NUMBER',
            }
          : null,
    };
  });
}
export function topology(nodes: WorkflowNode[]): string {
  return JSON.stringify(
    nodes
      .map((n) => ({
        key: n.key,
        type: n.type,
        parent: n.parentKey || '',
        next: ['BRANCH', 'PARALLEL', 'INCLUSIVE'].includes(n.type) ? null : n.next,
        branches: n.branches.map((b) => ({ key: b.key, target: b.target, fallback: b.fallback })),
      }))
      .sort((a, b) => a.key.localeCompare(b.key)),
  );
}
