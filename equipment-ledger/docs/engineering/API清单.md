# 工程改造接口清单

本清单由 `scripts/engineering/api-inventory.py` 从 Controller 入口生成；权限和事务规则以下方人工维护的契约为准，不通过路径猜测授权。

| 模块 | 调用方 | 权限与数据范围 | 版本及错误约定 |
|---|---|---|---|
| 设备 | 台账、编辑、档案、选择器 | equipment 对应操作；公司/部门/负责人范围，子设备/BOM保存重新校验 | 修改传 rowVersion；扩展配置传 customSchemaVersion |
| 维修 | 工单列表、报修、处理、工作台 | repair 对应操作；报修人/负责人/候选人/验收参与例外保留 | 修改/状态动作传 rowVersion；待派工才可编辑 |
| 保养 | 类型、标准、计划、任务 | maintenanceType/Standard/Plan/Task；任务参与者查看例外与执行/验收判定分开 | rowVersion；计划与任务独立扩展配置 |
| 点巡检 | 标准、日常/周期计划与任务 | inspection 各子菜单；任务参与者范围保留 | rowVersion；暂存/提交校验不同 |
| 库存 | 物料、仓库、入出库、领料与纠正 | 各库存子菜单与仓库范围；金额独立授权，不能由查看仓库代替业务授权 | rowVersion；出库确认另带幂等键和分配指纹 |
| 用户 | 用户列表、编辑、批量操作 | user 对应操作与组织范围；不扩大批量目标 | 保留现有更新版本契约，缺口逐入口登记 |

列表新查询契约：`data`＋`page:{number,size,total}`；错误保留 `code,message,fieldErrors`。400字段错误定位控件，401统一登录失效，403拒绝，409保留草稿；导出叠加导出授权。二进制导出通过公共文件请求读取错误。

## 当前 HTTP 入口

