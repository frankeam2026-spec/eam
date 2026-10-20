import { ref } from 'vue';
import {
  maintenanceApi,
  type MaintenanceKind,
  type MaintenanceDefinition,
  type MaintenanceTask,
  type Choice,
} from '../../maintenance';
export type MaintenanceListParameters = {
  page: number;
  size: number;
  q: string;
  conditions: string;
  filters: string;
  sortBy: string;
  direction: string;
  queue: string;
  organization: string;
};
/** List reads are independent of editor choices and cannot overwrite a newer query response. */
export function useMaintenanceList() {
  const rows = ref<(MaintenanceDefinition | MaintenanceTask)[]>([]),
    organizations = ref<Choice[]>([]),
    total = ref(0);
  let revision = 0;
  async function refresh(kind: MaintenanceKind, parameters: MaintenanceListParameters) {
    const current = ++revision;
    const [context, result] = await Promise.all([
      maintenanceApi.listContext(kind),
      maintenanceApi.query(kind, parameters),
    ]);
    if (current !== revision) return false;
    rows.value = result.data;
    total.value = result.page.total;
    organizations.value = context.organizations;
    return true;
  }
  function invalidate() {
    revision++;
  }
  return { rows, total, organizations, refresh, invalidate };
}
