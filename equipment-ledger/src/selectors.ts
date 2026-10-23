import { request } from './api';
export type SelectorRow = {
  id: string;
  name: string;
  code?: string;
  model?: string;
  category?: string;
  categoryId?: string;
  department?: string;
  departmentPath?: string;
  departmentOrgId?: string;
  departmentId?: string;
  functionLocation?: string;
  account?: string;
  companyId?: string;
  kind?: string;
  recordId?: string;
  enabled?: boolean;
};
export type SelectorPage<T> = {
  data: T[];
  page: { number?: number; size?: number; total: number };
};
export type PlanningModule = 'maintenance' | 'inspection';
export function planningSelectors(module: PlanningModule) {
  const root = `/${module}/selectors`;
  return {
    equipment: (q: string, page: number, size: number, category = '') =>
      request<SelectorPage<SelectorRow>>(
        `${root}/equipment?${new URLSearchParams({ q, page: String(page), size: String(size), category })}`,
      ),
    users: (q: string, page: number, size: number) =>
      request<SelectorPage<SelectorRow>>(
        `${root}/users?${new URLSearchParams({ q, page: String(page), size: String(size) })}`,
      ),
    standards: <T>(q: string, page: number, size: number, equipmentId = '') =>
      request<SelectorPage<T>>(
        `${root}/standards?${new URLSearchParams({ q, page: String(page), size: String(size), ...(equipmentId ? { equipmentId } : {}) })}`,
      ),
    resolve: <T>(body: { equipmentIds?: string[]; userIds?: string[]; standardIds?: string[] }) =>
      request<{ equipment: SelectorRow[]; users: SelectorRow[]; standards: T[] }>(
        `${root}/resolve`,
        { method: 'POST', body: JSON.stringify(body) },
      ),
    defaults: <T>(equipmentIds: string[]) =>
      request<{ data: T[] }>(`${root}/defaults`, {
        method: 'POST',
        body: JSON.stringify({ equipmentIds }),
      }),
  };
}
export function mergeOptions<T extends { id: string }>(old: T[], added: T[]) {
  return [...new Map([...old, ...added].map((row) => [row.id, row])).values()];
}
/** Resolve selected IDs only. Form payloads keep IDs even if a candidate has become unavailable. */
export async function resolvePlanning<T>(module: PlanningModule, value: unknown) {
  const equipment = new Set<string>(),
    users = new Set<string>(),
    standards = new Set<string>();
  function collect(value: unknown) {
    if (!value || typeof value !== 'object') return;
    if (Array.isArray(value)) {
      value.forEach(collect);
      return;
    }
    for (const [key, item] of Object.entries(value)) {
      const target =
        key === 'equipmentId' || key === 'equipmentIds'
          ? equipment
          : ['owners', 'reviewers', 'collaborators', 'users', 'followUpUserId'].includes(key)
            ? users
            : key === 'standardId'
              ? standards
              : undefined;
      if (target) {
        for (const id of Array.isArray(item) ? item : [item])
          if (typeof id === 'string' && /^\d+$/.test(id) && id !== '0') target.add(id);
      } else collect(item);
    }
  }
  collect(value);
  const eq = [...equipment],
    people = [...users],
    std = [...standards];
  const result: { equipment: SelectorRow[]; users: SelectorRow[]; standards: T[] } = {
    equipment: [],
    users: [],
    standards: [],
  };
  for (let at = 0; at < Math.max(eq.length, people.length, std.length); at += 200) {
    const batch = await planningSelectors(module).resolve<T>({
      equipmentIds: eq.slice(at, at + 200),
      userIds: people.slice(at, at + 200),
      standardIds: std.slice(at, at + 200),
    });
    result.equipment.push(...batch.equipment);
    result.users.push(...batch.users);
    result.standards.push(...batch.standards);
  }
  return result;
}
