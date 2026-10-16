CREATE TABLE eam_tenant_branding (
 tenant_id BIGINT NOT NULL PRIMARY KEY,
 system_name VARCHAR(30) NOT NULL DEFAULT '设备运维平台',
 logo_file_id VARCHAR(36) NULL,
 background_file_id VARCHAR(36) NULL,
 background_position VARCHAR(16) NOT NULL DEFAULT 'center',
 row_version INT NOT NULL DEFAULT 0,
 updated_by BIGINT NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE eam_branding_asset (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 kind VARCHAR(16) NOT NULL,
 content_type VARCHAR(32) NOT NULL,
 image_data MEDIUMBLOB NOT NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_branding_asset_tenant(tenant_id,created_by)
);
CREATE TABLE eam_branding_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 before_json JSON NOT NULL,
 after_json JSON NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_branding_event_tenant(tenant_id,created_at)
);
SET @branding_permission_id=(SELECT COALESCE(MAX(id),0) FROM eam_sys_role_menu_permission);
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json)
SELECT (@branding_permission_id:=@branding_permission_id+1),tenant_id,id,'personalization',JSON_ARRAY('查看'),JSON_ARRAY('all'),JSON_ARRAY()
FROM eam_sys_role r WHERE code='SYS_ADMIN' AND status=1 AND deleted=0
AND NOT EXISTS(SELECT 1 FROM eam_sys_role_menu_permission p WHERE p.tenant_id=r.tenant_id AND p.role_id=r.id AND p.menu_key='personalization');
