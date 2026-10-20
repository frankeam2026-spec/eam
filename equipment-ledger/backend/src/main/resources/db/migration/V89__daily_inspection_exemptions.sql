CREATE TABLE eam_inspection_daily_exemption (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '免检安排ID',tenant_id BIGINT NOT NULL COMMENT '租户ID',
 plan_id BIGINT NOT NULL COMMENT '计划ID',equipment_id BIGINT NOT NULL COMMENT '设备ID',
 start_at DATETIME(6) NOT NULL COMMENT '覆盖应检开始时间，含边界',end_at DATETIME(6) NOT NULL COMMENT '覆盖应检开始时间，不含边界',
 reason VARCHAR(1000) NOT NULL COMMENT '批准免检原因',approved_by BIGINT NOT NULL COMMENT '批准人',
 approved_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '批准时间',
 FOREIGN KEY(plan_id) REFERENCES eam_inspection_daily_plan(id),FOREIGN KEY(equipment_id) REFERENCES eam_equipment(id),
 KEY ix_daily_exempt_range(tenant_id,plan_id,equipment_id,start_at,end_at),CHECK(end_at>start_at)
) COMMENT='日常点检停机等按设备时间范围批准免检';
ALTER TABLE eam_inspection_task
 ADD COLUMN source_assignment_mode VARCHAR(16) NULL COMMENT '生成时指定人员/操作组/排班方式',
 ADD COLUMN source_team_id BIGINT NULL COMMENT '生成时操作组ID';
INSERT IGNORE INTO eam_role_menu_action(tenant_id,grant_id,action_name,sort_no)
 SELECT tenant_id,id,'免检',100 FROM eam_sys_role_menu_permission WHERE menu_key='inspectionDailyTask' AND role_id=72001;
