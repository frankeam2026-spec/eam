import { layoutProcess } from 'bpmn-auto-layout';
import type { WorkflowNode } from '../../workflow';
export const bpmnTypes: Record<WorkflowNode['type'], string> = {
  START: 'bpmn:StartEvent',
  END: 'bpmn:EndEvent',
  APPROVAL: 'bpmn:UserTask',
  BRANCH: 'bpmn:ExclusiveGateway',
  PARALLEL: 'bpmn:ParallelGateway',
  INCLUSIVE: 'bpmn:InclusiveGateway',
  SUBPROCESS: 'bpmn:SubProcess',
  SCRIPT: 'bpmn:ScriptTask',
};
export const gateway = (node: WorkflowNode) =>
  ['BRANCH', 'PARALLEL', 'INCLUSIVE'].includes(node.type);
export const edgeKey = (source: string, branch: string) => `edge_${source}__${branch}`;
const xml = (s: string) =>
  s
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
export function diagramXml(nodes: WorkflowNode[]): string {
  function contents(parent: string, depth = 0): string {
    if (depth > 8) throw Error('子流程不能超过8层');
    const members = nodes.filter((n) => (n.parentKey || '') === parent);
    const flows = members.flatMap((n) =>
      gateway(n)
        ? n.branches.map((b) => ({
            id: edgeKey(n.key, b.key),
            branchKey: b.key,
            source: n.key,
            target: b.target,
            name: b.name,
            expression: n.type !== 'PARALLEL' && !b.fallback ? b.expression : '',
          }))
        : n.next
          ? [
              {
                id: edgeKey(n.key, 'next'),
                branchKey: '',
                source: n.key,
                target: n.next,
                name: '',
                expression: '',
              },
            ]
          : [],
    );
    const elements = members
      .map((n) => {
        const name = bpmnTypes[n.type].split(':')[1]!;
        const tag = 'bpmn:' + name[0]!.toLowerCase() + name.slice(1);
        const fallback = n.branches.find((b) => b.fallback);
        const attrs = `id="${xml(n.key)}" name="${xml(n.name)}"${fallback && gateway(n) && n.type !== 'PARALLEL' ? ` default="${xml(edgeKey(n.key, fallback.key))}"` : ''}`;
        const metadata =
          n.type === 'APPROVAL'
            ? ` eam:approvalMode="${n.approvalMode}" eam:users="${xml(JSON.stringify(n.users))}" eam:roles="${xml(JSON.stringify(n.roles))}"`
            : '';
        const references =
          flows
            .filter((f) => f.target === n.key)
            .map((f) => `<bpmn:incoming>${xml(f.id)}</bpmn:incoming>`)
            .join('') +
          flows
            .filter((f) => f.source === n.key)
            .map((f) => `<bpmn:outgoing>${xml(f.id)}</bpmn:outgoing>`)
            .join('');
        if (n.type === 'SUBPROCESS')
          return `<${tag} ${attrs}>${references}${contents(n.key, depth + 1)}</${tag}>`;
        if (n.type === 'SCRIPT')
          return `<${tag} ${attrs} scriptFormat="javascript" eam:outputKey="${xml(n.script?.outputKey || '')}" eam:outputType="${n.script?.outputType || 'NUMBER'}">${references}<bpmn:script>${xml(n.script?.source || '')}</bpmn:script></${tag}>`;
        return `<${tag} ${attrs}${metadata}>${references}</${tag}>`;
      })
      .join('');
    return (
      elements +
      flows
        .map(
          (e) =>
            `<bpmn:sequenceFlow id="${xml(e.id)}" sourceRef="${xml(e.source)}" targetRef="${xml(e.target)}" name="${xml(e.name)}"${e.branchKey ? ` eam:branchKey="${xml(e.branchKey)}"` : ''}>${e.expression ? `<bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${xml(e.expression)}</bpmn:conditionExpression>` : ''}</bpmn:sequenceFlow>`,
        )
        .join('')
    );
  }
  const seed = nodes
    .filter((n) => n.type === 'SUBPROCESS')
    .map(
      (n) =>
        `<bpmndi:BPMNShape id="seed_${xml(n.key)}" bpmnElement="${xml(n.key)}" isExpanded="true"><dc:Bounds x="0" y="0" width="300" height="200"/></bpmndi:BPMNShape>`,
    )
    .join('');
  return `<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:eam="https://eam.local/workflow" id="Definitions_EAM" targetNamespace="https://eam.local/workflow"><bpmn:process id="Process_EAM" isExecutable="false">${contents('')}</bpmn:process>${seed ? `<bpmndi:BPMNDiagram id="Seed"><bpmndi:BPMNPlane id="SeedPlane" bpmnElement="Process_EAM">${seed}</bpmndi:BPMNPlane></bpmndi:BPMNDiagram>` : ''}</bpmn:definitions>`;
}
export async function layoutDiagram(nodes: WorkflowNode[]): Promise<string> {
  const result = await layoutProcess(diagramXml(nodes));
  const document = new DOMParser().parseFromString(result, 'application/xml');
  const di = 'http://www.omg.org/spec/BPMN/20100524/DI',
    pointsNs = 'http://www.omg.org/spec/DD/20100524/DI';
  const shapes = new Map(
    [...document.getElementsByTagNameNS(di, 'BPMNShape')].map((s) => {
      const b = s.firstElementChild!;
      return [
        s.getAttribute('bpmnElement')!,
        {
          x: Number(b.getAttribute('x')),
          y: Number(b.getAttribute('y')),
          width: Number(b.getAttribute('width')),
          height: Number(b.getAttribute('height')),
        },
      ] as const;
    }),
  );
  const edges = new Map(
    [...document.getElementsByTagNameNS(di, 'BPMNEdge')].map((e) => [
      e.getAttribute('bpmnElement'),
      e,
    ]),
  );
  const groups = new Map<string, Element[]>();
  for (const flow of document.getElementsByTagNameNS(
    'http://www.omg.org/spec/BPMN/20100524/MODEL',
    'sequenceFlow',
  )) {
    const key = flow.getAttribute('sourceRef') + ':' + flow.getAttribute('targetRef');
    groups.set(key, [...(groups.get(key) || []), flow]);
  }
  let lane = 0;
  for (const flows of groups.values()) {
    if (flows.length < 2) continue;
    const source = shapes.get(flows[0]!.getAttribute('sourceRef')!),
      target = shapes.get(flows[0]!.getAttribute('targetRef')!);
    if (!source || !target) continue;
    for (const flow of flows.slice(1)) {
      const edge = edges.get(flow.getAttribute('id'));
      if (!edge) continue;
      const y = Math.max(source.y + source.height, target.y + target.height) + 40 * ++lane;
      for (const child of [...edge.children])
        if (child.localName === 'waypoint') edge.removeChild(child);
      const label = edge.firstElementChild;
      for (const p of [
        { x: source.x + source.width / 2, y: source.y + source.height },
        { x: source.x + source.width / 2, y },
        { x: target.x + target.width / 2, y },
        { x: target.x + target.width / 2, y: target.y + target.height },
      ]) {
        const point = document.createElementNS(pointsNs, 'di:waypoint');
        point.setAttribute('x', String(p.x));
        point.setAttribute('y', String(p.y));
        edge.insertBefore(point, label);
      }
    }
  }
  return new XMLSerializer().serializeToString(document);
}
