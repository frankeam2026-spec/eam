# 工程改造接口清单

本清单由 `scripts/engineering/api-inventory.py` 从 Controller 入口生成；权限和事务规则以下方人工维护的契约为准，不通过路径猜测授权。

| 模块 | 调用方 | 权限与数据范围 | 版本及错误约定 |
|---|---|---|---|
| 设备 | 台账、编辑、档案、选择器 | equipment 对应操作；公司/部门/负责人范围，子设备/BOM保存重新校验 | 修改传 rowVersion；扩展配置传 customSchemaVersion |
| 维修 | 工单列表、报修、处理、工作台 | repair 对应操作；退回待提交单仅报修人按报修权限编辑与重提；审批按分配任务办理 | 修改/状态动作传 rowVersion；未要求审批的待派工可编辑，审批中及通过后不可改内容 |
| 保养 | 类型、标准、计划、任务 | maintenanceType/Standard/Plan/Task；任务参与者查看例外与执行/验收判定分开 | rowVersion；计划与任务独立扩展配置 |
| 点巡检 | 标准、日常/周期计划与任务 | inspection 各子菜单；任务参与者范围保留 | rowVersion；暂存/提交校验不同 |
| 库存 | 物料、仓库、入出库、领料与纠正 | 各库存子菜单与仓库范围；金额独立授权，不能由查看仓库代替业务授权 | rowVersion；出库确认另带幂等键和分配指纹 |
| 用户 | 用户列表、编辑、批量操作 | user 对应操作与组织范围；不扩大批量目标 | 保留现有更新版本契约，缺口逐入口登记 |

列表新查询契约：`data`＋`page:{number,size,total}`；错误保留 `code,message,fieldErrors`。400字段错误定位控件，401统一登录失效，403拒绝，409保留草稿；导出叠加导出授权。二进制导出通过公共文件请求读取错误。

设备编辑保存边界（2026-10-21）：`PUT /equipment/{id}/details` 只更新核心资料、图片、文件与扩展字段；`PUT /equipment/{id}/children` 和 `/bom` 分别只替换对应关系，请求体为 `{rows:[...]}`。三者均要求父设备编辑权限与 `rowVersion`，关系接口沿用库存互斥→公司锁顺序、ID/租户/权限校验及既有子设备与BOM规则；原整体更新接口保留兼容。

## 当前 HTTP 入口

2026-10-30补充：`GET/PUT /api/v1/workflow/templates/repair-approval-setting?companyId=...`查询／维护公司报修审批开关；查看／发布权限及公司范围分别校验，PUT传`required,rowVersion`。`POST /api/v1/repairs/{id}/approval-submit`仅本人重提待提交报修，请求体传`rowVersion`。审批办理仍复用`/api/v1/workflow/instances/{id}/act`和`/withdraw`，只允许被分配办理人／申请人。`GET /api/v1/repairs/{id}/workflow`增加`approvalRounds,canResubmit`，报修查看授权先于审批历史返回。

| 模块 | 方法 | 路径 | Controller | 处理方法 |
|---|---|---|---|---|
| equipment | POST | /api/v1/equipment | EquipmentController | create |
| equipment | PUT | /api/v1/equipment/{id} | EquipmentController | update |
| equipment | PUT | /api/v1/equipment/{id}/details | EquipmentController | updateDetails |
| equipment | PUT | /api/v1/equipment/{id}/children | EquipmentController | updateChildren |
| equipment | PUT | /api/v1/equipment/{id}/bom | EquipmentController | updateBom |
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


## 2026-10-23 日常点检人员与排班

- GET `/api/v1/inspection/daily-device-options`：执行权限范围内的设备分页选择，参数q/page/size；权限、过滤、计数与分页均在SQL执行。
- GET `/api/v1/inspection/daily-device-tasks`：按equipmentId或精确设备编号code查询当前任务，参数page/size；不创建任务。设备点检入口与设备概览共用。
- POST `/api/v1/inspection/daily-plans/assignment-impact`：提交现有计划草稿、关联ID和rowVersion，校验编辑权限与版本后返回将受影响的未开始任务数量；不修改任务。
- 既有日常计划保存／详情／预览增加安排级regularRoster/dateRoster；personMode新增open，followUpUserId在open模式下可选。常规与日期人员分别落关系表，不保存为payload。
- 既有任务start/submit继续使用rowVersion、行锁、候选及开放范围检查，开始确定实际点检人；不增加独立认领步骤。
- 完整字段、迁移、兼容性和验证边界见[日常点检人员与排班](../features/日常点检人员与排班-20261023.md)。

