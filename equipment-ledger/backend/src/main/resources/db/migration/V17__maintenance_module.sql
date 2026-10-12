CREATE TABLE eam_maintenance_definition (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, kind VARCHAR(20) NOT NULL,
 code VARCHAR(64) NOT NULL, name VARCHAR(150) NOT NULL, department_id BIGINT NULL,
 creator_id BIGINT NOT NULL, payload JSON NOT NULL, row_version INT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_maintenance_code(tenant_id,kind,code), KEY idx_maintenance_definition(tenant_id,kind,department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_maintenance_schedule (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, plan_id BIGINT NOT NULL,
 equipment_id BIGINT NOT NULL, type_id BIGINT NOT NULL, standard_id BIGINT NOT NULL,
 next_date DATE NOT NULL, anchor_date DATE NOT NULL, cycle_index INT NOT NULL DEFAULT 0,
 payload JSON NOT NULL, enabled TINYINT NOT NULL DEFAULT 1,
 KEY idx_maintenance_due(enabled,next_date), KEY idx_maintenance_plan(tenant_id,plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_maintenance_task (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL,
 plan_id BIGINT NOT NULL, schedule_id BIGINT NOT NULL, equipment_id BIGINT NOT NULL,
 company_id BIGINT NULL, department_id BIGINT NULL, due_date DATE NOT NULL, expires_date DATE NULL,
 owner_id BIGINT NULL, status VARCHAR(20) NOT NULL DEFAULT '待执行',
 snapshot JSON NOT NULL, result JSON NULL, row_version INT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_maintenance_occurrence(tenant_id,schedule_id,due_date), UNIQUE KEY uk_maintenance_task_code(tenant_id,code),
 KEY idx_maintenance_task(tenant_id,status,department_id,owner_id,due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_maintenance_event (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, task_id BIGINT NOT NULL,
 actor_id BIGINT NULL, actor_name VARCHAR(100) NOT NULL, action VARCHAR(30) NOT NULL,
 details JSON NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_maintenance_event(tenant_id,task_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_maintenance_inbox (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, task_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 kind VARCHAR(30) NOT NULL, title VARCHAR(250) NOT NULL, is_todo TINYINT NOT NULL DEFAULT 1,
 done TINYINT NOT NULL DEFAULT 0, read_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_maintenance_inbox(tenant_id,user_id,done,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json)
SELECT UUID_SHORT(),10001,72001,'maintenance',JSON_ARRAY('查看','新增','编辑','执行','验收','删除','导出'),JSON_ARRAY('all'),JSON_ARRAY()
WHERE EXISTS(SELECT 1 FROM eam_sys_role WHERE id=72001 AND tenant_id=10001)
AND NOT EXISTS(SELECT 1 FROM eam_sys_role_menu_permission WHERE tenant_id=10001 AND role_id=72001 AND menu_key='maintenance');
INSERT INTO eam_maintenance_definition(id,tenant_id,kind,code,name,creator_id,payload)
SELECT UUID_SHORT(),10001,'types',x.code,x.name,73001,JSON_OBJECT('period',x.period,'unit',x.unit,'symbol',x.symbol,'system',true,'enabled',true,'remark','')
FROM (SELECT 'MT-W' code,'周' name,1 period,'周' unit,'▽' symbol UNION ALL SELECT 'MT-15D','半月',15,'日','◀' UNION ALL SELECT 'MT-M','月',1,'月','△' UNION ALL SELECT 'MT-Q','季',3,'月','◁' UNION ALL SELECT 'MT-H','半年',6,'月','▲' UNION ALL SELECT 'MT-Y','年',12,'月','▷') x;
CREATE TABLE eam_maintenance_file (
 id VARCHAR(36) PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 name VARCHAR(255) NOT NULL, extension VARCHAR(12) NOT NULL, content_type VARCHAR(120) NOT NULL,
 size BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
