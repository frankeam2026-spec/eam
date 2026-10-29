<script setup lang="ts">
import { ref } from 'vue';
import { ElButton, ElInput, ElMessage, ElAlert, ElUpload } from 'element-plus';
import KnowledgeSourcePreview from './KnowledgeSourcePreview.vue';
import { useRouter } from 'vue-router';
import AppDialog from '../common/AppDialog.vue';
import { request } from '../../api';
import { tr } from '../../i18n';
import { useAuthStore } from '../../stores/auth';
import { useUnsavedChanges } from '../../composables/useUnsavedChanges';
const props = defineProps<{
  mode: 'REPORT' | 'RECORD' | 'DIAGNOSE';
  orderId?: string;
  input?: string;
}>();
const emit = defineEmits<{ apply: [text: string] }>();
const auth = useAuthStore();
const router = useRouter();
type Source = {
  chunkId: string;
  documentId: string;
  versionId?: string;
  pageNo: number;
  title: string;
  excerpt: string;
  hasOriginal?: boolean;
  repairOrderId?: string;
};
const sources = ref<Source[]>([]),
  previewSource = ref<Source>();
const open = ref(false),
  busy = ref(false),
  text = ref(''),
  draft = ref(''),
  missing = ref<string[]>([]),
  photo = ref<File>(),
  audio = ref<File>(),
  error = ref('');
const guard = useUnsavedChanges(
  () => ({
    text: text.value,
    draft: draft.value,
    photo: photo.value?.name,
    audio: audio.value?.name,
  }),
  () => open.value,
  () => busy.value,
);
async function cancel() {
  if (await guard.permitLeave()) open.value = false;
}
function apply() {
  emit('apply', draft.value);
  guard.markSaved();
  open.value = false;
}
function start() {
  text.value = props.input || '';
  draft.value = '';
  sources.value = [];
  missing.value = [];
  photo.value = undefined;
  audio.value = undefined;
  error.value = '';
  open.value = true;
  guard.markSaved();
}
async function generate() {
  busy.value = true;
  error.value = '';
  try {
    const body = new FormData();
    body.append('mode', props.mode);
    body.append('text', text.value);
    if (props.orderId) body.append('orderId', props.orderId);
    if (photo.value) body.append('photo', photo.value);
    sources.value = [];
    const result = await request<{ text: string; missingInfo: string[]; citations?: Source[] }>(
      '/repair-assist',
      {
        method: 'POST',
        body,
      },
    );
    draft.value = result.text;
    sources.value = result.citations || [];
    missing.value = result.missingInfo;
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  } finally {
    busy.value = false;
  }
}
async function transcribe() {
  if (!audio.value) return;
  busy.value = true;
  error.value = '';
  try {
    const body = new FormData();
    body.append('audio', audio.value);
    const result = await request<{ text: string }>('/repair-assist/transcribe', {
      method: 'POST',
      body,
    });
    text.value = [text.value, result.text].filter(Boolean).join('\n');
    ElMessage.success(tr('assist.transcribed'));
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  } finally {
    busy.value = false;
  }
}
function choose(e: { raw?: File }, kind: 'photo' | 'audio') {
  const f = e.raw;
  if (kind === 'photo') photo.value = f;
  else audio.value = f;
}
</script>
<template>
  <ElButton v-if="auth.can('knowledge', '查看')" type="primary" link @click="start">{{
    tr('assist.' + mode)
  }}</ElButton
  ><AppDialog
    v-model="open"
    :title="tr('assist.' + mode)"
    width="760px"
    :busy="busy"
    :before-close="guard.permitLeave"
    ><ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <p>{{ tr('assist.notice') }}</p>
    <ElInput
      v-model="text"
      type="textarea"
      :rows="4"
      maxlength="5000"
      :disabled="busy"
      :placeholder="tr('assist.input')"
    />
    <div class="assist-files">
      <label
        >{{ tr('assist.photo')
        }}<ElUpload
          :auto-upload="false"
          :limit="1"
          accept="image/png,image/jpeg"
          :disabled="busy"
          @remove="photo = undefined"
          @change="choose($event, 'photo')"
          ><ElButton :disabled="busy">{{ tr('assist.chooseFile') }}</ElButton></ElUpload
        ></label
      ><label v-if="mode === 'REPORT'"
        >{{ tr('assist.audio')
        }}<ElUpload
          :auto-upload="false"
          :limit="1"
          accept="audio/wav,.wav"
          :disabled="busy"
          @remove="audio = undefined"
          @change="choose($event, 'audio')"
          ><ElButton :disabled="busy">{{ tr('assist.chooseFile') }}</ElButton></ElUpload
        ><ElButton :disabled="busy || !audio" @click="transcribe">{{
          tr('assist.transcribe')
        }}</ElButton></label
      >
    </div>
    <ElButton :loading="busy" @click="generate">{{ tr('assist.generate') }}</ElButton
    ><template v-if="draft"
      ><h4>{{ tr('assist.preview') }}</h4>
      <ElInput v-model="draft" type="textarea" :rows="8" :readonly="mode === 'DIAGNOSE'" />
      <ul v-if="missing.length">
        <li v-for="item in missing" :key="item">{{ item }}</li>
      </ul>
      <section v-if="sources.length">
        <h4>{{ tr('assist.references') }}</h4>
        <details v-for="source in sources" :key="source.chunkId">
          <summary>
            {{ source.title }} ·
            {{
              source.hasOriginal === false
                ? tr('knowledge.case')
                : tr('knowledge.page', { page: source.pageNo })
            }}
          </summary>
          <p>{{ source.excerpt }}</p>
          <ElButton
            v-if="source.versionId && source.hasOriginal !== false"
            text
            @click="previewSource = source"
            >{{ tr('xiaozhu.viewPage') }}</ElButton
          ><ElButton
            v-if="source.repairOrderId"
            text
            @click="router.push({ name: 'repair-detail', params: { id: source.repairOrderId } })"
            >{{ tr('knowledge.sourceOrder') }}</ElButton
          >
        </details>
      </section></template
    ><template #footer
      ><ElButton :disabled="busy" @click="cancel">{{ tr('knowledge.cancel') }}</ElButton
      ><ElButton
        v-if="mode !== 'DIAGNOSE'"
        type="primary"
        :disabled="busy || !draft.trim()"
        @click="apply"
        >{{ tr('assist.apply') }}</ElButton
      ></template
    ></AppDialog
  >
  <KnowledgeSourcePreview :source="previewSource" @close="previewSource = undefined" />
</template>
<style scoped>
.assist-files {
  display: grid;
  gap: 12px;
  margin: 12px 0;
}
.assist-files label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.assist-files input {
  max-width: 300px;
}
</style>
