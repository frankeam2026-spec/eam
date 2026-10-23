-- Reminder configuration and notification transport are separate domains from business tasks.
CREATE TABLE eam_notify_policy (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '策略ID', tenant_id BIGINT NOT NULL COMMENT '租户',
 name VARCHAR(150) NOT NULL COMMENT '名称', business_kind VARCHAR(20) NOT NULL COMMENT 'repair/daily/periodic/maintenance',
 enabled TINYINT NOT NULL DEFAULT 0 COMMENT '草稿0/启用1', row_version INT NOT NULL DEFAULT 0 COMMENT '版本',
 effective_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '仅此后新触发时间生效',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间',
 UNIQUE KEY uk_notify_policy_name(tenant_id,business_kind,name), KEY ix_notify_policy(tenant_id,business_kind,enabled)
) COMMENT='统一提醒策略，不承载业务任务';
CREATE TABLE eam_notify_rule (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '稳定规则ID', tenant_id BIGINT NOT NULL COMMENT '租户', policy_id BIGINT NOT NULL COMMENT '策略',
 scene VARCHAR(32) NOT NULL COMMENT '业务触发场景', offset_value INT NOT NULL COMMENT '时长', offset_unit VARCHAR(8) NOT NULL COMMENT 'MINUTE/HOUR/DAY',
 enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用', archived TINYINT NOT NULL DEFAULT 0 COMMENT '移除但保留发送引用', sort_no INT NOT NULL DEFAULT 0 COMMENT '顺序',
 effective_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '规则生效下界',
 FOREIGN KEY(policy_id) REFERENCES eam_notify_policy(id), CHECK(offset_value>=0 AND offset_value<=525600),
 KEY ix_notify_rule(policy_id,enabled,sort_no)
) COMMENT='提醒规则，多行分级';
CREATE TABLE eam_notify_rule_person (
 tenant_id BIGINT NOT NULL COMMENT '租户', rule_id BIGINT NOT NULL COMMENT '规则', user_id BIGINT NOT NULL COMMENT '固定用户',
 recipient_kind VARCHAR(4) NOT NULL COMMENT 'TO/CC', PRIMARY KEY(rule_id,user_id),
 FOREIGN KEY(rule_id) REFERENCES eam_notify_rule(id), FOREIGN KEY(user_id) REFERENCES eam_sys_user(id)
) COMMENT='固定提醒与抄送人员，提醒优先';
CREATE TABLE eam_notify_default (
 tenant_id BIGINT NOT NULL COMMENT '租户', business_kind VARCHAR(20) NOT NULL COMMENT '业务类型', policy_id BIGINT NULL COMMENT '默认策略',
 row_version INT NOT NULL DEFAULT 0 COMMENT '版本', effective_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '绑定生效时间',
 PRIMARY KEY(tenant_id,business_kind), FOREIGN KEY(policy_id) REFERENCES eam_notify_policy(id)
) COMMENT='各业务默认策略';
CREATE TABLE eam_notify_channel (
 tenant_id BIGINT NOT NULL PRIMARY KEY COMMENT '租户', row_version INT NOT NULL DEFAULT 0 COMMENT '版本',
 system_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '站内消息', email_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '邮件启用',
 smtp_host VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'SMTP主机', smtp_port INT NOT NULL DEFAULT 587 COMMENT '端口',
 smtp_security VARCHAR(12) NOT NULL DEFAULT 'STARTTLS' COMMENT 'NONE/STARTTLS/SSL',
 smtp_username VARCHAR(255) NOT NULL DEFAULT '' COMMENT '账号', smtp_secret TEXT NULL COMMENT 'AES-GCM密文',
 sender_address VARCHAR(254) NOT NULL DEFAULT '' COMMENT '发件邮箱', public_url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '系统访问地址',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间'
) COMMENT='全系统渠道配置；App尚未接入';
CREATE TABLE eam_notify_message (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '通知ID', tenant_id BIGINT NOT NULL COMMENT '租户',
 source_key VARCHAR(200) NOT NULL COMMENT '来源防重键', module VARCHAR(30) NOT NULL COMMENT '业务模块', object_id BIGINT NOT NULL COMMENT '业务对象ID',
 legacy_source VARCHAR(40) NULL COMMENT '既有站内消息来源', legacy_id BIGINT NULL COMMENT '既有消息ID',
 rule_id BIGINT NULL COMMENT '规则ID', policy_version INT NULL COMMENT '策略版本',
 scene VARCHAR(32) NULL COMMENT '提醒场景', subject VARCHAR(500) NOT NULL COMMENT '标题快照', content TEXT NULL COMMENT '消息正文',
 message_key VARCHAR(120) NULL COMMENT '国际化键', message_args JSON NULL COMMENT '仅模板参数',
 recipient_id BIGINT NOT NULL COMMENT '接收用户', recipient_kind VARCHAR(4) NOT NULL DEFAULT 'TO' COMMENT 'TO/CC',
 trigger_at DATETIME(6) NULL COMMENT '规则触发时间', created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '生成时间',
 read_at DATETIME(6) NULL COMMENT '站内已读', cancelled_at DATETIME(6) NULL COMMENT '发送前已失效',
 UNIQUE KEY uk_notify_source(tenant_id,source_key), KEY ix_notify_user(tenant_id,recipient_id,created_at),
 KEY ix_notify_object(tenant_id,module,object_id,rule_id),
 FOREIGN KEY(recipient_id) REFERENCES eam_sys_user(id), FOREIGN KEY(rule_id) REFERENCES eam_notify_rule(id)
) COMMENT='公共通知事实，非通用业务对象';
CREATE TABLE eam_notify_delivery (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '渠道发送ID', message_id BIGINT NOT NULL COMMENT '通知',
 channel VARCHAR(10) NOT NULL COMMENT 'SYSTEM/EMAIL/APP', status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '发送状态',
 attempts INT NOT NULL DEFAULT 0 COMMENT '尝试次数', next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次尝试',
 lease_until DATETIME(6) NULL COMMENT '发送租约', last_error VARCHAR(1000) NULL COMMENT '结果说明', sent_at DATETIME(6) NULL COMMENT '发送成功时间',
 UNIQUE KEY uk_notify_delivery(message_id,channel), KEY ix_notify_delivery(status,next_attempt_at),
 FOREIGN KEY(message_id) REFERENCES eam_notify_message(id)
) COMMENT='每用户每渠道独立发送、重试与租约';
ALTER TABLE eam_sys_user ADD COLUMN email VARCHAR(254) NOT NULL DEFAULT '' COMMENT '通知邮箱';
ALTER TABLE eam_inspection_daily_plan ADD COLUMN reminder_mode VARCHAR(12) NOT NULL DEFAULT 'DEFAULT' COMMENT 'DEFAULT/POLICY/OFF', ADD COLUMN reminder_policy_id BIGINT NULL COMMENT '专用策略', ADD COLUMN reminder_effective_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '绑定生效下界', ADD CONSTRAINT fk_inspection_daily_plan_notify FOREIGN KEY(reminder_policy_id) REFERENCES eam_notify_policy(id);
ALTER TABLE eam_inspection_periodic_plan ADD COLUMN reminder_mode VARCHAR(12) NOT NULL DEFAULT 'DEFAULT' COMMENT 'DEFAULT/POLICY/OFF', ADD COLUMN reminder_policy_id BIGINT NULL COMMENT '专用策略', ADD COLUMN reminder_effective_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '绑定生效下界', ADD CONSTRAINT fk_inspection_periodic_plan_notify FOREIGN KEY(reminder_policy_id) REFERENCES eam_notify_policy(id);
ALTER TABLE eam_maintenance_plan ADD COLUMN reminder_mode VARCHAR(12) NOT NULL DEFAULT 'DEFAULT' COMMENT 'DEFAULT/POLICY/OFF', ADD COLUMN reminder_policy_id BIGINT NULL COMMENT '专用策略', ADD COLUMN reminder_effective_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '绑定生效下界', ADD CONSTRAINT fk_maintenance_plan_notify FOREIGN KEY(reminder_policy_id) REFERENCES eam_notify_policy(id);

