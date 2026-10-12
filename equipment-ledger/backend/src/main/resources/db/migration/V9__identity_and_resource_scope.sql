ALTER TABLE eam_sys_user ADD COLUMN password_hash VARCHAR(100) NULL;

CREATE TABLE eam_auth_session (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_auth_token_hash (token_hash),
  KEY idx_auth_user (tenant_id, user_id),
  KEY idx_auth_expiry (expires_at),
  CONSTRAINT fk_auth_user FOREIGN KEY (user_id) REFERENCES eam_sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE eam_base_item ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 10001;
ALTER TABLE eam_base_item DROP INDEX uk_base_type_code;
ALTER TABLE eam_base_item ADD UNIQUE KEY uk_base_tenant_type_code (tenant_id, item_type, code, deleted);
ALTER TABLE eam_base_item ADD KEY idx_base_tenant_type (tenant_id, item_type, status, deleted);

ALTER TABLE eam_equipment
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 10001,
  ADD COLUMN company_org_id BIGINT NULL,
  ADD COLUMN department_org_id BIGINT NULL,
  ADD COLUMN owner_user_id BIGINT NULL;
ALTER TABLE eam_equipment DROP INDEX uk_equipment_code;
ALTER TABLE eam_equipment ADD UNIQUE KEY uk_equipment_tenant_code (tenant_id, equipment_code, deleted);
ALTER TABLE eam_equipment ADD KEY idx_equipment_access (tenant_id, company_org_id, department_org_id, owner_user_id, deleted);
UPDATE eam_equipment e
SET company_org_id = (SELECT o.id FROM eam_org_unit o WHERE o.tenant_id=e.tenant_id AND o.org_type='公司' AND o.name=e.company AND o.deleted=0 LIMIT 1),
    department_org_id = (SELECT o.id FROM eam_org_unit o WHERE o.tenant_id=e.tenant_id AND o.org_type='部门' AND o.name=e.department AND o.deleted=0 LIMIT 1),
    owner_user_id = (SELECT u.id FROM eam_sys_user u WHERE u.tenant_id=e.tenant_id AND u.display_name=e.owner_name AND u.deleted=0 LIMIT 1);

ALTER TABLE eam_repair_order
  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 10001,
  ADD COLUMN department_org_id BIGINT NULL,
  ADD COLUMN reporter_user_id BIGINT NULL,
  ADD COLUMN assignee_user_id BIGINT NULL;
ALTER TABLE eam_repair_order DROP INDEX uk_repair_order_no;
ALTER TABLE eam_repair_order ADD UNIQUE KEY uk_repair_tenant_no (tenant_id, order_no, deleted);
ALTER TABLE eam_repair_order ADD KEY idx_repair_access (tenant_id, department_org_id, reporter_user_id, assignee_user_id, deleted);
UPDATE eam_repair_order r
SET department_org_id = (SELECT o.id FROM eam_org_unit o WHERE o.tenant_id=r.tenant_id AND o.org_type='部门' AND o.name=r.department AND o.deleted=0 LIMIT 1),
    reporter_user_id = (SELECT u.id FROM eam_sys_user u WHERE u.tenant_id=r.tenant_id AND u.display_name=r.reporter AND u.deleted=0 LIMIT 1),
    assignee_user_id = (SELECT u.id FROM eam_sys_user u WHERE u.tenant_id=r.tenant_id AND u.display_name=r.assignee AND u.deleted=0 LIMIT 1);
