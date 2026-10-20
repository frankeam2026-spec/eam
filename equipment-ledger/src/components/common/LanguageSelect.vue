<script setup lang="ts">
import { ElSelect, ElOption, ElMessage } from 'element-plus';
import { currentLocale, languageOptions, languageBusy, chooseLanguage, tr } from '../../i18n';
defineProps<{ authenticated?: boolean }>();
async function change(locale: string, authenticated: boolean) {
  try {
    await chooseLanguage(locale, authenticated);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('common.error.requestFailed'));
  }
}
</script>
<template>
  <ElSelect
    :model-value="currentLocale"
    :disabled="languageBusy"
    :loading="languageBusy"
    :aria-label="tr('language.field.displayLanguage')"
    style="width: 220px"
    @change="(value: string) => change(value, !!authenticated)"
    ><ElOption
      v-for="option in languageOptions"
      :key="option.locale"
      :label="option.nativeName"
      :value="option.locale"
  /></ElSelect>
</template>
