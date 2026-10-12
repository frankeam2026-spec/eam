CREATE TABLE inv_operation (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, kind VARCHAR(20) NOT NULL,
 code VARCHAR(64) NOT NULL, company_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL,
 target_warehouse_id BIGINT NULL, status VARCHAR(20) NOT NULL DEFAULT '草稿',
 payload JSON NOT NULL, row_version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0,
 created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 confirmed_at DATETIME(3) NULL, UNIQUE KEY uq_operation_code(tenant_id,code),
 KEY ix_operation_list(tenant_id,kind,deleted,created_at)
);
CREATE TABLE inv_movement_allocation (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, document_id BIGINT NOT NULL,
 balance_id BIGINT NOT NULL, cost_layer_id BIGINT NOT NULL, target_bin_id BIGINT NULL,
 target_layer_id BIGINT NULL, quantity DECIMAL(20,6) NOT NULL, amount DECIMAL(20,2) NOT NULL,
 origin_received_at DATETIME(3) NOT NULL, unit_cost DECIMAL(20,6) NOT NULL,
 KEY ix_movement_document(tenant_id,document_id), CHECK(quantity<>0)
);
ALTER TABLE inv_issue_allocation ADD returned_quantity DECIMAL(20,6) NOT NULL DEFAULT 0,
 ADD returned_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
 ADD used_quantity DECIMAL(20,6) NOT NULL DEFAULT 0,
 ADD CHECK(returned_quantity>=0 AND used_quantity>=0 AND returned_quantity+used_quantity<=quantity);
CREATE TABLE inv_usage (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, allocation_id BIGINT NOT NULL,
 requisition_id BIGINT NOT NULL, equipment_id BIGINT NULL, material_id BIGINT NOT NULL,
 quantity DECIMAL(20,6) NOT NULL, kind VARCHAR(20) NOT NULL, destination VARCHAR(500) NOT NULL DEFAULT '',
 confirmation_key VARCHAR(64) NOT NULL,
 UNIQUE KEY uq_usage_confirmation(tenant_id,confirmation_key),
 status VARCHAR(20) NOT NULL DEFAULT '已使用', actor_name VARCHAR(100) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), removed_at DATETIME(3) NULL,
 KEY ix_usage_equipment(tenant_id,equipment_id,status), CHECK(quantity>0)
);
CREATE TABLE inv_inbox (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 document_id BIGINT NOT NULL, route_kind VARCHAR(30) NOT NULL, kind VARCHAR(30) NOT NULL,
 title VARCHAR(250) NOT NULL, is_todo TINYINT NOT NULL, done TINYINT NOT NULL DEFAULT 0,
 read_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY ix_inv_inbox(tenant_id,user_id,is_todo,done)
);
CREATE TABLE inv_tenant_lock (tenant_id BIGINT PRIMARY KEY);
ALTER TABLE inv_receipt ADD deleted TINYINT NOT NULL DEFAULT 0;
ALTER TABLE inv_requisition ADD deleted TINYINT NOT NULL DEFAULT 0;
ALTER TABLE inv_receipt_line ADD tax_rate DECIMAL(8,6) NOT NULL DEFAULT 0,
 ADD currency VARCHAR(3) NOT NULL DEFAULT 'CNY', ADD tax_amount DECIMAL(20,2) NULL,
 ADD gross_amount DECIMAL(20,2) NULL;

ALTER TABLE inv_issue ADD reversed TINYINT NOT NULL DEFAULT 0;
