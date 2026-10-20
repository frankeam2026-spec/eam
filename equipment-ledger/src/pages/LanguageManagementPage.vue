<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  ElButton,
  ElMessage,
  ElTableColumn,
  ElTag,
  ElAlert,
  ElDescriptions,
  ElDescriptionsItem,
} from 'element-plus';
import AppListToolbar from '../components/common/AppListToolbar.vue';
import AppBusinessTable from '../components/common/AppBusinessTable.vue';
import AppDialog from '../components/common/AppDialog.vue';
import AppFilePicker from '../components/common/AppFilePicker.vue';
import { request } from '../api';
import { tr, refreshLanguage, type LanguageOption } from '../i18n';
import { confirmAction } from '../services/dialog';
const rows = ref<LanguageOption[]>([]),
  loading = ref(false),
  error = ref(''),
  busy = ref(false),
  open = ref(false);
const pack = ref<Record<string, unknown>>();
type Report = {
  valid: boolean;
  invalid: string[];
  unknown: string[];
  missing: string[];
  added: number;
  changed: number;
  accepted: number;
  total: number;
};
const report = ref<Report>();
const locale = computed(() => String(pack.value?.locale || ''));
async function load() {
  loading.value = true;
  error.value = '';
  try {
    rows.value = (await request<{ data: LanguageOption[] }>('/system/languages')).data;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
function download(data: unknown, name: string) {
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(data, null, 2)], { type: 'application/json;charset=utf-8' }),
  );
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}
async function template(locale = 'fr-FR') {
  try {
    download(
      await request('/system/languages/template?locale=' + encodeURIComponent(locale)),
      locale + '-language.json',
    );
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
async function select(files: File[]) {
  const file = files[0];
  if (!file) return;
  if (!file.name.toLowerCase().endsWith('.json') || file.size > 2 * 1024 * 1024) {
    ElMessage.error(tr('language.manager.fileLimit'));
    return;
  }
  busy.value = true;
  try {
    pack.value = JSON.parse(await file.text());
    report.value = (
      await request<{ data: Report }>('/system/languages/imports/validate', {
        method: 'POST',
        body: JSON.stringify(pack.value),
      })
    ).data;
    open.value = true;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
async function publish() {
  if (!report.value?.valid || !pack.value) return;
  busy.value = true;
  try {
    const existing = rows.value.find((r) => r.locale === locale.value);
    await request('/system/languages/' + encodeURIComponent(locale.value), {
      method: 'PUT',
      body: JSON.stringify({ rowVersion: existing?.rowVersion || 0, package: pack.value }),
    });
    open.value = false;
    await load();
    await refreshLanguage();
    ElMessage.success(tr('language.manager.saved'));
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
async function state(row: LanguageOption) {
  busy.value = true;
  try {
    await request('/system/languages/' + row.locale + '/state', {
      method: 'PATCH',
      body: JSON.stringify({ enabled: !row.enabled, rowVersion: row.rowVersion }),
    });
    await load();
    await refreshLanguage();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
async function restore(row: LanguageOption) {
  if (!(await confirmAction(tr('language.manager.restoreConfirm')))) return;
  busy.value = true;
  try {
    await request('/system/languages/' + row.locale + '/restore', {
      method: 'POST',
      body: JSON.stringify({ rowVersion: row.rowVersion }),
    });
    await load();
    await refreshLanguage();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="language-management">
    <AppListToolbar
      ><div class="language-toolbar-left">
        <AppFilePicker accept=".json" :disabled="busy" @select="select"
          ><ElButton type="primary" :loading="busy">{{
            tr('language.manager.import')
          }}</ElButton></AppFilePicker
        ><ElButton @click="template()">{{ tr('language.manager.downloadTemplate') }}</ElButton>
      </div>
      <div>
        <ElButton :loading="loading" @click="load">{{ tr('language.manager.refresh') }}</ElButton>
      </div></AppListToolbar
    >
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <AppBusinessTable :pagination="false" :rows="rows" :loading="loading" row-key="locale">
      <ElTableColumn prop="nativeName" :label="tr('language.manager.name')" min-width="180" />
      <ElTableColumn prop="locale" :label="tr('language.manager.code')" width="130" />
      <ElTableColumn :label="tr('language.manager.source')" width="130"
        ><template #default="{ row }">{{
          row.builtin ? tr('language.manager.builtin') : tr('language.manager.imported')
        }}</template></ElTableColumn
      >
      <ElTableColumn :label="tr('language.manager.coverage')" width="140"
        ><template #default="{ row }"
          >{{ Number(row.coverage).toFixed(2) }}%</template
        ></ElTableColumn
      >
      <ElTableColumn :label="tr('language.manager.status')" width="120"
        ><template #default="{ row }"
          ><ElTag :type="row.enabled ? 'success' : 'info'">{{
            row.enabled ? tr('language.manager.enabled') : tr('language.manager.disabled')
          }}</ElTag></template
        ></ElTableColumn
      >
      <ElTableColumn prop="updatedAt" :label="tr('language.manager.updatedAt')" min-width="180" />
      <ElTableColumn :label="tr('language.manager.actions')" width="230" fixed="right"
        ><template #default="{ row }"
          ><ElButton link type="primary" @click="template(row.locale)">{{
            tr('language.manager.export')
          }}</ElButton
          ><ElButton
            v-if="!row.builtin"
            link
            type="primary"
            :disabled="busy"
            @click="state(row as LanguageOption)"
            >{{
              row.enabled ? tr('language.manager.disable') : tr('language.manager.enable')
            }}</ElButton
          ><ElButton
            v-else
            link
            type="primary"
            :disabled="busy || !row.rowVersion"
            @click="restore(row as LanguageOption)"
            >{{ tr('language.manager.restore') }}</ElButton
          ></template
        ></ElTableColumn
      >
    </AppBusinessTable>
    <ElAlert :title="tr('language.manager.note')" type="info" :closable="false" />
    <AppDialog v-model="open" :title="tr('language.manager.validation')" width="760px" :busy="busy">
      <template v-if="report"
        ><ElDescriptions :column="2" border
          ><ElDescriptionsItem :label="tr('language.manager.code')">{{ locale }}</ElDescriptionsItem
          ><ElDescriptionsItem :label="tr('language.manager.name')">{{
            pack?.nativeName
          }}</ElDescriptionsItem
          ><ElDescriptionsItem :label="tr('language.manager.added')">{{
            report.added
          }}</ElDescriptionsItem
          ><ElDescriptionsItem :label="tr('language.manager.changed')">{{
            report.changed
          }}</ElDescriptionsItem
          ><ElDescriptionsItem :label="tr('language.manager.missing')">{{
            report.missing.length
          }}</ElDescriptionsItem
          ><ElDescriptionsItem :label="tr('language.manager.unknown')">{{
            report.unknown.length
          }}</ElDescriptionsItem></ElDescriptions
        >
        <ElAlert
          v-if="report.missing.length"
          :title="tr('language.manager.fallbackNote')"
          type="warning"
          :closable="false"
        />
        <details v-if="report.invalid.length" open>
          <summary>{{ tr('language.manager.invalid') }}</summary>
          <ul>
            <li v-for="(item, index) in report.invalid" :key="index">{{ item }}</li>
          </ul>
        </details>
        <details v-if="report.unknown.length">
          <summary>{{ tr('language.manager.unknown') }}</summary>
          <p v-for="item in report.unknown" :key="item">{{ item }}</p>
        </details>
        <details v-if="report.missing.length">
          <summary>{{ tr('language.manager.missing') }}</summary>
          <p v-for="item in report.missing" :key="item">{{ item }}</p>
        </details>
      </template>
      <template #footer
        ><ElButton @click="open = false">{{ tr('language.manager.cancel') }}</ElButton
        ><ElButton type="primary" :disabled="!report?.valid" :loading="busy" @click="publish">{{
          tr('language.manager.confirmImport')
        }}</ElButton></template
      >
    </AppDialog>
  </section>
</template>
<style scoped>
.language-toolbar-left {
  display: flex;
  gap: 12px;
}
.language-management {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
  min-height: 0;
  padding: 12px;
}
.language-management :deep(.el-alert) {
  flex-shrink: 0;
}
details {
  margin-top: 12px;
  max-height: 220px;
  overflow: auto;
}
details p {
  margin: 4px 0;
  overflow-wrap: anywhere;
}
</style>
