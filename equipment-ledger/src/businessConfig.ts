import { tr } from './i18n';
import { request } from './api';
import type { FilterField } from './query/advancedFilters';
export const EXTENSION_GROUP = '扩展信息';
export type BusinessObject =
  | 'equipment'
  | 'repair_report'
  | 'repair_process'
  | 'inventory_outbound'
  | 'inventory_receipt'
  | 'maintenance_plan'
  | 'maintenance_task'
  | 'inspection_daily_plan'
  | 'inspection_daily_task'
  | 'inspection_periodic_plan'
  | 'inspection_periodic_task';
export type FieldValue = string | boolean | string[] | null;
export type CustomRecord = {
  customFields?: Record<string, FieldValue>;
  customFieldLabels?: Record<string, string>;
  customSchemaVersion?: number;
};
export type FieldType =
  | 'TEXT'
  | 'LONG_TEXT'
  | 'NUMBER'
  | 'DATE'
  | 'DATETIME'
  | 'CHOICE'
  | 'MULTI_CHOICE'
  | 'BOOLEAN'
  | 'USER'
  | 'DEPARTMENT';
export type FieldRule = {
  mode: 'all' | 'any';
  items: { field: string; operator: 'eq' | 'ne' | 'in' | 'range'; values: string[] }[];
};
export type FieldOption = { id: string; name: string; enabled: boolean; sortOrder: number };
export type BusinessField = {
  id: string;
  name: string;
  type: FieldType;
  source: string;
  options?: FieldOption[];
  enabled: boolean;
  required: boolean;
  group: string;
  width: 'half' | 'full';
  span?: number;
  sortOrder: number;
  description: string;
  placeholder: string;
  maxLength: number;
  min: string;
  max: string;
  defaultValue?: FieldValue;
  defaultLabels?: Record<string, string>;
  multiple: boolean;
  listable: boolean;
  filterable: boolean;
  exportable: boolean;
  visibleWhen: FieldRule;
  requiredWhen: FieldRule;
};
export type FormLayout = { columns: number; maxWidth: number };
export type Configuration = { fields: BusinessField[]; layout?: FormLayout };
export function formLayout(configuration: Configuration): FormLayout {
  return {
    columns: configuration.layout?.columns ?? 2,
    maxWidth: configuration.layout?.maxWidth ?? 0,
  };
}
export function fieldSpan(field: BusinessField, columns: number): number {
  return Math.max(1, Math.min(columns, field.span ?? (field.width === 'full' ? columns : 1)));
}
export type Schema = {
  draft: Configuration;
  published: Configuration;
  version: number;
  publishedVersion: number;
};
export type Choice = {
  id: string;
  name: string;
  enabled: number | boolean;
  sortOrder?: number;
  rowVersion?: number;
  parentId?: string;
};
export const FIELD_TYPES: Record<FieldType, string> = {
  TEXT: '单行文本',
  LONG_TEXT: '长文本',
  NUMBER: '数值',
  DATE: '日期',
  DATETIME: '日期时间',
  CHOICE: '单选',
  MULTI_CHOICE: '多选',
  BOOLEAN: '是非',
  USER: '系统用户',
  DEPARTMENT: '部门',
};
export const businessApi = {
  objects: () =>
    request<{
      data: { key: BusinessObject; name: string }[];
    }>('/business-config/objects'),
  schema: (object: BusinessObject) => request<Schema>(`/business-config/objects/${object}`),
  runtime: (object: BusinessObject) =>
    request<{ configuration: Configuration; version: number }>(
      `/business-config/runtime/${object}`,
    ),
  save: (object: BusinessObject, configuration: Configuration, version: number) =>
    request<Schema>(`/business-config/objects/${object}/draft`, {
      method: 'PUT',
      body: JSON.stringify({ configuration, version }),
    }),
  publish: (object: BusinessObject, version: number) =>
    request<Schema>(`/business-config/objects/${object}/publish`, {
      method: 'POST',
      body: JSON.stringify({ version }),
    }),
  versions: (object: BusinessObject) =>
    request<{ data: { version: number; created_at: string; actor_id: string }[] }>(
      `/business-config/objects/${object}/versions`,
    ),
  restore: (object: BusinessObject, target: number, version: number) =>
    request<Schema>(`/business-config/objects/${object}/restore`, {
      method: 'POST',
      body: JSON.stringify({ target, version }),
    }),
  events: (object: BusinessObject) =>
    request<{ data: { actor: string; action: string; time: string; details: unknown }[] }>(
      `/business-config/objects/${object}/events`,
    ),
  choices: (
    object: BusinessObject,
    source: string,
    q = '',
    page = 1,
    size = 20,
    inactive = false,
  ) =>
    request<{ data: Choice[]; total: number }>(
      `/business-config/runtime/${object}/options?` +
        new URLSearchParams({
          source,
          q,
          page: String(page),
          size: String(size),
          inactive: String(inactive),
        }),
    ),
};
export function newField(): BusinessField {
  return {
    id: crypto.randomUUID(),
    name: '',
    type: 'TEXT',
    source: '',
    enabled: true,
    required: false,
    group: EXTENSION_GROUP,
    width: 'half',
    sortOrder: 0,
    description: '',
    placeholder: '',
    maxLength: 200,
    min: '',
    max: '',
    multiple: false,
    listable: true,
    filterable: true,
    exportable: true,
    visibleWhen: { mode: 'all', items: [] },
    requiredWhen: { mode: 'all', items: [] },
  };
}
export const inlineOptions = (field: BusinessField) =>
  ['CHOICE', 'MULTI_CHOICE'].includes(field.type) && Array.isArray(field.options);