## 2026-10-23 日常点检班次与删除补充

- 日常计划保存及预览仅接受shift（历史slots等价规范化），班次数可为0；weeklyRestDays与monthlyRestDays可同时设置。日历数组为关系表查询投影，不是JSON核心存储。
- 既有`POST /api/v1/inspection/tasks/{id}/delete`保留权限、锁及rowVersion，日常任务增加尚未执行的状态限制，description必填。任务返回canDelete供列表和详情使用。
- `DELETE /api/v1/inspection/daily-plans/{id}`只由deleted=0的任务阻止删除；已删除任务历史继续保留，免检保护不变。
- 人工事件选项入口返回空集合，原创建事件入口明确拒绝；前端入口已移除。详细边界和证据见[班次与任务删除记录](../features/日常点检班次与任务删除-20261023.md)。


## 统一提醒策略与通知渠道（2026-10-23）

- `GET /api/v1/notifications/policies`：提醒策略查看，按业务类型、关键字、分页查询；`GET /policies/{id}`读取规则和固定人员；`POST /policies`编辑权限，校验关联、租户、版本、场景、人员有效性及启用规则。
- `GET /notifications/policies/choices`：对应计划/维修查看权限或策略查看权限，只返回本业务启用策略；支持SQL搜索分页。`GET /notifications/users`：策略查看权限，只选择当前租户有效用户。
- `GET /notifications/defaults`、`PUT /notifications/defaults/{kind}`：默认绑定查看/编辑，独立版本防覆盖；启用策略须与业务类型一致。
- `GET/PUT /notifications/channels`：渠道查看/编辑，密码不回显，空密码保留原加密凭据；`POST /notifications/channels/test-email`使用已保存配置，仅对明确的测试邮箱发送。
- `GET /notifications/deliveries`：发送记录查看，默认近7个自然日，SQL按日期（from/to含当天）、业务类型（businessKind）、接收用户（recipientId）、渠道、状态、关键词筛选、计数与分页。默认隐藏DISABLED/NOT_INTEGRATED，showInactive=true或明确选择对应状态时显示；`POST /notifications/deliveries/{id}/retry`：重试权限及当前尝试次数校验，只重试可恢复失败，不重发UNKNOWN或未接入App。
- `GET /notifications/deliveries/recipients`：发送记录查看权限，当前租户历史接收人分页选择，含已停用的历史接收人。
- `GET/PUT /notifications/deliveries/retention`：查看复用发送记录查看权限，保存需通知渠道编辑权限及rowVersion，成功和异常保留天数均为1至3650天。默认90/180天；定时清理保留站内消息与渠道防重凭据，不清理待发送、发送中和自动重试记录。
- 三类计划详情/保存增加`reminderMode`（DEFAULT/POLICY/OFF）、`reminderPolicyId`；复用原计划权限、关联校验及行版本，同事务保存。正常维修实际派工/接单及保养提交时间落明确列；特殊流程不做提醒。
- 公共站内提醒沿用`work-inbox`查询与未读汇总，新增消息ID前缀`n:`；标记已读始终按当前租户和接收人限制，查看业务详情仍走原接口权限。

- 用户邮箱沿用`/api/v1/system/users`创建、编辑接口，返回`email`和`emailVersion`；编辑包含邮箱时必须携带对应版本，使用CAS并在冲突时回滚整个用户保存。未携带邮箱的旧请求兼容，其他用户字段并发保护不在本轮扩展范围。

## 小卓设备台账只读查询（2026-10-29）

