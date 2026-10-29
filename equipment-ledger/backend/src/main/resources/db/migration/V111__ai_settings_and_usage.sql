CREATE TABLE eam_ai_setting (
 tenant_id BIGINT PRIMARY KEY COMMENT '租户ID', enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '启用AI',
 base_url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '百炼OpenAI兼容地址', api_secret TEXT NULL COMMENT 'AES-GCM密钥密文',
 chat_model VARCHAR(128) NOT NULL DEFAULT 'qwen-plus' COMMENT '文本模型版本',
 embedding_model VARCHAR(128) NOT NULL DEFAULT '' COMMENT '向量模型版本',
 monthly_limit DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '月度硬限额人民币',
 warning_percent INT NOT NULL DEFAULT 80 COMMENT '预算预警百分比',
 input_price DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '文本输入每百万Token估算价格人民币',
 output_price DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '文本输出每百万Token估算价格人民币',
 embedding_price DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '向量输入每百万Token估算价格人民币',
 row_version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本'
) COMMENT='租户AI渠道与预算';
CREATE TABLE eam_ai_monthly_usage (
 tenant_id BIGINT NOT NULL COMMENT '租户ID', billing_month CHAR(7) NOT NULL COMMENT '北京时间账期YYYY-MM',
 estimated_spent DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '估算已消费含未知结果保守计费',
 reserved_amount DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '进行中调用预占金额',
 PRIMARY KEY (tenant_id,billing_month)
) COMMENT='AI预算并发计量';
CREATE TABLE eam_ai_call (
 id BIGINT PRIMARY KEY COMMENT '调用ID', tenant_id BIGINT NOT NULL COMMENT '租户ID', user_id BIGINT NOT NULL COMMENT '调用人ID',
 billing_month CHAR(7) NOT NULL COMMENT '预占账期', purpose VARCHAR(24) NOT NULL COMMENT 'CHAT/EMBEDDING/INTENT',
 model_name VARCHAR(128) NOT NULL COMMENT '实际模型版本', status VARCHAR(24) NOT NULL COMMENT 'RUNNING/SUCCEEDED/UNKNOWN',
 reserved_amount DECIMAL(18,6) NOT NULL COMMENT '本次预占金额', estimated_cost DECIMAL(18,6) NULL COMMENT '估算费用非供应商账单',
 input_tokens BIGINT NULL COMMENT '服务商返回输入Token', output_tokens BIGINT NULL COMMENT '服务商返回输出Token',
 elapsed_ms BIGINT NULL COMMENT '耗时毫秒', created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '开始时间',
 completed_at DATETIME(6) NULL COMMENT '结束时间', KEY ix_aicall_list(tenant_id,created_at,id),
 KEY ix_aicall_recovery(status,created_at,id)
) COMMENT='AI调用记录不存提示词和密钥';
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key)
 SELECT -(11100000000000+p.tenant_id),p.tenant_id,p.role_id,'aiSetting' FROM eam_sys_role_menu_permission p
 WHERE p.menu_key='role' AND p.role_id=72001 AND NOT EXISTS(SELECT 1 FROM eam_sys_role_menu_permission q WHERE q.tenant_id=p.tenant_id AND q.role_id=p.role_id AND q.menu_key='aiSetting');
INSERT IGNORE INTO eam_role_menu_action(tenant_id,grant_id,action_name,sort_no)
 SELECT p.tenant_id,p.id,x.action_name,x.seq FROM eam_sys_role_menu_permission p CROSS JOIN (SELECT 1 seq,'查看' action_name UNION ALL SELECT 2,'编辑') x WHERE p.role_id=72001 AND p.menu_key='aiSetting';
INSERT IGNORE INTO eam_role_menu_scope(tenant_id,grant_id,scope_code,sort_no)
 SELECT tenant_id,id,'all',0 FROM eam_sys_role_menu_permission WHERE role_id=72001 AND menu_key='aiSetting';
