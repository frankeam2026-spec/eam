import type { TreeSelectNode } from '../components/TreeSelect.vue';
export type FilterCondition = { field: string; operator: string; values: string[] };
export type FilterField = {
  key: string;
  label: string;
  type: 'text' | 'choice' | 'date' | 'number' | 'tree';
  dateOnly?: boolean;
  pathValue?: boolean;
  options?: string[];
  nodes?: TreeSelectNode[];
};
export function conditionLabel(condition: FilterCondition, fields: FilterField[]) {
  const field = fields.find((item) => item.key === condition.field);
  const operators: Record<string, string> = {
    contains: '包含',
    eq: '为',
    in: '为',
    range: '范围',
    empty: '为空',
    notEmpty: '不为空',
  };
  if (condition.operator === 'range') {
    const [lower, upper] = condition.values.map((v) => (v || '').replace('T', ' '));
    return `${field?.label ?? condition.field} ${lower && upper ? `${lower} 至 ${upper}` : lower ? `≥ ${lower}` : `≤ ${upper}`}`;
  }
  return `${field?.label ?? condition.field} ${operators[condition.operator] ?? ''} ${condition.values.map((v) => v.replace('T', ' ')).join(condition.operator === 'range' ? ' 至 ' : '、')}`.trim();
}

/** Filters already-authorized collections used by the local list modules. */
export function matchesAdvanced(conditions: FilterCondition[], read: (key: string) => unknown) {
  return conditions.every((c) => {
    const value = read(c.field);
    const text = value == null ? '' : String(value);
    switch (c.operator) {
      case 'empty':
        return !text.trim() || text === '—';
      case 'notEmpty':
        return !!text.trim() && text !== '—';
      case 'contains':
        return text.toLocaleLowerCase().includes((c.values[0] || '').toLocaleLowerCase());
      case 'eq':
        return text === c.values[0];
      case 'in':
        return c.values.includes(text);
      case 'range': {
        if (!text || text === '—') return false;
        const numeric = /^-?\d+(\.\d+)?$/.test(text);
        const comparable = numeric ? Number(text) : text.replace('T', ' ');
        const bound = (v: string) => (numeric ? Number(v) : v.replace('T', ' '));
        return (
          (!c.values[0] || comparable >= bound(c.values[0])) &&
          (!c.values[1] || comparable <= bound(c.values[1]))
        );
      }
      default:
        return false;
    }
  });
}
