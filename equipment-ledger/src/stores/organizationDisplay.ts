import { defineStore } from 'pinia';
import { ref, watch } from 'vue';
import { listOrganizations, type OrganizationRecord } from '../api';
import { useAuthStore } from './auth';

/** Small authorized organization lookup, shared by display cells and isolated by account. */
export const useOrganizationDisplayStore = defineStore('organization-display', () => {
  const auth = useAuthStore();
  const rows = ref<OrganizationRecord[]>([]);
  const identity = () => (auth.user ? `${auth.user.tenantId}:${auth.user.id}` : '');
  let attempted = false;
  let generation = 0;
  let pending: Promise<void> | undefined;
  watch(
    identity,
    () => {
      generation++;
      rows.value = [];
      attempted = false;
      pending = undefined;
    },
    { flush: 'sync' },
  );
  function load() {
    const account = identity();
    const revision = generation;
    if (!account || attempted) return pending ?? Promise.resolve();
    attempted = true;
    pending = listOrganizations()
      .then(({ data }) => {
        if (identity() === account && revision === generation) rows.value = data;
      })
      .catch(() => {
        // Missing display metadata must not prevent business pages from rendering.
      })
      .finally(() => {
        if (identity() === account && revision === generation) pending = undefined;
      });
    return pending;
  }
  function remember(data: OrganizationRecord[]) {
    generation++;
    rows.value = data;
    attempted = true;
    pending = undefined;
  }
  return { rows, load, remember };
});
