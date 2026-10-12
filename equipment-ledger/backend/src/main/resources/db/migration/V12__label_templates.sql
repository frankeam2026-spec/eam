CREATE TABLE eam_label_template (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 name VARCHAR(100) NOT NULL,
 width_mm DECIMAL(6,2) NOT NULL,
 height_mm DECIMAL(6,2) NOT NULL,
 remark VARCHAR(500) NOT NULL DEFAULT '',
 elements_json JSON NOT NULL,
 row_version INT NOT NULL DEFAULT 0,
 deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 KEY idx_label_tenant (tenant_id,deleted,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO eam_sys_role_menu_permission (id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json)
SELECT 74009,10001,72001,'equipmentTag',JSON_ARRAY('查看','新增','编辑','删除','打印','导出'),JSON_ARRAY('all'),JSON_ARRAY()
WHERE EXISTS (SELECT 1 FROM eam_sys_role WHERE id=72001 AND tenant_id=10001)
AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission WHERE role_id=72001 AND menu_key='equipmentTag');
