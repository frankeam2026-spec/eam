import { describe, it, expect } from 'vitest';
// @ts-expect-error bpmn-moddle 10 exports element types but omits its runtime factory declaration.
import { BpmnModdle } from 'bpmn-moddle';
import BpmnFactory from 'bpmn-js/lib/features/modeling/BpmnFactory';
import { Ids } from 'ids';
import {
  canvasShape,
  canvasConnection,
  diagramWaypoints,
  flowLabelBounds,
} from '../components/workflow/workflowCanvasModel';

describe('workflow canvas identity and routing', () => {
  it('assigns the real BPMN node and connection IDs rather than only visual IDs', () => {
    const moddle = new BpmnModdle();
    (moddle as any).ids = new Ids([32, 36, 1]);
    const factory = new BpmnFactory(moddle);
    expect(
      (canvasShape(factory, { key: 'inserted_branch', type: 'BRANCH' }).businessObject as any).id,
    ).toBe('inserted_branch');
    expect((canvasConnection(factory, 'edge_start__next').businessObject as any).id).toBe(
      'edge_start__next',
    );
  });
  it('does not mistake a label element for an extra zero-coordinate waypoint', () => {
    const xml =
      '<edge xmlns:di="http://www.omg.org/spec/DD/20100524/DI"><di:waypoint x="100" y="70"/><di:waypoint x="200" y="70"/><label><bounds x="120" y="30"/></label></edge>';
    expect(
      diagramWaypoints(new DOMParser().parseFromString(xml, 'application/xml').documentElement),
    ).toEqual([
      { x: 100, y: 70 },
      { x: 200, y: 70 },
    ]);
  });
  it('places two flow captions outside nodes and outside each other', () => {
    const points = [
        { x: 100, y: 70 },
        { x: 200, y: 70 },
      ],
      obstacles = [
        { x: 0, y: 30, width: 100, height: 80 },
        { x: 200, y: 30, width: 100, height: 80 },
      ];
    const a = flowLabelBounds(points, '申请数量合计大于0', obstacles, []),
      b = flowLabelBounds(points, '其他情况', obstacles, [a]);
    const overlap = (x: typeof a, y: typeof a) =>
      x.x < y.x + y.width && x.x + x.width > y.x && x.y < y.y + y.height && x.y + x.height > y.y;
    expect(obstacles.some((o) => overlap(a, o) || overlap(b, o))).toBe(false);
    expect(overlap(a, b)).toBe(false);
  });
});
