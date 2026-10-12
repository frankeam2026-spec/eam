import { ref, watch } from 'vue';
import { defineStore } from 'pinia';
export type Theme = 'blue' | 'navy' | 'pink';
export const useUiStore = defineStore('ui', () => {
  const stored = localStorage.getItem('eam-theme');
  const palette = ref<Theme>(stored === 'navy' || stored === 'pink' ? stored : 'blue');
  const collapsed = ref(localStorage.getItem('eam-nav-collapsed') !== 'false');
  watch(
    palette,
    (value) => {
      localStorage.setItem('eam-theme', value);
      document.documentElement.dataset.theme = value;
    },
    { immediate: true },
  );
  watch(collapsed, (value) => localStorage.setItem('eam-nav-collapsed', String(value)));
  return { palette, collapsed };
});
