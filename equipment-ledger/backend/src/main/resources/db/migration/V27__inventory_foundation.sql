CREATE TABLE inv_material (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL,
 name VARCHAR(120) NOT NULL, model VARCHAR(200) NOT NULL DEFAULT '', unit VARCHAR(40) NOT NULL,
 category VARCHAR(120) NOT NULL DEFAULT '', usage_type VARCHAR(20) NOT NULL DEFAULT '消耗型',
 reference_price DECIMAL(20,6) NULL, batch_enabled TINYINT NOT NULL DEFAULT 0,
 expiry_enabled TINYINT NOT NULL DEFAULT 0, allocation_rule VARCHAR(10) NOT NULL DEFAULT '',
 enabled TINYINT NOT NULL DEFAULT 1, remark VARCHAR(1000) NOT NULL DEFAULT '',
 row_version INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_material_code(tenant_id,code), CHECK(reference_price IS NULL OR reference_price>=0),
 CHECK(expiry_enabled=0 OR batch_enabled=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inv_warehouse (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, company_id BIGINT NOT NULL,
 code VARCHAR(64) NOT NULL, name VARCHAR(120) NOT NULL, manager_id BIGINT NULL,
 allocation_rule VARCHAR(10) NOT NULL DEFAULT 'FIFO', enabled TINYINT NOT NULL DEFAULT 1,
 remark VARCHAR(1000) NOT NULL DEFAULT '', row_version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_warehouse_code(tenant_id,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inv_bin (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL,
 code VARCHAR(64) NOT NULL, name VARCHAR(120) NOT NULL, enabled TINYINT NOT NULL DEFAULT 1,
 remark VARCHAR(1000) NOT NULL DEFAULT '', row_version INT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_bin_code(tenant_id,warehouse_id,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inv_batch (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, material_id BIGINT NOT NULL,
 code VARCHAR(64) NOT NULL, supplier_batch VARCHAR(120) NOT NULL DEFAULT '',
 production_date DATE NULL, expiry_date DATE NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_batch_code(tenant_id,code), KEY idx_batch_material(tenant_id,material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inv_balance (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, company_id BIGINT NOT NULL,
 warehouse_id BIGINT NOT NULL, bin_id BIGINT NOT NULL, material_id BIGINT NOT NULL,
 batch_id BIGINT NOT NULL DEFAULT 0, stock_status VARCHAR(20) NOT NULL DEFAULT '可用',
 quantity DECIMAL(20,6) NOT NULL DEFAULT 0, reserved_quantity DECIMAL(20,6) NOT NULL DEFAULT 0,
 row_version INT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_balance(tenant_id,warehouse_id,bin_id,material_id,batch_id,stock_status),
 CHECK(quantity>=0), CHECK(reserved_quantity>=0 AND reserved_quantity<=quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inv_cost_layer (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, balance_id BIGINT NOT NULL,
 source_line_id BIGINT NOT NULL, origin_layer_id BIGINT NULL, origin_received_at DATETIME(3) NOT NULL,
 quantity DECIMAL(20,6) NOT NULL, remaining_quantity DECIMAL(20,6) NOT NULL,
 unit_cost DECIMAL(20,6) NULL, remaining_cost DECIMAL(20,6) NULL,
 currency VARCHAR(3) NOT NULL DEFAULT 'CNY', cost_status VARCHAR(20) NOT NULL DEFAULT '已确定',
 KEY idx_layer_balance(tenant_id,balance_id,origin_received_at,id),
 CHECK(quantity>0), CHECK(remaining_quantity>=0 AND remaining_quantity<=quantity),
 CHECK(unit_cost IS NULL OR unit_cost>=0), CHECK(remaining_cost IS NULL OR remaining_cost>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inv_legacy_stock_map (
 tenant_id BIGINT NOT NULL, stock_id BIGINT NOT NULL, material_id BIGINT NULL,
 warehouse_id BIGINT NULL, opening_line_id BIGINT NULL,
 migration_status VARCHAR(20) NOT NULL DEFAULT '待初始化', PRIMARY KEY(tenant_id,stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO inv_legacy_stock_map(tenant_id,stock_id) SELECT tenant_id,id FROM eam_spare_stock;
CREATE TABLE inv_master_event (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, resource_kind VARCHAR(20) NOT NULL,
 resource_id BIGINT NOT NULL, action VARCHAR(20) NOT NULL, actor_id BIGINT NOT NULL,
 actor_name VARCHAR(100) NOT NULL, details_json JSON NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_master_event(tenant_id,resource_kind,resource_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
