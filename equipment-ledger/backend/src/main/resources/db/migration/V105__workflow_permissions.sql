-- Bootstrap only the existing system administrator; company roles require explicit grants.
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key)
 SELECT -(10500000000000+p.tenant_id),p.tenant_id,p.role_id,'workflow'
 FROM eam_sys_role_menu_permission p WHERE p.menu_key='role' AND p.role_id=72001
 AND NOT EXISTS(SELECT 1 FROM eam_sys_role_menu_permission q WHERE q.tenant_id=p.tenant_id AND q.role_id=p.role_id AND q.menu_key='workflow');
INSERT IGNORE INTO eam_role_menu_action(tenant_id,grant_id,action_name,sort_no)
 SELECT p.tenant_id,p.id,x.action_name,x.seq FROM eam_sys_role_menu_permission p CROSS JOIN
 (SELECT 1 seq,'查看' action_name UNION ALL SELECT 2,'编辑' UNION ALL SELECT 3,'发布' UNION ALL SELECT 4,'停用') x
 WHERE p.role_id=72001 AND p.menu_key='workflow';
INSERT IGNORE INTO eam_role_menu_scope(tenant_id,grant_id,scope_code,sort_no)
 SELECT tenant_id,id,'all',0 FROM eam_sys_role_menu_permission WHERE role_id=72001 AND menu_key='workflow';
