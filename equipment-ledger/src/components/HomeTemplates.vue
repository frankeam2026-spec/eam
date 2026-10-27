<script setup lang="ts">import { tr } from '../i18n';

import { ElButton, ElForm } from 'element-plus';

const cloneLayout = <T,>(value: T): T => JSON.parse(JSON.stringify(value));
import { onMounted, ref } from 'vue';
import { ElSelect, ElOption, ElInput, ElInputNumber, ElMessage, ElMessageBox } from 'element-plus';
import OverviewTemplateDesigner from './common/OverviewTemplateDesigner.vue';
import type { OverviewLayout } from '../homeOverview';
import AppFormFooter from './common/AppFormFooter.vue';
import AppFormField from './common/AppFormField.vue';
import AppIcon from './common/AppIcon.vue';
import { homeApi, type HomeTemplate } from '../home';
import { request } from '../api';
import { useAuthStore } from '../stores/auth';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
const auth = useAuthStore();
const overviewDesigner = ref<InstanceType<typeof OverviewTemplateDesigner>>();
const defaultOverview = (): OverviewLayout => ({
  layoutMode: 'work',
  period: { preset: 'month' },
  metrics: ['pending', 'active', 'today', 'notification'],
  blocks: [
    {
      id: 'template-work',
      type: 'work',
      get title(){return tr('ui.common.dynamic38');},
      column: 'main',
      width: 12,
      limit: 5,
      scope: 'personal',
    },
  ],
});
const templates = ref<HomeTemplate[]>([]),
  current = ref<HomeTemplate>(),
  roles = ref<{ id: string; name: string }[]>([]),
  catalog = ref<string[]>([]),
  busy = ref(false),
  error = ref(''),
  preview = ref(false);
