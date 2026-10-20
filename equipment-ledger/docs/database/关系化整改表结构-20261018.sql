-- 设备管理系统：数据库关系化整改结构参考
-- 2026-10-18；隔离迁移副本实际结构，Flyway V79。
-- 仅供阅读和核对；升级必须通过正式 Flyway 迁移，不直接执行本文件。
-- 不包含业务记录、账号凭据或迁移归档。

-- eam_anomaly_attachment
CREATE TABLE `eam_anomaly_attachment` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `anomaly_id` bigint NOT NULL COMMENT '设备异常ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `file_id` varchar(64) DEFAULT NULL COMMENT '文件ID；无附件的历史资料可为空',
  `name_snapshot` varchar(255) DEFAULT NULL COMMENT '名称快照',
  `content_type` varchar(150) DEFAULT NULL COMMENT '文件内容类型',
  `size_bytes` bigint DEFAULT NULL COMMENT '文件大小，字节',
  `url_snapshot` varchar(1000) DEFAULT NULL COMMENT '文件访问地址快照',
  PRIMARY KEY (`tenant_id`,`anomaly_id`,`sort_no`),
  KEY `tenant_id` (`tenant_id`,`file_id`),
  KEY `anomaly_id` (`anomaly_id`),
  CONSTRAINT `eam_anomaly_attachment_ibfk_1` FOREIGN KEY (`anomaly_id`) REFERENCES `eam_equipment_anomaly` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='异常附件';

-- eam_equipment
CREATE TABLE `eam_equipment` (
  `id` bigint NOT NULL,
  `equipment_code` varchar(32) NOT NULL,
  `asset_no` varchar(80) DEFAULT NULL,
  `tag_code` varchar(100) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `category` varchar(100) NOT NULL,
  `model` varchar(120) DEFAULT NULL,
  `serial_no` varchar(120) DEFAULT NULL,
  `company` varchar(120) NOT NULL,
  `department` varchar(120) NOT NULL,
  `owner_name` varchar(6000) DEFAULT NULL,
  `brand` varchar(100) DEFAULT NULL,
  `equipment_status` varchar(24) NOT NULL DEFAULT '正常',
  `use_status` varchar(40) DEFAULT NULL,
  `source` varchar(40) DEFAULT NULL,
  `unit_name` varchar(40) DEFAULT NULL,
  `supplier` varchar(160) DEFAULT NULL,
  `purchase_date` date DEFAULT NULL,
  `acquired_at` date DEFAULT NULL,
  `acquired_amount` decimal(18,2) DEFAULT NULL,
  `warranty_until` date DEFAULT NULL,
  `in_service_date` date DEFAULT NULL,
  `expected_retirement_date` date DEFAULT NULL,
  `equipment_level` varchar(30) DEFAULT NULL,
  `function_location` varchar(1200) DEFAULT NULL,
  `special_equipment` varchar(8) NOT NULL DEFAULT '否',
  `metrology_equipment` varchar(8) NOT NULL DEFAULT '否',
  `depreciation_enabled` varchar(8) NOT NULL DEFAULT '否',
  `technical_params` varchar(2000) DEFAULT NULL,
  `image_name` varchar(255) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `row_version` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint NOT NULL DEFAULT '0',
  `tenant_id` bigint NOT NULL DEFAULT '10001',
  `company_org_id` bigint DEFAULT NULL,
  `department_org_id` bigint DEFAULT NULL,
  `owner_user_id` bigint DEFAULT NULL,
  `calibration_date` date DEFAULT NULL,
  `calibration_valid_until` date DEFAULT NULL,
  `inspector_name` varchar(100) DEFAULT NULL,
  `image_file_id` char(36) DEFAULT NULL,
  `function_location_id` bigint DEFAULT NULL,
  `category_id` bigint DEFAULT NULL,
  `unit_id` bigint DEFAULT NULL,
  `use_status_id` bigint DEFAULT NULL,
  `source_id` bigint DEFAULT NULL,
  `department_path` varchar(1200) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_equipment_tenant_code` (`tenant_id`,`equipment_code`,`deleted`),
  KEY `idx_equipment_name` (`name`),
  KEY `idx_equipment_department_state` (`department`,`equipment_status`,`deleted`),
  KEY `idx_equipment_category_use` (`category`,`use_status`,`deleted`),
  KEY `idx_equipment_serial` (`serial_no`),
  KEY `idx_equipment_asset_no` (`asset_no`),
  KEY `idx_equipment_tag_code` (`tag_code`),
  KEY `idx_equipment_created` (`created_at`),
  KEY `idx_equipment_access` (`tenant_id`,`company_org_id`,`department_org_id`,`owner_user_id`,`deleted`),
  KEY `ix_equipment_location` (`tenant_id`,`company_org_id`,`function_location_id`),
  KEY `idx_equipment_function_location` (`function_location`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备台账';

-- eam_equipment_document
CREATE TABLE `eam_equipment_document` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `equipment_id` bigint NOT NULL COMMENT '设备ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `file_id` varchar(64) DEFAULT NULL COMMENT '文件ID；无附件的历史资料可为空',
  `name_snapshot` varchar(255) DEFAULT NULL COMMENT '名称快照',
  `content_type` varchar(150) DEFAULT NULL COMMENT '文件内容类型',
  `size_bytes` bigint DEFAULT NULL COMMENT '文件大小，字节',
  `url_snapshot` varchar(1000) DEFAULT NULL COMMENT '文件访问地址快照',
  `original_name` varchar(255) DEFAULT NULL COMMENT '文件原始名称快照',
  `document_code` varchar(64) DEFAULT NULL COMMENT '设备资料编号',
  `document_type` varchar(100) DEFAULT NULL COMMENT '设备资料类型',
  `security_level` varchar(24) DEFAULT NULL COMMENT '资料密级',
  `quantity` decimal(18,2) DEFAULT NULL COMMENT '数量，单位沿用关联物料或资料',
  `remark` text COMMENT '备注',
  PRIMARY KEY (`tenant_id`,`equipment_id`,`sort_no`),
  KEY `tenant_id` (`tenant_id`,`file_id`),
  KEY `equipment_id` (`equipment_id`),
  CONSTRAINT `eam_equipment_document_ibfk_1` FOREIGN KEY (`equipment_id`) REFERENCES `eam_equipment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='适用设备资料';

-- eam_equipment_image
CREATE TABLE `eam_equipment_image` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `equipment_id` bigint NOT NULL COMMENT '设备ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `file_id` varchar(64) DEFAULT NULL COMMENT '文件ID；无附件的历史资料可为空',
  `name_snapshot` varchar(255) DEFAULT NULL COMMENT '名称快照',
  PRIMARY KEY (`tenant_id`,`equipment_id`,`sort_no`),
  KEY `tenant_id` (`tenant_id`,`file_id`),
  KEY `equipment_id` (`equipment_id`),
  CONSTRAINT `eam_equipment_image_ibfk_1` FOREIGN KEY (`equipment_id`) REFERENCES `eam_equipment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='适用设备图片';

-- eam_home_template_role
CREATE TABLE `eam_home_template_role` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `template_id` bigint NOT NULL COMMENT '首页模板ID',
  `role_id` bigint NOT NULL COMMENT '系统角色ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`template_id`,`role_id`),
  KEY `tenant_id` (`tenant_id`,`role_id`),
  KEY `template_id` (`template_id`),
  CONSTRAINT `eam_home_template_role_ibfk_1` FOREIGN KEY (`template_id`) REFERENCES `eam_home_template` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='首页模板角色';

-- eam_home_template_version_role
CREATE TABLE `eam_home_template_version_role` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `template_id` bigint NOT NULL COMMENT '首页模板ID',
  `version` int NOT NULL COMMENT '不可变发布版本号',
  `role_id` bigint NOT NULL COMMENT '系统角色ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`template_id`,`version`,`role_id`),
  KEY `tenant_id` (`tenant_id`,`role_id`),
  CONSTRAINT `eam_home_template_version_role_ibfk_1` FOREIGN KEY (`tenant_id`, `template_id`, `version`) REFERENCES `eam_home_template_version` (`tenant_id`, `template_id`, `version`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='首页模板版本角色';

-- eam_inspection_category_label
CREATE TABLE `eam_inspection_category_label` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `label_kind` varchar(16) NOT NULL COMMENT '名称来源：display展示快照、legacy待确认历史名称',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `name_snapshot` varchar(255) NOT NULL COMMENT '名称快照',
  PRIMARY KEY (`tenant_id`,`standard_id`,`label_kind`,`sort_no`),
  KEY `standard_id` (`standard_id`),
  CONSTRAINT `eam_inspection_category_label_ibfk_1` FOREIGN KEY (`standard_id`) REFERENCES `eam_inspection_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检适用类别名称快照';

-- eam_inspection_daily_holiday
CREATE TABLE `eam_inspection_daily_holiday` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `holiday_date` date NOT NULL COMMENT '休息日期',
  PRIMARY KEY (`tenant_id`,`plan_id`,`sort_no`),
  KEY `plan_id` (`plan_id`),
  CONSTRAINT `eam_inspection_daily_holiday_ibfk_1` FOREIGN KEY (`plan_id`) REFERENCES `eam_inspection_daily_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检日常点检休息日期';

-- eam_inspection_daily_plan
CREATE TABLE `eam_inspection_daily_plan` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `name` varchar(150) NOT NULL COMMENT '名称',
  `department_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `creator_id` bigint NOT NULL COMMENT '创建人用户ID',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `enabled` tinyint NOT NULL COMMENT '是否启用',
  `remark` text NOT NULL COMMENT '备注',
  `type_id` bigint DEFAULT NULL COMMENT '保养或巡检类型ID',
  `review_mode` varchar(24) NOT NULL COMMENT '验收或审核方式',
  `start_date` date DEFAULT NULL COMMENT '计划开始日期',
  `end_date` date DEFAULT NULL COMMENT '计划结束日期',
  `valid_duration` decimal(18,2) NOT NULL COMMENT '任务有效时长数值，单位见valid_unit',
  `advance_duration` decimal(18,2) NOT NULL COMMENT '提前生成数值，单位见advance_unit',
  `remind_duration` decimal(18,2) NOT NULL COMMENT '开始前提醒数值，单位见remind_unit',
  `expire_remind_duration` decimal(18,2) NOT NULL COMMENT '过期前提醒数值，单位见expire_remind_unit',
  `valid_unit` varchar(8) NOT NULL COMMENT '任务有效时长单位',
  `advance_unit` varchar(8) NOT NULL COMMENT '提前生成单位',
  `remind_unit` varchar(8) NOT NULL COMMENT '开始前提醒单位',
  `expire_remind_unit` varchar(8) NOT NULL COMMENT '过期前提醒单位',
  `scan_mode` varchar(40) NOT NULL COMMENT '设备扫码校验方式',
  `only_abnormal` tinyint NOT NULL COMMENT '是否仅填写异常项目',
  `random_photo_count` int NOT NULL COMMENT '随机拍照数量',
  `allow_late` tinyint NOT NULL COMMENT '是否允许逾期提交',
  `person_mode` varchar(40) NOT NULL COMMENT '日常点检人员模式',
  `skip_unstaffed` tinyint NOT NULL COMMENT '无人排班时是否跳过',
  `rest_cycle` varchar(8) NOT NULL COMMENT '休息日周期：按周或按月',
  `use_shift_end` tinyint NOT NULL COMMENT '是否使用班次结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `tenant_id` (`tenant_id`,`code`),
  KEY `tenant_id_2` (`tenant_id`,`department_id`,`created_at`,`id`),
  KEY `type_id` (`type_id`),
  CONSTRAINT `eam_inspection_daily_plan_ibfk_1` FOREIGN KEY (`type_id`) REFERENCES `eam_inspection_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检日常点检计划';

-- eam_inspection_daily_plan_device
CREATE TABLE `eam_inspection_daily_plan_device` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `device_key` varchar(128) NOT NULL COMMENT '计划路线设备行稳定ID',
  `equipment_id` bigint NOT NULL COMMENT '设备ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`plan_id`,`device_key`),
  UNIQUE KEY `tenant_id` (`tenant_id`,`plan_id`,`equipment_id`),
  KEY `tenant_id_2` (`tenant_id`,`equipment_id`),
  KEY `plan_id` (`plan_id`),
  CONSTRAINT `eam_inspection_daily_plan_device_ibfk_1` FOREIGN KEY (`plan_id`) REFERENCES `eam_inspection_daily_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检日常点检计划路线设备';

-- eam_inspection_daily_plan_device_person
CREATE TABLE `eam_inspection_daily_plan_device_person` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `device_key` varchar(128) NOT NULL COMMENT '计划路线设备行稳定ID',
  `role_code` varchar(16) NOT NULL COMMENT '人员角色：owner负责人、reviewer审核人、collaborator协同人员',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`plan_id`,`device_key`,`role_code`,`user_id`),
  CONSTRAINT `eam_inspection_daily_plan_device_person_ibfk_1` FOREIGN KEY (`tenant_id`, `plan_id`, `device_key`) REFERENCES `eam_inspection_daily_plan_device` (`tenant_id`, `plan_id`, `device_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检日常点检计划路线设备人员关联';

-- eam_inspection_daily_plan_device_project
CREATE TABLE `eam_inspection_daily_plan_device_project` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `device_key` varchar(128) NOT NULL COMMENT '计划路线设备行稳定ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `item_key` varchar(128) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`plan_id`,`device_key`,`standard_id`,`item_key`),
  CONSTRAINT `eam_inspection_daily_plan_device_project_ibfk_1` FOREIGN KEY (`tenant_id`, `plan_id`, `device_key`) REFERENCES `eam_inspection_daily_plan_device` (`tenant_id`, `plan_id`, `device_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检日常点检计划路线设备选定项目';

-- eam_inspection_daily_plan_person
CREATE TABLE `eam_inspection_daily_plan_person` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `parent_id` bigint NOT NULL COMMENT '当前表所属主记录ID',
  `role_code` varchar(16) NOT NULL COMMENT '人员角色：owner负责人、reviewer审核人、collaborator协同人员',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`parent_id`,`role_code`,`user_id`),
  KEY `tenant_id` (`tenant_id`,`user_id`),
  KEY `parent_id` (`parent_id`),
  CONSTRAINT `eam_inspection_daily_plan_person_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `eam_inspection_daily_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检日常点检计划人员关联';

