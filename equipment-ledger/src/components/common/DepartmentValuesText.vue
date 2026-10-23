<script setup lang="ts">
import { computed } from 'vue';
import DepartmentText from './DepartmentText.vue';
const props = defineProps<{ value?: string; ids?: unknown }>();
const departments = computed(() => {
  const ids = Array.isArray(props.ids)
    ? props.ids.map(String)
    : props.ids
      ? [String(props.ids)]
      : [];
  const names = (props.value || '').split('、');
  return Array.from({ length: Math.max(ids.length, names.length) }, (_, index) => ({
    id: ids[index] || '',
    name: names[index] || '',
  }));
});
</script>
<template>
  <span
    ><template v-for="(department, index) in departments" :key="index"
      ><span v-if="index">、</span
      ><DepartmentText :value="department.name" :department-id="department.id" /></template
  ></span>
</template>
