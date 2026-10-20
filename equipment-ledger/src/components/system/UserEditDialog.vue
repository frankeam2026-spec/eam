<script setup lang="ts">import { tr } from '../../i18n';

import { ref } from 'vue';
import {
  ElForm,
  ElInput,
  ElSelect,
  ElOption,
  ElTreeSelect,
  ElButton,
  type FormInstance,
  type FormRules,
  type TreeNodeData,
} from 'element-plus';
import AppDialog from '../common/AppDialog.vue';
import AppFormField from '../common/AppFormField.vue';
import type { SystemUserRecord } from '../../api';
type UserForm = SystemUserRecord & { password?: string };
defineProps<{
  modelValue: boolean;
  editing: boolean;
  busy: boolean;
  form: UserForm;
  rules: FormRules<UserForm>;
  errors: Record<string, string>;
  departments: TreeNodeData[];
  roles: { id: string; name: string; status: string }[];
  canAssignRoles: boolean;
  roleText: string;
  beforeClose: () => Promise<boolean>;
}>();
const emit = defineEmits<{ 'update:modelValue': [boolean]; save: []; cancel: [] }>();
const formRef = ref<FormInstance>();
defineExpose({
  validate: () => formRef.value?.validate() ?? Promise.resolve(false),
  clearValidate: () => formRef.value?.clearValidate(),
});
</script>
<template>
  <AppDialog
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    :title="editing ? tr('ui.equipment.text1602') : tr('ui.equipment.text1192')"
    :busy="busy"
    :before-close="beforeClose"
    width="680px"
  >
    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="88px"
      :disabled="busy"
      class="user-edit-form"
      @submit.prevent="emit('save')"
    >
      <AppFormField :label="tr('ui.equipment.text0740')" prop="account" :error="errors.account"
        ><ElInput v-model="form.account" :placeholder="tr('ui.equipment.text1183')" autocomplete="off"
      /></AppFormField>
      <AppFormField :label="tr('ui.equipment.text1168')" prop="name" :error="errors.name"
        ><ElInput v-model="form.name" :placeholder="tr('ui.equipment.text1185')"
      /></AppFormField>
      <AppFormField :label="tr('ui.equipment.text1169')" prop="employeeNo" :error="errors.employeeNo"
        ><ElInput v-model="form.employeeNo" :placeholder="tr('ui.equipment.text1603')"
      /></AppFormField>
      <AppFormField :label="tr('ui.equipment.text1172')" prop="phone" :error="errors.phone"
        ><ElInput v-model="form.phone" :placeholder="tr('ui.equipment.text1604')"
      /></AppFormField>
      <AppFormField :label="tr('ui.equipment.text1170')" prop="orgId" :error="errors.orgId" class="user-form-wide"
        ><ElTreeSelect
          v-model="form.orgId"
          :data="departments"
          node-key="id"
          :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
          check-strictly
          filterable
          clearable
          :placeholder="tr('ui.equipment.text1605')"
      /></AppFormField>
      <AppFormField
        :label="editing ? tr('ui.equipment.text1195') : tr('ui.equipment.text1606')"
        prop="password"
        :error="errors.password"
        :required="!editing"
        class="user-form-wide"
        ><ElInput
          v-model="form.password"
          type="password"
          show-password
          autocomplete="new-password"
          :placeholder="editing ? tr('ui.equipment.text1608') : tr('ui.equipment.text1607')"
      /></AppFormField>
      <AppFormField :label="tr('ui.equipment.text0170')" prop="status" :error="errors.status"
        ><ElSelect v-model="form.status"
          ><ElOption :label="tr('ui.equipment.text0216')" value="启用" /><ElOption :label="tr('ui.equipment.text0260')" value="停用" /></ElSelect
      ></AppFormField>
      <AppFormField :label="tr('ui.equipment.text1171')" prop="roleIds" :error="errors.roleIds"
        ><ElSelect
          v-if="canAssignRoles"
          v-model="form.roleIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          :max-collapse-tags="1"
          :placeholder="tr('ui.equipment.text1609')"
          ><ElOption
            v-for="role in roles"
            :key="role.id"
            :label="role.name"
            :value="role.id"
            :disabled="role.status !== '启用'" /></ElSelect
        ><span v-else>{{ roleText || tr('ui.equipment.text1610') }}</span></AppFormField
      >
    </ElForm>
    <template #footer
      ><ElButton :disabled="busy" @click="emit('cancel')">{{ tr('ui.equipment.text0157') }}</ElButton
      ><ElButton type="primary" :loading="busy" @click="emit('save')">{{ tr('ui.equipment.text0159') }}</ElButton></template
    >
  </AppDialog>
</template>
<style scoped>
.user-edit-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  column-gap: 24px;
}
.user-edit-form :deep(.el-select) {
  width: 100%;
}
.user-form-wide {
  grid-column: 1/-1;
}
@media (max-width: 600px) {
  .user-edit-form {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
