/// <reference types="vite/client" />
import { createI18n } from 'vue-i18n';
import { computed, ref, watch } from 'vue';
import dayjs from 'dayjs';
import dayjsEn from 'dayjs/locale/en';
import type { Language } from 'element-plus/es/locale/index';
import zhCN from './zh-CN.json';
export type LanguageOption = {
  locale: string;
  nativeName: string;
  enabled: boolean;
  builtin: boolean;
  rowVersion: number;
  coverage: number;
  updatedAt?: string;
};
export const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  flatJson: true,
  messages: { 'zh-CN': { ...zhCN } } as Record<string, Record<string, string>>,
  missingWarn: false,
  fallbackWarn: false,
  escapeParameter: true,
  missing: (_locale, key) =>
    (zhCN as Record<string, string>)[key] ?? zhCN['common.error.requestFailed'],
});
export const languageOptions = ref<LanguageOption[]>([
  {
    locale: 'zh-CN',
    nativeName: '简体中文',
    enabled: true,
    builtin: true,
    rowVersion: 0,
    coverage: 100,
  },
  {
    locale: 'zh-TW',
    nativeName: '繁體中文',
    enabled: true,
    builtin: true,
    rowVersion: 0,
    coverage: 100,
  },
  {
    locale: 'en-US',
    nativeName: 'English',
    enabled: true,
    builtin: true,
    rowVersion: 0,
    coverage: 100,
  },
]);
export const currentLocale = computed(() => i18n.global.locale.value);
export const languageBusy = ref(false);
let identity = '',
  revision = 0;
let lastCatalog = '',
  checking = false;
// Refresh published overrides when returning to the app; never remount the business form.
async function checkPublishedLanguage() {
  if (checking || languageBusy.value || document.visibilityState === 'hidden') return;
  checking = true;
  const rev = revision;
  try {
    const rows = await loadLanguages();
    const signature = JSON.stringify(rows.map((r) => [r.locale, r.rowVersion]));
    if (lastCatalog && signature !== lastCatalog) {
      const actual = await resource(currentLocale.value, rev);
      if (rev === revision) activate(actual);
    }
    lastCatalog = signature;
  } catch {
    /* Keep the usable language when offline. */
  } finally {
    checking = false;
  }
}
window.addEventListener('focus', checkPublishedLanguage);
document.addEventListener('visibilitychange', checkPublishedLanguage);
const refreshTimer = window.setInterval(checkPublishedLanguage, 60000);
if (import.meta.hot)
  import.meta.hot.dispose(() => {
    clearInterval(refreshTimer);
    window.removeEventListener('focus', checkPublishedLanguage);
    document.removeEventListener('visibilitychange', checkPublishedLanguage);
  });
