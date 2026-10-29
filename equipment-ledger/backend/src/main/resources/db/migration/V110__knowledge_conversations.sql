CREATE TABLE eam_ai_conversation (
 id BIGINT PRIMARY KEY COMMENT '会话ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 user_id BIGINT NOT NULL COMMENT '所属用户ID', title VARCHAR(120) NOT NULL COMMENT '会话标题',
 deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '用户删除标识',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '建立时间',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近交互时间',
 UNIQUE KEY uk_aiconv_tenant (tenant_id,id), KEY ix_aiconv_user (tenant_id,user_id,deleted,updated_at,id)
) COMMENT='小卓用户会话';
CREATE TABLE eam_ai_exchange (
 id BIGINT PRIMARY KEY COMMENT '问答ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 conversation_id BIGINT NOT NULL COMMENT '所属会话ID', request_key VARCHAR(36) NOT NULL COMMENT '客户端幂等键',
 question TEXT NOT NULL COMMENT '用户问题', answer TEXT NULL COMMENT '生成回答',
 status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCEEDED/FAILED',
 lease_until DATETIME(6) NOT NULL COMMENT '本轮执行租约到期时间',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '提问时间',
 completed_at DATETIME(6) NULL COMMENT '结束时间',
 UNIQUE KEY uk_aiexchange_tenant (tenant_id,id),
 UNIQUE KEY uk_aiexchange_request (tenant_id,conversation_id,request_key),
 KEY ix_aiexchange_history (tenant_id,conversation_id,id),
 FOREIGN KEY (tenant_id,conversation_id) REFERENCES eam_ai_conversation(tenant_id,id)
) COMMENT='小卓问答轮次';
CREATE TABLE eam_ai_exchange_citation (
 tenant_id BIGINT NOT NULL COMMENT '租户ID', exchange_id BIGINT NOT NULL COMMENT '问答ID',
 chunk_id BIGINT NOT NULL COMMENT '来源切片ID', sort_no INT NOT NULL COMMENT '引用顺序',
 PRIMARY KEY (tenant_id,exchange_id,chunk_id),
 FOREIGN KEY (tenant_id,exchange_id) REFERENCES eam_ai_exchange(tenant_id,id),
 FOREIGN KEY (tenant_id,chunk_id) REFERENCES eam_knowledge_chunk(tenant_id,id)
) COMMENT='小卓问答来源关联';
