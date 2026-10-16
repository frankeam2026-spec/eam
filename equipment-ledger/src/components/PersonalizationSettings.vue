<script setup lang="ts">
import AppFilePicker from './common/AppFilePicker.vue';
import { ElButton } from 'element-plus';

import { onMounted, onBeforeUnmount, ref, computed } from 'vue';
import { ElForm, ElInput, ElSelect, ElOption, ElMessage } from 'element-plus';
import AppFormField from './common/AppFormField.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import AppIcon from './common/AppIcon.vue';
import {
  getBrandingSettings,
  saveBrandingSettings,
  uploadBrandingImage,
  discardBrandingUpload,
  defaultBranding,
  type BrandingSettings,
} from '../branding';
import { ApiError } from '../api';
import { useBrandingStore } from '../stores/branding';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
const store = useBrandingStore();
const staged = new Set<string>();
function discardStaged() {
  for (const id of staged) void discardBrandingUpload(id).catch(() => {});
  staged.clear();
}
onBeforeUnmount(discardStaged);
const current = ref<BrandingSettings>();
const saved = ref<BrandingSettings>();
const loading = ref(false),
  saving = ref(false),
  uploading = ref(''),
  error = ref(''),
  nameError = ref('');
const busy = computed(() => loading.value || saving.value || !!uploading.value);
const logoPicker = ref<InstanceType<typeof AppFilePicker>>(),
  backgroundPicker = ref<InstanceType<typeof AppFilePicker>>();