- `GET /api/v1/knowledge/assistant/equipment-records`：同时要求knowledge/查看、equipment/查看。filters为最多6项field/operator/value条件，仅允许name/code/model/brand/state/useState/category，操作符eq/contains（选项类仅eq）；page正数，size仅20/50/100。equipmentId可选，调用原设备详情权限核验并与筛选取交集。复用EquipmentQueryService执行租户、数据权限、SQL筛选/计数/排序/分页；返回编号、名称、型号、状态及ID，不返回成本、附件或自定义字段。
- 助手提问先经AiQuestionRouter输出受限意图及条件，再分别进入台账、维修、指标、知识库或澄清路径；只有明确KNOWLEDGE意图进入向量检索。模型不生成SQL，台账结果不送模型二次生成。会话历史保留文字摘要，列表卡片不落库，加载历史需重新查询。


## 小卓统一业务查询（2026-10-29）

- `POST /api/v1/knowledge/assistant/business-query`：body为`plan/equipmentId/page/size`。需要knowledge/查看及所查询业务的查看权限；size仅20/50/100。计划只接受目录字段、类型化条件、LIST/COUNT/GROUP/TREND、有界分组、聚合和最多100条排行；未知字段/操作拒绝。API请求和模型计划通过同一校验。
- 适配设备台账、维修工单、保养任务/计划、日常点检任务/计划、周期巡检任务/计划、库存余额。业务Service复用原列表数据权限，Repository执行参数化SQL的筛选、分组、排序、计数和分页。库存不返回成本，数量汇总强制保留单位；既有指标查询继续执行原指标与金额权限。
- 返回查询时间、时间口径、筛选条件、总记录数、分组总数、列定义和稳定关联ID。分组记录占比的分母是过滤后记录数；排行总数限制不改变分母。明细穿透叠加分组名称与ID，重新鉴权和查询。
- 会话提问/stream增加可选`queryContext`，仅携带上轮查询结构，后台重新校验；不携带结果行、不授予权限、不作为SQL。最多6组、每组条件深度4层/24节点。当前会话内追问可沿用；历史业务结果不持久化数值，需重新查询。
- 本人待办复用`GET /api/v1/work-inbox`的类型/状态/搜索与分页。无审批、接单、出库等写操作。只有明确知识问题才进入向量检索，业务结果不发送模型二次生成。
- 无新增数据库表或迁移；保养/点巡检快照及原业务存储保持原样。接口设计与实测边界见[统一业务查询记录](../features/小卓统一业务查询-20261029.md)。


## 领料草稿类型契约（2026-10-30）

原路径与成功200状态不变；调用方为`src/inventory.ts`及`InventoryRequisitions.vue`。

| 入口 | 请求/响应 | 权限与版本 |
|---|---|---|
| POST `/api/v1/inventory/requisitions` | RequisitionDraftForm → DataResponse<RequisitionDetail> | 任务权限及资格、仓库新增范围或本人执行任务例外；公司一致 |
| PUT `/api/v1/inventory/requisitions/{id}` | 同上，完整替换可编辑字段和明细 | 本人草稿、rowVersion必填非负整数、同上关联复核；冲突409 |
| GET `/api/v1/inventory/requisitions/{id}` | DataResponse<RequisitionDetail> | 原申请人/流程参与者/仓库范围；金额独立授权 |

可编辑字段为taskKind（维修/保养/点巡检）、taskId、warehouseId、remark、lines[{materialId,quantity}]；仅修改必传rowVersion。ID接受十进制字符串或精确整数，拒绝小数、布尔及溢出；数量接受十进制字符串/JSON数值，以BigDecimal解析，沿用正数、最多14位整数及6位小数；明细1～500行、物料不能重复，备注trim后最多1000字符。

客户端原有只读主单字段及明细展示字段按DTO白名单忽略，不采信其公司、申请人、编号或状态。未登记字段（包括拼写错误）返回400；格式错误保留INVALID_ARGUMENT，业务字段校验使用既有VALIDATION_ERROR及fieldErrors，如`lines[0].quantity`。这项错误定位改进不同于仅替换Java类型，已单独回归。

响应字段及null/空字符串兼容范围、旧内部调用适配和验证证据见[领料草稿类型化重构](../plans/领料草稿类型化重构-20261030.md)。其他库存路由未在本批改为类型化，不据此认定其已满足新规范。


