CREATE TABLE migration_v65_role_archive (
 tenant_id BIGINT NOT NULL,role_id BIGINT NOT NULL,org_ids JSON NULL,permissions JSON NULL,
 PRIMARY KEY(tenant_id,role_id)
) COMMENT='V65角色旧授权归档，仅供核对和回退';
INSERT INTO migration_v65_role_archive SELECT tenant_id,id,org_ids_json,permissions_json FROM eam_sys_role;
CREATE TABLE migration_v65_menu_archive (
 tenant_id BIGINT NOT NULL,grant_id BIGINT NOT NULL,actions JSON NULL,scopes JSON NULL,targets JSON NULL,
 PRIMARY KEY(tenant_id,grant_id)
) COMMENT='V65菜单旧授权归档，仅供核对和回退';
INSERT INTO migration_v65_menu_archive SELECT tenant_id,id,actions_json,data_scopes_json,org_ids_json FROM eam_sys_role_menu_permission;
CREATE TABLE eam_role_organization (
 tenant_id BIGINT NOT NULL COMMENT '租户ID',role_id BIGINT NOT NULL COMMENT '角色ID',org_id BIGINT NOT NULL COMMENT '旧角色数据范围组织ID',sort_no INT NOT NULL COMMENT '顺序',
 PRIMARY KEY(tenant_id,role_id,org_id),INDEX ix_role_organization(tenant_id,org_id),FOREIGN KEY(role_id) REFERENCES eam_sys_role(id) ON DELETE CASCADE
) COMMENT='旧角色级组织范围，保留授权语义';
INSERT INTO eam_role_organization SELECT DISTINCT r.tenant_id,r.id,j.org_id,MIN(j.sort_no) FROM eam_sys_role r JOIN JSON_TABLE(COALESCE(r.org_ids_json,JSON_ARRAY()),'$[*]' COLUMNS(sort_no FOR ORDINALITY,org_id BIGINT PATH '$')) j GROUP BY r.tenant_id,r.id,j.org_id;
CREATE TABLE eam_role_legacy_action (
 tenant_id BIGINT NOT NULL COMMENT '租户ID',role_id BIGINT NOT NULL COMMENT '角色ID',menu_key VARCHAR(80) NOT NULL COMMENT '旧菜单标识',action_code VARCHAR(80) NOT NULL COMMENT '旧操作标识',sort_no INT NOT NULL COMMENT '顺序',
 PRIMARY KEY(tenant_id,role_id,menu_key,action_code),FOREIGN KEY(role_id) REFERENCES eam_sys_role(id) ON DELETE CASCADE
) COMMENT='旧角色操作授权，用于既有授权兼容';
INSERT INTO eam_role_legacy_action SELECT DISTINCT r.tenant_id,r.id,SUBSTRING_INDEX(j.permission,'.',1),SUBSTRING(j.permission,LOCATE('.',j.permission)+1),MIN(j.sort_no) FROM eam_sys_role r JOIN JSON_TABLE(COALESCE(r.permissions_json,JSON_ARRAY()),'$[*]' COLUMNS(sort_no FOR ORDINALITY,permission VARCHAR(160) PATH '$')) j WHERE LOCATE('.',j.permission)>0 GROUP BY r.tenant_id,r.id,j.permission;
CREATE TABLE eam_role_menu_action (
 tenant_id BIGINT NOT NULL COMMENT '租户ID',grant_id BIGINT NOT NULL COMMENT '菜单授权ID',action_name VARCHAR(40) NOT NULL COMMENT '操作名称',sort_no INT NOT NULL COMMENT '顺序',
 PRIMARY KEY(tenant_id,grant_id,action_name),FOREIGN KEY(grant_id) REFERENCES eam_sys_role_menu_permission(id) ON DELETE CASCADE
) COMMENT='角色菜单操作授权';
INSERT INTO eam_role_menu_action SELECT DISTINCT p.tenant_id,p.id,j.action_name,MIN(j.sort_no) FROM eam_sys_role_menu_permission p JOIN JSON_TABLE(COALESCE(p.actions_json,JSON_ARRAY()),'$[*]' COLUMNS(sort_no FOR ORDINALITY,action_name VARCHAR(40) PATH '$')) j GROUP BY p.tenant_id,p.id,j.action_name;
CREATE TABLE eam_role_menu_scope (
 tenant_id BIGINT NOT NULL COMMENT '租户ID',grant_id BIGINT NOT NULL COMMENT '菜单授权ID',scope_code VARCHAR(32) NOT NULL COMMENT '范围类型',sort_no INT NOT NULL COMMENT '顺序',
 PRIMARY KEY(tenant_id,grant_id,scope_code),FOREIGN KEY(grant_id) REFERENCES eam_sys_role_menu_permission(id) ON DELETE CASCADE
) COMMENT='角色菜单数据范围类型';
INSERT INTO eam_role_menu_scope SELECT DISTINCT p.tenant_id,p.id,j.scope_code,MIN(j.sort_no) FROM eam_sys_role_menu_permission p JOIN JSON_TABLE(COALESCE(p.data_scopes_json,JSON_ARRAY()),'$[*]' COLUMNS(sort_no FOR ORDINALITY,scope_code VARCHAR(32) PATH '$')) j GROUP BY p.tenant_id,p.id,j.scope_code;
CREATE TABLE eam_role_menu_target (
 tenant_id BIGINT NOT NULL COMMENT '租户ID',grant_id BIGINT NOT NULL COMMENT '菜单授权ID',target_kind VARCHAR(16) NOT NULL COMMENT 'warehouse/company/department',target_id BIGINT NOT NULL COMMENT '仓库或组织ID',sort_no INT NOT NULL COMMENT '顺序',
 PRIMARY KEY(tenant_id,grant_id,target_kind,target_id),INDEX ix_role_menu_target(tenant_id,target_kind,target_id),FOREIGN KEY(grant_id) REFERENCES eam_sys_role_menu_permission(id) ON DELETE CASCADE
) COMMENT='角色菜单授权的具体仓库和组织';
INSERT INTO eam_role_menu_target SELECT DISTINCT p.tenant_id,p.id,CASE WHEN JSON_CONTAINS(p.data_scopes_json,JSON_QUOTE('warehouse')) THEN 'warehouse' WHEN JSON_CONTAINS(p.data_scopes_json,JSON_QUOTE('companies')) THEN 'company' ELSE 'department' END,j.target_id,MIN(j.sort_no) FROM eam_sys_role_menu_permission p JOIN JSON_TABLE(COALESCE(p.org_ids_json,JSON_ARRAY()),'$[*]' COLUMNS(sort_no FOR ORDINALITY,target_id BIGINT PATH '$')) j GROUP BY p.tenant_id,p.id,j.target_id;
ALTER TABLE eam_sys_role DROP COLUMN org_ids_json,DROP COLUMN permissions_json;
ALTER TABLE eam_sys_role_menu_permission DROP COLUMN actions_json,DROP COLUMN data_scopes_json,DROP COLUMN org_ids_json;
