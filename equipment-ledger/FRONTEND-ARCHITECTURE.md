# 前端架构与维护约定

## 技术栈

Vue 3、TypeScript、Vite、Element Plus、Vue Router、Pinia。依赖采用明确版本，提交 package-lock.json。

## 代码职责

- `App.vue`：中文组件配置与路由入口。
- `WorkspaceLayout.vue`：导航、页面容器，以及现有台账和基础数据列表。
- `router/index.ts`：Hash 路由、模块按需加载、菜单与操作权限入口检查。刷新保留当前页面，新建、编辑、履历使用独立路由。
- `stores/auth.ts`：当前用户、菜单与操作授权、登录状态。
- `stores/ui.ts`：主题与导航收起状态。
- `stores/baseData.ts`：服务端基础数据的共享加载。
- `components/EquipmentEditor.vue`：设备新增与编辑表单。
- `components/common/`：公共业务表格、对话框、分页、返回按钮及无权限页面。普通业务表格使用 `AppBusinessTable`，复杂列使用 `BusinessTableColumn`；排序图标和交互采用 Element Plus 默认能力，页面通过事件处理业务数据。
- `components/TreeSelect.vue`、`BooleanSwitch.vue`、`ListToolbarIcons.vue`：统一树选择、是否开关和列表工具栏。
- `services/dialog.ts`：统一确认对话框。
- `styles/tokens.css`：产品颜色与主题变量。
- `styles/element-theme.css`：Element Plus 与产品样式的适配。

普通表格列拖动使用 ElTable 默认能力，列偏好由页面管理；设备履历业务、打印单元格画布和合并权限矩阵保留专用结构。共享用户、主题和基础数据放在 Pinia；未保存的表单草稿放在页面组件。

复用以降低复杂度为目标。基础交互相同可以共用；业务差异大、需要许多条件分支或彼此牵连修改的页面分别实现，不为复用而复用。

## 产品 UI 约定

默认蓝色，支持深蓝、粉色。列表与表单正文 13px，保持紧凑、清晰的企业系统布局。

列表左侧为业务操作，右侧为列筛选、关键字输入框内搜索、按业务需要显示的筛选器和重置。分页在底栏左侧，设置列与导出图标在底栏右侧，底栏与表格一体。具体规则以根目录[UI规范](../UI交互与视觉规范-v1.2.md)中的已确认项为准，本文不另立布局标准。树形基础数据统一使用 TreeSelect。

详情和编辑页左上角返回入口统一使用 `AppBackButton`，以设备详情样式为基准：透明无描边、32px 点击区域、浅蓝悬停反馈。通过 `label` 指明返回目标，保存/提交期间禁用；不在单页重写返回图标样式。

使用主题变量配置颜色，公共控件样式统一调整。不要通过不断追加页面级覆盖规则修补公共控件。Element Plus 样式按组件引入。

## 权限与数据

前端权限决定入口和按钮显示；接口仍由 Java 后端校验操作权限与数据权限。基础数据由后端读取。开发免登录仅由本地后端配置提供，不在前端伪造身份。

## 维护命令

```sh
npm run format:check
npm run typecheck
npm run build
npm run check:icons
npm run check:tables
zsh run-local.sh frontend
```

源码按 Prettier 配置展开排版，使用两空格缩进；压缩只在构建产物中进行。格式化仅针对本次涉及文件；`npm run format` 会全量写入源码，不作为局部修改的默认操作。文档改动不要求运行应用构建或业务测试。

## 样式隔离

业务表格统一声明 `business-table`，共享样式放在 `styles/list-typography.css`。不得使用裸 `table`、`thead th`、`tbody td` 选择器设置业务尺寸、字体、颜色或悬停样式，以免污染日期控件和打印画布。宽列表的最小宽度由具体表格类声明；弹窗中的小型表格默认适应容器。公共默认值使用低优先级选择器，组件负责自身的列宽和滚动布局。
