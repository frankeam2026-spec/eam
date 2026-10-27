-- Workflow business model. Flowable execution tables are managed separately.
CREATE TABLE wf_template (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '模板ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 company_id BIGINT NOT NULL COMMENT '适用公司ID', business_type VARCHAR(32) NOT NULL COMMENT '业务类型',
 name VARCHAR(100) NOT NULL COMMENT '模板名称', description VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '说明',
 row_version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本', created_by BIGINT NOT NULL COMMENT '创建人',
 updated_by BIGINT NOT NULL COMMENT '修改人', created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '修改时间',
 UNIQUE KEY uk_wf_template_tenant (tenant_id,id), KEY ix_wf_template_list(tenant_id,company_id,business_type,updated_at,id)
) COMMENT='审批流程模板';
CREATE TABLE wf_version (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '版本ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 template_id BIGINT NOT NULL COMMENT '模板ID', version_no INT NOT NULL COMMENT '版本号',
 state VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT或PUBLISHED',
 bpmn_xml LONGTEXT NULL COMMENT '生成的BPMN执行产物，不代替关系模型',
 engine_definition_id VARCHAR(100) NULL COMMENT 'Flowable定义ID',
 published_by BIGINT NULL COMMENT '发布人', published_at DATETIME(3) NULL COMMENT '发布时间',
 UNIQUE KEY uk_wf_version (tenant_id,template_id,version_no), UNIQUE KEY uk_wf_version_tenant(tenant_id,id),
 FOREIGN KEY (tenant_id,template_id) REFERENCES wf_template(tenant_id,id)
) COMMENT='不可变已发布流程版本';
CREATE TABLE wf_binding (
 tenant_id BIGINT NOT NULL COMMENT '租户ID', company_id BIGINT NOT NULL COMMENT '适用公司ID',
 business_type VARCHAR(32) NOT NULL COMMENT '业务类型', version_id BIGINT NULL COMMENT '生效版本ID',
 enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否切换新提交到审批', row_version BIGINT NOT NULL DEFAULT 0 COMMENT '并发版本',
 PRIMARY KEY(tenant_id,company_id,business_type), FOREIGN KEY(tenant_id,version_id) REFERENCES wf_version(tenant_id,id)
) COMMENT='公司业务生效流程';
CREATE TABLE wf_node (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '节点ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 version_id BIGINT NOT NULL COMMENT '流程版本ID', node_key VARCHAR(50) NOT NULL COMMENT '稳定节点标识',
 node_type VARCHAR(16) NOT NULL COMMENT 'START/APPROVAL/BRANCH/END', name VARCHAR(100) NOT NULL COMMENT '节点名称',
 next_key VARCHAR(50) NULL COMMENT '后继节点标识', approval_mode VARCHAR(8) NULL COMMENT 'ANY/ALL', sort_no INT NOT NULL COMMENT '显示顺序',
 UNIQUE KEY uk_wf_node(tenant_id,version_id,node_key), UNIQUE KEY uk_wf_node_tenant(tenant_id,id),
 FOREIGN KEY(tenant_id,version_id) REFERENCES wf_version(tenant_id,id)
) COMMENT='流程节点';
CREATE TABLE wf_condition (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '条件ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
 version_id BIGINT NOT NULL COMMENT '版本ID', parent_id BIGINT NULL COMMENT '父条件ID', sort_no INT NOT NULL COMMENT '子项顺序',
 operator VARCHAR(20) NOT NULL COMMENT '受限操作符', field_key VARCHAR(80) NULL COMMENT '变量稳定标识',
 value_type VARCHAR(16) NOT NULL COMMENT '数值/文本/关联/布尔类型',
 number_value DECIMAL(38,12) NULL COMMENT '数值字面值', text_value VARCHAR(1000) NULL COMMENT '文本字面值',
 reference_value BIGINT NULL COMMENT '关联对象ID字面值', boolean_value TINYINT NULL COMMENT '布尔字面值',
 UNIQUE KEY uk_wf_condition_tenant(tenant_id,id), KEY ix_wf_condition_tree(tenant_id,version_id,parent_id,sort_no),
 FOREIGN KEY(tenant_id,version_id) REFERENCES wf_version(tenant_id,id),
 FOREIGN KEY(tenant_id,parent_id) REFERENCES wf_condition(tenant_id,id)
) COMMENT='类型化表达式树';
CREATE TABLE wf_branch (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '分支ID', tenant_id BIGINT NOT NULL COMMENT '租户ID', node_id BIGINT NOT NULL COMMENT '分支节点ID',
 branch_key VARCHAR(50) NOT NULL COMMENT '稳定标识', name VARCHAR(100) NOT NULL COMMENT '分支名称',
 sort_no INT NOT NULL COMMENT '匹配顺序', fallback TINYINT NOT NULL DEFAULT 0 COMMENT '最后默认分支',
 target_key VARCHAR(50) NOT NULL COMMENT '分支入口节点', condition_id BIGINT NULL COMMENT '条件树根ID',
 expression_source VARCHAR(8000) NULL COMMENT '用户编辑原文，仅用于编辑；执行依据类型化条件树',
 UNIQUE KEY uk_wf_branch_order(tenant_id,node_id,sort_no), UNIQUE KEY uk_wf_branch_key(tenant_id,node_id,branch_key),
 FOREIGN KEY(tenant_id,node_id) REFERENCES wf_node(tenant_id,id), FOREIGN KEY(tenant_id,condition_id) REFERENCES wf_condition(tenant_id,id)
) COMMENT='有序互斥流程分支';
CREATE TABLE wf_node_user (
 tenant_id BIGINT NOT NULL COMMENT '租户ID', node_id BIGINT NOT NULL COMMENT '节点ID', user_id BIGINT NOT NULL COMMENT '固定人员ID',
 PRIMARY KEY(tenant_id,node_id,user_id), FOREIGN KEY(tenant_id,node_id) REFERENCES wf_node(tenant_id,id)
) COMMENT='节点固定审批人';
CREATE TABLE wf_node_role (
 tenant_id BIGINT NOT NULL COMMENT '租户ID', node_id BIGINT NOT NULL COMMENT '节点ID', role_id BIGINT NOT NULL COMMENT '审批角色ID',
 PRIMARY KEY(tenant_id,node_id,role_id), FOREIGN KEY(tenant_id,node_id) REFERENCES wf_node(tenant_id,id)
) COMMENT='节点审批角色';
CREATE TABLE wf_instance (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '实例ID', tenant_id BIGINT NOT NULL COMMENT '租户ID', version_id BIGINT NOT NULL COMMENT '固定流程版本',
 business_type VARCHAR(32) NOT NULL COMMENT '业务类型', business_id BIGINT NOT NULL COMMENT '业务单据ID', company_id BIGINT NOT NULL COMMENT '单据公司',
 applicant_id BIGINT NOT NULL COMMENT '申请人', round_no INT NOT NULL COMMENT '审批轮次', engine_instance_id VARCHAR(100) NULL COMMENT '引擎实例',
 state VARCHAR(16) NOT NULL COMMENT 'RUNNING/APPROVED/REJECTED/WITHDRAWN', row_version BIGINT NOT NULL DEFAULT 0 COMMENT '并发版本',
 started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间', ended_at DATETIME(3) NULL COMMENT '结束时间',
 UNIQUE KEY uk_wf_instance_round(tenant_id,business_type,business_id,round_no), UNIQUE KEY uk_wf_instance_tenant(tenant_id,id),
 KEY ix_wf_instance_company(tenant_id,company_id,state,started_at), FOREIGN KEY(tenant_id,version_id) REFERENCES wf_version(tenant_id,id)
) COMMENT='业务审批实例';
CREATE TABLE wf_value (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '快照值ID', tenant_id BIGINT NOT NULL COMMENT '租户ID', instance_id BIGINT NOT NULL COMMENT '审批实例',
 field_key VARCHAR(80) NOT NULL COMMENT '变量标识', line_no INT NOT NULL DEFAULT 0 COMMENT '0为主单字段，正数为明细序号',
 value_type VARCHAR(16) NOT NULL COMMENT '数值/文本/关联/布尔类型', is_null TINYINT NOT NULL DEFAULT 0 COMMENT '明确空值',
 number_value DECIMAL(38,12) NULL COMMENT '数值', text_value VARCHAR(1000) NULL COMMENT '文本', reference_value BIGINT NULL COMMENT '关联ID', boolean_value TINYINT NULL COMMENT '布尔',
 UNIQUE KEY uk_wf_value(tenant_id,instance_id,field_key,line_no), FOREIGN KEY(tenant_id,instance_id) REFERENCES wf_instance(tenant_id,id)
) COMMENT='提交时条件数据快照';
CREATE TABLE wf_approval (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '审批任务ID', tenant_id BIGINT NOT NULL COMMENT '租户ID', instance_id BIGINT NOT NULL COMMENT '实例ID',
 node_key VARCHAR(50) NOT NULL COMMENT '审批节点', engine_task_id VARCHAR(100) NOT NULL COMMENT '引擎任务ID', user_id BIGINT NOT NULL COMMENT '本轮审批人',
 state VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
 opinion VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '审批意见', acted_at DATETIME(3) NULL COMMENT '办理时间',
 UNIQUE KEY uk_wf_approval_user(tenant_id,instance_id,node_key,user_id), UNIQUE KEY uk_wf_approval_tenant(tenant_id,id),
 KEY ix_wf_approval_pending(tenant_id,user_id,state,id), FOREIGN KEY(tenant_id,instance_id) REFERENCES wf_instance(tenant_id,id)
) COMMENT='审批人员任务及结果';
CREATE TABLE wf_event (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '事件ID', tenant_id BIGINT NOT NULL COMMENT '租户ID', instance_id BIGINT NOT NULL COMMENT '审批实例',
 node_key VARCHAR(50) NULL COMMENT '节点标识', event_type VARCHAR(32) NOT NULL COMMENT '事件类型', actor_id BIGINT NULL COMMENT '操作人',
 description VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '事件说明', created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
 KEY ix_wf_event(tenant_id,instance_id,created_at,id), FOREIGN KEY(tenant_id,instance_id) REFERENCES wf_instance(tenant_id,id)
) COMMENT='流程操作及分支命中记录';
CREATE TABLE wf_todo (
 tenant_id BIGINT NOT NULL COMMENT '租户ID', approval_id BIGINT NOT NULL COMMENT '审批任务ID', inbox_id BIGINT NOT NULL COMMENT '站内待办ID',
 PRIMARY KEY(tenant_id,approval_id), UNIQUE KEY uk_wf_todo_message(tenant_id,inbox_id),
 FOREIGN KEY(tenant_id,approval_id) REFERENCES wf_approval(tenant_id,id)
) COMMENT='审批任务与站内待办精确关联';
