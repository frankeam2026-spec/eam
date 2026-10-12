-- Actions include related document numbers (e.g. 确认出库 · CK-202610-0001).
ALTER TABLE inv_document_event MODIFY action VARCHAR(255) NOT NULL;
CREATE TABLE inv_issue_group (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,code VARCHAR(64) NOT NULL,
 warehouse_id BIGINT NOT NULL,confirmation_key VARCHAR(64) NOT NULL,request_ids JSON NOT NULL,
 actor_id BIGINT NOT NULL,actor_name VARCHAR(100) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_issue_group_key(tenant_id,confirmation_key),UNIQUE KEY uk_issue_group_code(tenant_id,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE inv_issue ADD group_id BIGINT NULL, ADD KEY idx_issue_group(tenant_id,group_id);