| 模块 | 方法 | 路径 | Controller | 处理方法 |
|---|---|---|---|---|
| equipment | POST | /api/v1/equipment | EquipmentController | create |
| equipment | PUT | /api/v1/equipment/{id} | EquipmentController | update |
| equipment | DELETE | /api/v1/equipment/{id} | EquipmentController | delete |
| equipment | POST | /api/v1/equipment/batch | EquipmentController | batch |
| equipment | POST | /api/v1/equipment/files | EquipmentFileController | upload |
| equipment | GET | /api/v1/equipment/files/{id} | EquipmentFileController | download |
| equipment | GET | /api/v1/equipment/files/{id}/preview | EquipmentFileController | preview |
| equipment | GET | /api/v1/equipment | EquipmentQueryController | list |
| equipment | GET | /api/v1/equipment/export | EquipmentQueryController | export |
| equipment | GET | /api/v1/equipment/{id} | EquipmentQueryController | get |
| equipment | GET | /api/v1/equipment/{id}/qrcode | EquipmentQueryController | qrCode |
| equipment | GET | /api/v1/equipment/navigation-tree | EquipmentQueryController | navigationTree |
| equipment | GET | /api/v1/equipment/batch-options | EquipmentQueryController | batchOptions |
| equipment | GET | /api/v1/equipment/{id}/history | EquipmentQueryController | history |
| equipment | GET | /api/v1/equipment/child-candidates | EquipmentSelectionController | childCandidates |
| equipment | GET | /api/v1/equipment/bom-materials | EquipmentSelectionController | bomMaterials |
| repair | POST | /api/v1/repairs/attachments | RepairOrderController | upload |
| repair | GET | /api/v1/repairs/attachments/{id} | RepairOrderController | download |
| repair | GET | /api/v1/repairs | RepairOrderController | list |
| repair | GET | /api/v1/repairs/export | RepairOrderController | export |
| repair | POST | /api/v1/repairs | RepairOrderController | create |
| repair | PUT | /api/v1/repairs/{id} | RepairOrderController | update |
| repair | POST | /api/v1/repairs/{id}/actions | RepairOrderController | action |
| repair | GET | /api/v1/repairs/user-choices | RepairOrderController | userChoices |
| repair | GET | /api/v1/repairs/inbox | RepairOrderController | inbox |
| repair | PATCH | /api/v1/repairs/inbox/{id}/read | RepairOrderController | readInbox |
| repair | GET | /api/v1/repairs/{id}/workflow | RepairOrderController | detail |
| repair | POST | /api/v1/repairs/{id}/workflow | RepairOrderController | workflowAction |
| repair | GET | /api/v1/repairs/{id}/stocks | RepairOrderController | stockChoices |
| repair | GET | /api/v1/repairs/stocks | RepairOrderController | inventory |
| repair | POST | /api/v1/repairs/stocks | RepairOrderController | stock |
| maintenance | GET | /api/v1/maintenance/list-context | MaintenanceController | listContext |
| maintenance | GET | /api/v1/maintenance/choices | MaintenanceController | choices |
| maintenance | GET | /api/v1/maintenance/tasks | MaintenanceController | tasks |
| maintenance | GET | /api/v1/maintenance/tasks/{id} | MaintenanceController | task |
| maintenance | POST | /api/v1/maintenance/tasks/{id}/{action} | MaintenanceController | action |
| maintenance | POST | /api/v1/maintenance/batch-accept | MaintenanceController | batch |
| maintenance | POST | /api/v1/maintenance/batch-delete | MaintenanceController | batchDelete |
| maintenance | POST | /api/v1/maintenance/batch-postpone | MaintenanceController | batchPostpone |
| maintenance | GET | /api/v1/maintenance/inbox | MaintenanceController | inbox |
| maintenance | PATCH | /api/v1/maintenance/inbox/{id}/read | MaintenanceController | read |
| maintenance | POST | /api/v1/maintenance/plans/{id}/generate | MaintenanceController | generate |
| maintenance | GET | /api/v1/maintenance/{kind} | MaintenanceController | list |
| maintenance | GET | /api/v1/maintenance/{kind}/{id} | MaintenanceController | get |
| maintenance | POST | /api/v1/maintenance/{kind} | MaintenanceController | create |
| maintenance | PUT | /api/v1/maintenance/{kind}/{id} | MaintenanceController | update |
| maintenance | DELETE | /api/v1/maintenance/{kind}/{id} | MaintenanceController | delete |
| maintenance | GET | /api/v1/maintenance/{kind}/export | MaintenanceController | exportQuery |
| maintenance | GET | /api/v1/maintenance/{kind}/export.csv | MaintenanceController | export |
| maintenance | POST | /api/v1/maintenance/files | MaintenanceFileController | upload |
| maintenance | GET | /api/v1/maintenance/files/{id} | MaintenanceFileController | download |
| inspection | GET | /api/v1/inspection/list-context | InspectionController | context |
| inspection | GET | /api/v1/inspection/{kind}/export | InspectionController | exportCsv |
| inspection | GET | /api/v1/inspection/choices | InspectionController | choices |
| inspection | GET | /api/v1/inspection/tasks | InspectionController | tasks |
| inspection | GET | /api/v1/inspection/tasks/{id} | InspectionController | task |
| inspection | POST | /api/v1/inspection/tasks/{id}/{action} | InspectionController | action |
| inspection | POST | /api/v1/inspection/batch-review | InspectionController | review |
| inspection | POST | /api/v1/inspection/export | InspectionController | export |
| inspection | GET | /api/v1/inspection/{kind} | InspectionController | list |
| inspection | GET | /api/v1/inspection/{kind}/{id} | InspectionController | definition |
| inspection | POST | /api/v1/inspection/{kind} | InspectionController | create |
| inspection | PUT | /api/v1/inspection/{kind}/{id} | InspectionController | update |
| inspection | DELETE | /api/v1/inspection/{kind}/{id} | InspectionController | delete |
| inspection | POST | /api/v1/inspection/files | InspectionFileController | upload |
| inspection | GET | /api/v1/inspection/files/{id} | InspectionFileController | download |
| inventory | GET | /api/v1/inventory/choices | InventoryController | choices |
| inventory | GET | /api/v1/inventory/masters/{kind} | InventoryController | list |
| inventory | GET | /api/v1/inventory/masters/{kind}/export | InventoryController | export |
| inventory | POST | /api/v1/inventory/masters/{kind} | InventoryController | create |
| inventory | PUT | /api/v1/inventory/masters/{kind}/{id} | InventoryController | update |
| inventory | GET | /api/v1/inventory/operations/{kind} | InventoryFlowController | list |
| inventory | GET | /api/v1/inventory/operations/{kind}/export | InventoryFlowController | export |
| inventory | GET | /api/v1/inventory/operations/{kind}/choices | InventoryFlowController | choices |
| inventory | GET | /api/v1/inventory/operations/{kind}/{id} | InventoryFlowController | detail |
| inventory | POST | /api/v1/inventory/operations/{kind} | InventoryFlowController | create |
| inventory | PUT | /api/v1/inventory/operations/{kind}/{id} | InventoryFlowController | update |
| inventory | POST | /api/v1/inventory/operations/{kind}/{id}/confirm | InventoryFlowController | confirm |
| inventory | POST | /api/v1/inventory/operations/{kind}/{id}/receive | InventoryFlowController | receive |
| inventory | DELETE | /api/v1/inventory/operations/{kind}/{id} | InventoryFlowController | delete |
| inventory | POST | /api/v1/inventory/balances/{id}/state | InventoryFlowController | freeze |
| inventory | GET | /api/v1/inventory/queries/{kind} | InventoryQueryController | list |
| inventory | GET | /api/v1/inventory/queries/{kind}/export | InventoryQueryController | export |
| inventory | GET | /api/v1/inventory/selectors/tasks | InventorySelectorController | tasks |
| inventory | GET | /api/v1/inventory/selectors/users | InventorySelectorController | users |
| inventory | POST | /api/v1/inventory/selectors/users/resolve | InventorySelectorController | resolveUsers |
| inventory | GET | /api/v1/inventory/task-materials/{kind}/{id} | InventoryUsageController | list |
| inventory | POST | /api/v1/inventory/task-materials/{kind}/{id}/use | InventoryUsageController | use |
| inventory | POST | /api/v1/inventory/task-materials/{kind}/{id}/remove | InventoryUsageController | remove |
| inventory | GET | /api/v1/inventory/outbounds | OutboundController | list |
| inventory | GET | /api/v1/inventory/outbounds/materials | OutboundController | materials |
| inventory | GET | /api/v1/inventory/outbounds/choices | OutboundController | choices |
| inventory | GET | /api/v1/inventory/outbounds/export | OutboundController | export |
| inventory | GET | /api/v1/inventory/outbounds/stock | OutboundController | stock |
| inventory | POST | /api/v1/inventory/outbounds/from-requests | OutboundController | fromRequests |
| inventory | GET | /api/v1/inventory/outbounds/{id} | OutboundController | detail |
| inventory | POST | /api/v1/inventory/outbounds | OutboundController | create |
| inventory | PUT | /api/v1/inventory/outbounds/{id} | OutboundController | update |
| inventory | POST | /api/v1/inventory/outbounds/{id}/preview | OutboundController | preview |
| inventory | POST | /api/v1/inventory/outbounds/{id}/confirm | OutboundController | confirm |
| inventory | DELETE | /api/v1/inventory/outbounds/{id} | OutboundController | delete |
| inventory | GET | /api/v1/inventory/outbounds/pending | OutboundController | pending |
| inventory | GET | /api/v1/inventory/outbounds/pending-export | OutboundController | pendingExport |
| inventory | GET | /api/v1/inventory/receipts | ReceiptController | list |
| inventory | GET | /api/v1/inventory/receipts/export | ReceiptController | export |
| inventory | GET | /api/v1/inventory/receipts/choices | ReceiptController | choices |
| inventory | GET | /api/v1/inventory/receipts/materials | ReceiptController | materials |
| inventory | GET | /api/v1/inventory/receipts/{id} | ReceiptController | detail |
| inventory | POST | /api/v1/inventory/receipts | ReceiptController | create |
| inventory | PUT | /api/v1/inventory/receipts/{id} | ReceiptController | update |
| inventory | POST | /api/v1/inventory/receipts/{id}/confirm | ReceiptController | confirm |
| inventory | DELETE | /api/v1/inventory/receipts/{id} | ReceiptController | delete |
| inventory | POST | /api/v1/inventory/receipts/{id}/allocate-fees | ReceiptController | allocate |
| inventory | GET | /api/v1/inventory/requisitions | RequisitionController | list |
| inventory | GET | /api/v1/inventory/requisitions/export | RequisitionController | export |
| inventory | GET | /api/v1/inventory/requisitions/choices | RequisitionController | choices |
| inventory | GET | /api/v1/inventory/requisitions/materials | RequisitionController | materials |
| inventory | GET | /api/v1/inventory/requisitions/{id} | RequisitionController | detail |
| inventory | POST | /api/v1/inventory/requisitions | RequisitionController | create |
| inventory | PUT | /api/v1/inventory/requisitions/{id} | RequisitionController | update |
| inventory | POST | /api/v1/inventory/requisitions/{id}/submit | RequisitionController | submit |
| inventory | POST | /api/v1/inventory/requisitions/merged-preview | RequisitionController | mergedPreview |
| inventory | POST | /api/v1/inventory/requisitions/merged-confirm | RequisitionController | mergedConfirm |
| inventory | GET | /api/v1/inventory/requisitions/{id}/stock-pool | RequisitionController | stockPool |
| inventory | POST | /api/v1/inventory/requisitions/{id}/reserve | RequisitionController | reserve |
| inventory | POST | /api/v1/inventory/requisitions/{id}/release | RequisitionController | release |
| inventory | POST | /api/v1/inventory/requisitions/{id}/cancel | RequisitionController | cancel |
| inventory | POST | /api/v1/inventory/requisitions/{id}/issue-preview | RequisitionController | previewIssue |
| inventory | POST | /api/v1/inventory/requisitions/{id}/issue-confirm | RequisitionController | confirmIssue |
| inventory | DELETE | /api/v1/inventory/requisitions/{id} | RequisitionController | delete |
| inventory | POST | /api/v1/inventory/requisitions/{id}/close-remaining | RequisitionController | closeRemaining |
| system | GET | /api/v1/system/roles/warehouse-choices | SystemManagementController | roleWarehouses |
| system | GET | /api/v1/system/organizations | SystemManagementController | organizations |
| system | POST | /api/v1/system/organizations | SystemManagementController | createOrg |
| system | PUT | /api/v1/system/organizations/{id} | SystemManagementController | updateOrg |
| system | DELETE | /api/v1/system/organizations/{id} | SystemManagementController | deleteOrg |
| system | GET | /api/v1/system/users | SystemManagementController | users |
| system | GET | /api/v1/system/users/summary | SystemManagementController | userSummary |
| system | GET | /api/v1/system/users/export | SystemManagementController | userExport |
| system | POST | /api/v1/system/users | SystemManagementController | createUser |
| system | PUT | /api/v1/system/users/{id} | SystemManagementController | updateUser |
| system | PATCH | /api/v1/system/users/{id}/status | SystemManagementController | userStatus |
| system | POST | /api/v1/system/users/batch | SystemManagementController | batchUsers |
| system | DELETE | /api/v1/system/users/{id} | SystemManagementController | deleteUser |
| system | GET | /api/v1/system/roles | SystemManagementController | roles |
| system | POST | /api/v1/system/roles | SystemManagementController | createRole |
| system | PUT | /api/v1/system/roles/{id} | SystemManagementController | updateRole |
| system | DELETE | /api/v1/system/roles/{id} | SystemManagementController | deleteRole |
| planning | GET | /api/v1/{module:maintenance|inspection}/selectors/equipment | PlanningSelectorController | equipment |
| planning | GET | /api/v1/{module:maintenance|inspection}/selectors/users | PlanningSelectorController | users |
| planning | GET | /api/v1/{module:maintenance|inspection}/selectors/standards | PlanningSelectorController | standards |
| planning | POST | /api/v1/{module:maintenance|inspection}/selectors/resolve | PlanningSelectorController | resolve |
| planning | POST | /api/v1/{module:maintenance|inspection}/selectors/defaults | PlanningSelectorController | defaults |

