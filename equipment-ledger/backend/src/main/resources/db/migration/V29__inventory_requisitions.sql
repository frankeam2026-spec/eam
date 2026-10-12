CREATE TABLE inv_requisition (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL,
 company_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL,
 task_kind VARCHAR(20) NOT NULL, task_id BIGINT NOT NULL, task_code VARCHAR(64) NOT NULL,
 applicant_id BIGINT NOT NULL, applicant_name VARCHAR(100) NOT NULL,
 remark VARCHAR(1000) NOT NULL DEFAULT '', status VARCHAR(20) NOT NULL DEFAULT '草稿',
 row_version INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_requisition_code(tenant_id,code), KEY idx_requisition_task(tenant_id,task_kind,task_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inv_requisition_line (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, requisition_id BIGINT NOT NULL,
 line_no INT NOT NULL, material_id BIGINT NOT NULL, quantity DECIMAL(20,6) NOT NULL,
 issued_quantity DECIMAL(20,6) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_requisition_line(tenant_id,requisition_id,line_no),
 CHECK(quantity>0), CHECK(issued_quantity>=0 AND issued_quantity<=quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inv_reservation (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, requisition_id BIGINT NOT NULL,
 requisition_line_id BIGINT NOT NULL, balance_id BIGINT NOT NULL,
 quantity DECIMAL(20,6) NOT NULL, remaining_quantity DECIMAL(20,6) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_reservation_request(tenant_id,requisition_id,requisition_line_id),
 KEY idx_reservation_balance(tenant_id,balance_id),
 CHECK(quantity>0), CHECK(remaining_quantity>=0 AND remaining_quantity<=quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
