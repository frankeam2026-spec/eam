-- 当前本地 eam_demo 设备相关表结构（2026-10-14）
-- 仅供查看；不包含业务数据。

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
  `documents_json` json DEFAULT NULL,
  `children_json` json DEFAULT NULL,
  `related_spares_json` json DEFAULT NULL,
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

CREATE TABLE `eam_equipment_anomaly` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `equipment_id` bigint NOT NULL,
  `source_kind` varchar(20) NOT NULL DEFAULT '人工',
  `source_task_id` bigint DEFAULT NULL,
  `source_item_id` varchar(100) DEFAULT NULL,
  `description` varchar(4000) NOT NULL,
  `severity` varchar(40) NOT NULL DEFAULT '',
  `attachments` json NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT '待处理',
  `discovered_by` bigint NOT NULL,
  `discovered_name` varchar(100) NOT NULL,
  `discovered_at` datetime(3) NOT NULL,
  `repair_id` bigint DEFAULT NULL,
  `plan_id` bigint DEFAULT NULL,
  `resolution` varchar(4000) NOT NULL DEFAULT '',
  `resolved_at` datetime(3) DEFAULT NULL,
  `row_version` int NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_anomaly_code` (`tenant_id`,`code`),
  UNIQUE KEY `uq_anomaly_source` (`tenant_id`,`source_kind`,`source_task_id`,`equipment_id`,`source_item_id`),
  KEY `ix_anomaly_equipment` (`tenant_id`,`equipment_id`,`status`),
  KEY `ix_anomaly_repair` (`tenant_id`,`repair_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `eam_equipment_bom` (
  `tenant_id` bigint NOT NULL,
  `equipment_id` bigint NOT NULL,
  `material_id` bigint NOT NULL,
  `quantity` decimal(18,2) NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(120) NOT NULL,
  `model` varchar(255) DEFAULT NULL,
  `unit` varchar(80) DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`tenant_id`,`equipment_id`,`material_id`),
  KEY `equipment_id` (`equipment_id`),
  KEY `material_id` (`material_id`),
  CONSTRAINT `eam_equipment_bom_ibfk_1` FOREIGN KEY (`equipment_id`) REFERENCES `eam_equipment` (`id`),
  CONSTRAINT `eam_equipment_bom_ibfk_2` FOREIGN KEY (`material_id`) REFERENCES `inv_material` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `eam_equipment_child` (
  `tenant_id` bigint NOT NULL,
  `parent_id` bigint NOT NULL,
  `child_id` bigint NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`tenant_id`,`child_id`),
  KEY `idx_equipment_child_parent` (`tenant_id`,`parent_id`),
  KEY `parent_id` (`parent_id`),
  KEY `child_id` (`child_id`),
  CONSTRAINT `eam_equipment_child_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `eam_equipment` (`id`),
  CONSTRAINT `eam_equipment_child_ibfk_2` FOREIGN KEY (`child_id`) REFERENCES `eam_equipment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `eam_equipment_file` (
  `id` char(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `extension` varchar(12) NOT NULL,
  `content_type` varchar(100) NOT NULL,
  `size` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_equipment_file_owner` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `eam_equipment_history` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `equipment_id` bigint NOT NULL,
  `record_type` varchar(30) NOT NULL,
  `record_no` varchar(64) NOT NULL DEFAULT '',
  `occurred_at` datetime NOT NULL,
  `description` text,
  `operator_name` varchar(100) NOT NULL DEFAULT '',
  `status` varchar(30) NOT NULL DEFAULT '',
  `result` text,
  PRIMARY KEY (`id`),
  KEY `idx_history_equipment` (`tenant_id`,`equipment_id`,`record_type`,`occurred_at`),
  KEY `fk_history_equipment` (`equipment_id`),
  CONSTRAINT `fk_history_equipment` FOREIGN KEY (`equipment_id`) REFERENCES `eam_equipment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `eam_equipment_owner` (
  `tenant_id` bigint NOT NULL,
  `equipment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `owner_name` varchar(100) NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`tenant_id`,`equipment_id`,`user_id`),
  KEY `idx_equipment_owner_user` (`tenant_id`,`user_id`,`equipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `eam_equipment_relation_legacy` (
  `tenant_id` bigint NOT NULL,
  `equipment_id` bigint NOT NULL,
  `kind` varchar(16) NOT NULL,
  `source_index` int NOT NULL,
  `snapshot_json` json NOT NULL,
  `reason` varchar(120) NOT NULL,
  PRIMARY KEY (`tenant_id`,`equipment_id`,`kind`,`source_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `eam_equipment_spare` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `equipment_id` bigint NOT NULL,
  `stock_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `quantity` decimal(14,3) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT '在用',
  `installed_at` datetime NOT NULL,
  `removed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_equipment_spare` (`tenant_id`,`equipment_id`,`status`),
  CONSTRAINT `eam_equipment_spare_chk_1` CHECK ((`quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
