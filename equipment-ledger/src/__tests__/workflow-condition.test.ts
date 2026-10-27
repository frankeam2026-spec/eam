import { describe, expect, it } from 'vitest';
import { conditionText, type Condition } from '../workflow';

const expr = (op: string, children: Condition[] = []): Condition => ({
  op,
  children,
  field: null,
  literal: null,
  type: 'BOOLEAN',
});
const field = (name: string): Condition => ({ ...expr('FIELD'), field: name });
const literal = (value: string, type = 'NUMBER'): Condition => ({
  ...expr('LITERAL'),
  literal: value,
  type,
});

describe('workflow visual expression serialization', () => {
  it('preserves decimal and reference precision without converting to JS numbers', () => {
    expect(conditionText(expr('>=', [field('amount'), literal('5000.001')]))).toBe(
      'amount >= 5000.001',
    );
    expect(
      conditionText(expr('IN', [field('applicantId'), literal('9007199254740993', 'REFERENCE')])),
    ).toBe('applicantId IN (9007199254740993)');
  });
  it('keeps nested logic and same-line predicates grouped', () => {
    const condition = expr('ANY', [
      expr('AND', [
        expr('STARTS_WITH', [field('lines.code'), literal('WX-', 'TEXT')]),
        expr('>', [field('lines.quantity'), literal('10')]),
      ]),
    ]);
    expect(conditionText(expr('NOT', [condition]))).toBe(
      'NOT (ANY(lines, (lines.code STARTS_WITH "WX-" AND lines.quantity > 10)))',
    );
  });
  it('escapes text literals and keeps explicit null checks', () => {
    expect(conditionText(literal('A"B\\C', 'TEXT'))).toBe('"A\\"B\\\\C"');
    expect(conditionText(expr('IS_NOT_NULL', [field('applicantDepartmentId')]))).toBe(
      'applicantDepartmentId IS NOT NULL',
    );
  });
});
