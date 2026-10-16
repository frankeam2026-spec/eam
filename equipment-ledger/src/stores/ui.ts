import { ref, watch } from 'vue';
import { defineStore } from 'pinia';
import { getPersonalSettings, savePersonalTheme } from '../api';
export type Theme = 'blue' | 'navy' | 'pink';
export const useUiStore = defineStore('ui', () => {
  const palette = ref<Theme>('blue');
  const preferenceError = ref('');
  const savingTheme = ref(false);
  let identity = '';
  let revision = 0;
  const validTheme = (value: unknown): value is Theme =>
    ['blue', 'navy', 'pink'].includes(String(value));
  function resetUser() {
    identity = '';
    revision++;
    preferenceError.value = '';
    palette.value = 'blue';
  }
  async function bindUser(tenantId: string, userId: string) {
    const key = `${tenantId}:${userId}`;
    const version = ++revision;
    identity = key;
    const cached = localStorage.getItem(`eam-theme:${key}`);
    palette.value = validTheme(cached) ? cached : 'blue';
    preferenceError.value = '';
    try {
      const { data } = await getPersonalSettings();
      if (version === revision && identity === key && validTheme(data.theme))
        palette.value = data.theme;
    } catch (error) {
      if (version === revision)
        preferenceError.value = error instanceof Error ? error.message : '主题加载失败';
    }
  }
  async function setTheme(value: Theme) {
    if (!identity || savingTheme.value) return;
    const key = identity,
      version = revision,
      previous = palette.value;
    savingTheme.value = true;
    palette.value = value;
    preferenceError.value = '';
    try {
      await savePersonalTheme(value);
    } catch (error) {
      if (identity === key && revision === version) {
        palette.value = previous;
        preferenceError.value = error instanceof Error ? error.message : '主题保存失败';
      }
      throw error;
    } finally {
      savingTheme.value = false;
    }
  }
  const collapsed = ref(localStorage.getItem('eam-nav-collapsed') !== 'false');
  watch(
    palette,
    (value) => {
      if (identity) localStorage.setItem(`eam-theme:${identity}`, value);
      document.documentElement.dataset.theme = value;
    },
    { immediate: true },
  );
  watch(collapsed, (value) => localStorage.setItem('eam-nav-collapsed', String(value)));
  return { palette, collapsed, savingTheme, preferenceError, bindUser, resetUser, setTheme };
});