### 保养任务批量操作（2026-10-18）

- 删除：请求任务ID和rowVersion列表，沿用删除权限及数据范围；限待执行、已作废、过期作废且无执行及用料关联的任务。V80新增逻辑删除字段，保留原任务、发生日期唯一键及操作记录，业务视图排除已删除任务。
- 延期：请求tasks（ID、rowVersion）、endDate（YYYY-MM-DD）、reason；沿用编辑权限及数据范围。限待执行、执行中、挂起且有截止日期的任务。新日期须晚于所有参与任务的原结束日期，且不早于今天。只延长本次结束时间，不调整计划开始时间和后续计划。
- 验收：沿用原入口、指定验收人、验收权限、状态及版本校验，前端增加整批资格提示。
- 每批最多100条，拒绝重复ID，按ID固定顺序锁定；任一校验失败整批回滚，不自动跳过任务。
- 前后端构建及静态核对已通过；隔离数据、并发及浏览器业务操作验收尚未执行。

### 保养计划设备有效时长单位（2026-10-18）

- 计划details新增validDuration（整数数量）、validUnit（日、周、月）；validDays保留兼容，旧请求没有新字段时按天处理。响应从关系字段投影返回，非存储JSON核心业务数据。
- 保存错误返回details.<索引>.validDuration，显示在对应设备行控件下。0表示不过期，其他数量按日历计算；生成任务时计算expires_date。
- V81新增关系字段并按原天数回填，不修改历史迁移和已有任务时间。

