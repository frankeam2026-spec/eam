-- Daily arrangements are explicit relational business data; existing periodic tasks are unchanged.
CREATE TABLE eam_inspection_daily_arrangement (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '检查安排ID',
 tenant_id BIGINT NOT NULL COMMENT '租户ID', plan_id BIGINT NOT NULL COMMENT '日常点检计划ID',
 name VARCHAR(150) NOT NULL COMMENT '检查安排名称', sort_no INT NOT NULL COMMENT '顺序',
 enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
 frequency VARCHAR(16) NOT NULL COMMENT 'daily/weekly/monthly/shift/slots/interval/event',
 interval_count INT NOT NULL DEFAULT 1 COMMENT '每N天周月或每N分钟',
 anchor_date DATE NOT NULL COMMENT '固定起算日',
 start_time TIME NOT NULL COMMENT '每日起始时间', end_time TIME NOT NULL COMMENT '每日结束时间，可跨夜',
 window_minutes INT NOT NULL COMMENT '固定间隔或人工事件的完成窗口分钟',
 person_mode VARCHAR(16) NOT NULL COMMENT 'specified/team/roster',
 team_id BIGINT NULL COMMENT '操作组ID', follow_up_user_id BIGINT NOT NULL COMMENT '跟进负责人',
 event_type VARCHAR(24) NULL COMMENT 'startup/shutdown/changeover/temporary',
 allow_late TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许补检',
 FOREIGN KEY(plan_id) REFERENCES eam_inspection_daily_plan(id),
 UNIQUE KEY uk_daily_arrangement_tenant(tenant_id,id),
 KEY ix_daily_arrangement_plan(tenant_id,plan_id,sort_no,id),
 CHECK(interval_count>0),CHECK(window_minutes>0)
) COMMENT='日常点检检查安排';
CREATE TABLE eam_inspection_daily_arrangement_day (
 tenant_id BIGINT NOT NULL,arrangement_id BIGINT NOT NULL,day_no INT NOT NULL COMMENT '周1至7/月1至31',
 PRIMARY KEY(tenant_id,arrangement_id,day_no),
 FOREIGN KEY(arrangement_id) REFERENCES eam_inspection_daily_arrangement(id) ON DELETE CASCADE
) COMMENT='检查安排周或月指定日期';
CREATE TABLE eam_inspection_daily_arrangement_slot (
 tenant_id BIGINT NOT NULL,arrangement_id BIGINT NOT NULL,slot_key VARCHAR(64) NOT NULL,
 name VARCHAR(100) NOT NULL,start_time TIME NOT NULL,end_time TIME NOT NULL,sort_no INT NOT NULL,
 PRIMARY KEY(tenant_id,arrangement_id,slot_key),
 FOREIGN KEY(arrangement_id) REFERENCES eam_inspection_daily_arrangement(id) ON DELETE CASCADE
) COMMENT='检查安排班次或每日时段';
CREATE TABLE eam_inspection_daily_arrangement_person (
 tenant_id BIGINT NOT NULL,arrangement_id BIGINT NOT NULL,user_id BIGINT NOT NULL,sort_no INT NOT NULL,
 PRIMARY KEY(tenant_id,arrangement_id,user_id), KEY ix_daily_candidate(tenant_id,user_id,arrangement_id),
 FOREIGN KEY(arrangement_id) REFERENCES eam_inspection_daily_arrangement(id) ON DELETE CASCADE
) COMMENT='检查安排候选操作工';
CREATE TABLE eam_inspection_daily_arrangement_equipment (
 tenant_id BIGINT NOT NULL,arrangement_id BIGINT NOT NULL,equipment_id BIGINT NOT NULL,
 PRIMARY KEY(tenant_id,arrangement_id,equipment_id),
 FOREIGN KEY(arrangement_id) REFERENCES eam_inspection_daily_arrangement(id) ON DELETE CASCADE,
 FOREIGN KEY(equipment_id) REFERENCES eam_equipment(id)
) COMMENT='检查安排适用设备，空表示全部计划设备';
CREATE TABLE eam_inspection_daily_arrangement_project (
 tenant_id BIGINT NOT NULL,arrangement_id BIGINT NOT NULL,standard_id BIGINT NOT NULL,item_key VARCHAR(128) NOT NULL,
 PRIMARY KEY(tenant_id,arrangement_id,standard_id,item_key),
 FOREIGN KEY(arrangement_id) REFERENCES eam_inspection_daily_arrangement(id) ON DELETE CASCADE,
 FOREIGN KEY(tenant_id,standard_id,item_key) REFERENCES eam_inspection_standard_item(tenant_id,standard_id,item_key)
) COMMENT='检查安排项目，空表示设备上全部已选项目';
CREATE TABLE eam_inspection_operation_team (
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,name VARCHAR(150) NOT NULL,
 department_id BIGINT NOT NULL,created_by BIGINT NOT NULL,row_version INT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_operation_team_scope(tenant_id,department_id,id)
) COMMENT='日常点检操作组，不是组织层级';
CREATE TABLE eam_inspection_operation_team_person (
 tenant_id BIGINT NOT NULL,team_id BIGINT NOT NULL,user_id BIGINT NOT NULL,sort_no INT NOT NULL,
 PRIMARY KEY(tenant_id,team_id,user_id),
 FOREIGN KEY(team_id) REFERENCES eam_inspection_operation_team(id) ON DELETE CASCADE
) COMMENT='操作组成员';
ALTER TABLE eam_inspection_daily_arrangement ADD CONSTRAINT fk_daily_team FOREIGN KEY(team_id) REFERENCES eam_inspection_operation_team(id);
CREATE TABLE eam_inspection_daily_workday (
 tenant_id BIGINT NOT NULL,plan_id BIGINT NOT NULL,work_date DATE NOT NULL,
 PRIMARY KEY(tenant_id,plan_id,work_date),
 FOREIGN KEY(plan_id) REFERENCES eam_inspection_daily_plan(id) ON DELETE CASCADE
) COMMENT='特殊开工日，优先于休息日';
ALTER TABLE eam_inspection_task
 ADD COLUMN arrangement_id BIGINT NULL COMMENT '日常检查安排历史来源ID',
 ADD COLUMN arrangement_name VARCHAR(150) NULL COMMENT '生成时检查安排名称',
 ADD COLUMN business_date DATE NULL COMMENT '应检业务日期，跨夜以开始日期归属',
 ADD COLUMN follow_up_user_id BIGINT NULL COMMENT '跟进负责人',
 ADD COLUMN event_key VARCHAR(64) NULL COMMENT '人工触发幂等键',
 ADD COLUMN exemption_reason VARCHAR(1000) NULL COMMENT '免检原因',
 ADD COLUMN exempted_by BIGINT NULL COMMENT '批准免检人',
 ADD COLUMN exempted_at DATETIME(6) NULL COMMENT '免检时间',
 ADD KEY ix_daily_arrangement_task(tenant_id,arrangement_id,business_date,id),
 ADD KEY ix_daily_follow_up(tenant_id,follow_up_user_id,status,expires_at);
