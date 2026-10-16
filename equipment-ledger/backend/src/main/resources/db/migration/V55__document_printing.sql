CREATE TABLE eam_print_template (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, company_id BIGINT NULL,
 name VARCHAR(100) NOT NULL, document_type VARCHAR(32) NOT NULL DEFAULT 'outbound',
 config_json JSON NOT NULL, enabled TINYINT NOT NULL DEFAULT 1, is_default TINYINT NOT NULL DEFAULT 0,
 row_version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0,
 default_scope BIGINT GENERATED ALWAYS AS (IF(is_default=1 AND enabled=1 AND deleted=0,COALESCE(company_id,0),NULL)) STORED,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_print_default(tenant_id,document_type,default_scope),
 KEY ix_print_template(tenant_id,deleted,company_id,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE eam_print_job (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, company_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL,
 document_type VARCHAR(32) NOT NULL DEFAULT 'outbound', document_id VARCHAR(64) NOT NULL,
 document_code VARCHAR(80) NOT NULL, document_status VARCHAR(24) NOT NULL,
 template_id BIGINT NULL, template_name VARCHAR(100) NOT NULL, template_version INT NOT NULL,
 template_snapshot JSON NOT NULL, document_snapshot JSON NOT NULL, contains_money TINYINT NOT NULL,
 actor_id BIGINT NOT NULL, actor_name VARCHAR(100) NOT NULL, request_key VARCHAR(64) NOT NULL,
 request_hash VARCHAR(64) NOT NULL, pdf_sha256 VARCHAR(64) NOT NULL, page_count INT NOT NULL,
 pdf_content LONGBLOB NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_print_request(tenant_id,actor_id,request_key),
 KEY ix_print_document(tenant_id,document_type,document_id,created_at),
 KEY ix_print_scope(tenant_id,company_id,warehouse_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Grant the existing demo administrator; other roles are explicitly configured by administrators.
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json)
SELECT COALESCE((SELECT MAX(p.id) FROM eam_sys_role_menu_permission p),0)+1,r.tenant_id,r.id,
 'printTemplate',JSON_ARRAY('查看','新增','编辑','删除'),JSON_ARRAY('all'),JSON_ARRAY()
FROM eam_sys_role r WHERE r.id=72001 AND r.tenant_id=10001 AND r.deleted=0
AND NOT EXISTS(SELECT 1 FROM eam_sys_role_menu_permission p WHERE p.tenant_id=r.tenant_id AND p.role_id=r.id AND p.menu_key='printTemplate');
UPDATE eam_sys_role_menu_permission SET actions_json=JSON_ARRAY_APPEND(actions_json,'$','打印')
WHERE tenant_id=10001 AND role_id=72001 AND menu_key='inventoryOutbound'
AND NOT JSON_CONTAINS(actions_json,JSON_QUOTE('打印'));
