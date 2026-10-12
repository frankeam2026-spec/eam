-- Split parent grants once. Existing child grants take precedence; subsequent edits never
-- fall back to the old parent grant, so revoking one child menu remains effective.
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json)
SELECT (SELECT COALESCE(MAX(id),0) FROM eam_sys_role_menu_permission base)+ROW_NUMBER() OVER (ORDER BY tenant_id,role_id,menu_key), tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json
FROM (
SELECT p.tenant_id,p.role_id,'maintenanceType' menu_key,
 (SELECT JSON_ARRAYAGG(a.action_name) FROM JSON_TABLE(p.actions_json,'$[*]' COLUMNS(action_name VARCHAR(30) PATH '$')) a WHERE a.action_name IN ('查看','新增','编辑','删除','导出')) actions_json,
 JSON_ARRAY('all') data_scopes_json,JSON_ARRAY() org_ids_json
 FROM eam_sys_role_menu_permission p WHERE p.menu_key='maintenance'
 AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission existing WHERE existing.tenant_id=p.tenant_id AND existing.role_id=p.role_id AND existing.menu_key='maintenanceType')
UNION ALL
SELECT p.tenant_id,p.role_id,'maintenanceStandard' menu_key,
 (SELECT JSON_ARRAYAGG(a.action_name) FROM JSON_TABLE(p.actions_json,'$[*]' COLUMNS(action_name VARCHAR(30) PATH '$')) a WHERE a.action_name IN ('查看','新增','编辑','删除','导出')) actions_json,
 p.data_scopes_json data_scopes_json,p.org_ids_json org_ids_json
 FROM eam_sys_role_menu_permission p WHERE p.menu_key='maintenance'
 AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission existing WHERE existing.tenant_id=p.tenant_id AND existing.role_id=p.role_id AND existing.menu_key='maintenanceStandard')
UNION ALL
SELECT p.tenant_id,p.role_id,'maintenancePlan' menu_key,
 (SELECT JSON_ARRAYAGG(a.action_name) FROM JSON_TABLE(p.actions_json,'$[*]' COLUMNS(action_name VARCHAR(30) PATH '$')) a WHERE a.action_name IN ('查看','新增','编辑','删除','导出')) actions_json,
 p.data_scopes_json data_scopes_json,p.org_ids_json org_ids_json
 FROM eam_sys_role_menu_permission p WHERE p.menu_key='maintenance'
 AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission existing WHERE existing.tenant_id=p.tenant_id AND existing.role_id=p.role_id AND existing.menu_key='maintenancePlan')
UNION ALL
SELECT p.tenant_id,p.role_id,'maintenanceTask' menu_key,
 (SELECT JSON_ARRAYAGG(a.action_name) FROM JSON_TABLE(p.actions_json,'$[*]' COLUMNS(action_name VARCHAR(30) PATH '$')) a WHERE a.action_name IN ('查看','编辑','执行','验收','删除','导出')) actions_json,
 p.data_scopes_json data_scopes_json,p.org_ids_json org_ids_json
 FROM eam_sys_role_menu_permission p WHERE p.menu_key='maintenance'
 AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission existing WHERE existing.tenant_id=p.tenant_id AND existing.role_id=p.role_id AND existing.menu_key='maintenanceTask')
UNION ALL
SELECT p.tenant_id,p.role_id,'inspectionType' menu_key,
 (SELECT JSON_ARRAYAGG(a.action_name) FROM JSON_TABLE(p.actions_json,'$[*]' COLUMNS(action_name VARCHAR(30) PATH '$')) a WHERE a.action_name IN ('查看','新增','编辑','删除','导出')) actions_json,
 JSON_ARRAY('all') data_scopes_json,JSON_ARRAY() org_ids_json
 FROM eam_sys_role_menu_permission p WHERE p.menu_key='inspection'
 AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission existing WHERE existing.tenant_id=p.tenant_id AND existing.role_id=p.role_id AND existing.menu_key='inspectionType')
UNION ALL
SELECT p.tenant_id,p.role_id,'inspectionStandard' menu_key,
 (SELECT JSON_ARRAYAGG(a.action_name) FROM JSON_TABLE(p.actions_json,'$[*]' COLUMNS(action_name VARCHAR(30) PATH '$')) a WHERE a.action_name IN ('查看','新增','编辑','删除','导出')) actions_json,
 p.data_scopes_json data_scopes_json,p.org_ids_json org_ids_json
 FROM eam_sys_role_menu_permission p WHERE p.menu_key='inspection'
 AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission existing WHERE existing.tenant_id=p.tenant_id AND existing.role_id=p.role_id AND existing.menu_key='inspectionStandard')
UNION ALL
SELECT p.tenant_id,p.role_id,'inspectionDailyPlan' menu_key,
 (SELECT JSON_ARRAYAGG(a.action_name) FROM JSON_TABLE(p.actions_json,'$[*]' COLUMNS(action_name VARCHAR(30) PATH '$')) a WHERE a.action_name IN ('查看','新增','编辑','删除','导出')) actions_json,
 p.data_scopes_json data_scopes_json,p.org_ids_json org_ids_json
 FROM eam_sys_role_menu_permission p WHERE p.menu_key='inspection'
 AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission existing WHERE existing.tenant_id=p.tenant_id AND existing.role_id=p.role_id AND existing.menu_key='inspectionDailyPlan')
UNION ALL
SELECT p.tenant_id,p.role_id,'inspectionPeriodicPlan' menu_key,
 (SELECT JSON_ARRAYAGG(a.action_name) FROM JSON_TABLE(p.actions_json,'$[*]' COLUMNS(action_name VARCHAR(30) PATH '$')) a WHERE a.action_name IN ('查看','新增','编辑','删除','导出')) actions_json,
 p.data_scopes_json data_scopes_json,p.org_ids_json org_ids_json
 FROM eam_sys_role_menu_permission p WHERE p.menu_key='inspection'
 AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission existing WHERE existing.tenant_id=p.tenant_id AND existing.role_id=p.role_id AND existing.menu_key='inspectionPeriodicPlan')
UNION ALL
SELECT p.tenant_id,p.role_id,'inspectionDailyTask' menu_key,
 (SELECT JSON_ARRAYAGG(a.action_name) FROM JSON_TABLE(p.actions_json,'$[*]' COLUMNS(action_name VARCHAR(30) PATH '$')) a WHERE a.action_name IN ('查看','执行','审核','转派','删除','导出')) actions_json,
 p.data_scopes_json data_scopes_json,p.org_ids_json org_ids_json
 FROM eam_sys_role_menu_permission p WHERE p.menu_key='inspection'
 AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission existing WHERE existing.tenant_id=p.tenant_id AND existing.role_id=p.role_id AND existing.menu_key='inspectionDailyTask')
UNION ALL
SELECT p.tenant_id,p.role_id,'inspectionPeriodicTask' menu_key,
 (SELECT JSON_ARRAYAGG(a.action_name) FROM JSON_TABLE(p.actions_json,'$[*]' COLUMNS(action_name VARCHAR(30) PATH '$')) a WHERE a.action_name IN ('查看','执行','审核','转派','删除','导出')) actions_json,
 p.data_scopes_json data_scopes_json,p.org_ids_json org_ids_json
 FROM eam_sys_role_menu_permission p WHERE p.menu_key='inspection'
 AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission existing WHERE existing.tenant_id=p.tenant_id AND existing.role_id=p.role_id AND existing.menu_key='inspectionPeriodicTask')
) split_grants WHERE actions_json IS NOT NULL;
DELETE FROM eam_sys_role_menu_permission WHERE menu_key IN ('maintenance','inspection');