export function optionError(field: BusinessField): string {
  if (!inlineOptions(field)) return '';
  const options = field.options || [];
  if (!options.length && field.enabled) return tr('ui.validation.message4');
  if (options.length > 200) return tr('ui.validation.message5');
  const names = options.map((o) => o.name.trim().toLocaleLowerCase());
  if (names.some((n) => !n)) return tr('ui.validation.message6');
  if (new Set(names).size !== names.length) return tr('ui.validation.message7');
  return '';
}
export const isMultiple = (field: BusinessField) => field.type === 'MULTI_CHOICE' || field.multiple;
export const isReference = (field: BusinessField) =>
  ['CHOICE', 'MULTI_CHOICE', 'USER', 'DEPARTMENT'].includes(field.type);
export const emptyValue = (value: unknown) =>
  value == null ||
  value === '' ||
  (typeof value === 'string' && !value.trim()) ||
  (Array.isArray(value) && !value.length);
function ruleMatches(
  rule: FieldRule | undefined,
  fields: BusinessField[],
  values: Record<string, FieldValue>,
  category: string,
  fallback: boolean,
  path = new Set<string>(),
): boolean {
  if (!rule?.items.length) return fallback;
  const checks = rule.items.map((condition) => {
    let actual: FieldValue | undefined =
      condition.field === 'categoryId' ? category : values[condition.field];
    const source = fields.find((f) => f.id === condition.field);
    if (source) {
      if (path.has(source.id)) return false;
      const next = new Set([...path, source.id]);
      if (!source.enabled || !ruleMatches(source.visibleWhen, fields, values, category, true, next))
        actual = null;
    }
    const selections = Array.isArray(actual) ? actual : emptyValue(actual) ? [] : [String(actual)];
    if (condition.operator === 'range') {
      const n = Number(actual);
      return (
        !emptyValue(actual) &&
        Number.isFinite(n) &&
        (!condition.values[0] || n >= Number(condition.values[0])) &&
        (!condition.values[1] || n <= Number(condition.values[1]))
      );
    }
    const intersects = selections.some((v) => condition.values.includes(v));
    return condition.operator === 'ne' ? !intersects : intersects;
  });
  return rule.mode === 'any' ? checks.some(Boolean) : checks.every(Boolean);
}
export const fieldVisible = (
  field: BusinessField,
  fields: BusinessField[],
  values: Record<string, FieldValue>,
  category = '',
) => field.enabled && ruleMatches(field.visibleWhen, fields, values, category, true);
export const fieldRequired = (
  field: BusinessField,
  fields: BusinessField[],
  values: Record<string, FieldValue>,
  category = '',
) =>
  fieldVisible(field, fields, values, category) &&
  (field.required || ruleMatches(field.requiredWhen, fields, values, category, false));
export function customColumns(fields: BusinessField[]) {
  return fields
    .filter((f) => f.listable)
    .map((f) => ({
      key: `custom:${f.id}`,
      label: f.name + (f.enabled ? '' : '（已停用）'),
      width: 160,
      sortable: !isMultiple(f) && f.type !== 'LONG_TEXT',
    }));
}
export function isDepartmentCustomColumn(fields: BusinessField[], key: string) {
  return key.startsWith('custom:') && fields.some((field) => field.id === key.slice(7) && (field.type === 'DEPARTMENT' || field.source === 'department'));
}
export function customFilters(fields: BusinessField[], object: BusinessObject): FilterField[] {
  return fields
    .filter((f) => f.filterable)
    .map((f) => ({
      key: `custom:${f.id}`,
      label: f.name + (f.enabled ? '' : '（已停用）'),
      type:
        f.type === 'NUMBER'
          ? 'number'
          : ['DATE', 'DATETIME'].includes(f.type)
            ? 'date'
            : isReference(f) || f.type === 'BOOLEAN'
              ? 'choice'
              : 'text',
      dateOnly: f.type === 'DATE',
      ...(f.type === 'BOOLEAN'
        ? { options: ['true', 'false'], optionLabels: { true: '是', false: '否' } }
        : {}),
      ...(isReference(f)
        ? {
            businessSource: {
              object,
              source: inlineOptions(f) ? `field:${f.id}` : f.source,
              department: f.type === 'DEPARTMENT',
            },
          }
        : {}),
    }));
}
export function customCell(row: CustomRecord, key: string) {
  return row.customFieldLabels?.[key.slice(7)] || '';
}