-- eam_inspection_daily_rest_day
CREATE TABLE `eam_inspection_daily_rest_day` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `day_no` int NOT NULL COMMENT '周内或月内休息日序号',
  PRIMARY KEY (`tenant_id`,`plan_id`,`sort_no`),
  KEY `plan_id` (`plan_id`),
  CONSTRAINT `eam_inspection_daily_rest_day_ibfk_1` FOREIGN KEY (`plan_id`) REFERENCES `eam_inspection_daily_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检日常点检休息日';

-- eam_inspection_daily_roster
CREATE TABLE `eam_inspection_daily_roster` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `roster_key` varchar(128) NOT NULL COMMENT '排班行稳定ID',
  `roster_date` date NOT NULL COMMENT '排班日期',
  `shift_key` varchar(128) NOT NULL COMMENT '班次稳定ID',
  PRIMARY KEY (`tenant_id`,`plan_id`,`sort_no`),
  KEY `plan_id` (`plan_id`),
  CONSTRAINT `eam_inspection_daily_roster_ibfk_1` FOREIGN KEY (`plan_id`) REFERENCES `eam_inspection_daily_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检日常点检排班';

-- eam_inspection_daily_roster_user
CREATE TABLE `eam_inspection_daily_roster_user` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `roster_sort` int NOT NULL COMMENT '所属排班行顺序号',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`plan_id`,`roster_sort`,`user_id`),
  CONSTRAINT `eam_inspection_daily_roster_user_ibfk_1` FOREIGN KEY (`tenant_id`, `plan_id`, `roster_sort`) REFERENCES `eam_inspection_daily_roster` (`tenant_id`, `plan_id`, `sort_no`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检日常点检排班人员';

-- eam_inspection_daily_shift
CREATE TABLE `eam_inspection_daily_shift` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `shift_key` varchar(128) NOT NULL COMMENT '班次稳定ID',
  `name` varchar(150) NOT NULL COMMENT '名称',
  `start_time` time NOT NULL COMMENT '班次开始时间',
  `end_time` time NOT NULL COMMENT '班次结束时间',
  PRIMARY KEY (`tenant_id`,`plan_id`,`sort_no`),
  KEY `plan_id` (`plan_id`),
  CONSTRAINT `eam_inspection_daily_shift_ibfk_1` FOREIGN KEY (`plan_id`) REFERENCES `eam_inspection_daily_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检日常点检班次';

-- eam_inspection_execution
CREATE TABLE `eam_inspection_execution` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `description` text COMMENT '说明',
  `started_at` datetime(6) DEFAULT NULL COMMENT '实际开始时间',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '有效提交时间',
  `skip_reason` text COMMENT '跳过设备原因',
  `review_decision` varchar(24) DEFAULT NULL COMMENT '审核或验收结论',
  `review_description` text COMMENT '审核或验收说明',
  `reviewer_name` varchar(150) DEFAULT NULL COMMENT '审核人名称快照',
  `reviewed_at` datetime(6) DEFAULT NULL COMMENT '审核或验收时间',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审核人ID',
  PRIMARY KEY (`tenant_id`,`task_id`),
  KEY `task_id` (`task_id`),
  CONSTRAINT `eam_inspection_execution_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `eam_inspection_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检执行记录';

-- eam_inspection_execution_equipment
CREATE TABLE `eam_inspection_execution_equipment` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `action_code` varchar(16) NOT NULL COMMENT '扫码或跳过设备的动作类别',
  `equipment_id` bigint NOT NULL COMMENT '设备ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`task_id`,`action_code`,`equipment_id`),
  CONSTRAINT `eam_inspection_execution_equipment_ibfk_1` FOREIGN KEY (`tenant_id`, `task_id`) REFERENCES `eam_inspection_execution` (`tenant_id`, `task_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检执行记录适用设备';

-- eam_inspection_execution_file
CREATE TABLE `eam_inspection_execution_file` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `stage_code` varchar(16) NOT NULL COMMENT '附件阶段：execution执行、acceptance验收、review审核',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `file_id` varchar(64) NOT NULL COMMENT '文件ID；无附件的历史资料可为空',
  `name_snapshot` varchar(255) NOT NULL COMMENT '名称快照',
  `content_type` varchar(150) NOT NULL COMMENT '文件内容类型',
  `size_bytes` bigint NOT NULL COMMENT '文件大小，字节',
  `url_snapshot` varchar(1000) NOT NULL COMMENT '文件访问地址快照',
  PRIMARY KEY (`tenant_id`,`task_id`,`stage_code`,`sort_no`),
  CONSTRAINT `eam_inspection_execution_file_ibfk_1` FOREIGN KEY (`tenant_id`, `task_id`) REFERENCES `eam_inspection_execution` (`tenant_id`, `task_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检执行记录附件';

-- eam_inspection_execution_image
CREATE TABLE `eam_inspection_execution_image` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `item_key` varchar(255) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `file_id` varchar(64) NOT NULL COMMENT '文件ID；无附件的历史资料可为空',
  `name_snapshot` varchar(255) NOT NULL COMMENT '名称快照',
  `content_type` varchar(150) NOT NULL COMMENT '文件内容类型',
  `size_bytes` bigint NOT NULL COMMENT '文件大小，字节',
  `url_snapshot` varchar(1000) NOT NULL COMMENT '文件访问地址快照',
  PRIMARY KEY (`tenant_id`,`task_id`,`item_key`,`sort_no`),
  CONSTRAINT `eam_inspection_execution_image_ibfk_1` FOREIGN KEY (`tenant_id`, `task_id`, `item_key`) REFERENCES `eam_inspection_execution_item` (`tenant_id`, `task_id`, `item_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检执行记录图片';

-- eam_inspection_execution_item
CREATE TABLE `eam_inspection_execution_item` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `item_key` varchar(255) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `result_value` text NOT NULL COMMENT '执行结果值',
  `abnormal` tinyint NOT NULL COMMENT '是否异常',
  `resolved` tinyint NOT NULL COMMENT '异常是否已处理',
  `measure` text NOT NULL COMMENT '处理措施',
  `remark` text NOT NULL COMMENT '备注',
  `implicit_normal` tinyint NOT NULL COMMENT '是否按仅填异常规则隐含正常',
  `skipped` tinyint NOT NULL COMMENT '本项目是否跳过',
  PRIMARY KEY (`tenant_id`,`task_id`,`item_key`),
  CONSTRAINT `eam_inspection_execution_item_ibfk_1` FOREIGN KEY (`tenant_id`, `task_id`) REFERENCES `eam_inspection_execution` (`tenant_id`, `task_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检执行记录项目';

-- eam_inspection_execution_review_item
CREATE TABLE `eam_inspection_execution_review_item` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `item_key` varchar(255) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `decision` varchar(24) NOT NULL COMMENT '逐项审核结论',
  `description` text NOT NULL COMMENT '说明',
  PRIMARY KEY (`tenant_id`,`task_id`,`item_key`),
  CONSTRAINT `eam_inspection_execution_review_item_ibfk_1` FOREIGN KEY (`tenant_id`, `task_id`) REFERENCES `eam_inspection_execution` (`tenant_id`, `task_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检执行记录审核项目';

-- eam_inspection_item_file
CREATE TABLE `eam_inspection_item_file` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `item_key` varchar(128) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `file_id` varchar(64) NOT NULL COMMENT '文件ID；无附件的历史资料可为空',
  `name_snapshot` varchar(255) NOT NULL COMMENT '名称快照',
  `content_type` varchar(150) NOT NULL COMMENT '文件内容类型',
  `size_bytes` bigint NOT NULL COMMENT '文件大小，字节',
  `url_snapshot` varchar(1000) NOT NULL COMMENT '文件访问地址快照',
  PRIMARY KEY (`tenant_id`,`standard_id`,`item_key`,`sort_no`),
  CONSTRAINT `eam_inspection_item_file_ibfk_1` FOREIGN KEY (`tenant_id`, `standard_id`, `item_key`) REFERENCES `eam_inspection_standard_item` (`tenant_id`, `standard_id`, `item_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检项目附件';

-- eam_inspection_item_option
CREATE TABLE `eam_inspection_item_option` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `item_key` varchar(128) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `option_value` varchar(500) NOT NULL COMMENT '选项值',
  `normal_value` tinyint NOT NULL COMMENT '是否正常选项',
  PRIMARY KEY (`tenant_id`,`standard_id`,`item_key`,`sort_no`),
  CONSTRAINT `eam_inspection_item_option_ibfk_1` FOREIGN KEY (`tenant_id`, `standard_id`, `item_key`) REFERENCES `eam_inspection_standard_item` (`tenant_id`, `standard_id`, `item_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检项目选项';

-- eam_inspection_periodic_plan
CREATE TABLE `eam_inspection_periodic_plan` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `name` varchar(150) NOT NULL COMMENT '名称',
  `department_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `creator_id` bigint NOT NULL COMMENT '创建人用户ID',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `enabled` tinyint NOT NULL COMMENT '是否启用',
  `remark` text NOT NULL COMMENT '备注',
  `type_id` bigint DEFAULT NULL COMMENT '保养或巡检类型ID',
  `review_mode` varchar(24) NOT NULL COMMENT '验收或审核方式',
  `start_date` date DEFAULT NULL COMMENT '计划开始日期',
  `end_date` date DEFAULT NULL COMMENT '计划结束日期',
  `valid_duration` decimal(18,2) NOT NULL COMMENT '任务有效时长数值，单位见valid_unit',
  `advance_duration` decimal(18,2) NOT NULL COMMENT '提前生成数值，单位见advance_unit',
  `remind_duration` decimal(18,2) NOT NULL COMMENT '开始前提醒数值，单位见remind_unit',
  `expire_remind_duration` decimal(18,2) NOT NULL COMMENT '过期前提醒数值，单位见expire_remind_unit',
  `valid_unit` varchar(8) NOT NULL COMMENT '任务有效时长单位',
  `advance_unit` varchar(8) NOT NULL COMMENT '提前生成单位',
  `remind_unit` varchar(8) NOT NULL COMMENT '开始前提醒单位',
  `expire_remind_unit` varchar(8) NOT NULL COMMENT '过期前提醒单位',
  `scan_mode` varchar(40) NOT NULL COMMENT '设备扫码校验方式',
  `only_abnormal` tinyint NOT NULL COMMENT '是否仅填写异常项目',
  `random_photo_count` int NOT NULL COMMENT '随机拍照数量',
  `allow_late` tinyint NOT NULL COMMENT '是否允许逾期提交',
  `period_count` int NOT NULL COMMENT '周期数值',
  `period_unit` varchar(8) NOT NULL COMMENT '周期单位',
  `next_at` datetime DEFAULT NULL COMMENT '下次周期巡检时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `tenant_id` (`tenant_id`,`code`),
  KEY `tenant_id_2` (`tenant_id`,`department_id`,`created_at`,`id`),
  KEY `type_id` (`type_id`),
  CONSTRAINT `eam_inspection_periodic_plan_ibfk_1` FOREIGN KEY (`type_id`) REFERENCES `eam_inspection_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检周期巡检计划';

-- eam_inspection_periodic_plan_device
CREATE TABLE `eam_inspection_periodic_plan_device` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `device_key` varchar(128) NOT NULL COMMENT '计划路线设备行稳定ID',
  `equipment_id` bigint NOT NULL COMMENT '设备ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`plan_id`,`device_key`),
  UNIQUE KEY `tenant_id` (`tenant_id`,`plan_id`,`equipment_id`),
  KEY `tenant_id_2` (`tenant_id`,`equipment_id`),
  KEY `plan_id` (`plan_id`),
  CONSTRAINT `eam_inspection_periodic_plan_device_ibfk_1` FOREIGN KEY (`plan_id`) REFERENCES `eam_inspection_periodic_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检周期巡检计划路线设备';

-- eam_inspection_periodic_plan_device_person
CREATE TABLE `eam_inspection_periodic_plan_device_person` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `device_key` varchar(128) NOT NULL COMMENT '计划路线设备行稳定ID',
  `role_code` varchar(16) NOT NULL COMMENT '人员角色：owner负责人、reviewer审核人、collaborator协同人员',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`plan_id`,`device_key`,`role_code`,`user_id`),
  CONSTRAINT `eam_inspection_periodic_plan_device_person_ibfk_1` FOREIGN KEY (`tenant_id`, `plan_id`, `device_key`) REFERENCES `eam_inspection_periodic_plan_device` (`tenant_id`, `plan_id`, `device_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检周期巡检计划路线设备人员关联';

-- eam_inspection_periodic_plan_device_project
CREATE TABLE `eam_inspection_periodic_plan_device_project` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `device_key` varchar(128) NOT NULL COMMENT '计划路线设备行稳定ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `item_key` varchar(128) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`plan_id`,`device_key`,`standard_id`,`item_key`),
  CONSTRAINT `eam_inspection_periodic_plan_device_project_ibfk_1` FOREIGN KEY (`tenant_id`, `plan_id`, `device_key`) REFERENCES `eam_inspection_periodic_plan_device` (`tenant_id`, `plan_id`, `device_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检周期巡检计划路线设备选定项目';

-- eam_inspection_periodic_plan_person
CREATE TABLE `eam_inspection_periodic_plan_person` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `parent_id` bigint NOT NULL COMMENT '当前表所属主记录ID',
  `role_code` varchar(16) NOT NULL COMMENT '人员角色：owner负责人、reviewer审核人、collaborator协同人员',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`parent_id`,`role_code`,`user_id`),
  KEY `tenant_id` (`tenant_id`,`user_id`),
  KEY `parent_id` (`parent_id`),
  CONSTRAINT `eam_inspection_periodic_plan_person_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `eam_inspection_periodic_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检周期巡检计划人员关联';

-- eam_inspection_standard
CREATE TABLE `eam_inspection_standard` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `name` varchar(150) NOT NULL COMMENT '名称',
  `department_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `creator_id` bigint NOT NULL COMMENT '创建人用户ID',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `enabled` tinyint NOT NULL COMMENT '是否启用',
  `remark` text NOT NULL COMMENT '备注',
  `requirement` text NOT NULL COMMENT '检查要求',
  PRIMARY KEY (`id`),
  UNIQUE KEY `tenant_id` (`tenant_id`,`code`),
  KEY `tenant_id_2` (`tenant_id`,`department_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检标准';

-- eam_inspection_standard_category
CREATE TABLE `eam_inspection_standard_category` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `category_id` bigint NOT NULL COMMENT '设备类别ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`standard_id`,`category_id`),
  KEY `tenant_id` (`tenant_id`,`category_id`),
  KEY `standard_id` (`standard_id`),
  CONSTRAINT `eam_inspection_standard_category_ibfk_1` FOREIGN KEY (`standard_id`) REFERENCES `eam_inspection_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检标准适用类别';

-- eam_inspection_standard_equipment
CREATE TABLE `eam_inspection_standard_equipment` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `equipment_id` bigint NOT NULL COMMENT '设备ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`standard_id`,`equipment_id`),
  KEY `tenant_id` (`tenant_id`,`equipment_id`),
  KEY `standard_id` (`standard_id`),
  CONSTRAINT `eam_inspection_standard_equipment_ibfk_1` FOREIGN KEY (`standard_id`) REFERENCES `eam_inspection_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检标准适用设备';

-- eam_inspection_standard_item
CREATE TABLE `eam_inspection_standard_item` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `item_key` varchar(128) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `name` varchar(200) NOT NULL COMMENT '名称',
  `content` text NOT NULL COMMENT '项目内容或执行方法',
  `type_id` bigint DEFAULT NULL COMMENT '保养或巡检类型ID',
  `value_type` varchar(16) NOT NULL COMMENT '执行结果类型',
  `lower_bound` decimal(30,8) DEFAULT NULL COMMENT '正常数值范围下限',
  `upper_bound` decimal(30,8) DEFAULT NULL COMMENT '正常数值范围上限',
  `required` tinyint NOT NULL COMMENT '是否必填',
  `photo_required` tinyint NOT NULL COMMENT '是否必须拍照',
  `remark` text NOT NULL COMMENT '备注',
  PRIMARY KEY (`tenant_id`,`standard_id`,`item_key`),
  KEY `tenant_id` (`tenant_id`,`type_id`),
  KEY `standard_id` (`standard_id`),
  CONSTRAINT `eam_inspection_standard_item_ibfk_1` FOREIGN KEY (`standard_id`) REFERENCES `eam_inspection_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检标准项目';

-- eam_inspection_standard_person
CREATE TABLE `eam_inspection_standard_person` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `parent_id` bigint NOT NULL COMMENT '当前表所属主记录ID',
  `role_code` varchar(16) NOT NULL COMMENT '人员角色：owner负责人、reviewer审核人、collaborator协同人员',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`parent_id`,`role_code`,`user_id`),
  KEY `tenant_id` (`tenant_id`,`user_id`),
  KEY `parent_id` (`parent_id`),
  CONSTRAINT `eam_inspection_standard_person_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `eam_inspection_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检标准人员关联';

-- eam_inspection_task
CREATE TABLE `eam_inspection_task` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `kind` varchar(16) NOT NULL,
  `code` varchar(64) NOT NULL,
  `plan_id` bigint NOT NULL,
  `equipment_id` bigint NOT NULL DEFAULT '0',
  `shift_id` varchar(64) NOT NULL DEFAULT '',
  `company_id` bigint DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `owner_id` bigint DEFAULT NULL,
  `due_at` datetime NOT NULL,
  `expires_at` datetime NOT NULL,
  `status` varchar(24) NOT NULL,
  `snapshot` json NOT NULL,
  `row_version` int NOT NULL DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `type_id` bigint DEFAULT NULL,
  `person_mode` varchar(40) NOT NULL DEFAULT '' COMMENT '当前执行人员方式',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inspection_occurrence` (`tenant_id`,`plan_id`,`equipment_id`,`shift_id`,`due_at`),
  UNIQUE KEY `uk_inspection_code` (`tenant_id`,`code`),
  KEY `idx_inspection_task` (`tenant_id`,`kind`,`status`,`department_id`,`owner_id`,`due_at`),
  KEY `ix_inspection_planned_analysis` (`tenant_id`,`deleted`,`kind`,`due_at`,`company_id`,`department_id`),
  KEY `ix_inspection_task_type` (`tenant_id`,`type_id`),
  KEY `fk_inspection_task_type` (`type_id`),
  KEY `ix_inspection_task_page` (`tenant_id`,`kind`,`deleted`,`due_at`,`id`),
  CONSTRAINT `fk_inspection_task_type_v69` FOREIGN KEY (`type_id`) REFERENCES `eam_inspection_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- eam_inspection_task_equipment
CREATE TABLE `eam_inspection_task_equipment` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '点巡检任务ID',
  `equipment_id` bigint NOT NULL COMMENT '设备ID',
  `sort_no` int NOT NULL COMMENT '顺序',
  PRIMARY KEY (`tenant_id`,`task_id`,`equipment_id`),
  KEY `tenant_id` (`tenant_id`,`equipment_id`,`task_id`),
  KEY `task_id` (`task_id`),
  CONSTRAINT `eam_inspection_task_equipment_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `eam_inspection_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检任务关联设备，历史身份描述仍保留生成快照';

-- eam_inspection_task_person
CREATE TABLE `eam_inspection_task_person` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `role_code` varchar(16) NOT NULL COMMENT '人员角色：owner负责人、reviewer审核人、collaborator协同人员',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`task_id`,`role_code`,`user_id`),
  KEY `tenant_id` (`tenant_id`,`user_id`,`role_code`),
  KEY `task_id` (`task_id`),
  CONSTRAINT `eam_inspection_task_person_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `eam_inspection_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检任务人员关联';

-- eam_inspection_task_project
CREATE TABLE `eam_inspection_task_project` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `item_key` varchar(255) NOT NULL COMMENT '任务内稳定项目ID',
  `equipment_id` bigint DEFAULT NULL COMMENT '对应设备ID',
  `source_standard_id` bigint DEFAULT NULL COMMENT '原标准ID；无法唯一确定的历史记录为空',
  `source_item_key` varchar(128) DEFAULT NULL COMMENT '原标准项目ID',
  `type_id` bigint DEFAULT NULL COMMENT '保养或巡检类型ID',
  `sort_no` int NOT NULL COMMENT '顺序',
  PRIMARY KEY (`tenant_id`,`task_id`,`item_key`),
  KEY `tenant_id` (`tenant_id`,`equipment_id`,`task_id`),
  KEY `tenant_id_2` (`tenant_id`,`source_standard_id`,`source_item_key`),
  KEY `task_id` (`task_id`),
  CONSTRAINT `eam_inspection_task_project_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `eam_inspection_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务项目ID及来源关联；项目当时的定义为只读快照';

-- eam_inspection_type
CREATE TABLE `eam_inspection_type` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `name` varchar(150) NOT NULL COMMENT '名称',
  `department_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `creator_id` bigint NOT NULL COMMENT '创建人用户ID',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `enabled` tinyint NOT NULL COMMENT '是否启用',
  `remark` text NOT NULL COMMENT '备注',
  `period_count` int NOT NULL COMMENT '周期数值',
  `period_unit` varchar(8) NOT NULL COMMENT '周期单位',
  `sort_order` int NOT NULL COMMENT '类型显示顺序',
  PRIMARY KEY (`id`),
  UNIQUE KEY `tenant_id` (`tenant_id`,`code`),
  KEY `tenant_id_2` (`tenant_id`,`department_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点巡检类型';

-- eam_maintenance_device_person
CREATE TABLE `eam_maintenance_device_person` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `device_id` bigint NOT NULL COMMENT '保养计划设备调度行ID',
  `role_code` varchar(16) NOT NULL COMMENT '人员角色：owner负责人、reviewer审核人、collaborator协同人员',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`device_id`,`role_code`,`user_id`),
  KEY `device_id` (`device_id`),
  CONSTRAINT `eam_maintenance_device_person_ibfk_1` FOREIGN KEY (`device_id`) REFERENCES `eam_maintenance_plan_device` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养路线设备人员关联';

-- eam_maintenance_device_project
CREATE TABLE `eam_maintenance_device_project` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `device_id` bigint NOT NULL COMMENT '保养计划设备调度行ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `item_key` varchar(128) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`device_id`,`standard_id`,`item_key`),
  KEY `device_id` (`device_id`),
  CONSTRAINT `eam_maintenance_device_project_ibfk_1` FOREIGN KEY (`device_id`) REFERENCES `eam_maintenance_plan_device` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养路线设备选定项目';

-- eam_maintenance_execution
CREATE TABLE `eam_maintenance_execution` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `description` text COMMENT '说明',
  `started_at` datetime(6) DEFAULT NULL COMMENT '实际开始时间',
  `work_start_at` datetime(6) DEFAULT NULL COMMENT '登记工作开始时间',
  `work_end_at` datetime(6) DEFAULT NULL COMMENT '登记工作结束时间',
  `review_decision` varchar(24) DEFAULT NULL COMMENT '审核或验收结论',
  `review_description` text COMMENT '审核或验收说明',
  `acceptance_score` decimal(8,2) DEFAULT NULL COMMENT '验收评分，1至5分',
  `reviewer_id` bigint DEFAULT NULL COMMENT '验收人ID',
  `reviewer_name` varchar(150) DEFAULT NULL COMMENT '验收人名称快照',
  `reviewed_at` datetime(6) DEFAULT NULL COMMENT '验收时间',
  PRIMARY KEY (`tenant_id`,`task_id`),
  KEY `task_id` (`task_id`),
  CONSTRAINT `eam_maintenance_execution_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `eam_maintenance_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养执行记录';

-- eam_maintenance_execution_file
CREATE TABLE `eam_maintenance_execution_file` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `stage_code` varchar(16) NOT NULL COMMENT '附件阶段：execution执行、acceptance验收、review审核',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `file_id` varchar(64) NOT NULL COMMENT '文件ID；无附件的历史资料可为空',
  `name_snapshot` varchar(255) NOT NULL COMMENT '名称快照',
  `content_type` varchar(150) NOT NULL COMMENT '文件内容类型',
  `size_bytes` bigint NOT NULL COMMENT '文件大小，字节',
  `url_snapshot` varchar(1000) NOT NULL COMMENT '文件访问地址快照',
  PRIMARY KEY (`tenant_id`,`task_id`,`stage_code`,`sort_no`),
  CONSTRAINT `eam_maintenance_execution_file_ibfk_1` FOREIGN KEY (`tenant_id`, `task_id`) REFERENCES `eam_maintenance_execution` (`tenant_id`, `task_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养执行记录附件';

-- eam_maintenance_execution_image
CREATE TABLE `eam_maintenance_execution_image` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `item_key` varchar(255) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `file_id` varchar(64) NOT NULL COMMENT '文件ID；无附件的历史资料可为空',
  `name_snapshot` varchar(255) NOT NULL COMMENT '名称快照',
  `content_type` varchar(150) NOT NULL COMMENT '文件内容类型',
  `size_bytes` bigint NOT NULL COMMENT '文件大小，字节',
  `url_snapshot` varchar(1000) NOT NULL COMMENT '文件访问地址快照',
  PRIMARY KEY (`tenant_id`,`task_id`,`item_key`,`sort_no`),
  CONSTRAINT `eam_maintenance_execution_image_ibfk_1` FOREIGN KEY (`tenant_id`, `task_id`, `item_key`) REFERENCES `eam_maintenance_execution_item` (`tenant_id`, `task_id`, `item_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养执行记录图片';

-- eam_maintenance_execution_item
CREATE TABLE `eam_maintenance_execution_item` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `item_key` varchar(255) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `result_value` text NOT NULL COMMENT '执行结果值',
  `abnormal` tinyint NOT NULL COMMENT '是否异常',
  `resolved` tinyint NOT NULL COMMENT '异常是否已处理',
  `measure` text NOT NULL COMMENT '处理措施',
  `remark` text NOT NULL COMMENT '备注',
  `implicit_normal` tinyint NOT NULL COMMENT '是否按仅填异常规则隐含正常',
  `skipped` tinyint NOT NULL COMMENT '本项目是否跳过',
  PRIMARY KEY (`tenant_id`,`task_id`,`item_key`),
  CONSTRAINT `eam_maintenance_execution_item_ibfk_1` FOREIGN KEY (`tenant_id`, `task_id`) REFERENCES `eam_maintenance_execution` (`tenant_id`, `task_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养执行记录项目';

-- eam_maintenance_item_file
CREATE TABLE `eam_maintenance_item_file` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `item_key` varchar(128) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `file_id` varchar(64) NOT NULL COMMENT '文件ID；无附件的历史资料可为空',
  `name_snapshot` varchar(255) NOT NULL COMMENT '名称快照',
  `content_type` varchar(150) NOT NULL COMMENT '文件内容类型',
  `size_bytes` bigint NOT NULL COMMENT '文件大小，字节',
  `url_snapshot` varchar(1000) NOT NULL COMMENT '文件访问地址快照',
  PRIMARY KEY (`tenant_id`,`standard_id`,`item_key`,`sort_no`),
  CONSTRAINT `eam_maintenance_item_file_ibfk_1` FOREIGN KEY (`tenant_id`, `standard_id`, `item_key`) REFERENCES `eam_maintenance_standard_item` (`tenant_id`, `standard_id`, `item_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养项目附件';

-- eam_maintenance_item_option
CREATE TABLE `eam_maintenance_item_option` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `item_key` varchar(128) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `option_value` varchar(500) NOT NULL COMMENT '选项值',
  `normal_value` tinyint NOT NULL COMMENT '是否正常选项',
  PRIMARY KEY (`tenant_id`,`standard_id`,`item_key`,`sort_no`),
  CONSTRAINT `eam_maintenance_item_option_ibfk_1` FOREIGN KEY (`tenant_id`, `standard_id`, `item_key`) REFERENCES `eam_maintenance_standard_item` (`tenant_id`, `standard_id`, `item_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养项目选项';

-- eam_maintenance_item_spare
CREATE TABLE `eam_maintenance_item_spare` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `item_key` varchar(128) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `stock_id` bigint NOT NULL COMMENT '关联原备件库存ID',
  `quantity` decimal(18,2) NOT NULL COMMENT '数量，单位沿用关联物料或资料',
  PRIMARY KEY (`tenant_id`,`standard_id`,`item_key`,`sort_no`),
  CONSTRAINT `eam_maintenance_item_spare_ibfk_1` FOREIGN KEY (`tenant_id`, `standard_id`, `item_key`) REFERENCES `eam_maintenance_standard_item` (`tenant_id`, `standard_id`, `item_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养项目备件要求';

-- eam_maintenance_plan
CREATE TABLE `eam_maintenance_plan` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `name` varchar(150) NOT NULL COMMENT '名称',
  `department_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `creator_id` bigint NOT NULL COMMENT '创建人用户ID',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `enabled` tinyint NOT NULL COMMENT '是否启用',
  `remark` text NOT NULL COMMENT '备注',
  `calculation_mode` varchar(24) NOT NULL COMMENT '下次保养日期计算方式',
  `advance_days` int NOT NULL COMMENT '提前生成天数',
  `remind_days` int NOT NULL COMMENT '开始前提醒天数',
  `auto_fill` varchar(24) NOT NULL COMMENT '执行结果自动填写方式',
  `scan_start` tinyint NOT NULL COMMENT '开始时是否校验设备',
  `review_mode` varchar(40) NOT NULL COMMENT '验收或审核方式',
  `review_rule` varchar(100) NOT NULL COMMENT '验收规则',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_eam_maintenance_plan` (`tenant_id`,`code`),
  KEY `ix_eam_maintenance_plan_page` (`tenant_id`,`created_at`,`id`),
  KEY `ix_eam_maintenance_plan_org` (`tenant_id`,`department_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养计划';

-- eam_maintenance_plan_device
CREATE TABLE `eam_maintenance_plan_device` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `equipment_id` bigint NOT NULL COMMENT '设备ID',
  `type_id` bigint NOT NULL COMMENT '保养或巡检类型ID',
  `next_date` date NOT NULL COMMENT '下次保养日期',
  `anchor_date` date NOT NULL COMMENT '周期起算日期',
  `cycle_index` int NOT NULL DEFAULT '0' COMMENT '当前周期序号',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `period_count` int NOT NULL COMMENT '周期数值',
  `period_unit` varchar(8) NOT NULL COMMENT '周期单位',
  `valid_days` int NOT NULL COMMENT '任务有效天数',
  `remark` text NOT NULL COMMENT '备注',
  `legacy_standard_id` bigint DEFAULT NULL COMMENT '历史整标准选择ID；明确项目选择时为空',
  PRIMARY KEY (`id`),
  KEY `ix_maintenance_plan_device_due` (`enabled`,`next_date`),
  KEY `ix_maintenance_plan_device_plan` (`tenant_id`,`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养计划路线设备';

-- eam_maintenance_plan_person
CREATE TABLE `eam_maintenance_plan_person` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `role_code` varchar(16) NOT NULL COMMENT '人员角色：owner负责人、reviewer审核人、collaborator协同人员',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`plan_id`,`role_code`,`user_id`),
  KEY `ix_plan_person` (`tenant_id`,`user_id`),
  KEY `plan_id` (`plan_id`),
  CONSTRAINT `eam_maintenance_plan_person_ibfk_1` FOREIGN KEY (`plan_id`) REFERENCES `eam_maintenance_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养计划人员关联';

-- eam_maintenance_standard
CREATE TABLE `eam_maintenance_standard` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `name` varchar(150) NOT NULL COMMENT '名称',
  `department_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `creator_id` bigint NOT NULL COMMENT '创建人用户ID',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `enabled` tinyint NOT NULL COMMENT '是否启用',
  `remark` text NOT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_eam_maintenance_standard` (`tenant_id`,`code`),
  KEY `ix_eam_maintenance_standard_page` (`tenant_id`,`created_at`,`id`),
  KEY `ix_eam_maintenance_standard_org` (`tenant_id`,`department_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养标准';

-- eam_maintenance_standard_category
CREATE TABLE `eam_maintenance_standard_category` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `category_id` bigint NOT NULL COMMENT '设备类别ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`standard_id`,`category_id`),
  KEY `ix_standard_category_target` (`tenant_id`,`category_id`),
  KEY `standard_id` (`standard_id`),
  CONSTRAINT `eam_maintenance_standard_category_ibfk_1` FOREIGN KEY (`standard_id`) REFERENCES `eam_maintenance_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养标准适用类别';

-- eam_maintenance_standard_category_label
CREATE TABLE `eam_maintenance_standard_category_label` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `label_kind` varchar(16) NOT NULL COMMENT '名称来源：display展示快照、legacy待确认历史名称',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `name_snapshot` varchar(255) NOT NULL COMMENT '名称快照',
  PRIMARY KEY (`tenant_id`,`standard_id`,`label_kind`,`sort_no`),
  KEY `standard_id` (`standard_id`),
  CONSTRAINT `eam_maintenance_standard_category_label_ibfk_1` FOREIGN KEY (`standard_id`) REFERENCES `eam_maintenance_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养标准适用类别名称快照';

-- eam_maintenance_standard_equipment
CREATE TABLE `eam_maintenance_standard_equipment` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `equipment_id` bigint NOT NULL COMMENT '设备ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`standard_id`,`equipment_id`),
  KEY `ix_standard_equipment_target` (`tenant_id`,`equipment_id`),
  KEY `standard_id` (`standard_id`),
  CONSTRAINT `eam_maintenance_standard_equipment_ibfk_1` FOREIGN KEY (`standard_id`) REFERENCES `eam_maintenance_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养标准适用设备';

-- eam_maintenance_standard_item
CREATE TABLE `eam_maintenance_standard_item` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `item_key` varchar(128) NOT NULL COMMENT '稳定项目ID，兼容历史字符串ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `module_name` varchar(150) NOT NULL COMMENT '保养部位',
  `name` varchar(200) NOT NULL COMMENT '名称',
  `content` text NOT NULL COMMENT '项目内容或执行方法',
  `type_id` bigint DEFAULT NULL COMMENT '保养或巡检类型ID',
  `value_type` varchar(16) NOT NULL COMMENT '执行结果类型',
  `lower_bound` decimal(30,8) DEFAULT NULL COMMENT '正常数值范围下限',
  `upper_bound` decimal(30,8) DEFAULT NULL COMMENT '正常数值范围上限',
  `required` tinyint NOT NULL COMMENT '是否必填',
  `photo_required` tinyint NOT NULL COMMENT '是否必须拍照',
  `remark` text NOT NULL COMMENT '备注',
  `spare_description` text NOT NULL COMMENT '备件要求说明',
  PRIMARY KEY (`tenant_id`,`standard_id`,`item_key`),
  KEY `ix_maintenance_item_type` (`tenant_id`,`type_id`),
  KEY `standard_id` (`standard_id`),
  CONSTRAINT `eam_maintenance_standard_item_ibfk_1` FOREIGN KEY (`standard_id`) REFERENCES `eam_maintenance_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养标准项目';

-- eam_maintenance_standard_person
CREATE TABLE `eam_maintenance_standard_person` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `standard_id` bigint NOT NULL COMMENT '标准ID',
  `role_code` varchar(16) NOT NULL COMMENT '人员角色：owner负责人、reviewer审核人、collaborator协同人员',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`standard_id`,`role_code`,`user_id`),
  KEY `ix_standard_person` (`tenant_id`,`user_id`),
  KEY `standard_id` (`standard_id`),
  CONSTRAINT `eam_maintenance_standard_person_ibfk_1` FOREIGN KEY (`standard_id`) REFERENCES `eam_maintenance_standard` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养标准人员关联';

-- eam_maintenance_task
CREATE TABLE `eam_maintenance_task` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `plan_id` bigint NOT NULL,
  `schedule_id` bigint NOT NULL,
  `equipment_id` bigint NOT NULL,
  `company_id` bigint DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `due_date` date NOT NULL,
  `expires_date` date DEFAULT NULL,
  `owner_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT '待执行',
  `snapshot` json NOT NULL,
  `row_version` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `type_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_maintenance_occurrence` (`tenant_id`,`schedule_id`,`due_date`),
  UNIQUE KEY `uk_maintenance_task_code` (`tenant_id`,`code`),
  KEY `idx_maintenance_task` (`tenant_id`,`status`,`department_id`,`owner_id`,`due_date`),
  KEY `ix_maintenance_planned_analysis` (`tenant_id`,`due_date`,`company_id`,`department_id`),
  KEY `ix_maintenance_task_type` (`tenant_id`,`type_id`),
  KEY `fk_maintenance_task_type` (`type_id`),
  CONSTRAINT `fk_maintenance_task_type_v67` FOREIGN KEY (`type_id`) REFERENCES `eam_maintenance_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- eam_maintenance_task_person
CREATE TABLE `eam_maintenance_task_person` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `role_code` varchar(16) NOT NULL COMMENT '人员角色：owner负责人、reviewer审核人、collaborator协同人员',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`task_id`,`role_code`,`user_id`),
  KEY `tenant_id` (`tenant_id`,`user_id`,`role_code`),
  KEY `task_id` (`task_id`),
  CONSTRAINT `eam_maintenance_task_person_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `eam_maintenance_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养任务人员关联';

-- eam_maintenance_task_project
CREATE TABLE `eam_maintenance_task_project` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `item_key` varchar(255) NOT NULL COMMENT '任务内稳定项目ID',
  `equipment_id` bigint DEFAULT NULL COMMENT '对应设备ID',
  `source_standard_id` bigint DEFAULT NULL COMMENT '原标准ID；无法唯一确定的历史记录为空',
  `source_item_key` varchar(128) DEFAULT NULL COMMENT '原标准项目ID',
  `type_id` bigint DEFAULT NULL COMMENT '保养或巡检类型ID',
  `sort_no` int NOT NULL COMMENT '顺序',
  PRIMARY KEY (`tenant_id`,`task_id`,`item_key`),
  KEY `tenant_id` (`tenant_id`,`equipment_id`,`task_id`),
  KEY `tenant_id_2` (`tenant_id`,`source_standard_id`,`source_item_key`),
  KEY `task_id` (`task_id`),
  CONSTRAINT `eam_maintenance_task_project_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `eam_maintenance_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务项目ID及来源关联；项目当时的定义为只读快照';

-- eam_maintenance_type
CREATE TABLE `eam_maintenance_type` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `name` varchar(150) NOT NULL COMMENT '名称',
  `department_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `creator_id` bigint NOT NULL COMMENT '创建人用户ID',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `period_count` int NOT NULL COMMENT '周期数值',
  `period_unit` varchar(8) NOT NULL COMMENT '周期单位',
  `symbol` varchar(40) NOT NULL COMMENT '保留的类型标记',
  `is_system` tinyint NOT NULL COMMENT '是否系统预置',
  `enabled` tinyint NOT NULL COMMENT '是否启用',
  `remark` text NOT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_eam_maintenance_type` (`tenant_id`,`code`),
  KEY `ix_eam_maintenance_type_page` (`tenant_id`,`created_at`,`id`),
  KEY `ix_eam_maintenance_type_org` (`tenant_id`,`department_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保养类型';

-- eam_repair_collaborator
CREATE TABLE `eam_repair_collaborator` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `order_id` bigint NOT NULL COMMENT '维修工单ID',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `name_snapshot` varchar(100) NOT NULL DEFAULT '' COMMENT '名称快照',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`order_id`,`user_id`),
  KEY `ix_repair_collaborator_user` (`tenant_id`,`user_id`,`order_id`),
  KEY `order_id` (`order_id`),
  CONSTRAINT `eam_repair_collaborator_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `eam_repair_order` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='维修collaborator';

-- eam_repair_order
CREATE TABLE `eam_repair_order` (
  `id` bigint NOT NULL,
  `order_no` varchar(32) NOT NULL,
  `equipment_code` varchar(32) NOT NULL,
  `equipment_name` varchar(100) NOT NULL,
  `department` varchar(120) DEFAULT NULL,
  `fault_type` varchar(80) NOT NULL,
  `severity` varchar(16) NOT NULL DEFAULT '一般',
  `description` varchar(2000) NOT NULL,
  `reporter` varchar(80) NOT NULL,
  `assignee` varchar(80) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT '待派工',
  `repair_action` varchar(2000) DEFAULT NULL,
  `downtime_minutes` int NOT NULL DEFAULT '0',
  `reported_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `completed_at` datetime(3) DEFAULT NULL,
  `acceptance_comment` varchar(1000) DEFAULT NULL,
  `row_version` int NOT NULL DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `tenant_id` bigint NOT NULL DEFAULT '10001',
  `department_org_id` bigint DEFAULT NULL,
  `reporter_user_id` bigint DEFAULT NULL,
  `assignee_user_id` bigint DEFAULT NULL,
  `dispatcher_user_id` bigint DEFAULT NULL,
  `fault_cause` text,
  `repair_method` varchar(20) NOT NULL DEFAULT '自修',
  `processing_result` varchar(100) DEFAULT NULL,
  `preventive_measures` text,
  `outsource_vendor` varchar(200) DEFAULT NULL,
  `equipment_id` bigint DEFAULT NULL,
  `company_org_id` bigint DEFAULT NULL,
  `fault_type_id` bigint DEFAULT NULL,
  `fault_cause_id` bigint DEFAULT NULL,
  `accepted_at` datetime(3) DEFAULT NULL,
  `accepted_owner_id` bigint DEFAULT NULL,
  `acceptance_score` int DEFAULT NULL,
  `processing_result_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_repair_tenant_no` (`tenant_id`,`order_no`,`deleted`),
  KEY `idx_repair_status_reported` (`status`,`reported_at`),
  KEY `idx_repair_equipment` (`equipment_code`,`reported_at`),
  KEY `idx_repair_department` (`department`,`status`),
  KEY `idx_repair_access` (`tenant_id`,`department_org_id`,`reporter_user_id`,`assignee_user_id`,`deleted`),
  KEY `idx_repair_assignee_queue` (`tenant_id`,`assignee_user_id`,`deleted`,`status`,`reported_at`,`id`),
  KEY `idx_repair_reporter_queue` (`tenant_id`,`reporter_user_id`,`deleted`,`status`,`reported_at`,`id`),
  KEY `idx_repair_tenant_queue` (`tenant_id`,`deleted`,`status`,`reported_at`,`id`),
  KEY `ix_repair_report_analytics` (`tenant_id`,`deleted`,`reported_at`,`department_org_id`),
  KEY `ix_repair_accept_analytics` (`tenant_id`,`deleted`,`accepted_at`,`assignee_user_id`),
  KEY `ix_repair_processing_result` (`tenant_id`,`processing_result_id`),
  KEY `fk_repair_result_dictionary` (`processing_result_id`),
  CONSTRAINT `fk_repair_result_dictionary` FOREIGN KEY (`processing_result_id`) REFERENCES `eam_base_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='维修工单';

-- eam_repair_plan_file
CREATE TABLE `eam_repair_plan_file` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `file_id` varchar(64) DEFAULT NULL COMMENT '文件ID；无附件的历史资料可为空',
  `name_snapshot` varchar(255) DEFAULT NULL COMMENT '名称快照',
  `content_type` varchar(150) DEFAULT NULL COMMENT '文件内容类型',
  `size_bytes` bigint DEFAULT NULL COMMENT '文件大小，字节',
  `url_snapshot` varchar(1000) DEFAULT NULL COMMENT '文件访问地址快照',
  PRIMARY KEY (`tenant_id`,`plan_id`,`sort_no`),
  KEY `tenant_id` (`tenant_id`,`file_id`),
  KEY `plan_id` (`plan_id`),
  CONSTRAINT `eam_repair_plan_file_ibfk_1` FOREIGN KEY (`plan_id`) REFERENCES `eam_single_repair_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='维修计划附件';

-- eam_repair_process_file
CREATE TABLE `eam_repair_process_file` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `order_id` bigint NOT NULL COMMENT '维修工单ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `file_id` varchar(64) DEFAULT NULL COMMENT '文件ID；无附件的历史资料可为空',
  `name_snapshot` varchar(255) DEFAULT NULL COMMENT '名称快照',
  `content_type` varchar(150) DEFAULT NULL COMMENT '文件内容类型',
  `size_bytes` bigint DEFAULT NULL COMMENT '文件大小，字节',
  `url_snapshot` varchar(1000) DEFAULT NULL COMMENT '文件访问地址快照',
  PRIMARY KEY (`tenant_id`,`order_id`,`sort_no`),
  KEY `tenant_id` (`tenant_id`,`file_id`),
  KEY `order_id` (`order_id`),
  CONSTRAINT `eam_repair_process_file_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `eam_repair_order` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='维修处理附件';

-- eam_repair_report_file
CREATE TABLE `eam_repair_report_file` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `order_id` bigint NOT NULL COMMENT '维修工单ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  `file_id` varchar(64) DEFAULT NULL COMMENT '文件ID；无附件的历史资料可为空',
  `name_snapshot` varchar(255) DEFAULT NULL COMMENT '名称快照',
  `content_type` varchar(150) DEFAULT NULL COMMENT '文件内容类型',
  `size_bytes` bigint DEFAULT NULL COMMENT '文件大小，字节',
  `url_snapshot` varchar(1000) DEFAULT NULL COMMENT '文件访问地址快照',
  PRIMARY KEY (`tenant_id`,`order_id`,`sort_no`),
  KEY `tenant_id` (`tenant_id`,`file_id`),
  KEY `order_id` (`order_id`),
  CONSTRAINT `eam_repair_report_file_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `eam_repair_order` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='维修报修附件';

-- eam_role_legacy_action
CREATE TABLE `eam_role_legacy_action` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `role_id` bigint NOT NULL COMMENT '系统角色ID',
  `menu_key` varchar(80) NOT NULL COMMENT '菜单权限标识',
  `action_code` varchar(80) NOT NULL COMMENT '扫码或跳过设备的动作类别',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`role_id`,`menu_key`,`action_code`),
  KEY `role_id` (`role_id`),
  CONSTRAINT `eam_role_legacy_action_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `eam_sys_role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色兼容旧版操作授权';

-- eam_role_menu_action
CREATE TABLE `eam_role_menu_action` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `grant_id` bigint NOT NULL COMMENT '角色菜单授权主记录ID',
  `action_name` varchar(40) NOT NULL COMMENT '授权操作名称',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`grant_id`,`action_name`),
  KEY `grant_id` (`grant_id`),
  CONSTRAINT `eam_role_menu_action_ibfk_1` FOREIGN KEY (`grant_id`) REFERENCES `eam_sys_role_menu_permission` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单操作授权';

-- eam_role_menu_scope
CREATE TABLE `eam_role_menu_scope` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `grant_id` bigint NOT NULL COMMENT '角色菜单授权主记录ID',
  `scope_code` varchar(32) NOT NULL COMMENT '数据范围类型',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`grant_id`,`scope_code`),
  KEY `grant_id` (`grant_id`),
  CONSTRAINT `eam_role_menu_scope_ibfk_1` FOREIGN KEY (`grant_id`) REFERENCES `eam_sys_role_menu_permission` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单范围';

-- eam_role_menu_target
CREATE TABLE `eam_role_menu_target` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `grant_id` bigint NOT NULL COMMENT '角色菜单授权主记录ID',
  `target_kind` varchar(16) NOT NULL COMMENT '授权对象类型：warehouse/company/department',
  `target_id` bigint NOT NULL COMMENT '授权对象真实ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`grant_id`,`target_kind`,`target_id`),
  KEY `ix_role_menu_target` (`tenant_id`,`target_kind`,`target_id`),
  KEY `grant_id` (`grant_id`),
  CONSTRAINT `eam_role_menu_target_ibfk_1` FOREIGN KEY (`grant_id`) REFERENCES `eam_sys_role_menu_permission` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单对象';

-- eam_role_organization
CREATE TABLE `eam_role_organization` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `role_id` bigint NOT NULL COMMENT '系统角色ID',
  `org_id` bigint NOT NULL COMMENT '授权组织ID',
  `sort_no` int NOT NULL COMMENT '显示顺序',
  PRIMARY KEY (`tenant_id`,`role_id`,`org_id`),
  KEY `ix_role_organization` (`tenant_id`,`org_id`),
  KEY `role_id` (`role_id`),
  CONSTRAINT `eam_role_organization_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `eam_sys_role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色组织关联';

-- eam_sys_role
CREATE TABLE `eam_sys_role` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `code` varchar(60) NOT NULL,
  `name` varchar(80) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `data_scope` varchar(32) NOT NULL DEFAULT 'DEPARTMENT',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_tenant_code` (`tenant_id`,`code`,`deleted`),
  KEY `idx_role_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- eam_sys_role_menu_permission
CREATE TABLE `eam_sys_role_menu_permission` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `menu_key` varchar(60) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`tenant_id`,`role_id`,`menu_key`),
  KEY `idx_role_menu_role` (`tenant_id`,`role_id`),
  KEY `fk_role_menu_role` (`role_id`),
  CONSTRAINT `fk_role_menu_role` FOREIGN KEY (`role_id`) REFERENCES `eam_sys_role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- inv_count
CREATE TABLE `inv_count` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `company_id` bigint NOT NULL COMMENT '所属公司ID',
  `warehouse_id` bigint NOT NULL COMMENT '所属仓库ID',
  `status` varchar(20) NOT NULL DEFAULT '草稿' COMMENT '业务状态',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  `created_by` bigint NOT NULL COMMENT '创建人用户ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `confirmed_at` datetime(3) DEFAULT NULL COMMENT '单据生效时间',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  `baseline_at` datetime(6) DEFAULT NULL COMMENT '盘点基准采集时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `tenant_id` (`tenant_id`,`code`),
  KEY `tenant_id_2` (`tenant_id`,`deleted`,`created_at`,`id`),
  KEY `tenant_id_3` (`tenant_id`,`warehouse_id`,`deleted`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存盘点单';

-- inv_count_line
CREATE TABLE `inv_count_line` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `document_id` bigint NOT NULL COMMENT '当前明细所属单据ID',
  `line_no` int NOT NULL COMMENT '单据明细行顺序号',
  `balance_id` bigint DEFAULT NULL COMMENT '库存余额ID',
  `baseline_quantity` decimal(20,6) DEFAULT NULL COMMENT '盘点基准数量',
  `counted_quantity` decimal(20,6) DEFAULT NULL COMMENT '实盘数量，未填写时为空',
  `unit_cost` decimal(20,6) DEFAULT NULL COMMENT '实际成本单价',
  `material_name` varchar(200) DEFAULT NULL COMMENT '物料名称快照',
  `bin_name` varchar(150) DEFAULT NULL COMMENT '库位名称快照',
  `batch_code` varchar(64) DEFAULT NULL COMMENT '批次号快照',
  `observed_quantity` decimal(20,6) DEFAULT NULL COMMENT '提交盘点时观察数量',
  `counted_at` datetime(6) DEFAULT NULL COMMENT '实盘登记时间',
  `difference` decimal(20,6) DEFAULT NULL COMMENT '实盘与观察数量之差',
  PRIMARY KEY (`tenant_id`,`document_id`,`line_no`),
  KEY `document_id` (`document_id`),
  CONSTRAINT `inv_count_line_ibfk_1` FOREIGN KEY (`document_id`) REFERENCES `inv_count` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存盘点单明细';

-- inv_issue
CREATE TABLE `inv_issue` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `requisition_id` bigint DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `warehouse_id` bigint NOT NULL,
  `confirmation_key` varchar(64) NOT NULL,
  `preview_fingerprint` varchar(64) NOT NULL,
  `total_amount` decimal(20,2) NOT NULL,
  `actor_id` bigint NOT NULL,
  `actor_name` varchar(100) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `reversed` tinyint NOT NULL DEFAULT '0',
  `group_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT '已出库',
  `source_kind` varchar(20) NOT NULL DEFAULT '历史领料',
  `row_version` int NOT NULL DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `confirmed_at` datetime(3) DEFAULT NULL,
  `purpose` varchar(1000) NOT NULL DEFAULT '' COMMENT '领用用途',
  `remark` varchar(1000) NOT NULL DEFAULT '' COMMENT '备注',
  `department_id` bigint DEFAULT NULL COMMENT '领用部门ID，多来源单据以明细为准',
  `recipient_id` bigint DEFAULT NULL COMMENT '领用人ID，多来源单据以明细为准',
  `recipient_name` varchar(100) NOT NULL DEFAULT '' COMMENT '领用人名称快照',
  `task_kind` varchar(20) NOT NULL DEFAULT '' COMMENT '关联任务类型',
  `task_id` bigint DEFAULT NULL COMMENT '关联任务ID',
  `legacy_fingerprint` varchar(512) NOT NULL DEFAULT '' COMMENT '旧确认入口的幂等内容指纹',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_issue_code` (`tenant_id`,`code`),
  UNIQUE KEY `uq_issue_confirmation` (`tenant_id`,`requisition_id`,`confirmation_key`),
  KEY `idx_issue_group` (`tenant_id`,`group_id`),
  KEY `ix_outbound_list` (`tenant_id`,`deleted`,`status`,`created_at`),
  KEY `ix_issue_recipient` (`tenant_id`,`recipient_id`),
  KEY `ix_issue_task` (`tenant_id`,`task_kind`,`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- inv_issue_group_request
CREATE TABLE `inv_issue_group_request` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `group_id` bigint NOT NULL COMMENT '历史合并出库组ID',
  `requisition_id` bigint NOT NULL COMMENT '来源领料申请ID',
  `sort_no` int NOT NULL COMMENT '顺序',
  PRIMARY KEY (`tenant_id`,`group_id`,`requisition_id`),
  KEY `tenant_id` (`tenant_id`,`requisition_id`),
  KEY `group_id` (`group_id`),
  CONSTRAINT `inv_issue_group_request_ibfk_1` FOREIGN KEY (`group_id`) REFERENCES `inv_issue_group` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='历史合并出库组来源申请';

-- inv_opening
CREATE TABLE `inv_opening` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `company_id` bigint NOT NULL COMMENT '所属公司ID',
  `warehouse_id` bigint NOT NULL COMMENT '所属仓库ID',
  `status` varchar(20) NOT NULL DEFAULT '草稿' COMMENT '业务状态',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  `created_by` bigint NOT NULL COMMENT '创建人用户ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `confirmed_at` datetime(3) DEFAULT NULL COMMENT '单据生效时间',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  `conversion_material_id` bigint DEFAULT NULL COMMENT '批次初始化对应物料ID',
  `receipt_id` bigint DEFAULT NULL COMMENT '关联入库单ID',
  `receipt_code` varchar(64) DEFAULT NULL COMMENT '关联入库单编号快照',
  PRIMARY KEY (`id`),
  UNIQUE KEY `tenant_id` (`tenant_id`,`code`),
  KEY `tenant_id_2` (`tenant_id`,`deleted`,`created_at`,`id`),
  KEY `tenant_id_3` (`tenant_id`,`warehouse_id`,`deleted`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存期初单';

-- inv_opening_line
CREATE TABLE `inv_opening_line` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `document_id` bigint NOT NULL COMMENT '当前明细所属单据ID',
  `line_no` int NOT NULL COMMENT '单据明细行顺序号',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID',
  `quantity` decimal(20,6) DEFAULT NULL COMMENT '数量，单位沿用关联物料或资料',
  `unit_price` decimal(20,6) DEFAULT NULL COMMENT '不含税采购单价',
  `bin_id` bigint DEFAULT NULL COMMENT '库位ID',
  `batch_code` varchar(64) DEFAULT NULL COMMENT '批次号快照',
  `supplier_batch` varchar(100) DEFAULT NULL COMMENT '供应商批次号',
  `production_date` date DEFAULT NULL COMMENT '生产日期',
  `expiry_date` date DEFAULT NULL COMMENT '有效期截止日期',
  `legacy_stock_id` bigint DEFAULT NULL COMMENT '待初始化旧库存ID',
  `allocated_fee` decimal(20,2) DEFAULT NULL COMMENT '分摊费用金额',
  `material_name` varchar(200) DEFAULT NULL COMMENT '物料名称快照',
  `balance_id` bigint DEFAULT NULL COMMENT '库存余额ID',
  `warehouse_id` bigint DEFAULT NULL COMMENT '所属仓库ID',
  `balance_version` int DEFAULT NULL COMMENT '批次初始化时的库存余额版本',
  PRIMARY KEY (`tenant_id`,`document_id`,`line_no`),
  KEY `document_id` (`document_id`),
  CONSTRAINT `inv_opening_line_ibfk_1` FOREIGN KEY (`document_id`) REFERENCES `inv_opening` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存期初单明细';

-- inv_outbound_selection
CREATE TABLE `inv_outbound_selection` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `issue_id` bigint NOT NULL COMMENT '出库单ID',
  `line_no` int NOT NULL COMMENT '出库明细行号',
  `sort_no` int NOT NULL COMMENT '选择顺序',
  `balance_id` bigint NOT NULL COMMENT '指定库存余额ID，对应库位和批次',
  `quantity` decimal(20,6) NOT NULL COMMENT '指定数量，保持库存数量精度',
  PRIMARY KEY (`tenant_id`,`issue_id`,`line_no`,`sort_no`),
  KEY `ix_outbound_selection_balance` (`tenant_id`,`balance_id`),
  KEY `fk_outbound_selection_issue` (`issue_id`),
  CONSTRAINT `fk_outbound_selection_issue` FOREIGN KEY (`issue_id`) REFERENCES `inv_issue` (`id`),
  CONSTRAINT `ck_outbound_selection_quantity` CHECK ((`quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='出库草稿指定库位批次选择，非实际扣库记录';

-- inv_return
CREATE TABLE `inv_return` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `company_id` bigint NOT NULL COMMENT '所属公司ID',
  `warehouse_id` bigint NOT NULL COMMENT '所属仓库ID',
  `status` varchar(20) NOT NULL DEFAULT '草稿' COMMENT '业务状态',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  `created_by` bigint NOT NULL COMMENT '创建人用户ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `confirmed_at` datetime(3) DEFAULT NULL COMMENT '单据生效时间',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  `requested_by` bigint DEFAULT NULL COMMENT '余料退回申请人用户ID',
  `requested_by_name` varchar(150) DEFAULT NULL COMMENT '余料退回申请人名称快照',
  PRIMARY KEY (`id`),
  UNIQUE KEY `tenant_id` (`tenant_id`,`code`),
  KEY `tenant_id_2` (`tenant_id`,`deleted`,`created_at`,`id`),
  KEY `tenant_id_3` (`tenant_id`,`warehouse_id`,`deleted`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存退料单';

-- inv_return_line
CREATE TABLE `inv_return_line` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `document_id` bigint NOT NULL COMMENT '当前明细所属单据ID',
  `line_no` int NOT NULL COMMENT '单据明细行顺序号',
  `allocation_id` bigint DEFAULT NULL COMMENT '原出库实际分配ID',
  `quantity` decimal(20,6) DEFAULT NULL COMMENT '数量，单位沿用关联物料或资料',
  `bin_id` bigint DEFAULT NULL COMMENT '库位ID',
  `material_name` varchar(200) DEFAULT NULL COMMENT '物料名称快照',
  `issue_code` varchar(64) DEFAULT NULL COMMENT '原出库单编号快照',
  `returned_amount` decimal(20,2) DEFAULT NULL COMMENT '本次退料原分配成本金额',
  PRIMARY KEY (`tenant_id`,`document_id`,`line_no`),
  KEY `document_id` (`document_id`),
  CONSTRAINT `inv_return_line_ibfk_1` FOREIGN KEY (`document_id`) REFERENCES `inv_return` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存退料单明细';

-- inv_reversal
CREATE TABLE `inv_reversal` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `company_id` bigint NOT NULL COMMENT '所属公司ID',
  `warehouse_id` bigint NOT NULL COMMENT '所属仓库ID',
  `status` varchar(20) NOT NULL DEFAULT '草稿' COMMENT '业务状态',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  `created_by` bigint NOT NULL COMMENT '创建人用户ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `confirmed_at` datetime(3) DEFAULT NULL COMMENT '单据生效时间',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  `source_kind` varchar(24) DEFAULT NULL COMMENT '被纠正来源单据类型',
  `source_id` bigint DEFAULT NULL COMMENT '被纠正来源单据ID',
  `source_code` varchar(64) DEFAULT NULL COMMENT '被纠正来源单据编号快照',
  PRIMARY KEY (`id`),
  UNIQUE KEY `tenant_id` (`tenant_id`,`code`),
  KEY `tenant_id_2` (`tenant_id`,`deleted`,`created_at`,`id`),
  KEY `tenant_id_3` (`tenant_id`,`warehouse_id`,`deleted`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存冲销单';

-- inv_transfer
CREATE TABLE `inv_transfer` (
  `id` bigint NOT NULL COMMENT '全局业务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(64) NOT NULL COMMENT '业务编号',
  `company_id` bigint NOT NULL COMMENT '所属公司ID',
  `warehouse_id` bigint NOT NULL COMMENT '所属仓库ID',
  `status` varchar(20) NOT NULL DEFAULT '草稿' COMMENT '业务状态',
  `row_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  `created_by` bigint NOT NULL COMMENT '创建人用户ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `confirmed_at` datetime(3) DEFAULT NULL COMMENT '单据生效时间',
  `target_warehouse_id` bigint DEFAULT NULL COMMENT '调入仓库ID',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `tenant_id` (`tenant_id`,`code`),
  KEY `tenant_id_2` (`tenant_id`,`deleted`,`created_at`,`id`),
  KEY `tenant_id_3` (`tenant_id`,`warehouse_id`,`deleted`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存调拨单';

-- inv_transfer_line
CREATE TABLE `inv_transfer_line` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `document_id` bigint NOT NULL COMMENT '当前明细所属单据ID',
  `line_no` int NOT NULL COMMENT '单据明细行顺序号',
  `balance_id` bigint DEFAULT NULL COMMENT '库存余额ID',
  `quantity` decimal(20,6) DEFAULT NULL COMMENT '数量，单位沿用关联物料或资料',
  `target_bin_id` bigint DEFAULT NULL COMMENT '调入库位ID',
  PRIMARY KEY (`tenant_id`,`document_id`,`line_no`),
  KEY `document_id` (`document_id`),
  CONSTRAINT `inv_transfer_line_ibfk_1` FOREIGN KEY (`document_id`) REFERENCES `inv_transfer` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存调拨单明细';
