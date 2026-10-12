CREATE TABLE inv_issue (
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  requisition_id BIGINT NOT NULL,
  company_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  confirmation_key VARCHAR(64) NOT NULL,
  preview_fingerprint VARCHAR(64) NOT NULL,
  total_amount DECIMAL(20,2) NOT NULL,
  actor_id BIGINT NOT NULL,
  actor_name VARCHAR(100) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_issue_code (tenant_id,code),
  UNIQUE KEY uq_issue_confirmation (tenant_id,requisition_id,confirmation_key)
);
CREATE TABLE inv_issue_allocation (
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  issue_id BIGINT NOT NULL,
  requisition_line_id BIGINT NOT NULL,
  reservation_id BIGINT NOT NULL,
  balance_id BIGINT NOT NULL,
  cost_layer_id BIGINT NOT NULL,
  quantity DECIMAL(20,6) NOT NULL,
  amount DECIMAL(20,2) NOT NULL,
  unit_cost DECIMAL(20,6) NOT NULL,
  CHECK (quantity>0),
  CHECK (amount>=0),
  KEY ix_issue_allocations (tenant_id,issue_id),
  KEY ix_issue_origin (tenant_id,cost_layer_id)
);
