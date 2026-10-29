<script setup lang="ts">import { tr } from '../i18n';

import { ElTableColumn } from 'element-plus';
import { ElButton, ElInput, ElCheckboxGroup, ElCheckbox } from 'element-plus';

import AppBusinessTable from './common/AppBusinessTable.vue';
import AppDecimalInput from './common/AppDecimalInput.vue';
import { formatNumber } from '../utils/number';
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import AppDialog from './common/AppDialog.vue';
import AppPagination from './common/AppPagination.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import { useAuthStore } from '../stores/auth';
import * as api from '../api';
const auth = useAuthStore(),
  rows = ref<api.StockRow[]>([]),
  query = ref(''),
  page = ref(1),
  size = ref(20),
  open = ref(false),
  busy = ref(false),
  error = ref('');
const form = ref({ code: '', name: '', model: '', quantity: 1 }),
  columns = ref(['code', 'name', 'model', 'quantity']),
  filtersOpen = ref(false),
  columnsOpen = ref(false),
  filters = ref<Record<string, string>>({});
const labels: Record<string, string> = {
  code: '备件编号',
  name: '备件名称',
  model: '规格型号',
  quantity: '库存数量',
};
const filtered = computed(() =>
  rows.value.filter(
    (x) =>
      [x.code, x.name, x.model].some((v) => v.includes(query.value.trim())) &&
      Object.entries(filters.value).every(([key, value]) =>
        String(x[key as keyof api.StockRow]).includes(value),
      ),
  ),
);
const pageRows = computed(() =>
  filtered.value.slice((page.value - 1) * size.value, page.value * size.value),
);
async function load() {
  try {
    rows.value = (await api.listSpareStocks()).data;
    error.value = '';
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('ui.equipment.text0160');
  }
}
async function save() {
  if (busy.value) return;
  busy.value = true;
  try {
    await api.createSpareStock(form.value);
    open.value = false;
    await load();
    ElMessage.success(tr('ui.equipment.text1157'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text1158'));
  } finally {
    busy.value = false;
  }
}
function exportRows() {
  const data =
    '\ufeff' +
    [
      '备件编号,备件名称,规格型号,库存数量',
      ...filtered.value.map((x) =>
        [x.code, x.name, x.model, x.quantity]
          .map((v) => {
            const text = String(v);
            return '"' + (/^[\s]*[=+@-]/.test(text) ? "'" : '') + text.replaceAll('"', '""') + '"';
          })
          .join(','),
      ),
    ].join('\n');
  const url = URL.createObjectURL(new Blob([data], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = '备件库存.csv';
  a.click();
  URL.revokeObjectURL(url);
}
onMounted(load);
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => pageRows.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];
</script>
<template>
  <section class="inventory-page">
    <div class="inventory-toolbar">
      <ElButton
        v-if="auth.can('inventory', '入库')"
        class=""
        @click="
          form = { code: '', name: '', model: '', quantity: 1 };
          open = true;
        "
        type="primary"
      >
        {{ tr('ui.equipment.text0624') }}
      </ElButton>
      <div>
        <ListToolbarIcons
          mode="filter"
          :can-export="auth.can('inventory', '导出')"
          :filter-open="filtersOpen"
          :columns-open="columnsOpen"
          @toggle-filter="filtersOpen = !filtersOpen"
          @toggle-columns="columnsOpen = !columnsOpen"
          @export="exportRows"
        /><ListSearchInput v-model="query" :placeholder="tr('ui.equipment.text1159')" /><ElButton
          class="text-action"
          @click="
            query = '';
            filters = {};
            load();
          "
          type="primary"
          link
        >
          {{ tr('ui.equipment.text0204') }}
        </ElButton>
      </div>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="inventory-card">
      <div class="inventory-scroll">
        <AppBusinessTable
          :rows="pageRows"
          :pagination="false"
          :fill="true"
          :framed="false"
          class="business-table"
          ><!-- @vue-generic {TableColumnRow1} -->
          <ElTableColumn
            show-overflow-tooltip
            v-for="key in columns"
            :min-width="150"
            :prop="String(key)"
            :key="String(key)"
            ><template #header
              ><div class="table-header-label">{{ labels[key] }}</div>
              <div v-if="filtersOpen" class="table-column-filter" @click.stop>
                <ElInput
                  v-model="filters[key]"
                  :placeholder="`筛选${labels[key]}`"
                /></div></template
            ><template #default="{ row: row, $index: tableRowIndex }">
              {{
                key === 'quantity'
                  ? formatNumber(row[key as keyof api.StockRow])
                  : row[key as keyof api.StockRow]
              }}
            </template></ElTableColumn
          ><template #empty
            ><div v-if="!pageRows.length">{{ tr('ui.equipment.text1160') }}</div></template
          ></AppBusinessTable
        >
      </div>
      <footer>
        <span>{{ tr('ui.equipment.text0309') }} {{ filtered.length }} {{ tr('ui.equipment.text0296') }}</span
        ><AppPagination v-model:page="page" v-model:page-size="size" :total="filtered.length" />
        <ListToolbarIcons
          mode="footer"
          :can-export="auth.can('inventory', '导出')"
          :filter-open="filtersOpen"
          :columns-open="columnsOpen"
          @toggle-filter="filtersOpen = !filtersOpen"
          @toggle-columns="columnsOpen = !columnsOpen"
          @export="exportRows"
          ><template #columns
            ><ElCheckboxGroup v-model="columns"
              ><ElCheckbox v-for="(label, key) in labels" :key="key" :value="key">{{
                label
              }}</ElCheckbox></ElCheckboxGroup
            ></template
          ></ListToolbarIcons
        >
      </footer>
    </div>
    <AppDialog v-model="open" :title="tr('ui.equipment.text1161')" :busy="busy"
      ><div class="stock-form">
        <label
          ><span>{{ tr('ui.equipment.text1162') }}</span
          ><ElInput v-model="form.code" maxlength="64" :placeholder="tr('ui.equipment.text1163')" /></label
        ><label
          ><span>{{ tr('ui.equipment.text1164') }}</span
          ><ElInput v-model="form.name" maxlength="100" :placeholder="tr('ui.equipment.text1165')" /></label
        ><label
          ><span>{{ tr('ui.equipment.text0091') }}</span
          ><ElInput v-model="form.model" maxlength="200" :placeholder="tr('ui.equipment.text1166')" /></label
        ><label
          ><span>{{ tr('ui.equipment.text1167') }}</span
          ><AppDecimalInput
            :model-value="form.quantity"
            @update:model-value="form.quantity = Number($event)"
        /></label>
      </div>
      <template #footer
        ><ElButton class="" :disabled="busy" @click="open = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton class="" :disabled="busy" @click="save" type="primary">{{ tr('ui.equipment.text0503') }}</ElButton></template
      ></AppDialog
    >
  </section>
</template>
<style scoped>
.inventory-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  font-size: 13px;
}
.inventory-page h1 {
  font-size: 18px;
  margin: 0 0 12px;
}
.inventory-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 12px;
}
.inventory-toolbar > div {
  display: flex;
  gap: 10px;
  align-items: center;
}
.inventory-card {
  flex: 1;
  min-height: 0;
  background: #fff;
  border: 1px solid #e1e7ef;
  border-radius: 5px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.inventory-scroll {
  flex: 1;
  overflow: auto;
  min-height: 0;
}
.inventory-card table {
  width: 100%;
  min-width: 600px;
  table-layout: fixed;
}
.inventory-card footer {
  height: var(--list-footer-height);
  flex-shrink: 0;
  border-top: 1px solid #e1e7ef;
  padding: 0 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #718097;
}
.text-action {
  background: none;
  border: 0;
  font: inherit;
  color: #748196;
  cursor: pointer;
}
.empty {
  text-align: center;
  color: #94a3b8;
}
.stock-form {
  display: grid;
  gap: 18px;
}
.stock-form label {
  display: flex;
  gap: 12px;
  align-items: center;
}
.stock-form span {
  width: 90px;
  text-align: right;
  color: #64748b;
}
.error {
  color: #d74343;
}
</style>
