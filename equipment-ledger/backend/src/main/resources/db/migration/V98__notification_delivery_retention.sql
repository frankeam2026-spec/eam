CREATE TABLE eam_notify_retention (
 tenant_id BIGINT NOT NULL PRIMARY KEY COMMENT '租户，与通知渠道配置同作用域',
 success_days INT NOT NULL DEFAULT 90 COMMENT '成功发送日志保留天数',
 exception_days INT NOT NULL DEFAULT 180 COMMENT '异常或跳过发送日志保留天数',
 row_version INT NOT NULL DEFAULT 0 COMMENT '配置版本',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最后修改时间',
 CHECK(success_days BETWEEN 1 AND 3650), CHECK(exception_days BETWEEN 1 AND 3650)
) COMMENT='通知发送日志保留配置，非业务记录保留配置';
CREATE TABLE eam_notify_receipt (
 message_id BIGINT NOT NULL COMMENT '公共通知事实ID',
 channel VARCHAR(10) NOT NULL COMMENT 'SYSTEM/EMAIL/APP',
 terminal_status VARCHAR(20) NOT NULL COMMENT '清理前最终发送状态',
 sent_at DATETIME(6) NULL COMMENT '真实发送成功时间，非成功为空',
 PRIMARY KEY(message_id,channel),
 FOREIGN KEY(message_id) REFERENCES eam_notify_message(id)
) COMMENT='日志清理后保留的渠道凭据，防重复发送并维持站内消息';
ALTER TABLE eam_notify_delivery
 ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '日志生成时间',
 ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后状态或重试变更时间',
 ADD INDEX ix_notify_delivery_retention(status,updated_at,id);
UPDATE eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id
 SET d.created_at=m.created_at;
ALTER TABLE eam_notify_message ADD INDEX ix_notify_message_time(tenant_id,created_at,id);
