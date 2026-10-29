<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { tr } from '../i18n';
import { useAuthStore } from '../stores/auth';
import { visibleNotificationTabs } from '../utils/notificationAccess';
import AppListTabs from '../components/common/AppListTabs.vue';
import NotificationAdmin from '../components/system/NotificationAdmin.vue';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const tabs = computed(() => visibleNotificationTabs(auth.can));
const items = computed(() => tabs.value.map((tab) => ({ key: tab.key, label: tr(tab.label) })));
const active = computed(
  () => tabs.value.find((tab) => tab.key === route.query.tab)?.key ?? tabs.value[0]?.key,
);
function select(tab: string) {
  if (tab === active.value || !tabs.value.some((item) => item.key === tab)) return;
  void router.replace({ name: 'notification-policies', query: { ...route.query, tab } });
}
</script>

<template>
  <section class="content notification-settings-page">
    <AppListTabs v-if="active" :active="active" :items="items" @select="select" />
    <NotificationAdmin v-if="active" :view="active" />
  </section>
</template>

<style scoped>
.notification-settings-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.notification-settings-page :deep(.notification-page) {
  flex: 1;
  height: auto;
  padding: 0;
}
</style>
