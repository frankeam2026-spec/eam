<script setup lang="ts">import { tr } from '../../i18n';

import { ElTimeline, ElTimelineItem, ElEmpty } from 'element-plus';
import type { MaintenanceTask } from '../../maintenance';
import { acceptanceDecisionLabel } from '../../config/acceptance';
defineProps<{ events: MaintenanceTask['events'] }>();
</script>
<template>
  <ElTimeline v-if="events.length" class="task-timeline-events">
    <ElTimelineItem
      v-for="(event, index) in events"
      :key="index"
      :timestamp="event.time"
      placement="top"
      type="primary"
    >
      <strong>{{ event.action }}</strong>
      <p>{{ event.actor }}</p>
      <p v-if="event.details.description">{{ event.details.description }}</p>
      <p v-if="event.details.decision">
        {{ acceptanceDecisionLabel(String(event.details.decision))
        }}<template v-if="event.details.score != null"> · {{ event.details.score }} {{ tr('ui.equipment.text0439') }}</template>
      </p>
    </ElTimelineItem>
  </ElTimeline>
  <ElEmpty v-else description="暂无保养记录" :image-size="56" />
</template>
<style scoped>
.task-timeline-events {
  padding-left: 4px;
  margin: 0;
}
.task-timeline-events strong {
  font-size: 13px;
}
.task-timeline-events p {
  margin: 6px 0 0;
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
</style>
