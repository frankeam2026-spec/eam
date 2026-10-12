<script setup lang="ts">
import { ElDropdown, ElDropdownMenu, ElDropdownItem, ElButton } from 'element-plus';
import AppDialog from './AppDialog.vue';
import { version } from '../../../package.json';

const section = defineModel<string>({ default: '' });
const titles: Record<string, string> = {
  guide: '操作指南',
  feedback: '问题反馈',
  about: '关于系统',
};
</script>

<template>
  <ElDropdown class="help-menu" trigger="click" placement="bottom-end" @command="section = $event">
    <button class="help-trigger" type="button" title="帮助" aria-label="帮助">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="9" />
        <path d="M9.5 9a2.5 2.5 0 0 1 5 0c0 2-2.5 2-2.5 4M12 16.5v.1" />
      </svg>
    </button>
    <template #dropdown>
      <ElDropdownMenu>
        <ElDropdownItem command="guide">操作指南</ElDropdownItem>
        <ElDropdownItem command="feedback">问题反馈</ElDropdownItem>
        <ElDropdownItem command="about" divided>关于系统</ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>
  <AppDialog
    :model-value="Boolean(section)"
    :title="titles[section] || '帮助'"
    width="520px"
    @update:model-value="section = ''"
  >
    <div class="help-content">
      <template v-if="section === 'guide'">
        <h3>查找设备</h3>
        <p>
          在设备台账中通过编号、名称或型号搜索。点击设备编号查看基本信息及维修、保养、巡检和备件记录。
        </p>
        <h3>处理任务</h3>
        <p>
          进入工作台的待办，按维修、保养或点巡检查找任务。点击对应操作进入接单、处理或验收页面。
        </p>
        <h3>筛选列表</h3>
        <p>
          使用输入框快速查询，列筛选查询对应字段。支持筛选器的列表可以组合更多条件；设置列可调整展示字段。
        </p>
      </template>
      <template v-else-if="section === 'feedback'">
        <p>遇到问题时，请将以下信息提供给系统管理员，方便定位：</p>
        <ul>
          <li>所在模块、设备编号或任务编号。</li>
          <li>操作步骤、预期结果及实际结果。</li>
          <li>问题截图和发生时间。</li>
        </ul>
        <p>请勿在反馈中提供密码。</p>
      </template>
      <template v-else-if="section === 'about'">
        <h3>设备管理系统</h3>
        <p>版本：{{ version }}</p>
        <p>设备台账 · 维修 · 保养 · 点巡检 · 系统管理</p>
      </template>
    </div>
    <template #footer><ElButton @click="section = ''">关闭</ElButton></template>
  </AppDialog>
</template>

<style scoped>
.help-menu {
  color: inherit;
}
.help-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.help-trigger:hover {
  background: #ffffff20;
}
.help-trigger:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 2px;
}
.help-trigger svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.help-content {
  color: var(--text);
  font-size: 13px;
  line-height: 1.8;
}
.help-content h3 {
  margin: 16px 0 6px;
  font-size: 13px;
  font-weight: 600;
}
.help-content h3:first-child {
  margin-top: 0;
}
.help-content p {
  margin: 0 0 12px;
}
.help-content ul {
  padding-left: 20px;
}
</style>
