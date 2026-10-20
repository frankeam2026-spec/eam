import { ref } from 'vue';
import { ApiError } from '../api';
import type { CustomRecord } from '../businessConfig';
import type BusinessFields from '../components/business/BusinessFields.vue';
export function useBusinessExtension() {
  const extension = ref<InstanceType<typeof BusinessFields>>();
  const extensionErrors = ref<Record<string, string>>({});
  function prepareExtension(record: CustomRecord) {
    extensionErrors.value = {};
    if (!extension.value || !extension.value.validate()) return false;
    record.customFields = extension.value.prepare();
    return true;
  }
  function extensionError(error: unknown) {
    if (!(error instanceof ApiError)) return false;
    if (error.status === 409 && error.message.includes('字段配置')) void extension.value?.reload();
    extensionErrors.value = error.fieldErrors;
    return Object.keys(error.fieldErrors).some((k) => k.startsWith('customFields.'));
  }
  return { extension, extensionErrors, prepareExtension, extensionError };
}
