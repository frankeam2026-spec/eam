-- Safe to rerun after a failed migration; preserve existing workflow data.
SET @workflow_columns_exist = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_repair_order' AND column_name='dispatcher_user_id');
SET @workflow_alter = IF(@workflow_columns_exist=0, 'ALTER TABLE eam_repair_order ADD dispatcher_user_id BIGINT NULL, ADD fault_cause TEXT NULL, ADD repair_method VARCHAR(20) NOT NULL DEFAULT \'自修\', ADD processing_result VARCHAR(100) NULL, ADD preventive_measures TEXT NULL, ADD collaborators_json JSON NULL, ADD process_attachments_json JSON NULL, ADD outsource_vendor VARCHAR(200) NULL', 'SELECT 1');
PREPARE workflow_statement FROM @workflow_alter;
EXECUTE workflow_statement;
DEALLOCATE PREPARE workflow_statement;
CREATE TABLE IF NOT EXISTS eam_repair_candidate (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, order_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT '待接单', created_at DATETIME NOT NULL,
 UNIQUE KEY uk_candidate (tenant_id,order_id,user_id), KEY idx_candidate_user (tenant_id,user_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS eam_repair_event (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, order_id BIGINT NOT NULL,
 action VARCHAR(30) NOT NULL, actor_id BIGINT NOT NULL, actor_name VARCHAR(100) NOT NULL,
 details_json JSON NOT NULL, created_at DATETIME NOT NULL,
 KEY idx_event_order (tenant_id,order_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS eam_repair_inbox (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL, order_id BIGINT NOT NULL,
 kind VARCHAR(20) NOT NULL, title VARCHAR(250) NOT NULL, is_todo TINYINT NOT NULL,
 done TINYINT NOT NULL DEFAULT 0, read_at DATETIME NULL, created_at DATETIME NOT NULL,
 KEY idx_inbox_user (tenant_id,user_id,is_todo,done,created_at), KEY idx_inbox_order (tenant_id,order_id,done)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS eam_spare_stock (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, company_id BIGINT NOT NULL,
 code VARCHAR(64) NOT NULL, name VARCHAR(100) NOT NULL, model VARCHAR(200) NOT NULL DEFAULT '',
 quantity DECIMAL(14,3) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_stock_code (tenant_id,company_id,code), CHECK (quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS eam_repair_material (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, order_id BIGINT NOT NULL, stock_id BIGINT NOT NULL,
 applicant_id BIGINT NOT NULL, quantity DECIMAL(14,3) NOT NULL,
 issued_quantity DECIMAL(14,3) NOT NULL DEFAULT 0, installed_quantity DECIMAL(14,3) NOT NULL DEFAULT 0,
 status VARCHAR(20) NOT NULL DEFAULT '待出库', created_at DATETIME NOT NULL, issued_at DATETIME NULL,
 issuer_id BIGINT NULL, CHECK (quantity > 0), CHECK (installed_quantity <= issued_quantity),
 KEY idx_material_order (tenant_id,order_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS eam_equipment_spare (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, equipment_id BIGINT NOT NULL, stock_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL, quantity DECIMAL(14,3) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT '在用',
 installed_at DATETIME NOT NULL, removed_at DATETIME NULL,
 KEY idx_equipment_spare (tenant_id,equipment_id,status), CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Upgrade existing active orders without changing their accepted owner.
INSERT INTO eam_repair_candidate (id,tenant_id,order_id,user_id,status,created_at)
 SELECT id,tenant_id,id,assignee_user_id,'已接单',NOW() FROM eam_repair_order
 WHERE deleted=0 AND assignee_user_id IS NOT NULL AND status IN ('处理中','挂起','待验收')
 AND NOT EXISTS(SELECT 1 FROM eam_repair_candidate c WHERE c.tenant_id=eam_repair_order.tenant_id AND c.order_id=eam_repair_order.id AND c.user_id=eam_repair_order.assignee_user_id);

-- Local demo administrator retains access to the newly implemented warehouse operations.
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json)
SELECT UUID_SHORT(),10001,72001,'inventory',JSON_ARRAY('查看','新增','编辑','入库','出库','导入','导出'),JSON_ARRAY('all'),JSON_ARRAY()
WHERE EXISTS(SELECT 1 FROM eam_sys_role WHERE id=72001 AND tenant_id=10001)
AND NOT EXISTS(SELECT 1 FROM eam_sys_role_menu_permission WHERE tenant_id=10001 AND role_id=72001 AND menu_key='inventory');
