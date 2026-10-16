# 公共业务表格代码草稿

此目录保留首次审阅草稿。生产实现已接入 `src/components/common/AppBusinessTable.vue` 与 `BusinessTableColumn.vue`，当前页面使用生产组件；不要把本目录草稿当成最新实现。

- AppBusinessTable.vue：el-table渲染、固定列、服务端排序事件、多选保留、列宽事件、具名业务插槽、错误/空态和一体分页底栏。
- BusinessTable.types.ts：类型明确的列配置和排序结果。
- EquipmentTable.example.vue：插槽、权限显隐及底栏示例；数据为空、操作未连接业务接口，避免误认成真实台账。

## 边界

1. 页面负责接口、搜索/筛选器、权限、列偏好保存、业务状态和导航。
2. 列设置决定传入哪些列及其顺序；表格不创建第二套列设置存储。
3. 多选采用el-table row-key与reserve-selection；表头全选仅当前页。查询/组织/页签改变、权限失效时，页面调用clearSelection；最终操作服务端逐项校验。返回详情或刷新若需恢复跨页选择，后续补充受控ID选中接口，草稿不宣称已实现这部分。
4. 排序配置为custom，仅发事件，页面更新应用查询并请求服务端。分页由v-model传递，页面统一监听页码/每页条数，避免重复请求。
5. 固定操作列至少156px且不可拖至更窄；实际两个按钮较长时传入更大actionWidth。仅有actions插槽时生成操作列。
6. 普通列表容器需有确定高度（常用父级flex:1/min-height:0），表体滚动、底栏一体固定。详情明细/行内编辑/权限矩阵/打印画布本草稿不强制适用。
7. 原生列宽拖动需要el-table border启用；草稿移除竖线外观，保留拖动机制，不另写DOM拖动事件。

## 验证

独立vue-tsc配置已验证组件与示例类型；未做浏览器布局、多角色、跨页选择或服务端业务验收。本次未改变现有页面。

命令：node node_modules/vue-tsc/bin/vue-tsc.js -p proposals/business-table/tsconfig.json --noEmit