const { markSaved, permitLeave, dirty } = useUnsavedChanges(
  () => current.value,
  () => !!current.value,
  () => busy.value,
);
async function load() {
  busy.value = true;
  error.value = '';
  try {
    templates.value = (await homeApi.templates()).data;
    roles.value = (await homeApi.roles()).data;
    catalog.value = (await request<{ data: string[] }>('/home/catalog')).data;
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('ui.equipment.text0254');
  } finally {
    busy.value = false;
  }
}
async function select(t: HomeTemplate) {
  if (!(await permitLeave())) return;
  current.value = cloneLayout(t);
  current.value.overviewDraft ||= defaultOverview();
  preview.value = false;
  markSaved();
}
async function create(copy = false) {
  if (!(await permitLeave())) return;
  const source = copy && current.value ? cloneLayout(current.value) : undefined;
  current.value = {
    id: '',
    overviewDraft: source?.overviewDraft || defaultOverview(),
    name: source ? source.name + '（副本）' : '新首页模板',
    draft: source ? source.draft : { blocks: [] },
    roleIds: source ? source.roleIds : [],
    priority: 100,
    row_version: 0,
    published_version: 0,
    enabled: 1,
  };
  preview.value = false;
  markSaved();
}
async function save() {
  if (!current.value) return;
  if (!(await overviewDesigner.value?.validate())) return;
  busy.value = true;
  try {
    current.value = (await homeApi.store(current.value)).data;
    markSaved();
    ElMessage.success(tr('ui.equipment.text0255'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0195'));
  } finally {
    busy.value = false;
  }
  await load();
}
async function publish() {
  if (!current.value?.id) return;
  if (dirty.value) {
    ElMessage.warning(tr('ui.equipment.text0256'));
    return;
  }
  try {
    await ElMessageBox.confirm('发布后供关联角色使用。已保存的个人布局会保留。', '发布首页模板', {
      get confirmButtonText() { return tr('ui.equipment.text0257'); },
      get cancelButtonText() { return tr('ui.equipment.text0157'); },
      closeOnClickModal: false,
    });
  } catch {
    return;
  }
  busy.value = true;
  try {
    current.value = (await homeApi.publish(current.value)).data;
    markSaved();
    ElMessage.success(tr('ui.equipment.text0258'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0259'));
  } finally {
    busy.value = false;
  }
  await load();
}
async function disable() {
  if (!current.value?.id) return;
  try {
    await ElMessageBox.confirm('停用后，使用者将回到其他适用首页。', '停用模板', {
      get confirmButtonText() { return tr('ui.equipment.text0260'); },
      get cancelButtonText() { return tr('ui.equipment.text0157'); },
      closeOnClickModal: false,
    });
  } catch {
    return;
  }
  busy.value = true;
  try {
    await homeApi.disable(current.value);
    current.value = undefined;
    markSaved();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0261'));
  } finally {
    busy.value = false;
  }
  await load();
}
async function cancel() {
  if (!(await permitLeave())) return;
  current.value = undefined;
}
onMounted(load);
</script>
<template>
  <section class="content home-template-page">
    <aside class="template-library">
      <header>
        <strong>{{ tr('ui.equipment.text0034') }}</strong
        ><ElButton v-if="auth.can('homeTemplate', '新增')" @click="create()" type="primary"
          ><AppIcon name="Plus" />{{ tr('ui.equipment.text0149') }}
        </ElButton>
      </header>
      <p v-if="error" role="alert">{{ error }}</p>
      <div class="template-library-items">
        <ElButton
          v-for="t in templates"
          :key="t.id"
          :class="{ selected: current?.id === t.id }"
          :aria-pressed="current?.id === t.id"
          @click="select(t)"
          class="template-card app-content-button"
          ><strong>{{ t.name }}</strong>
          <p>
            {{
              Number(t.enabled) === 0
                ? tr('ui.equipment.text0262')
                : t.published_version
                  ? tr('ui.equipment.text0263') + t.published_version
                  : tr('ui.equipment.text0264')
            }}
            · {{ (t.overviewDraft?.blocks || t.draft.blocks).length }} {{ tr('ui.equipment.text0265') }}
          </p>
          <span>{{ t.roleIds.length }} {{ tr('ui.equipment.text0266') }}</span></ElButton
        >
      </div>
    </aside>
    <main v-if="current" class="template-designer">
      <div class="template-editor-scroll">
        <header class="template-editor-heading">
          <div>
            <h2>{{ current.name }}</h2>
            <p>{{ tr('ui.equipment.text0267') }}</p>
          </div>
          <div>
            <ElButton @click="preview = !preview">{{ preview ? tr('ui.equipment.text0268') : tr('ui.equipment.text0269') }}</ElButton
            ><ElButton v-if="auth.can('homeTemplate', '新增')" @click="create(true)"> {{ tr('ui.equipment.text0270') }}</ElButton
            ><ElButton
              v-if="auth.can('homeTemplate', '停用') && current.id"
              class=""
              @click="disable"
            >
              {{ tr('ui.equipment.text0260') }}
            </ElButton>
          </div>
        </header>
        <ElForm
          v-if="!preview"
          :model="current"
          label-position="top"
          class="template-properties"
          @submit.prevent
        >
          <AppFormField :label="tr('ui.equipment.text0271')" prop="name"
            ><ElInput v-model="current.name" maxlength="100"
          /></AppFormField>
          <AppFormField :label="tr('ui.equipment.text0272')" prop="roleIds"
            ><ElSelect v-model="current.roleIds" multiple collapse-tags collapse-tags-tooltip
              ><ElOption v-for="r in roles" :key="r.id" :value="r.id" :label="r.name" /></ElSelect
          ></AppFormField>
          <AppFormField :label="tr('ui.equipment.text0273')" prop="priority"
            ><ElInputNumber
              v-model="current.priority"
              :min="0"
              :max="10000"
              controls-position="right"
          /></AppFormField>
          <p>{{ tr('ui.equipment.text0274') }}</p>
        </ElForm>
        <OverviewTemplateDesigner
          v-if="current.overviewDraft"
          ref="overviewDesigner"
          v-model="current.overviewDraft"
          :preview="preview"
          :busy="busy"
          :legacy-catalog="catalog"
          :team-allowed="auth.can('workspace', '团队工作查看')"
        />
      </div>
      <AppFormFooter
        ><ElButton :disabled="busy" @click="cancel">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton
          v-if="auth.can('homeTemplate', current.id ? '编辑' : '新增')"
          class=""
          :disabled="busy"
          @click="save"
          type="primary"
        >
          {{ tr('ui.equipment.text0275') }}</ElButton
        ><ElButton
          v-if="auth.can('homeTemplate', '发布') && current.id"
          class=""
          :disabled="busy"
          @click="publish"
          type="primary"
        >
          {{ tr('ui.equipment.text0257') }}
        </ElButton></AppFormFooter
      >
    </main>
    <main v-else class="template-empty">{{ tr('ui.equipment.text0276') }}</main>
  </section>
</template>
<style scoped>
.home-template-page {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  gap: 16px;
  height: calc(100dvh - 54px);
  min-height: 0;
  background: var(--canvas);
  color: var(--text);
  font-size: 13px;
  overflow: hidden;
}
.template-library {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  margin-bottom: 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 12px;
}
.template-library > header {
  display: flex;
  flex: none;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.template-library-items {
  min-height: 0;
  overflow: auto;
  padding-right: 2px;
}
.template-card.el-button {
  display: block;
  width: 100%;
  height: auto;
  min-height: 94px;
  white-space: normal;
  text-align: left;
  border: 1px solid var(--line);
  background: var(--surface);
  border-radius: 6px;
  margin: 0 0 8px;
  padding: 12px;
  color: var(--text);
}
.template-card.el-button:last-child {
  margin-bottom: 0;
}
.template-card.el-button:hover {
  border-color: var(--brand);
}
.template-card.el-button.selected {
  background: var(--brand-soft);
  border-color: var(--brand);
}
.template-card strong {
  display: block;
  font-size: 13px;
  line-height: 20px;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.template-card p {
  margin: 6px 0;
  font-size: 12px;
  line-height: 18px;
}
.template-card span {
  color: var(--text);
}
.template-designer {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  container: template-editor / inline-size;
}
.template-editor-scroll {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: auto;
  padding: 0 2px 16px 0;
}
.template-editor-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.template-editor-heading > div:first-child {
  min-width: 0;
}
.template-editor-heading h2 {
  font-size: 18px;
  margin: 0;
  overflow-wrap: anywhere;
}
.template-editor-heading p {
  margin: 8px 0 0;
  line-height: 20px;
}
.template-editor-heading > div:last-child {
  display: flex;
  flex: none;
  gap: 8px;
}
.template-editor-heading .el-button + .el-button {
  margin-left: 0;
}
.template-properties {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr) 132px;
  column-gap: 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  padding: 16px;
  border-radius: 8px;
}
.template-properties .app-form-field {
  min-width: 0;
  margin-bottom: 12px;
}
.template-properties :deep(.el-select),
.template-properties :deep(.el-input-number) {
  width: 100%;
}
.template-properties > p {
  grid-column: 1/-1;
  margin: 0;
  line-height: 20px;
  font-size: 12px;
}
.template-designer > .page-form-footer {
  flex: none;
}
.template-empty {
  display: grid;
  place-items: center;
  min-width: 0;
  min-height: 0;
  padding: 24px;
}
@container template-editor (max-width: 700px) {
  .template-properties {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .template-editor-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }
}
@media (max-width: 1100px) {
  .home-template-page {
    grid-template-columns: 210px minmax(0, 1fr);
    gap: 12px;
    padding-left: 12px;
    padding-right: 12px;
  }
}
</style>
