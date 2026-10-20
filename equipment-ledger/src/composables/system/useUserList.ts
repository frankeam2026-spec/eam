import { ref } from 'vue';
import { querySystemUsers, type SystemUserRecord, type UserListParameters } from '../../api';
export function useUserList() {
  const rows = ref<SystemUserRecord[]>([]),
    total = ref(0);
  let revision = 0;
  async function refresh(parameters: UserListParameters) {
    const current = ++revision;
    const result = await querySystemUsers(parameters);
    if (current !== revision) return false;
    rows.value = result.data;
    total.value = result.page.total;
    return true;
  }
  function invalidate() {
    revision++;
  }
  return { rows, total, refresh, invalidate };
}
