import { describe, it, expect } from 'vitest';
import { diagramXml, layoutDiagram, edgeKey } from '../components/workflow/workflowDiagram';
import type { WorkflowNode } from '../workflow';
import { readWorkflowNodes } from '../components/workflow/workflowModel';

const node = (key: string, type: WorkflowNode['type'], next: string | null): WorkflowNode => ({
  key,
  type,
  next,
  name: key,
  approvalMode: 'ANY',
  users: [],
  roles: [],
  branches: [],
});
describe('BPMN editing artifact', () => {
  it('lays out two branches inserted after the start in execution order', async () => {
    const start = node('start', 'START', 'newBranch'),
      first = node('newBranch', 'BRANCH', null),
      second = node('oldBranch', 'BRANCH', null),
      approval = node('approval', 'APPROVAL', 'end'),
      end = node('end', 'END', null);
    for (const [n, target] of [
      [first, 'oldBranch'],
      [second, 'approval'],
    ] as const)
      n.branches = [
        {
          key: 'match',
          name: '条件一',
          target,
          fallback: false,
          expression: 'SUM(lines.quantity)>0',
        },
        { key: 'other', name: '其他情况', target, fallback: true, expression: '' },
      ];
    const xml = await layoutDiagram([start, first, second, approval, end]);
    const doc = new DOMParser().parseFromString(xml, 'application/xml');
    const positions = Object.fromEntries(
      [...doc.getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/DI', 'BPMNShape')].map(
        (s) => [s.getAttribute('bpmnElement'), Number(s.firstElementChild!.getAttribute('x'))],
      ),
    );
    expect(positions.start).toBeLessThan(positions.newBranch!);
    expect(positions.newBranch).toBeLessThan(positions.oldBranch!);
    expect(positions.oldBranch).toBeLessThan(positions.approval!);
    expect(positions.approval).toBeLessThan(positions.end!);
    const edges = [
      ...doc.getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/DI', 'BPMNEdge'),
    ];
    const points = (id: string) =>
      [...(edges.find((e) => e.getAttribute('bpmnElement') === id)?.children || [])]
        .filter((p) => p.localName === 'waypoint')
        .map((p) => [p.getAttribute('x'), p.getAttribute('y')]);
    expect(points(edgeKey('newBranch', 'match'))).not.toEqual(
      points(edgeKey('newBranch', 'other')),
    );
  });
  it('keeps two branches with a common successor and identifies the default edge', async () => {
    const start = node('start', 'START', 'choice'),
      choice = node('choice', 'BRANCH', null),
      a = node('a', 'APPROVAL', 'end'),
      b = node('b', 'APPROVAL', 'end'),
      end = node('end', 'END', null);
    choice.branches = [
      {
        key: 'large',
        name: '金额 > 100 & "审批"',
        target: 'a',
        fallback: false,
        expression: 'x > 100',
      },
      { key: 'other', name: '其他', target: 'b', fallback: true, expression: '' },
    ];
    const document = new DOMParser().parseFromString(
      await layoutDiagram([start, choice, a, b, end]),
      'application/xml',
    );
    expect(document.querySelector('parsererror')).toBeNull();
    const flows = [
      ...document.getElementsByTagNameNS(
        'http://www.omg.org/spec/BPMN/20100524/MODEL',
        'sequenceFlow',
      ),
    ];
    expect(flows.filter((e) => e.getAttribute('targetRef') === 'end')).toHaveLength(2);
    const edgeIds = [
      ...document.getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/DI', 'BPMNEdge'),
    ].map((e) => e.getAttribute('bpmnElement'));
    expect(edgeIds.sort()).toEqual(flows.map((e) => e.getAttribute('id')).sort());
    expect(
      document
        .getElementsByTagNameNS(
          'http://www.omg.org/spec/BPMN/20100524/MODEL',
          'exclusiveGateway',
        )[0]
        ?.getAttribute('default'),
    ).toBe(edgeKey('choice', 'other'));
    expect(
      flows.find((e) => e.getAttribute('id') === edgeKey('choice', 'large'))?.getAttribute('name'),
    ).toBe('金额 > 100 & "审批"');
    const shapes = [
      ...document.getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/DI', 'BPMNShape'),
    ];
    const y = (key: string) =>
      shapes
        .find((e) => e.getAttribute('bpmnElement') === key)!
        .firstElementChild!.getAttribute('y');
    expect(y('a')).not.toBe(y('b'));
  });
  it('preserves nested scope and script configuration in BPMN exports', async () => {
    const start = node('start', 'START', 'sub'),
      sub = node('sub', 'SUBPROCESS', 'end'),
      end = node('end', 'END', null);
    const innerStart = { ...node('innerStart', 'START', 'calc'), parentKey: 'sub' },
      innerEnd = { ...node('innerEnd', 'END', null), parentKey: 'sub' };
    const script: WorkflowNode = {
      ...node('calc', 'SCRIPT', 'innerEnd'),
      parentKey: 'sub',
      script: {
        language: 'javascript',
        source: 'return "A < B & C";',
        outputKey: 'computed_text',
        outputType: 'TEXT',
      },
    };
    const xml = await layoutDiagram([start, sub, end, innerStart, script, innerEnd]);
    const document = new DOMParser().parseFromString(xml, 'application/xml');
    expect(document.querySelector('parsererror')).toBeNull();
    const nested = document.getElementsByTagNameNS(
      'http://www.omg.org/spec/BPMN/20100524/MODEL',
      'subProcess',
    )[0]!;
    const scriptElement = nested.getElementsByTagNameNS(
      'http://www.omg.org/spec/BPMN/20100524/MODEL',
      'scriptTask',
    )[0]!;
    expect(scriptElement.getAttributeNS('https://eam.local/workflow', 'outputKey')).toBe(
      'computed_text',
    );
    expect(scriptElement.textContent).toContain('A < B & C');
    const shape = [
      ...document.getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/DI', 'BPMNShape'),
    ].find((e) => e.getAttribute('bpmnElement') === 'sub');
    expect(shape?.getAttribute('isExpanded')).toBe('true');
  });
  it('reads approvers inside a collapsed subprocess from semantics rather than visible shapes', () => {
    const process: any = { $type: 'bpmn:Process', id: 'p', flowElements: [] };
    const sub: any = {
      $type: 'bpmn:SubProcess',
      id: 'sub',
      name: '子流程',
      $parent: process,
      flowElements: [],
    };
    sub.flowElements = [
      {
        $type: 'bpmn:UserTask',
        id: 'inner',
        name: '内部审批',
        $parent: sub,
        $attrs: {
          'eam:users': '["9007199254740993"]',
          'eam:roles': '[]',
          'eam:approvalMode': 'ALL',
        },
      },
    ];
    process.flowElements = [sub];
    const nodes = readWorkflowNodes({ rootElements: [process] });
    expect(nodes.find((n) => n.key === 'inner')).toMatchObject({
      parentKey: 'sub',
      users: ['9007199254740993'],
      approvalMode: 'ALL',
    });
  });
});
