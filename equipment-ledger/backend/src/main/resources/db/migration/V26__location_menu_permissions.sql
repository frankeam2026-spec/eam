-- Locations are a separate existing submenu; retain its previous base-data actions.
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json)
SELECT (SELECT COALESCE(MAX(id),0) FROM eam_sys_role_menu_permission base)+ROW_NUMBER() OVER (ORDER BY p.tenant_id,p.role_id),p.tenant_id,p.role_id,'location',
(SELECT JSON_ARRAYAGG(a.action_name) FROM JSON_TABLE(p.actions_json,'$[*]' COLUMNS(action_name VARCHAR(30) PATH '$')) a WHERE a.action_name IN ('查看','新增','编辑','删除','导出')),JSON_ARRAY('all'),JSON_ARRAY()
FROM eam_sys_role_menu_permission p WHERE p.menu_key='base' AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission existing WHERE existing.tenant_id=p.tenant_id AND existing.role_id=p.role_id AND existing.menu_key='location');
