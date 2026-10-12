<script setup lang="ts">
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
  size = ref(10),
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
    error.value = e instanceof Error ? e.message : '加载失败';
  }
}
async function save() {
  if (busy.value) return;
  busy.value = true;
  try {
    await api.createSpareStock(form.value);
    open.value = false;
    await load();
    ElMessage.success('备件入库完成');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '入库失败');
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
</script>
<template>
  <section class="inventory-page">
    <div class="inventory-toolbar">
      <button
        v-if="auth.can('inventory', '入库')"
        class="btn primary"
        @click="
          form = { code: '', name: '', model: '', quantity: 1 };
          open = true;
        "
      >
        新增入库
      </button>
      <div>
        <ListToolbarIcons
          mode="filter"
          :can-export="auth.can('inventory', '导出')"
          :filter-open="filtersOpen"
          :columns-open="columnsOpen"
          @toggle-filter="filtersOpen = !filtersOpen"
          @toggle-columns="columnsOpen = !columnsOpen"
          @export="exportRows"
        /><ListSearchInput v-model="query" placeholder="搜索备件编号、名称、型号" /><button
          class="text-action"
          @click="
            query = '';
            filters = {};
            load();
          "
        >
          重置
        </button>
      </div>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="inventory-card">
      <div class="inventory-scroll">
        <table class="business-table">
          <thead>
            <tr>
              <th v-for="key in columns" :key="key">{{ labels[key] }}</th>
            </tr>
            <tr v-if="filtersOpen">
              <th v-for="key in columns" :key="key">
                <input v-model="filters[key]" :placeholder="`筛选${labels[key]}`" />
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in pageRows" :key="row.id">
              <td v-for="key in columns" :key="key">
                {{
                  key === 'quantity'
                    ? formatNumber(row[key as keyof api.StockRow])
                    : row[key as keyof api.StockRow]
                }}
              </td>
            </tr>
            <tr v-if="!pageRows.length">
              <td :colspan="columns.length" class="empty">暂无库存备件</td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer>
        <span>共 {{ filtered.length }} 条</span
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
            ><label v-for="(label, key) in labels" :key="key"
              ><input v-model="columns" type="checkbox" :value="key" />{{ label }}</label
            ></template
          ></ListToolbarIcons
        >
      </footer>
    </div>
    <AppDialog v-model="open" title="新增备件入库" :busy="busy"
      ><div class="stock-form">
        <label
          ><span>备件编号 *</span
          ><input v-model="form.code" maxlength="64" placeholder="输入备件编号" /></label
        ><label
          ><span>备件名称 *</span
          ><input v-model="form.name" maxlength="100" placeholder="输入备件名称" /></label
        ><label
          ><span>规格型号</span
          ><input v-model="form.model" maxlength="200" placeholder="输入规格型号" /></label
        ><label
          ><span>入库数量 *</span
          ><AppDecimalInput
            :model-value="form.quantity"
            @update:model-value="form.quantity = Number($event)"
        /></label>
      </div>
      <template #footer
        ><button class="btn secondary" :disabled="busy" @click="open = false">取消</button
        ><button class="btn primary" :disabled="busy" @click="save">入库</button></template
      ></AppDialog
    >
  </section>
</template>
<style scoped>
.inventory-page {
  height: calc(100dvh - 54px);
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 12px;
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
.inventory-card th,
.inventory-card td {
  font-size: 13px;
  padding: 12px 16px;
  height: 44px;
}
.inventory-card th {
  background: #f5f7fa;
}
.inventory-card footer {
  height: 46px;
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
.stock-form input,
.inventory-card th input {
  flex: 1;
  min-width: 0;
  border: 1px solid #d8e1ed;
  border-radius: 4px;
  font: inherit;
  padding: 8px 10px;
}
.error {
  color: #d74343;
}
</style>
