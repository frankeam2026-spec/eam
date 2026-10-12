<script setup lang="ts">
import AppDecimalInput from './common/AppDecimalInput.vue';
import { formatNumber } from '../utils/number';
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElButton, ElInput, ElSelect, ElOption, ElMessage } from 'element-plus';
import AppDialog from './common/AppDialog.vue';
import { request } from '../api';
import { useAuthStore } from '../stores/auth';
const props = defineProps<{
  kind: '维修' | '保养' | '点巡检';
  taskId: string;
  canWork?: boolean;
  canRequest?: boolean;
}>();
const emit = defineEmits<{ changed: [] }>();
const router = useRouter(),
  auth = useAuthStore();
type Row = Record<string, string>;
const data = ref<{
  equipment: Row[];
  requests: Row[];
  allocations: Row[];
  installed: Row[];
  usage: Row[];
}>({ equipment: [], requests: [], allocations: [], installed: [], usage: [] });
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  dialog = ref(''),
  selected = ref<Row>(),
  quantity = ref('1'),
  equipmentId = ref(''),
  destination = ref('');
let confirmationKey = '';
let revision = 0;
const path = () => `/inventory/task-materials/${encodeURIComponent(props.kind)}/${props.taskId}`;
async function load() {
  const current = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const response = await request<{ data: typeof data.value }>(path());
    if (current === revision) data.value = response.data;
  } catch (e) {
    if (current === revision) error.value = e instanceof Error ? e.message : '用料加载失败';
  } finally {
    if (current === revision) loading.value = false;
  }
}
watch(() => [props.kind, props.taskId], load, { immediate: true });
function open(row: Row, type: string) {
  selected.value = row;
  quantity.value = '1';
  equipmentId.value = data.value.equipment[0]?.id || '';
  destination.value = '';
  confirmationKey = crypto.randomUUID();
  dialog.value = type;
}
async function submit() {
  if (busy.value || !selected.value) return;
  busy.value = true;
  try {
    data.value = (
      await request<{ data: typeof data.value }>(
        path() + '/' + (dialog.value === 'remove' ? 'remove' : 'use'),
        {
          method: 'POST',
          body: JSON.stringify({
            allocationId: selected.value.id,
            usageId: selected.value.id,
            quantity: quantity.value,
            equipmentId: equipmentId.value,
            destination: destination.value,
            confirmationKey,
          }),
        },
      )
    ).data;
    dialog.value = '';
    ElMessage.success('已记录');
    emit('changed');
    window.dispatchEvent(new Event('task-materials-changed'));
    window.dispatchEvent(new Event('work-inbox-changed'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败');
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="task-materials">
    <header>
      <strong>任务用料</strong>
      <div>
        <ElButton text :loading="loading" @click="load">刷新</ElButton
        ><ElButton
          v-if="canRequest || canWork"
          type="primary"
          plain
          @click="
            router.push({
              name: 'inventory-requisition-detail',
              params: { id: 'new' },
              query: { taskKind: kind, taskId },
            })
          "
          >申请领料</ElButton
        >
      </div>
    </header>
    <p v-if="error" role="alert">{{ error }}</p>
    <div v-if="data.requests.length" class="requests">
      <span v-for="r in data.requests" :key="r.id"
        ><ElButton
          v-if="auth.can('inventoryRequisition', '查看')"
          link
          type="primary"
          @click="router.push({ name: 'inventory-requisition-detail', params: { id: r.id } })"
          >{{ r.code }}</ElButton
        ><span v-else>{{ r.code }}</span> · {{ r.status }}</span
      >
    </div>
    <div class="scroll">
      <table>
        <thead>
          <tr>
            <th>物料</th>
            <th>出库单号</th>
            <th>已发数量</th>
            <th>已用 / 安装</th>
            <th>已退数量</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in data.allocations" :key="a.id">
            <td>{{ a.materialName }}</td>
            <td>{{ a.issueCode }}</td>
            <td>{{ formatNumber(a.quantity) }} {{ a.unit }}</td>
            <td>{{ formatNumber(a.usedQuantity) }}</td>
            <td>{{ formatNumber(a.returnedQuantity) }}</td>
            <td class="actions">
              <ElButton v-if="canWork" link type="primary" @click="open(a, 'use')">{{
                a.usageType === '可安装' ? '安装' : '登记使用'
              }}</ElButton>
            </td>
          </tr>
          <tr v-if="!data.allocations.length">
            <td colspan="6" class="empty">{{ loading ? '正在加载…' : '暂无发料记录' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <template v-if="canWork && data.installed.length"
      ><h4>可换下备件</h4>
      <div class="scroll">
        <table>
          <thead>
            <tr>
              <th>备件名称</th>
              <th>规格型号</th>
              <th>数量</th>
              <th>安装时间</th>
              <th class="actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in data.installed" :key="u.id">
              <td>{{ u.name }}</td>
              <td>{{ u.model }}</td>
              <td>{{ formatNumber(u.quantity) }}</td>
              <td>{{ u.createdAt?.replace('T', ' ').slice(0, 19) }}</td>
              <td class="actions">
                <ElButton v-if="canWork" link type="primary" @click="open(u, 'remove')"
                  >换下</ElButton
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div></template
    >
    <template v-if="data.usage.length"
      ><h4>使用记录</h4>
      <div class="scroll">
        <table>
          <thead>
            <tr>
              <th>物料</th>
              <th>操作</th>
              <th>数量</th>
              <th>状态 / 去向</th>
              <th>操作人</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in data.usage" :key="u.id">
              <td>{{ u.materialName }}</td>
              <td>{{ u.kind }}</td>
              <td>{{ formatNumber(u.quantity) }}</td>
              <td>{{ u.status }} {{ u.destination }}</td>
              <td>{{ u.actorName }}</td>
              <td>{{ u.createdAt?.replace('T', ' ').slice(0, 19) }}</td>
            </tr>
          </tbody>
        </table>
      </div></template
    >
    <AppDialog
      :model-value="!!dialog"
      :title="
        dialog === 'remove'
          ? '换下备件'
          : selected?.usageType === '可安装'
            ? '安装备件'
            : '登记使用'
      "
      :busy="busy"
      @update:model-value="
        (v) => {
          if (!v) dialog = '';
        }
      "
      ><div class="fields">
        <label v-if="dialog === 'remove'"
          >换下去向<ElInput
            v-model="destination"
            placeholder="填写回收、维修或报废去向"
            maxlength="500" /></label
        ><template v-else
          ><label>数量<AppDecimalInput v-model="quantity" inputmode="decimal" /></label
          ><label v-if="selected?.usageType === '可安装'"
            >设备<ElSelect v-model="equipmentId"
              ><ElOption
                v-for="eq in data.equipment"
                :key="eq.id"
                :value="eq.id"
                :label="eq.name" /></ElSelect
          ></label>
          <p>发料已扣库存，本操作只记录使用或安装。</p></template
        >
      </div>
      <template #footer
        ><div class="buttons">
          <ElButton :disabled="busy" @click="dialog = ''">取消</ElButton
          ><ElButton type="primary" :loading="busy" @click="submit">提交</ElButton>
        </div></template
      ></AppDialog
    >
  </section>
</template>
<style scoped>
.task-materials {
  color: var(--text);
  font-size: 13px;
  background: white;
  margin-top: 20px;
  padding: 16px;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
header div,
.buttons {
  display: flex;
  gap: 16px;
}
.requests {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
}
.scroll {
  overflow: auto;
}
table {
  width: 100%;
  min-width: 660px;
  table-layout: fixed;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: 10px 12px;
  font-size: 13px;
  border-bottom: 1px solid #e4e9f0;
}
th {
  font-weight: 700;
  background: #f5f7fb;
}
.actions {
  position: sticky;
  right: 0;
  background: white;
  width: 120px;
}
.empty {
  text-align: center;
  padding: 28px;
}
h4 {
  margin: 20px 0 10px;
  font-size: 13px;
}
.fields {
  display: grid;
  gap: 16px;
}
.fields label {
  display: grid;
  grid-template-columns: 80px 1fr;
  align-items: center;
  gap: 10px;
}
.buttons {
  justify-content: center;
}
.buttons :deep(.el-button) {
  margin-left: 0;
  min-width: 100px;
}
</style>