-- No FK from historical tasks to editable arrangements: plan changes must not delete execution history.

CREATE OR REPLACE VIEW v_inspection_task AS SELECT t.id,t.tenant_id,t.arrangement_id,t.arrangement_name,t.business_date,t.follow_up_user_id,t.event_key,t.exemption_reason,t.exempted_by,t.exempted_at,t.code,t.plan_id,t.equipment_id,t.company_id,t.department_id,t.owner_id,t.status,t.row_version,t.created_at,t.updated_at,t.type_id,t.person_mode,t.kind,t.shift_id,t.due_at,t.expires_at,t.deleted,JSON_SET(t.snapshot,'$.owners',COALESCE((SELECT JSON_ARRAYAGG(CAST(r.user_id AS CHAR)) OVER(ORDER BY r.sort_no ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM eam_inspection_task_person r WHERE r.tenant_id=t.tenant_id AND r.task_id=t.id AND r.role_code='owner' LIMIT 1),JSON_ARRAY()),'$.reviewers',COALESCE((SELECT JSON_ARRAYAGG(CAST(r.user_id AS CHAR)) OVER(ORDER BY r.sort_no ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM eam_inspection_task_person r WHERE r.tenant_id=t.tenant_id AND r.task_id=t.id AND r.role_code='reviewer' LIMIT 1),JSON_ARRAY()),'$.personMode',t.person_mode) snapshot,IF(e.task_id IS NULL,NULL,JSON_OBJECT('description',COALESCE(e.`description`,''),'startedAt',COALESCE(DATE_FORMAT(e.`started_at`,'%Y-%m-%dT%H:%i:%s.%f'),''),'finishedAt',COALESCE(DATE_FORMAT(e.`finished_at`,'%Y-%m-%dT%H:%i:%s.%f'),''),'skipReason',COALESCE(e.`skip_reason`,''),'items',COALESCE((SELECT JSON_ARRAYAGG(r.item_view) OVER(ORDER BY r.sort_no ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM v_inspection_execution_item r WHERE r.tenant_id=t.tenant_id AND r.task_id=t.id LIMIT 1),JSON_ARRAY()),'attachments',COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('id',r.file_id,'name',r.name_snapshot,'contentType',r.content_type,'size',r.size_bytes,'url',r.url_snapshot)) OVER(ORDER BY r.sort_no ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM eam_inspection_execution_file r WHERE r.tenant_id=t.tenant_id AND r.task_id=t.id AND r.stage_code='execution' LIMIT 1),JSON_ARRAY()),'review',JSON_OBJECT('reviewerId',COALESCE(CAST(e.reviewer_id AS CHAR),''),'decision',COALESCE(e.`review_decision`,''),'description',COALESCE(e.`review_description`,''),'reviewer',COALESCE(e.`reviewer_name`,''),'reviewedAt',COALESCE(DATE_FORMAT(e.`reviewed_at`,'%Y-%m-%dT%H:%i:%s.%f'),''),'attachments',COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('id',r.file_id,'name',r.name_snapshot,'contentType',r.content_type,'size',r.size_bytes,'url',r.url_snapshot)) OVER(ORDER BY r.sort_no ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM eam_inspection_execution_file r WHERE r.tenant_id=t.tenant_id AND r.task_id=t.id AND r.stage_code='review' LIMIT 1),JSON_ARRAY())),'scannedEquipmentIds',COALESCE((SELECT JSON_ARRAYAGG(CAST(r.equipment_id AS CHAR)) OVER(ORDER BY r.sort_no ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM eam_inspection_execution_equipment r WHERE r.tenant_id=t.tenant_id AND r.task_id=t.id AND r.action_code='scan' LIMIT 1),JSON_ARRAY()),'skippedEquipmentIds',COALESCE((SELECT JSON_ARRAYAGG(CAST(r.equipment_id AS CHAR)) OVER(ORDER BY r.sort_no ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM eam_inspection_execution_equipment r WHERE r.tenant_id=t.tenant_id AND r.task_id=t.id AND r.action_code='skip' LIMIT 1),JSON_ARRAY()),'reviewItems',COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('id',r.item_key,'decision',r.decision,'description',r.description)) OVER(ORDER BY r.sort_no ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM eam_inspection_execution_review_item r WHERE r.tenant_id=t.tenant_id AND r.task_id=t.id LIMIT 1),JSON_ARRAY()))) result FROM eam_inspection_task t LEFT JOIN eam_inspection_execution e ON e.tenant_id=t.tenant_id AND e.task_id=t.id;