const { markSaved, dirty } = useUnsavedChanges(
  () => current.value,
  () => !!current.value,
  () => busy.value,
);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const { data } = await getBrandingSettings();
    current.value = { ...data };
    saved.value = { ...data };
    markSaved();
  } catch (e) {
    error.value = e instanceof Error ? e.message : '配置加载失败';
  } finally {
    loading.value = false;
  }
}
function reset() {
  if (!current.value || busy.value) return;
  Object.assign(current.value, defaultBranding, { logoFileId: '', backgroundFileId: '' });
  nameError.value = '';
  error.value = '';
}
function cancel() {
  if (!saved.value || busy.value) return;
  discardStaged();
  current.value = { ...saved.value };
  nameError.value = '';
  error.value = '';
  markSaved();
}
function remove(kind: 'logo' | 'background') {
  if (!current.value || busy.value) return;
  if (kind === 'logo') {
    current.value.logoFileId = '';
    current.value.logoUrl = defaultBranding.logoUrl;
  } else {
    current.value.backgroundFileId = '';
    current.value.backgroundUrl = defaultBranding.backgroundUrl;
    current.value.backgroundPosition = 'center';
  }
}
async function upload(files: File[], kind: 'logo' | 'background') {
  const file = files[0];

  if (!file || !current.value || busy.value) return;
  const limit = kind === 'logo' ? 2 : 10;
  if (!['image/png', 'image/jpeg'].includes(file.type)) {
    ElMessage.error('请选择PNG或JPG图片');
    return;
  }
  if (file.size > limit * 1024 * 1024) {
    ElMessage.error(`图片不能超过${limit}MB`);
    return;
  }
  uploading.value = kind;
  error.value = '';
  try {
    const { data } = await uploadBrandingImage(file, kind);
    staged.add(data.id);
    if (kind === 'logo') {
      current.value.logoFileId = data.id;
      current.value.logoUrl = data.url;
    } else {
      current.value.backgroundFileId = data.id;
      current.value.backgroundUrl = data.url;
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '上传失败';
  } finally {
    uploading.value = '';
  }
}
async function save() {
  if (!current.value || busy.value) return;
  nameError.value = current.value.systemName.trim() ? '' : '请输入系统名称';
  if (nameError.value) {
    document.querySelector<HTMLInputElement>('.personalization-name input')?.focus();
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    const { data } = await saveBrandingSettings(current.value);
    current.value = { ...data };
    saved.value = { ...data };
    store.apply(data);
    discardStaged();
    markSaved();
    ElMessage.success('个性化设置已保存');
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败';
    if (e instanceof ApiError && e.fieldErrors.systemName) {
      nameError.value = e.fieldErrors.systemName;
      error.value = '';
    }
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="personalization-page" v-loading="loading">
    <div class="settings-body">
      <div v-if="error" class="settings-error" role="alert">
        {{ error
        }}<ElButton v-if="!current" class="text-action" @click="load" type="primary" link
          >重试</ElButton
        >
      </div>
      <section v-if="current" class="settings-card">
        <header>
          <div>
            <h3>系统品牌</h3>
            <p>集团、公司和部门共用，保存后统一生效。</p>
          </div>
          <ElButton class="" :disabled="busy" @click="reset">恢复默认</ElButton>
        </header>
        <ElForm
          class="settings-fields"
          :model="current"
          :rules="{
            systemName: [
              { required: true, whitespace: true, message: '请输入系统名称', trigger: 'blur' },
            ],
          }"
          label-width="126px"
          @submit.prevent
        >
          <AppFormField
            class="personalization-name"
            prop="systemName"
            label="系统名称"
            :error="nameError"
            required
          >
            <ElInput
              id="system-name"
              v-model="current.systemName"
              maxlength="30"
              show-word-limit
              :disabled="busy"
              placeholder="请输入系统名称"
              @input="nameError = ''"
            />
          </AppFormField>
          <div class="setting-row">
            <label>系统Logo</label>
            <div class="field-content">
              <div class="image-setting">
                <div class="logo-frame"><img :src="current.logoUrl" alt="当前系统Logo" /></div>
                <div class="image-actions">
                  <div class="button-row">
                    <ElButton class="" :disabled="busy" @click="logoPicker?.open()"
                      ><AppIcon name="Upload" />{{
                        uploading === 'logo' ? '上传中…' : current.logoFileId ? '替换' : '上传'
                      }}</ElButton
                    ><ElButton
                      v-if="current.logoFileId"
                      class="text-action"
                      :disabled="busy"
                      @click="remove('logo')"
                      type="primary"
                      link
                    >
                      移除
                    </ElButton>
                  </div>
                  <small>PNG、JPG，最大2MB；建议使用透明背景的方形Logo。</small
                  ><small v-if="!current.logoFileId">当前使用默认Logo</small>
                </div>
              </div>
              <AppFilePicker
                ref="logoPicker"
                accept="image/png,image/jpeg"
                hidden
                @select="upload($event, 'logo')"
              />
            </div>
          </div>
          <div class="setting-row">
            <label>登录页背景</label>
            <div class="field-content">
              <div class="image-setting">
                <div class="background-frame">
                  <img :src="current.backgroundUrl" alt="当前登录背景图片" />
                </div>
                <div class="image-actions">
                  <div class="button-row">
                    <ElButton class="" :disabled="busy" @click="backgroundPicker?.open()"
                      ><AppIcon name="Upload" />{{
                        uploading === 'background'
                          ? '上传中…'
                          : current.backgroundFileId
                            ? '替换'
                            : '上传'
                      }}</ElButton
                    ><ElButton
                      v-if="current.backgroundFileId"
                      class="text-action"
                      :disabled="busy"
                      @click="remove('background')"
                      type="primary"
                      link
                    >
                      移除
                    </ElButton>
                  </div>
                  <small>PNG、JPG，最大10MB；建议1920×1080，图片等比例铺满。</small
                  ><small v-if="!current.backgroundFileId">当前使用默认背景</small>
                </div>
              </div>
              <AppFilePicker
                ref="backgroundPicker"
                accept="image/png,image/jpeg"
                hidden
                @select="upload($event, 'background')"
              />
            </div>
          </div>
          <div class="setting-row">
            <label>背景显示位置</label>
            <div class="field-content">
              <ElSelect v-model="current.backgroundPosition" :disabled="busy"
                ><ElOption
                  v-for="p in [
                    { value: 'center', label: '居中' },
                    { value: 'top', label: '靠上' },
                    { value: 'bottom', label: '靠下' },
                    { value: 'left', label: '靠左' },
                    { value: 'right', label: '靠右' },
                  ]"
                  :key="p.value"
                  :value="p.value"
                  :label="p.label" /></ElSelect
              ><small>调整图片裁切位置，保持登录表单清晰可见。</small>
            </div>
          </div>
        </ElForm>
      </section>
    </div>
    <AppFormFooter
      ><ElButton class="" :disabled="busy || !current" @click="cancel">取消</ElButton
      ><ElButton class="" :disabled="busy || !current || !dirty" @click="save" type="primary">{{
        saving ? '保存中…' : '保存'
      }}</ElButton></AppFormFooter
    >
  </div>
</template>
<style scoped>
.personalization-page {
  height: calc(100dvh - 54px);
  min-height: 0;
  display: flex;
  flex-direction: column;
  color: var(--text-primary);
  font-size: 13px;
}
.settings-body {
  flex: 1;
  overflow: auto;
  padding: 16px;
}
.settings-card {
  background: #fff;
  border: 1px solid var(--border-color, #e3e8f0);
  border-radius: 8px;
}
.settings-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  border-bottom: 1px solid #e9edf4;
}
h3 {
  font-size: 15px;
  margin: 0 0 6px;
}
p {
  margin: 0;
  line-height: 1.6;
}
.settings-fields {
  max-width: 960px;
  padding: 24px;
  display: grid;
  gap: 24px;
}
.setting-row {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  align-items: start;
  gap: 16px;
}
.setting-row > label {
  padding-top: 8px;
}
.required-mark {
  color: #c9303d;
  margin-left: 4px;
}
.field-content {
  display: grid;
  gap: 8px;
  max-width: 650px;
}
.field-content :deep(.el-select) {
  max-width: 250px;
}
.image-setting {
  display: flex;
  align-items: center;
  gap: 20px;
}
.logo-frame {
  width: 80px;
  height: 80px;
  flex-shrink: 0;
  padding: 8px;
  border: 1px solid #e2e8f1;
  border-radius: 8px;
  background: #f8faff;
}
.logo-frame img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.background-frame {
  width: 200px;
  height: 112px;
  border: 1px solid #e2e8f1;
  border-radius: 6px;
  overflow: hidden;
  flex-shrink: 0;
}
.background-frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.image-actions {
  display: grid;
  gap: 10px;
}
.button-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
small {
  font-size: 13px;
  line-height: 1.6;
}
.personalization-name {
  margin-bottom: 0;
  max-width: 776px;
}
.settings-error {
  padding: 12px 16px;
  margin-bottom: 16px;
  background: #fff2f1;
  border: 1px solid #f5ceca;
  border-radius: 6px;
  color: #b22f38;
  display: flex;
  gap: 16px;
}
@media (max-width: 800px) {
  .settings-fields {
    padding: 18px;
  }
  .setting-row {
    grid-template-columns: 90px minmax(0, 1fr);
  }
  .image-setting {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