const bundleRevision = ref(0);
const bundles: Record<string, Record<string, string>> = { 'zh-CN': zhCN };
export function t(key: string, params: Record<string, unknown> = {}) {
  return i18n.global.t(key, params as Record<string, string>);
}
export const tr = t;
/** Translation of registered product enums only; never pass user-entered labels. */
export function enumLabel(kind: string, value: unknown): string {
  const raw = String(value ?? '');
  const key = (enumKeys as Record<string, Record<string, string>>)[kind]?.[raw];
  return key ? t(key) : raw;
}
import enumKeys from './enum-keys.json';
async function api(path: string, init: RequestInit = {}) {
  const response = await fetch('/api/v1' + path, {
    ...init,
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      'Accept-Language': currentLocale.value,
      ...init.headers,
    },
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || t('common.error.requestFailed'));
  return body;
}
export async function loadLanguages() {
  const r = await api('/i18n/languages');
  languageOptions.value = r.data;
  if (!lastCatalog)
    lastCatalog = JSON.stringify(r.data.map((v: LanguageOption) => [v.locale, v.rowVersion]));
  return r.data as LanguageOption[];
}
async function resource(locale: string, expectedRevision = revision) {
  const r = await api('/i18n/resources/' + encodeURIComponent(locale));
  const actual = r.data.locale as string;
  if (expectedRevision !== revision) return actual;
  bundles[actual] = { ...zhCN, ...r.data.messages };
  i18n.global.setLocaleMessage(actual, { ...bundles[actual] });
  bundleRevision.value++;
  return actual;
}
function activate(locale: string) {
  i18n.global.locale.value = locale;
  document.documentElement.lang = locale;
  const b = bundles[locale] || zhCN;
  const list = (key: string, fallback: string[]) => b[key]?.split('|') || fallback;
  dayjs.locale(
    {
      ...dayjsEn,
      name: locale.toLowerCase(),
      months: list('calendar.months', []),
      weekdays: list('calendar.weekdays', []),
      monthsShort: list('calendar.monthsShort', []),
      weekdaysShort: list('calendar.weekdaysShort', []),
      weekdaysMin: list('calendar.weekdaysMin', []),
      weekStart: 1,
    },
    undefined,
    true,
  );
  dayjs.locale(locale.toLowerCase());
}
export async function initializeLanguage() {
  const rev = ++revision;
  try {
    await loadLanguages();
    const saved = localStorage.getItem('eam-language:anonymous') || 'zh-CN';
    const actual = await resource(saved, rev);
    if (rev === revision && !identity) activate(actual);
  } catch {
    /* Bundled simplified Chinese keeps login usable when the API is unavailable. */
  }
}
export async function bindLanguage(tenant: string, user: string) {
  if (identity !== tenant + ':' + user) clearResources();
  identity = tenant + ':' + user;
  const rev = ++revision;
  const r = await api('/auth/settings');
  const actual = await resource(r.data.effectiveLocale || 'zh-CN', rev);
  if (rev === revision) activate(actual);
}
function clearResources() {
  for (const locale of Object.keys(bundles)) {
    delete bundles[locale];
    i18n.global.setLocaleMessage(locale, {});
  }
  bundles['zh-CN'] = { ...zhCN };
  i18n.global.setLocaleMessage('zh-CN', { ...zhCN });
  bundleRevision.value++;
  lastCatalog = '';
}
export function resetLanguage() {
  clearResources();
  identity = '';
  revision++;
  activate('zh-CN');
}
export async function chooseLanguage(locale: string, authenticated: boolean) {
  if (languageBusy.value || locale === currentLocale.value) return;
  languageBusy.value = true;
  const rev = ++revision;
  try {
    const actual = await resource(locale, rev);
    if (rev !== revision) return;
    if (actual !== locale) throw new Error(t('language.error.unavailable'));
    if (authenticated)
      await api('/auth/settings/locale', { method: 'PATCH', body: JSON.stringify({ locale }) });
    if (rev === revision) {
      activate(actual);
      if (!authenticated) localStorage.setItem('eam-language:anonymous', actual);
    }
  } finally {
    languageBusy.value = false;
  }
}
export async function refreshLanguage() {
  const rev = revision;
  await loadLanguages();
  if (rev !== revision) return;
  const actual = await resource(currentLocale.value, rev);
  if (rev === revision) activate(actual);
}
export const elementLocale = computed(() => {
  bundleRevision.value;
  const messages = bundles[currentLocale.value] || zhCN;
  const result: Record<string, any> = { name: currentLocale.value.toLowerCase() };
  for (const key of Object.keys(zhCN).filter((k) => k.startsWith('component.'))) {
    let target = result;
    const parts = key.slice(10).split('.');
    parts.forEach((p, i) => {
      if (i === parts.length - 1)
        target[p] = messages[key] ?? (zhCN as Record<string, string>)[key];
      else target = target[p] ??= {};
    });
  }
  result.name = currentLocale.value.toLowerCase();
  return result as Language;
});
watch(currentLocale, () => {
  document.documentElement.lang = currentLocale.value;
});
