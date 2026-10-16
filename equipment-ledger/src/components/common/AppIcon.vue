<script setup lang="ts">
import { computed, type Component } from 'vue';
import Refresh from '@icon-park/vue-next/es/icons/Refresh';
import Grid from '@icon-park/vue-next/es/icons/ApplicationMenu';
import Equipment from '@icon-park/vue-next/es/icons/FactoryBuilding';
import Document from '@icon-park/vue-next/es/icons/FileText';
import Tool from '@icon-park/vue-next/es/icons/Tool';
import Clock from '@icon-park/vue-next/es/icons/Alarm';
import Check from '@icon-park/vue-next/es/icons/Check';
import Warehouse from '@icon-park/vue-next/es/icons/BuildingFour';
import Settings from '@icon-park/vue-next/es/icons/SettingTwo';
import Organization from '@icon-park/vue-next/es/icons/TreeDiagram';
import User from '@icon-park/vue-next/es/icons/People';
import Permissions from '@icon-park/vue-next/es/icons/Permissions';
import Location from '@icon-park/vue-next/es/icons/MapDraw';
import Plus from '@icon-park/vue-next/es/icons/Plus';
import Close from '@icon-park/vue-next/es/icons/Close';
import Left from '@icon-park/vue-next/es/icons/Left';
import Right from '@icon-park/vue-next/es/icons/Right';
import Down from '@icon-park/vue-next/es/icons/Down';
import Up from '@icon-park/vue-next/es/icons/Up';
import Sort from '@icon-park/vue-next/es/icons/Sort';
import ArrowUp from '@icon-park/vue-next/es/icons/ArrowUp';
import ArrowDown from '@icon-park/vue-next/es/icons/ArrowDown';
import Search from '@icon-park/vue-next/es/icons/Search';
import Download from '@icon-park/vue-next/es/icons/Download';
import Upload from '@icon-park/vue-next/es/icons/Upload';
import Filter from '@icon-park/vue-next/es/icons/Filter';
import Columns from '@icon-park/vue-next/es/icons/Table';
import Help from '@icon-park/vue-next/es/icons/Help';
import Menu from '@icon-park/vue-next/es/icons/MenuFold';
import Brand from '@icon-park/vue-next/es/icons/FactoryBuilding';
import Bell from '@icon-park/vue-next/es/icons/Alarm';
import Todo from '@icon-park/vue-next/es/icons/Checklist';
import Theme from '@icon-park/vue-next/es/icons/ClothesShortSleeve';
import Loading from '@icon-park/vue-next/es/icons/LoadingOne';
import Warning from '@icon-park/vue-next/es/icons/Attention';
import Picture from '@icon-park/vue-next/es/icons/Picture';
import Undo from '@icon-park/vue-next/es/icons/Undo';
import Redo from '@icon-park/vue-next/es/icons/Redo';
import Transfer from '@icon-park/vue-next/es/icons/Transform';
import QrCode from '@icon-park/vue-next/es/icons/TwoDimensionalCodeOne';
import Barcode from '@icon-park/vue-next/es/icons/Barcode';
import More from '@icon-park/vue-next/es/icons/More';
import Return from '@icon-park/vue-next/es/icons/Undo';
import HamburgerButton from '@icon-park/vue-next/es/icons/HamburgerButton';
import Remind from '@icon-park/vue-next/es/icons/Remind';

const props = defineProps<{ name: string }>();
const icons: Record<string, Component> = {
  Refresh,
  Grid,
  Equipment,
  Document,
  Tool,
  Clock,
  Check,
  Warehouse,
  Settings,
  Organization,
  User,
  Permissions,
  Location,
  Plus,
  Close,
  Left,
  Right,
  Down,
  Up,
  Sort,
  ArrowUp,
  ArrowDown,
  Search,
  Download,
  Upload,
  Filter,
  Columns,
  Help,
  Menu,
  Brand,
  Bell,
  Todo,
  Theme,
  Loading,
  Warning,
  Picture,
  Undo,
  Redo,
  Transfer,
  QrCode,
  Barcode,
  More,
  Return,
  HamburgerButton,
  Remind
};
const icon = computed(() => icons[props.name]);
</script>
<template>
  <span class="app-icon" aria-hidden="true" :data-icon="name">
    <component
      v-if="icon"
      :is="icon"
      theme="outline"
      size="1em"
      fill="currentColor"
      :stroke-width="3"
    />
  </span>
</template>
<style scoped>
.app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1em;
  height: 1em;
  flex: none;
  font-size: var(--app-icon-size, 16px) !important;
  line-height: 1;
  color: inherit;
  vertical-align: middle;
}
.app-icon :deep(.i-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: inherit !important;
}
.app-icon :deep(svg) {
  display: block;
  width: 1em;
  height: 1em;
}
</style>
