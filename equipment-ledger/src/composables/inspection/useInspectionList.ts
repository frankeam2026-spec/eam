import { ref } from 'vue';
import {
  inspectionApi,
  type InspectionKind,
  type InspectionDefinition,
  type InspectionTask,
  type InspectionChoices,
} from '../../inspection';
import type { MaintenanceListParameters } from '../maintenance/useMaintenanceList';
/** Owns list requests only; editors keep their own choices and draft lifetime. */
export function useInspectionList() {
  const rows = ref<(InspectionDefinition | InspectionTask)[]>([]),
    total = ref(0),
    organizations = ref<InspectionChoices['organizations']>([]);
  let revision = 0;
  async function refresh(kind: InspectionKind, parameters: MaintenanceListParameters) {
    const current = ++revision;
    const [context, result] = await Promise.all([
      inspectionApi.listContext(kind),
      inspectionApi.query(kind, parameters),
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
  function reset() {
    invalidate();
    rows.value = [];
    total.value = 0;
    organizations.value = [];
  }
  return { rows, total, organizations, refresh, invalidate, reset };
}
