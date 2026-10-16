CREATE TABLE eam_home_template (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,name VARCHAR(100) NOT NULL,
 preset_key VARCHAR(30) NULL,draft JSON NOT NULL,role_ids JSON NOT NULL,priority INT NOT NULL DEFAULT 100,
 published_version INT NOT NULL DEFAULT 0,enabled TINYINT NOT NULL DEFAULT 1,
 row_version INT NOT NULL DEFAULT 0,created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_home_preset(tenant_id,preset_key),KEY ix_home_template(tenant_id,enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_home_template_version (
 tenant_id BIGINT NOT NULL,template_id BIGINT NOT NULL,version INT NOT NULL,
 payload JSON NOT NULL,role_ids JSON NOT NULL,priority INT NOT NULL,
 published_by BIGINT NOT NULL,published_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(tenant_id,template_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_home_layout (
 tenant_id BIGINT NOT NULL,user_id BIGINT NOT NULL,template_id BIGINT NOT NULL,
 template_version INT NOT NULL,payload JSON NOT NULL,row_version INT NOT NULL DEFAULT 0,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(tenant_id,user_id,template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_home_preference (
 tenant_id BIGINT NOT NULL,user_id BIGINT NOT NULL,default_template_id BIGINT NULL,
 recent_json JSON NULL,PRIMARY KEY(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_equipment_anomaly (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,code VARCHAR(64) NOT NULL,equipment_id BIGINT NOT NULL,
 source_kind VARCHAR(20) NOT NULL DEFAULT '人工',source_task_id BIGINT NULL,source_item_id VARCHAR(100) NULL,
 description VARCHAR(4000) NOT NULL,severity VARCHAR(40) NOT NULL DEFAULT '',attachments JSON NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT '待处理',discovered_by BIGINT NOT NULL,discovered_name VARCHAR(100) NOT NULL,
 discovered_at DATETIME(3) NOT NULL,repair_id BIGINT NULL,plan_id BIGINT NULL,
 resolution VARCHAR(4000) NOT NULL DEFAULT '',resolved_at DATETIME(3) NULL,
 row_version INT NOT NULL DEFAULT 0,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_anomaly_code(tenant_id,code),
 UNIQUE KEY uq_anomaly_source(tenant_id,source_kind,source_task_id,equipment_id,source_item_id),
 KEY ix_anomaly_equipment(tenant_id,equipment_id,status),KEY ix_anomaly_repair(tenant_id,repair_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_single_repair_plan (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,code VARCHAR(64) NOT NULL,equipment_id BIGINT NOT NULL,
 description VARCHAR(4000) NOT NULL,planned_at DATETIME(3) NOT NULL,owner_id BIGINT NOT NULL,creator_id BIGINT NOT NULL,
 fault_type VARCHAR(150) NOT NULL,severity VARCHAR(40) NOT NULL,attachments JSON NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT '草稿',repair_id BIGINT NULL,row_version INT NOT NULL DEFAULT 0,
 generation_error VARCHAR(500) NOT NULL DEFAULT '',created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_repair_plan_code(tenant_id,code),KEY ix_repair_plan_due(status,planned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_experience_event (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,object_kind VARCHAR(20) NOT NULL,object_id BIGINT NOT NULL,
 action VARCHAR(60) NOT NULL,actor_id BIGINT NULL,actor_name VARCHAR(100) NOT NULL,details JSON NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),KEY ix_experience_event(tenant_id,object_kind,object_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inv_material_custody (
 tenant_id BIGINT NOT NULL,allocation_id BIGINT NOT NULL,holder_id BIGINT NOT NULL,holder_name VARCHAR(100) NOT NULL,
 remark VARCHAR(1000) NOT NULL,row_version INT NOT NULL DEFAULT 0,actor_id BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),PRIMARY KEY(tenant_id,allocation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- New capabilities are granted only to existing system-administrator roles. No business-role scope is broadened.
SET @experience_permission_id=(SELECT COALESCE(MAX(id),0) FROM eam_sys_role_menu_permission);
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json)
SELECT (@experience_permission_id:=@experience_permission_id+1),r.tenant_id,r.id,x.menu,x.actions,JSON_ARRAY('all'),JSON_ARRAY()
FROM eam_sys_role r JOIN (
 SELECT 'homeTemplate' menu,JSON_ARRAY('查看','新增','编辑','发布','停用') actions
 UNION ALL SELECT 'anomaly',JSON_ARRAY('查看','新增','消除','报修','安排维修','作废','导出')
 UNION ALL SELECT 'repairPlan',JSON_ARRAY('查看','新增','编辑','启用','生成工单','取消','导出')
 UNION ALL SELECT 'materialCustody',JSON_ARRAY('查看','登记暂存','导出')
) x WHERE r.code='SYS_ADMIN' AND r.status=1
AND NOT EXISTS(SELECT 1 FROM eam_sys_role_menu_permission p WHERE p.tenant_id=r.tenant_id AND p.role_id=r.id AND p.menu_key=x.menu);
UPDATE eam_sys_role_menu_permission p JOIN eam_sys_role r ON r.tenant_id=p.tenant_id AND r.id=p.role_id
SET p.actions_json=JSON_ARRAY_APPEND(p.actions_json,'$','团队工作查看')
WHERE p.menu_key='workspace' AND r.code='SYS_ADMIN' AND NOT JSON_CONTAINS(p.actions_json,JSON_QUOTE('团队工作查看'));