## 设备台账核心写入契约（2026-10-30）

- 调用方保持`src/api.ts`的saveEquipment、saveEquipmentRelations及deleteEquipment；POST设备、PUT设备及details分区、children/bom、DELETE和batch原路径不变。
- 新增/编辑/详情返回DataResponse<EquipmentView>；删除返回DataResponse<EquipmentMutationResult.Deleted>，批量返回DataResponse<EquipmentMutationResult.Count>，导航树使用DataResponse<List<EquipmentNavigationNode>>。JSON结构与成功200不变。
- 核心关联ID及负责人ID支持十进制字符串和整数，拒绝小数、布尔、溢出及非正值。可选ID仍允许null；实际前端已将空字符串转换为null。ownerIds的空元素定位到ownerIds[0]等字段。
- 新增与编辑统一执行已有长度、精度和业务字段校验。非法规格型号等在写SQL前返回400/VALIDATION_ERROR及fieldErrors；版本查询参数仍必填，冲突409，保留原权限及公司锁顺序。
- details只保存基础信息；children/bom仍为独立替换入口，保持原关联校验，不因封装调整而改变协议。深层旧结构及实际测试范围见[设备台账核心写入优化](../plans/设备台账核心写入优化-20261030.md)。

## 设备AI查询内部契约（2026-10-30）

- POST `/api/v1/knowledge/assistant/business-query`路径及原`plan/equipmentId/page/size`请求不变。设备分支保持count/list/group/trend结果、columns、identities、跳转及时间元数据兼容；其他业务分支沿用原路径。
- AI适配层转换为EquipmentAssistantQuery；设备Service校验查看权限、查询能力及选中设备的对象权限，Repository执行并返回EquipmentAssistantResult。跨设备模块边界不传SQL、Wrapper或AssistantReadSource。
- 设备字段目录与台账字段表达式、授权构造器同源；过滤条件只能缩小授权范围。设备Service再次限制page为1～1000000、size为20/50/100、topN为0～100、分组最多2维，并校验日期、操作符、值类型及条件复杂度；非法计划与同名对象歧义使用本地化错误。
- 旧动态响应仅在AI兼容边界组装。具体兼容范围、隔离MySQL证据和未验证事项见[设备AI查询边界重构](../plans/设备AI查询边界重构-20261030.md)。

## IDEA整改涉及的校验兼容（2026-10-30）

- PUT `/api/v1/notifications/channels`的smtpSecurity必须非空，仍只接受NONE/STARTTLS/SSL。缺失字段由原统一错误入口返回400/VALIDATION_ERROR及fieldErrors.smtpSecurity；原合法请求及成功响应不变。Service独立拒绝null，内部调用不能绕过。
- 首页布局metrics只接受原指标白名单中的字符串且不能重复，含null或错误类型时返回原输入错误，避免500；原顺序及空列表语义不变。
- 方法级参数校验内部换用Spring现行API，原字段错误、参数别名及服务端返回值错误协议不变。模板列表的初始化事务和保养类型导出的只读事务变化见[IDEA检查整改](../plans/IDEA检查整改-20261030.md)。

## BOM物料候选查询契约（2026-10-30）

GET `/api/v1/equipment/bom-materials`的q/page/size参数、默认值及成功200不变。响应保持`data[{id,code,name,model,unit,category,enabled}]`、`page{total}`，不增加页码字段；id为精确十进制字符串，字符串空值及字段名不变。

查看条件保持当前租户、enabled=1、设备新增或编辑权限；四列LIKE通配符语义及code/id排序不变。原page收敛至至少1、size收敛至1～100的兼容行为保留；偏移以long计算，int范围内的大页码返回空页，越出int或非数字仍由原绑定错误返回400。只查询物料，不变更库存或关联写入。

Controller/Service使用明确查询及响应类型，设备关联Repository复用物料Repository的MyBatis-Plus查询，Service将内部物料实体转换为协议DTO。回归证据与范围见[设备台账专项治理第一批](../plans/设备台账专项治理-实施记录-20261030.md)。