import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import * as api from '../api';
import { useUiStore } from './ui';

export const useAuthStore = defineStore('auth', () => {
  const user = ref<api.CurrentUser | null>(null);
  const grantedActions = ref<Record<string, string[]>>({});
  const developmentMode = ref(false);
  const authState = ref<'loading' | 'login' | 'ready' | 'unavailable'>('loading');
  const authMessage = ref('');
  const currentUser = computed(() => user.value?.name || '');
  const currentCompany = computed(() => user.value?.company || '');
  const currentDepartment = computed(() => user.value?.department || '');
  let pending: Promise<void> | undefined;

  function can(menu: string, action = '查看') {
    return grantedActions.value[menu]?.includes(action) === true;
  }
  function expire() {
    useUiStore().resetUser();
    user.value = null;
    grantedActions.value = {};
    authState.value = 'login';
    authMessage.value = '登录已过期，请重新登录';
  }
  async function load(force = false) {
    if (pending) return pending;
    if (!force && authState.value === 'ready') return;
    authState.value = 'loading';
    authMessage.value = '';
    pending = (async () => {
      try {
        const result = await api.me();
        user.value = result.data;
        grantedActions.value = result.permissions || {};
        developmentMode.value = !!result.developmentMode;
        await useUiStore().bindUser(result.data.tenantId, result.data.id);
        authState.value = 'ready';
      } catch (error) {
        useUiStore().resetUser();
        user.value = null;
        grantedActions.value = {};
        authState.value =
          error instanceof api.ApiError && error.status === 401 ? 'login' : 'unavailable';
        authMessage.value =
          authState.value === 'unavailable' ? '服务暂时无法连接，请检查后端服务' : '';
      }
    })().finally(() => {
      pending = undefined;
    });
    return pending;
  }
  return {
    user,
    grantedActions,
    developmentMode,
    authState,
    authMessage,
    currentUser,
    currentCompany,
    currentDepartment,
    can,
    load,
    expire,
  };
});