-- Explicit bootstrap for the existing system administrator only; normal roles remain unchanged.
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key)
 SELECT -(9600000000000+p.tenant_id*10+x.seq),p.tenant_id,p.role_id,x.menu_key
 FROM eam_sys_role_menu_permission p CROSS JOIN
 (SELECT 1 seq,'notificationPolicy' menu_key UNION ALL SELECT 2,'notificationChannel' UNION ALL SELECT 3,'notificationDelivery') x
 WHERE p.menu_key='role' AND p.role_id=72001
 AND NOT EXISTS(SELECT 1 FROM eam_sys_role_menu_permission q WHERE q.tenant_id=p.tenant_id AND q.role_id=p.role_id AND q.menu_key=x.menu_key);
INSERT IGNORE INTO eam_role_menu_action(tenant_id,grant_id,action_name,sort_no)
 SELECT p.tenant_id,p.id,x.action_name,x.seq FROM eam_sys_role_menu_permission p CROSS JOIN
 (SELECT 1 seq,'查看' action_name UNION ALL SELECT 2,'编辑' UNION ALL SELECT 3,'重试') x
 WHERE p.role_id=72001 AND p.menu_key IN ('notificationPolicy','notificationChannel','notificationDelivery')
 AND ((p.menu_key='notificationDelivery' AND x.action_name IN ('查看','重试')) OR (p.menu_key<>'notificationDelivery' AND x.action_name IN ('查看','编辑')));
INSERT IGNORE INTO eam_role_menu_scope(tenant_id,grant_id,scope_code,sort_no)
 SELECT tenant_id,id,'all',0 FROM eam_sys_role_menu_permission WHERE role_id=72001 AND menu_key IN ('notificationPolicy','notificationChannel','notificationDelivery');

-- Normal workflow anchors only. Suspension/transfer/rework reminder rounds are intentionally excluded.
ALTER TABLE eam_repair_order ADD COLUMN dispatched_at DATETIME(6) NULL COMMENT '正常首次派工时间', ADD COLUMN claimed_at DATETIME(6) NULL COMMENT '正常首次接单时间';
UPDATE eam_repair_order r JOIN (SELECT tenant_id,order_id,MIN(CASE WHEN action='派工' THEN created_at END) dispatch_at,MIN(CASE WHEN action='接单' THEN created_at END) claim_at FROM eam_repair_event GROUP BY tenant_id,order_id) e ON e.tenant_id=r.tenant_id AND e.order_id=r.id SET r.dispatched_at=e.dispatch_at,r.claimed_at=e.claim_at;
ALTER TABLE eam_maintenance_task ADD COLUMN submitted_at DATETIME(6) NULL COMMENT '正常流程提交验收时间';
UPDATE eam_maintenance_task t JOIN (SELECT tenant_id,task_id,MIN(created_at) submitted_at FROM eam_maintenance_event WHERE action='提交保养' GROUP BY tenant_id,task_id) e ON e.tenant_id=t.tenant_id AND e.task_id=t.id SET t.submitted_at=e.submitted_at;
