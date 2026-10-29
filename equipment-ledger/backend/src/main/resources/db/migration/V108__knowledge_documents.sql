-- Knowledge source of truth. Vector stores are rebuildable indexes only.
CREATE TABLE eam_knowledge_document (
 id BIGINT PRIMARY KEY COMMENT '文档ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 company_id BIGINT NOT NULL COMMENT '所属公司ID', title VARCHAR(200) NOT NULL COMMENT '文档名称',
 document_type VARCHAR(24) NOT NULL COMMENT 'MANUAL/SOP/CASE',
 status VARCHAR(24) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/DISABLED',
 published_version_id BIGINT NULL COMMENT '当前发布版本ID，发布事务校验同文档',
 created_by BIGINT NOT NULL COMMENT '创建人ID', row_version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间',
 UNIQUE KEY uk_kdoc_tenant (tenant_id,id),
 KEY ix_kdoc_list (tenant_id,company_id,status,updated_at,id)
) COMMENT='知识文档';
CREATE TABLE eam_knowledge_version (
 id BIGINT PRIMARY KEY COMMENT '版本ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 document_id BIGINT NOT NULL COMMENT '文档ID', version_no INT NOT NULL COMMENT '文档内版本号',
 file_key VARCHAR(128) NOT NULL COMMENT '受控原文件键', sha256 CHAR(64) NOT NULL COMMENT '文件摘要',
 parse_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/READY/NEEDS_OCR/FAILED',
 parser_version VARCHAR(80) NOT NULL COMMENT '解析器版本', error_message VARCHAR(1000) NULL COMMENT '解析错误',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '建立时间',
 UNIQUE KEY uk_kver_tenant (tenant_id,id), UNIQUE KEY uk_kver_number (tenant_id,document_id,version_no),
 FOREIGN KEY (tenant_id,document_id) REFERENCES eam_knowledge_document(tenant_id,id)
) COMMENT='知识文档不可变内容版本';
CREATE TABLE eam_knowledge_page (
 id BIGINT PRIMARY KEY COMMENT '页面ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 version_id BIGINT NOT NULL COMMENT '版本ID', page_no INT NOT NULL COMMENT '原PDF页码，从1开始',
 text_content MEDIUMTEXT NOT NULL COMMENT '页面提取正文', needs_ocr BOOLEAN NOT NULL COMMENT '是否需要OCR',
 UNIQUE KEY uk_kpage_tenant (tenant_id,id), UNIQUE KEY uk_kpage_number (tenant_id,version_id,page_no),
 FOREIGN KEY (tenant_id,version_id) REFERENCES eam_knowledge_version(tenant_id,id)
) COMMENT='知识文档页面';
CREATE TABLE eam_knowledge_chunk (
 id BIGINT PRIMARY KEY COMMENT '切片ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 page_id BIGINT NOT NULL COMMENT '来源页面ID', sequence_no INT NOT NULL COMMENT '页内切片序号',
 text_content TEXT NOT NULL COMMENT '切片正文', content_hash CHAR(64) NOT NULL COMMENT '正文摘要',
 UNIQUE KEY uk_kchunk_tenant (tenant_id,id), UNIQUE KEY uk_kchunk_sequence (tenant_id,page_id,sequence_no),
 FOREIGN KEY (tenant_id,page_id) REFERENCES eam_knowledge_page(tenant_id,id)
) COMMENT='知识正文切片';
CREATE TABLE eam_knowledge_image (
 id BIGINT PRIMARY KEY COMMENT '图片ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 page_id BIGINT NOT NULL COMMENT '来源页面ID', file_key VARCHAR(128) NOT NULL COMMENT '图片文件键',
 x_pt DECIMAL(12,3) NOT NULL COMMENT '左上X坐标pt', y_pt DECIMAL(12,3) NOT NULL COMMENT '左上Y坐标pt',
 width_pt DECIMAL(12,3) NOT NULL COMMENT '宽度pt', height_pt DECIMAL(12,3) NOT NULL COMMENT '高度pt',
 caption TEXT NULL COMMENT '来源图注，未确认留空', UNIQUE KEY uk_kimage_tenant (tenant_id,id),
 KEY ix_kimage_page (tenant_id,page_id),
 FOREIGN KEY (tenant_id,page_id) REFERENCES eam_knowledge_page(tenant_id,id)
) COMMENT='知识原文图片';
CREATE TABLE eam_knowledge_chunk_image (
 tenant_id BIGINT NOT NULL COMMENT '租户ID', chunk_id BIGINT NOT NULL COMMENT '切片ID',
 image_id BIGINT NOT NULL COMMENT '图片ID', PRIMARY KEY (tenant_id,chunk_id,image_id),
 FOREIGN KEY (tenant_id,chunk_id) REFERENCES eam_knowledge_chunk(tenant_id,id),
 FOREIGN KEY (tenant_id,image_id) REFERENCES eam_knowledge_image(tenant_id,id)
) COMMENT='切片原图关联';
CREATE TABLE eam_knowledge_equipment (
 tenant_id BIGINT NOT NULL COMMENT '租户ID', document_id BIGINT NOT NULL COMMENT '文档ID',
 equipment_id BIGINT NOT NULL COMMENT '适用设备ID，保存时复核权限与有效性',
 PRIMARY KEY (tenant_id,document_id,equipment_id), KEY ix_kequipment (tenant_id,equipment_id,document_id),
 FOREIGN KEY (tenant_id,document_id) REFERENCES eam_knowledge_document(tenant_id,id)
) COMMENT='知识文档适用设备';
CREATE TABLE eam_knowledge_index_job (
 id BIGINT PRIMARY KEY COMMENT '索引任务ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 version_id BIGINT NOT NULL COMMENT '文档版本ID', embedding_model VARCHAR(128) NOT NULL COMMENT '向量模型及版本',
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/READY/FAILED',
 attempts INT NOT NULL DEFAULT 0 COMMENT '执行次数', next_attempt_at DATETIME(6) NOT NULL COMMENT '下次执行时间',
 lease_until DATETIME(6) NULL COMMENT '任务租约到期时间', error_message VARCHAR(1000) NULL COMMENT '错误摘要',
 UNIQUE KEY uk_kindex_version (tenant_id,version_id,embedding_model),
 KEY ix_kindex_poll (status,next_attempt_at,lease_until,id),
 FOREIGN KEY (tenant_id,version_id) REFERENCES eam_knowledge_version(tenant_id,id)
) COMMENT='可重试知识向量索引任务';
