import { tr } from '../i18n';
import { computed, ref, toValue, watch, type MaybeRefOrGetter } from 'vue';
import {
  businessApi,
  customColumns,
  customFilters,
  type BusinessObject,
  type BusinessField,
} from '../businessConfig';
export function useBusinessColumns(object: MaybeRefOrGetter<BusinessObject | undefined>) {
  const definitions = ref<BusinessField[]>([]);
  const configurationError = ref('');
  let revision = 0;
  watch(
    () => toValue(object),
    async (key) => {
      const current = ++revision;
      definitions.value = [];
      configurationError.value = '';
      if (!key) return;
      try {
        const result = await businessApi.runtime(key);
        if (current === revision) definitions.value = result.configuration.fields;
      } catch (error) {
        if (current === revision)
          configurationError.value =
            error instanceof Error ? error.message : tr('ui.equipment.text1611');
      }
    },
    { immediate: true },
  );
  return {
    definitions,
    configurationError,
    extensionColumns: computed(() => customColumns(definitions.value)),
    extensionFilters: computed(() =>
      toValue(object) ? customFilters(definitions.value, toValue(object)!) : [],
    ),
    extensionExportColumns: computed(() =>
      definitions.value
        .filter((f) => f.exportable)
        .map((f) => ({ key: `custom:${f.id}`, label: f.name })),
    ),
  };
}