### 验收结果公共契约（2026-10-18）

- 规范值：accept / reject，前端公共选项及AcceptanceResultPicker，后端公共AcceptanceDecision枚举。
- 保养/tasks/{id}/accept及/batch-accept的decision、点巡检review的decision及reviewItems[].decision使用上述编码；旧中文和return仅作为兼容输入接受，新写入使用规范编码。
- 维修工作流action支持accept / reject，旧return保留兼容；事件新增decision编码，原result继续保留显示文案快照。旧简化操作入口同样支持reject。
- 历史读取由公共显示映射兼容，不执行数据清洗或重算。前端读模型显式包含历史别名，新选择控件只输出规范值。

### 保养任务详情项目统计（2026-10-18）

- GET /api/v1/maintenance/tasks/{id}增加statistics：total、completed、abnormal、completionRate、completedItemIds、abnormalItemIds、submitted、reliable。
- 使用已授权任务快照、执行结果和同次详情查询的既有事件计算，无额外业务数据查询，不新增表字段；列表接口不增加此项计算。
- 无项目时completionRate为null；历史缺少提交证据时reliable=false，completed、abnormal和completionRate为null，前端显示待确认。预填结果不作为已执行依据。
- 本轮已完成构建与静态核对，尚未执行隔离业务数据及浏览器布局验收。


