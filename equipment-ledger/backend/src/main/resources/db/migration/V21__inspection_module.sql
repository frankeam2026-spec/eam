CREATE TABLE eam_inspection_definition (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, kind VARCHAR(24) NOT NULL,
 code VARCHAR(64) NOT NULL, name VARCHAR(150) NOT NULL, department_id BIGINT NULL,
 creator_id BIGINT NOT NULL, payload JSON NOT NULL, row_version INT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_inspection_definition(tenant_id,kind,code), KEY idx_inspection_definition(tenant_id,kind,department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_inspection_schedule (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, plan_id BIGINT NOT NULL,
 next_at DATETIME NOT NULL, anchor_at DATETIME NOT NULL, cycle_index INT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_inspection_schedule(tenant_id,plan_id), KEY idx_inspection_due(next_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_inspection_task (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, kind VARCHAR(16) NOT NULL, code VARCHAR(64) NOT NULL,
 plan_id BIGINT NOT NULL, equipment_id BIGINT NOT NULL DEFAULT 0, shift_id VARCHAR(64) NOT NULL DEFAULT '',
 company_id BIGINT NULL, department_id BIGINT NULL, owner_id BIGINT NULL,
 due_at DATETIME NOT NULL, expires_at DATETIME NOT NULL, status VARCHAR(24) NOT NULL,
 snapshot JSON NOT NULL, result JSON NULL, row_version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_inspection_occurrence(tenant_id,plan_id,equipment_id,shift_id,due_at),
 UNIQUE KEY uk_inspection_code(tenant_id,code), KEY idx_inspection_task(tenant_id,kind,status,department_id,owner_id,due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_inspection_event (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, task_id BIGINT NOT NULL,
 actor_id BIGINT NULL, actor_name VARCHAR(100) NOT NULL, action VARCHAR(30) NOT NULL,
 details JSON NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_inspection_event(tenant_id,task_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_inspection_inbox (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, task_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 kind VARCHAR(30) NOT NULL, title VARCHAR(250) NOT NULL, is_todo TINYINT NOT NULL DEFAULT 1,
 done TINYINT NOT NULL DEFAULT 0, read_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_inspection_inbox(tenant_id,user_id,done,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_inspection_file (
 id VARCHAR(36) PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 name VARCHAR(255) NOT NULL, extension VARCHAR(12) NOT NULL, content_type VARCHAR(120) NOT NULL,
 size BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json)
SELECT UUID_SHORT(),tenant_id,id,'inspection',JSON_ARRAY('查看','新增','编辑','执行','审核','转派','删除','导出'),JSON_ARRAY('all'),JSON_ARRAY()
FROM eam_sys_role r WHERE r.id=72001 AND r.tenant_id=10001
AND NOT EXISTS(SELECT 1 FROM eam_sys_role_menu_permission p WHERE p.tenant_id=r.tenant_id AND p.role_id=r.id AND p.menu_key='inspection');
INSERT INTO eam_inspection_definition(id,tenant_id,kind,code,name,creator_id,payload)
SELECT UUID_SHORT(),10001,'types',x.code,x.name,73001,JSON_OBJECT('period',x.period,'unit',x.unit,'enabled',true)
FROM (SELECT 'IT-D' code,'日' name,1 period,'日' unit UNION ALL SELECT 'IT-W','周',1,'周' UNION ALL SELECT 'IT-M','月',1,'月' UNION ALL SELECT 'IT-Q','季',3,'月' UNION ALL SELECT 'IT-Y','年',1,'年') x;
