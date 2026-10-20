import { tr } from '../i18n';
import type { TreeSelectNode } from '../components/TreeSelect.vue';
export type FilterCondition = {
  field: string;
  operator: string;
  values: string[];
  /** UI selection only; values contains the deduplicated query scope, including descendants. */
  selectedNodeIds?: string[];
  valueLabels?: Record<string, string>;
};
export type FilterField = {
  key: string;
  label: string;
  type: 'text' | 'choice' | 'date' | 'number' | 'tree';
  dateOnly?: boolean;
  pathValue?: boolean;
  options?: string[];
  optionLabels?: Record<string, string>;
  businessSource?: {
    object: import('../businessConfig').BusinessObject;
    source: string;
    department?: boolean;
  };
  nodes?: TreeSelectNode[];
};
export type FilterTreeOption = {
  id: string;
  label: string;
  output: string;
  disabled: boolean;
  children: FilterTreeOption[];
};

/** Preserve existing field value mappings; no business field names in the renderer. */
export function filterTreeOptions(field: FilterField): FilterTreeOption[] {
  const nodes = field.nodes ?? [];
  const ids = new Set(nodes.map((node) => node.id));
  function branch(parentId: string, path: string[], visited: Set<string>): FilterTreeOption[] {
    return nodes
      .filter((node) => node.parentId === parentId || (!parentId && !ids.has(node.parentId)))
      .filter((node) => !visited.has(node.id))
      .map((node) => {
        const names = [...path, node.name];
        return {
          id: node.id,
          label: field.pathValue ? names.join(' · ') : node.name,
          output: node.value ?? (field.pathValue ? names.join(' · ') : node.name),
          disabled: !!node.disabled || node.status === '停用',
          children: branch(node.id, names, new Set([...visited, node.id])),
        };
      });
  }
  return branch('', [], new Set());
}
export function flattenFilterTree(nodes: FilterTreeOption[]): FilterTreeOption[] {
  return nodes.flatMap((node) => [node, ...flattenFilterTree(node.children)]);
}
const operators: Record<FilterField['type'], string> = {
  text: 'contains',
  choice: 'in',
  tree: 'in',
  date: 'range',
  number: 'range',
};
export function filterDraft(field: FilterField, existing?: FilterCondition): FilterCondition {
  const values =
    existing && !['empty', 'notEmpty'].includes(existing.operator) ? [...existing.values] : [];
  const result: FilterCondition = {
    field: field.key,
    operator: operators[field.type],
    values,
    ...(existing?.valueLabels ? { valueLabels: { ...existing.valueLabels } } : {}),
  };
  if (field.type === 'tree') {
    const options = flattenFilterTree(filterTreeOptions(field));
    const selected =
      existing?.selectedNodeIds ??
      options.filter((n) => values.includes(n.output)).map((n) => n.id);
    result.selectedNodeIds = selected.filter((id) =>
      options.some((n) => n.id === id && !n.disabled),
    );
  }
  return result;
}
export function prepareFilter(field: FilterField, draft: FilterCondition): FilterCondition {
  const result = filterDraft(field, draft);
  result.values = result.values.map((v) => String(v ?? '').trim());
  if (field.type === 'tree') {
    const nodes = flattenFilterTree(filterTreeOptions(field));
    result.values = [
      ...new Set(
        nodes
          .filter((n) => result.selectedNodeIds?.includes(n.id))
          .flatMap((n) => flattenFilterTree([n]).map((child) => child.output)),
      ),
    ];
  }
  return result;
}
export function filterError(field: FilterField, condition: FilterCondition): string {
  const [lower, upper] = condition.values;
  if (field.type === 'number' && condition.values.some((v) => v && !Number.isFinite(Number(v))))
    return tr('ui.validation.message32');
  if (
    condition.operator === 'range' &&
    lower &&
    upper &&
    (field.type === 'number' ? Number(lower) > Number(upper) : lower > upper)
  )
    return field.type === 'number' ? '最小值不能大于最大值' : '开始时间不能晚于结束时间';
  return '';
}
export function conditionLabel(condition: FilterCondition, fields: FilterField[]) {
  const field = fields.find((item) => item.key === condition.field);
  const label = field?.label ?? condition.field;
  if (field?.type === 'tree' && condition.selectedNodeIds) {
    const selected = new Set(condition.selectedNodeIds);
    const labels = flattenFilterTree(filterTreeOptions(field))
      .filter((n) => selected.has(n.id))
      .map((n) => n.label);
    return `${label}：${labels.join('、')}（含下级）`;
  }
  if (condition.operator === 'range') {
    const [lower, upper] = condition.values.map((v) => (v || '').replace('T', ' '));
    return `${label}：${lower && upper ? `${lower} 至 ${upper}` : lower ? `≥ ${lower}` : `≤ ${upper}`}`;
  }
  const prefix: Record<string, string> = { contains: '包含 ', empty: '为空', notEmpty: '不为空' };
  return `${label}：${prefix[condition.operator] ?? ''}${condition.values.map((v) => condition.valueLabels?.[v] ?? field?.optionLabels?.[v] ?? v).join('、') + (field?.businessSource?.department ? '（含下级）' : '')}`;
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
        const bound = (v: string, upper = false) =>
          numeric
            ? Number(v)
            : v.replace('T', ' ') +
              (upper && /^\d{4}-\d{2}-\d{2}$/.test(v) ? ' 23:59:59.999999999' : '');
        return (
          (!c.values[0] || comparable >= bound(c.values[0])) &&
          (!c.values[1] || comparable <= bound(c.values[1], true))
        );
      }
      default:
        return false;
    }
  });
}
