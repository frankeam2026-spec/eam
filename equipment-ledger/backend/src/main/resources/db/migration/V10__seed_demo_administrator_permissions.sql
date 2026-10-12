INSERT INTO eam_sys_role_menu_permission (id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json)
SELECT seed.id,r.tenant_id,r.id,seed.menu_key,seed.actions_json,JSON_ARRAY('all'),JSON_ARRAY()
FROM eam_sys_role r
CROSS JOIN (
  SELECT 74001 AS id,'workspace' AS menu_key,JSON_ARRAY('查看') AS actions_json
  UNION ALL SELECT 74002,'equipment',JSON_ARRAY('查看','新增','编辑','删除','导入','导出')
  UNION ALL SELECT 74003,'category',JSON_ARRAY('查看','新增','编辑','删除')
  UNION ALL SELECT 74004,'repair',JSON_ARRAY('查看','报修','编辑','派工','处理','验收','撤回','删除','导出')
  UNION ALL SELECT 74005,'base',JSON_ARRAY('查看','新增','编辑','删除','导入','导出')
  UNION ALL SELECT 74006,'org',JSON_ARRAY('查看','新增','编辑','删除')
  UNION ALL SELECT 74007,'user',JSON_ARRAY('查看','新增','编辑','停用','分配角色','删除','导入','导出')
  UNION ALL SELECT 74008,'role',JSON_ARRAY('查看','新增','编辑','授权','删除','导出')
) seed
WHERE r.id=72001 AND r.tenant_id=10001
  AND NOT EXISTS (SELECT 1 FROM eam_sys_role_menu_permission p WHERE p.role_id=r.id);
