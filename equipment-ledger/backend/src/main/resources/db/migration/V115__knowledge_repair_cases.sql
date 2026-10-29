ALTER TABLE eam_knowledge_version ADD has_original BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否存在原始PDF';
CREATE TABLE eam_knowledge_case_source (
 tenant_id BIGINT NOT NULL COMMENT '租户ID', document_id BIGINT NOT NULL COMMENT '知识文档ID',
 repair_order_id BIGINT NOT NULL COMMENT '已验收维修工单ID', repair_row_version BIGINT NOT NULL COMMENT '提取时工单版本',
 PRIMARY KEY(tenant_id,document_id), KEY ix_kcase_repair(tenant_id,repair_order_id),
 FOREIGN KEY(tenant_id,document_id) REFERENCES eam_knowledge_document(tenant_id,id)
) COMMENT='维修知识案例来源关系';
