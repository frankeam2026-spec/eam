ALTER TABLE eam_knowledge_version
 ADD created_by BIGINT NULL COMMENT '发起解析的用户ID',
 ADD parse_attempts INT NOT NULL DEFAULT 0 COMMENT '解析尝试次数',
 ADD parse_lease_until DATETIME(6) NULL COMMENT '解析任务租约';
UPDATE eam_knowledge_version v JOIN eam_knowledge_document d ON d.tenant_id=v.tenant_id AND d.id=v.document_id SET v.created_by=d.created_by WHERE v.created_by IS NULL;
ALTER TABLE eam_knowledge_version ADD KEY ix_kversion_parse(parse_status,parse_lease_until,id);
ALTER TABLE eam_knowledge_index_job ADD created_by BIGINT NULL COMMENT '索引请求用户ID',ADD document_row_version BIGINT NULL COMMENT '请求时文档版本';
