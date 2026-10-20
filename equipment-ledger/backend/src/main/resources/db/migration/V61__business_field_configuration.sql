CREATE TABLE eam_business_schema (
 tenant_id BIGINT NOT NULL, object_key VARCHAR(32) NOT NULL,
 draft JSON NOT NULL, published JSON NOT NULL, published_version INT NOT NULL DEFAULT 0,
 row_version INT NOT NULL DEFAULT 0, updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(tenant_id,object_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_business_schema_version (
 tenant_id BIGINT NOT NULL, object_key VARCHAR(32) NOT NULL, version INT NOT NULL,
 configuration JSON NOT NULL, actor_id BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(tenant_id,object_key,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_business_field (
 id VARCHAR(36) PRIMARY KEY, tenant_id BIGINT NOT NULL, object_key VARCHAR(32) NOT NULL,
 data_type VARCHAR(24) NOT NULL, source_key VARCHAR(100) NOT NULL DEFAULT '', ever_published TINYINT NOT NULL DEFAULT 0,
 KEY ix_business_field(tenant_id,object_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_business_dictionary (
 id VARCHAR(36) PRIMARY KEY, tenant_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL,
 row_version INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1,
 UNIQUE KEY uk_business_dictionary_name(tenant_id,name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_business_option (
 id VARCHAR(36) PRIMARY KEY, tenant_id BIGINT NOT NULL, dictionary_id VARCHAR(36) NOT NULL,
 name VARCHAR(100) NOT NULL, sort_no INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1,
 row_version INT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_business_option_name(dictionary_id,name), KEY ix_business_option(tenant_id,dictionary_id,sort_no),
 CONSTRAINT fk_business_option_dictionary FOREIGN KEY(dictionary_id) REFERENCES eam_business_dictionary(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_business_value (
 tenant_id BIGINT NOT NULL, object_key VARCHAR(32) NOT NULL, record_id BIGINT NOT NULL,
 field_id VARCHAR(36) NOT NULL, ordinal_no INT NOT NULL DEFAULT 0,
 text_value TEXT NULL, number_value DECIMAL(24,6) NULL, date_value DATE NULL, datetime_value DATETIME NULL,
 bool_value TINYINT NULL, reference_id VARCHAR(64) NULL, reference_name VARCHAR(500) NULL,
 PRIMARY KEY(tenant_id,object_key,record_id,field_id,ordinal_no),
 KEY ix_business_value_reference(tenant_id,object_key,field_id,reference_id,record_id),
 KEY ix_business_value_number(tenant_id,object_key,field_id,number_value,record_id),
 KEY ix_business_value_date(tenant_id,object_key,field_id,date_value,record_id),
 KEY ix_business_value_datetime(tenant_id,object_key,field_id,datetime_value,record_id),
 CONSTRAINT fk_business_value_field FOREIGN KEY(field_id) REFERENCES eam_business_field(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_business_config_event (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, object_key VARCHAR(32) NOT NULL,
 record_id BIGINT NULL, actor_id BIGINT NOT NULL, actor_name VARCHAR(100) NOT NULL,
 action VARCHAR(40) NOT NULL, details JSON NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY ix_business_event(tenant_id,object_key,record_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO eam_business_schema(tenant_id,object_key,draft,published)
SELECT t.tenant_id,o.object_key,JSON_OBJECT('fields',JSON_ARRAY()),JSON_OBJECT('fields',JSON_ARRAY())
FROM (SELECT DISTINCT tenant_id FROM eam_org_unit) t
CROSS JOIN (SELECT 'equipment' object_key UNION ALL SELECT 'repair_report') o;
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json)
SELECT UUID_SHORT(),tenant_id,id,'businessConfig',JSON_ARRAY('查看','编辑','发布'),JSON_ARRAY('all'),JSON_ARRAY()
FROM eam_sys_role r WHERE r.code='SYS_ADMIN' AND r.deleted=0
AND NOT EXISTS(SELECT 1 FROM eam_sys_role_menu_permission p WHERE p.tenant_id=r.tenant_id AND p.role_id=r.id AND p.menu_key='businessConfig');