### 2026-10-18 设备负责人人员选择

- `GET /api/v1/equipment/owner-candidates`：设备新增／编辑负责人选择弹窗；参数 `action`（新增／编辑）、`q`、`page`、`size`，返回 `data`（id、name、account）和 `page`。
- Controller → EquipmentQueryService → EquipmentQueryRepository，数据库执行搜索、计数及分页；校验对应设备操作权限，人员范围沿用租户内启用用户。
- EquipmentEditor 使用公共 PagedChoiceField／DialogChoiceInput，多选、回填、清空；保存仍走原设备保存接口及负责人ID校验。类型检查、后端打包通过；尚未进行浏览器交互或业务数据验收。

### 2026-10-18 设备台账导入

全部接口要求设备导入及新增权限，批次仅当前租户、当前用户本人可访问。本次设备结果还要求查看权限，并在数据库执行当前设备数据范围过滤。

| 方法 | 路径（/api/v1前缀） | 作用 |
|---|---|---|
| GET | /equipment/imports/template | 下载含稳定字段ID及发布版本的xlsx模板 |
| POST | /equipment/imports | multipart file上传，返回VALIDATING批次，后台校验 |
| GET | /equipment/imports/{id} | 查询状态、数量、版本、提示及有效期 |
| GET | /equipment/imports/{id}/preview | errorsOnly、page、size；数据库分页原始行及错误 |
| GET | /equipment/imports/{id}/report | 下载含原值、错误标色和建议的可重新上传xlsx |
| POST | /equipment/imports/{id}/confirm | rowVersion、idempotencyKey；后台重新校验并整批事务写入 |
| GET | /equipment/imports/{id}/results | 当前设备查看范围内分页查询本批新增设备 |

状态为 VALIDATING / INVALID / READY / IMPORTING / SUCCEEDED / FAILED / EXPIRED。模板、报告先鉴权生成再设置Excel响应类型；校验不模拟保存。相同确认键不同版本或不同批次拒绝，同内容重试返回原批次。

使用方法、迁移、边界及验收脚本见[导入说明](../features/设备台账导入-使用与验收-20261018.md)。


## 多语言（2026-10-19）

| 接口 | 调用方/权限 | 版本 |
|---|---|---|
| GET /api/v1/i18n/languages | 登录页/个人设置；公开，仅启用语言 | 返回 rowVersion |
| GET /api/v1/i18n/resources/{locale} | 公共国际化；公开，无业务数据 | ETag / If-None-Match |
| GET /api/v1/auth/settings | 当前账号，增加 locale/effectiveLocale | 主题不覆盖语言 |
| PATCH /api/v1/auth/settings/locale | 仅当前账号 | 独立更新 locale |
| GET /api/v1/system/languages | language.查看 | 返回 rowVersion |
| GET /api/v1/system/languages/template | language.查看，template/export JSON | formatVersion=1 |
| POST /api/v1/system/languages/imports/validate | language.查看；只校验无发布 | 无写入 |
| PUT /api/v1/system/languages/{locale} | language.查看；确认覆盖 | rowVersion，不匹配409 |
| PATCH /api/v1/system/languages/{locale}/state | language.查看；启停 | rowVersion，不匹配409 |
| POST /api/v1/system/languages/{locale}/restore | language.查看；恢复内置 | rowVersion，不匹配409 |

错误仍为 code/message/fieldErrors；自定义字段及业务内容不自动翻译。语言包 JSON 是交换协议，核心业务字段仍使用明确建模。
