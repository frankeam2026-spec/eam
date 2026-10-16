import { ref } from 'vue';
import { defineStore } from 'pinia';
import { defaultBranding, publicBranding, type Branding } from '../branding';
export const useBrandingStore = defineStore('branding', () => {
  const branding = ref<Branding>({ ...defaultBranding });
  let generation = 0;
  function apply(value: Branding) {
    ++generation;
    branding.value = { ...value };
    document.title = value.systemName;
  }
  async function load(tenantId: string) {
    const revision = ++generation;
    try {
      const response = await publicBranding(tenantId);
      if (revision === generation) {
        branding.value = response.data;
        document.title = response.data.systemName;
      }
    } catch {
      if (revision === generation) {
        branding.value = { ...defaultBranding };
        document.title = defaultBranding.systemName;
      }
    }
  }
  return { branding, apply, load };
});
