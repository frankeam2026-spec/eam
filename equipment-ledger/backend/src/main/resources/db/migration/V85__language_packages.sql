ALTER TABLE eam_user_preference ADD COLUMN locale VARCHAR(35) NOT NULL DEFAULT 'zh-CN' COMMENT '个人首选语言，BCP47编码';
CREATE TABLE eam_language (
 tenant_id BIGINT NOT NULL COMMENT '租户ID',locale VARCHAR(35) NOT NULL COMMENT 'BCP47语言编码',
 native_name VARCHAR(100) NOT NULL COMMENT '语言自身名称',direction VARCHAR(3) NOT NULL DEFAULT 'ltr' COMMENT '文字方向；首期仅ltr',
 builtin TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统内置',enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否可选',
 row_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',updated_by BIGINT NULL COMMENT '最后操作人',
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
 PRIMARY KEY(tenant_id,locale)
) COMMENT='可用语言和语言包发布元数据';
CREATE TABLE eam_language_message (
 tenant_id BIGINT NOT NULL COMMENT '租户ID',locale VARCHAR(35) NOT NULL COMMENT '语言编码',
 message_key VARCHAR(190) NOT NULL COMMENT '稳定翻译词条键',message_text TEXT NOT NULL COMMENT '翻译文本；不执行HTML或脚本',
 PRIMARY KEY(tenant_id,locale,message_key),
 FOREIGN KEY(tenant_id,locale) REFERENCES eam_language(tenant_id,locale) ON DELETE CASCADE
) COMMENT='客户导入语言包的翻译覆盖词条';
CREATE TABLE eam_language_event (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',tenant_id BIGINT NOT NULL COMMENT '租户ID',locale VARCHAR(35) NOT NULL COMMENT '语言编码',
 action_code VARCHAR(20) NOT NULL COMMENT 'import/enable/disable/restore',actor_id BIGINT NOT NULL COMMENT '操作人',
 row_version INT NOT NULL COMMENT '操作后的版本',message_count INT NOT NULL DEFAULT 0 COMMENT '提交词条数',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
 PRIMARY KEY(id),INDEX ix_language_event(tenant_id,locale,created_at)
) COMMENT='语言包管理操作日志';
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key)
SELECT 850000000000000000 + ROW_NUMBER() OVER(ORDER BY r.id),r.tenant_id,r.id,'language'
FROM eam_sys_role r WHERE r.code='SYS_ADMIN' AND r.deleted=0
ON DUPLICATE KEY UPDATE menu_key=VALUES(menu_key);
INSERT INTO eam_role_menu_action(tenant_id,grant_id,action_name,sort_no)
SELECT p.tenant_id,p.id,'查看',0 FROM eam_sys_role_menu_permission p JOIN eam_sys_role r ON r.id=p.role_id
WHERE r.code='SYS_ADMIN' AND r.deleted=0 AND p.menu_key='language'
ON DUPLICATE KEY UPDATE sort_no=VALUES(sort_no);
INSERT INTO eam_role_menu_scope(tenant_id,grant_id,scope_code,sort_no)
SELECT p.tenant_id,p.id,'all',0 FROM eam_sys_role_menu_permission p JOIN eam_sys_role r ON r.id=p.role_id
WHERE r.code='SYS_ADMIN' AND r.deleted=0 AND p.menu_key='language'
ON DUPLICATE KEY UPDATE sort_no=VALUES(sort_no);
