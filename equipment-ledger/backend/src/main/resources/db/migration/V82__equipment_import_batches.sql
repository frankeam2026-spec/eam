CREATE TABLE eam_import_batch (
 id CHAR(36) NOT NULL COMMENT '导入批次ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 actor_id BIGINT NOT NULL COMMENT '操作人ID', object_key VARCHAR(40) NOT NULL COMMENT '业务对象',
 file_name VARCHAR(255) NOT NULL COMMENT '原文件名', file_size BIGINT NOT NULL COMMENT '原文件字节数',
 file_sha256 CHAR(64) NOT NULL COMMENT '文件摘要', state VARCHAR(24) NOT NULL COMMENT '批次状态',
 schema_version INT NOT NULL DEFAULT 0 COMMENT '扩展字段发布版本', row_version INT NOT NULL DEFAULT 0 COMMENT '批次版本',
 total_rows INT NOT NULL DEFAULT 0 COMMENT '总行数', error_rows INT NOT NULL DEFAULT 0 COMMENT '错误行数',
 error_count INT NOT NULL DEFAULT 0 COMMENT '错误项数', auto_number_rows INT NOT NULL DEFAULT 0 COMMENT '自动编号行数',
 success_rows INT NOT NULL DEFAULT 0 COMMENT '成功新增数', message VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '批次提示',
 purged_at DATETIME NULL COMMENT '临时数据清理时间',
 confirm_key CHAR(36) NULL COMMENT '确认幂等键', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间', expires_at DATETIME NOT NULL COMMENT '临时数据到期时间',
 PRIMARY KEY(id), KEY ix_import_actor(tenant_id,actor_id,created_at,id), KEY ix_import_cleanup(state,expires_at)
) COMMENT='通用导入批次摘要；不存储业务payload';
CREATE TABLE eam_import_column (
 batch_id CHAR(36) NOT NULL, field_key VARCHAR(100) NOT NULL COMMENT '稳定字段标识',
 column_no INT NOT NULL COMMENT 'Excel列索引', label VARCHAR(255) NOT NULL COMMENT '上传时列名',
 PRIMARY KEY(batch_id,field_key), UNIQUE KEY ux_import_column(batch_id,column_no),
 FOREIGN KEY(batch_id) REFERENCES eam_import_batch(id) ON DELETE CASCADE
) COMMENT='原文件列目录';
CREATE TABLE eam_import_cell (
 batch_id CHAR(36) NOT NULL, row_no INT NOT NULL COMMENT '原Excel行号', field_key VARCHAR(100) NOT NULL,
 raw_value MEDIUMTEXT NOT NULL COMMENT '原始输入，仅用于报告及重新校验', formula_flag TINYINT NOT NULL DEFAULT 0,
 PRIMARY KEY(batch_id,row_no,field_key), FOREIGN KEY(batch_id,field_key) REFERENCES eam_import_column(batch_id,field_key) ON DELETE CASCADE
) COMMENT='导入文件输入留底，不作为正式业务存储';
CREATE TABLE eam_import_issue (
 id BIGINT NOT NULL, batch_id CHAR(36) NOT NULL, row_no INT NOT NULL, field_key VARCHAR(100) NOT NULL,
 field_label VARCHAR(255) NOT NULL, raw_value MEDIUMTEXT NOT NULL, reason VARCHAR(1000) NOT NULL,
 suggestion VARCHAR(1000) NOT NULL, PRIMARY KEY(id), KEY ix_import_issue(batch_id,row_no,id),
 FOREIGN KEY(batch_id) REFERENCES eam_import_batch(id) ON DELETE CASCADE
) COMMENT='逐行逐字段导入校验错误';
CREATE TABLE eam_equipment_import_row (
 batch_id CHAR(36) NOT NULL, row_no INT NOT NULL COMMENT '原Excel行号',
 `code` varchar(32) NULL COMMENT '设备待新增字段 code',
 `name` varchar(200) NULL COMMENT '设备待新增字段 name',
 `category` varchar(200) NULL COMMENT '设备待新增字段 category',
 `model` varchar(200) NULL COMMENT '设备待新增字段 model',
 `serial` varchar(200) NULL COMMENT '设备待新增字段 serial',
 `company` varchar(200) NULL COMMENT '设备待新增字段 company',
 `department` varchar(200) NULL COMMENT '设备待新增字段 department',
 `owner` text NULL COMMENT '设备待新增字段 owner',
 `brand` varchar(200) NULL COMMENT '设备待新增字段 brand',
 `state` varchar(40) NULL COMMENT '设备待新增字段 state',
 `use_state` varchar(100) NULL COMMENT '设备待新增字段 useState',
 `source` varchar(100) NULL COMMENT '设备待新增字段 source',
 `acquired_at` date NULL COMMENT '设备待新增字段 acquiredAt',
 `amount` decimal(18,2) NULL COMMENT '设备待新增字段 amount',
 `warranty` date NULL COMMENT '设备待新增字段 warranty',
 `remark` text NULL COMMENT '设备待新增字段 remark',
 `asset_no` varchar(200) NULL COMMENT '设备待新增字段 assetNo',
 `tag_code` varchar(200) NULL COMMENT '设备待新增字段 tagCode',
 `supplier` varchar(200) NULL COMMENT '设备待新增字段 supplier',
 `unit` varchar(100) NULL COMMENT '设备待新增字段 unit',
 `purchase_date` date NULL COMMENT '设备待新增字段 purchaseDate',
 `in_service_date` date NULL COMMENT '设备待新增字段 inServiceDate',
 `retirement_date` date NULL COMMENT '设备待新增字段 retirementDate',
 `level` varchar(40) NULL COMMENT '设备待新增字段 level',
 `function_location` text NULL COMMENT '设备待新增字段 functionLocation',
 `special_equipment` varchar(8) NULL COMMENT '设备待新增字段 specialEquipment',
 `metrology_equipment` varchar(8) NULL COMMENT '设备待新增字段 metrologyEquipment',
 `depreciation_enabled` varchar(8) NULL COMMENT '设备待新增字段 depreciationEnabled',
 `technical_params` text NULL COMMENT '设备待新增字段 technicalParams',
 `calibration_date` date NULL COMMENT '设备待新增字段 calibrationDate',
 `calibration_valid_until` date NULL COMMENT '设备待新增字段 calibrationValidUntil',
 `inspector_name` varchar(100) NULL COMMENT '设备待新增字段 inspectorName',
 `department_org_id` bigint NULL COMMENT '设备待新增字段 departmentOrgId',
 `location_id` bigint NULL COMMENT '设备待新增字段 locationId',
 `category_id` bigint NULL COMMENT '设备待新增字段 categoryId',
 `unit_id` bigint NULL COMMENT '设备待新增字段 unitId',
 `use_state_id` bigint NULL COMMENT '设备待新增字段 useStateId',
 `source_id` bigint NULL COMMENT '设备待新增字段 sourceId',
 `parent_code` varchar(32) NULL COMMENT '设备待新增字段 parentCode',
 PRIMARY KEY(batch_id,row_no), FOREIGN KEY(batch_id) REFERENCES eam_import_batch(id) ON DELETE CASCADE
) COMMENT='校验后的设备暂存行；核心字段显式建模';
CREATE TABLE eam_equipment_import_owner (
 batch_id CHAR(36) NOT NULL,row_no INT NOT NULL,user_id BIGINT NOT NULL,sort_no INT NOT NULL,
 PRIMARY KEY(batch_id,row_no,user_id), FOREIGN KEY(batch_id,row_no) REFERENCES eam_equipment_import_row(batch_id,row_no) ON DELETE CASCADE
) COMMENT='待新增设备负责人ID';
CREATE TABLE eam_equipment_import_result (
 batch_id CHAR(36) NOT NULL,row_no INT NOT NULL,equipment_id BIGINT NOT NULL,
 PRIMARY KEY(batch_id,row_no), UNIQUE KEY ux_import_equipment(equipment_id),
 FOREIGN KEY(batch_id) REFERENCES eam_import_batch(id)
) COMMENT='成功导入设备关联；不随七天暂存清理删除';
