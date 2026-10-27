import type { WorkflowNode } from '../../workflow';
import { bpmnTypes } from './workflowDiagram';

// diagram-js IDs and BPMN semantic IDs must be identical. An ID on the visual
// shape alone leaves bpmn-js generating a different businessObject.id.
export function canvasShape(
  factory: { create: (type: string, attrs: Record<string, unknown>) => unknown },
  node: Pick<WorkflowNode, 'key' | 'type'>,
) {
  return {
    type: bpmnTypes[node.type],
    businessObject: factory.create(bpmnTypes[node.type], { id: node.key }),
    ...(node.type === 'SUBPROCESS' ? { isExpanded: true } : {}),
  };
}
export function canvasConnection(
  factory: { create: (type: string, attrs: Record<string, unknown>) => unknown },
  key: string,
) {
  return {
    type: 'bpmn:SequenceFlow',
    businessObject: factory.create('bpmn:SequenceFlow', { id: key }),
  };
}
export function diagramWaypoints(edge: Element) {
  return [...edge.children]
    .filter((point) => point.localName === 'waypoint')
    .map((point) => ({ x: Number(point.getAttribute('x')), y: Number(point.getAttribute('y')) }));
}

export type CanvasBounds = { x: number; y: number; width: number; height: number };
const overlaps = (a: CanvasBounds, b: CanvasBounds) =>
  a.x < b.x + b.width + 8 &&
  a.x + a.width + 8 > b.x &&
  a.y < b.y + b.height + 8 &&
  a.y + a.height + 8 > b.y;
export function flowLabelBounds(
  points: { x: number; y: number }[],
  text: string,
  obstacles: CanvasBounds[],
  occupied: CanvasBounds[],
): CanvasBounds {
  const width = Math.min(180, Math.max(100, [...text].length * 7)),
    height = 40;
  const segments = points
    .slice(1)
    .map((p, i) => ({
      a: points[i]!,
      b: p,
      length: Math.abs(p.x - points[i]!.x) + Math.abs(p.y - points[i]!.y),
    }))
    .sort((a, b) => b.length - a.length);
  const clear = (box: CanvasBounds) =>
    !obstacles.some((o) => overlaps(box, o)) && !occupied.some((o) => overlaps(box, o));
  for (const s of segments) {
    const x = (s.a.x + s.b.x) / 2 - width / 2,
      y = (s.a.y + s.b.y) / 2;
    for (const dy of [-height - 12, 12, -height - 40, 40]) {
      const box = { x, y: y + dy, width, height };
      if (clear(box)) return box;
    }
  }
  const anchor = segments[0],
    x = anchor ? (anchor.a.x + anchor.b.x) / 2 - width / 2 : 0;
  let y = Math.min(0, ...obstacles.map((o) => o.y)) - height - 16;
  while (!clear({ x, y, width, height })) y -= height + 12;
  return { x, y, width, height };
}
