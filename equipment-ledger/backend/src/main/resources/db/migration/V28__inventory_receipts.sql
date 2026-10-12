CREATE TABLE inv_receipt (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL,
 company_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL, supplier VARCHAR(120) NOT NULL DEFAULT '',
 remark VARCHAR(1000) NOT NULL DEFAULT '', status VARCHAR(20) NOT NULL DEFAULT '草稿',
 row_version INT NOT NULL DEFAULT 0, created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), confirmed_at DATETIME(3) NULL,
 UNIQUE KEY uk_receipt_code(tenant_id,code), KEY idx_receipt_warehouse(tenant_id,warehouse_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inv_receipt_line (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, receipt_id BIGINT NOT NULL, line_no INT NOT NULL,
 material_id BIGINT NOT NULL, bin_id BIGINT NOT NULL, quantity DECIMAL(20,6) NOT NULL,
 unit_price DECIMAL(20,6) NULL, allocated_fee DECIMAL(20,2) NOT NULL DEFAULT 0,
 batch_code VARCHAR(64) NOT NULL DEFAULT '', supplier_batch VARCHAR(120) NOT NULL DEFAULT '',
 production_date DATE NULL, expiry_date DATE NULL, batch_id BIGINT NOT NULL DEFAULT 0,
 total_cost DECIMAL(20,2) NULL,
 UNIQUE KEY uk_receipt_line(tenant_id,receipt_id,line_no),
 CHECK(quantity>0), CHECK(unit_price IS NULL OR unit_price>=0), CHECK(allocated_fee>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inv_transaction (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, company_id BIGINT NOT NULL,
 warehouse_id BIGINT NOT NULL, balance_id BIGINT NOT NULL, document_id BIGINT NOT NULL,
 source_line_id BIGINT NOT NULL, kind VARCHAR(20) NOT NULL, quantity DECIMAL(20,6) NOT NULL,
 amount DECIMAL(20,2) NOT NULL, actor_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_transaction_source(tenant_id,kind,source_line_id),
 KEY idx_transaction_balance(tenant_id,balance_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inv_document_event (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, document_kind VARCHAR(20) NOT NULL,
 document_id BIGINT NOT NULL, action VARCHAR(20) NOT NULL, actor_id BIGINT NOT NULL,
 actor_name VARCHAR(100) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_document_event(tenant_id,document_kind,document_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
