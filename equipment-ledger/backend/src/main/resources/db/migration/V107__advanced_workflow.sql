ALTER TABLE wf_node ADD COLUMN parent_key VARCHAR(50) NULL COMMENT '同版本嵌入子流程父节点',
  ADD KEY ix_wf_node_container(tenant_id,version_id,parent_key);
ALTER TABLE wf_version ADD COLUMN execution_version TINYINT NOT NULL DEFAULT 1 COMMENT '1旧提交时路由，2节点到达时路由';
CREATE TABLE wf_node_script (
 tenant_id BIGINT NOT NULL COMMENT '租户', node_id BIGINT NOT NULL COMMENT '脚本节点',
 language VARCHAR(16) NOT NULL COMMENT 'javascript', source_code TEXT NOT NULL COMMENT '计算脚本源码，最多32000字符',
 output_key VARCHAR(80) NOT NULL COMMENT 'computed_开头的结果变量', output_type VARCHAR(16) NOT NULL COMMENT 'NUMBER/TEXT/BOOLEAN',
 PRIMARY KEY(tenant_id,node_id), FOREIGN KEY(tenant_id,node_id) REFERENCES wf_node(tenant_id,id)
) COMMENT='脚本节点计算定义';
ALTER TABLE wf_approval DROP INDEX uk_wf_approval_user,
 ADD UNIQUE KEY uk_wf_approval_user(tenant_id,instance_id,engine_task_id,user_id);
CREATE TABLE wf_script_run (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '执行记录', tenant_id BIGINT NOT NULL COMMENT '租户',
 instance_id BIGINT NOT NULL COMMENT '审批实例', node_key VARCHAR(50) NOT NULL COMMENT '脚本节点',
 execution_id VARCHAR(100) NOT NULL COMMENT '引擎执行ID', source_hash CHAR(64) NOT NULL COMMENT '源码SHA256',
 started_at DATETIME(3) NOT NULL COMMENT '开始时间', finished_at DATETIME(3) NOT NULL COMMENT '完成时间',
 value_type VARCHAR(16) NOT NULL COMMENT '声明结果类型', number_value DECIMAL(38,12) NULL COMMENT '精确数值',
 text_value VARCHAR(1000) NULL COMMENT '文本结果', boolean_value TINYINT NULL COMMENT '布尔结果',
 KEY ix_wf_script_history(tenant_id,instance_id,started_at,id),
 FOREIGN KEY(tenant_id,instance_id) REFERENCES wf_instance(tenant_id,id)
) COMMENT='成功脚本执行审计，失败与业务共同回滚';

INSERT IGNORE INTO eam_role_menu_action(tenant_id,grant_id,action_name,sort_no)
 SELECT p.tenant_id,p.id,x.action_name,x.seq FROM eam_sys_role_menu_permission p CROSS JOIN
 (SELECT 5 seq,'脚本配置' action_name UNION ALL SELECT 6,'脚本发布' UNION ALL SELECT 7,'脚本试算') x
 WHERE p.role_id=72001 AND p.menu_key='workflow';
