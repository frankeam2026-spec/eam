ALTER TABLE eam_inspection_daily_arrangement
 MODIFY person_mode VARCHAR(16) NOT NULL COMMENT 'open/specified/team/roster，人员安排属于检查安排',
 MODIFY follow_up_user_id BIGINT NULL COMMENT '可选跟进负责人，不等于实际点检人';

CREATE TABLE eam_inspection_daily_staff_rule (
 tenant_id BIGINT NOT NULL COMMENT '租户ID', arrangement_id BIGINT NOT NULL COMMENT '检查安排ID',
 rule_key VARCHAR(32) NOT NULL COMMENT '规则稳定标识', weekday_no TINYINT NOT NULL DEFAULT 0 COMMENT '0每天，1至7星期',
 slot_key VARCHAR(64) NOT NULL DEFAULT '' COMMENT '引用安排班次时段；空表示按日期时间覆盖',
 start_time TIME NOT NULL DEFAULT '00:00:00' COMMENT '人员覆盖开始时间',
 end_time TIME NOT NULL DEFAULT '00:00:00' COMMENT '覆盖结束；等于开始表示全天，小于开始表示跨夜',
 unassigned TINYINT NOT NULL DEFAULT 0 COMMENT '明确无人，不回退',
 PRIMARY KEY(tenant_id,arrangement_id,rule_key),
 UNIQUE KEY uk_daily_staff_rule(tenant_id,arrangement_id,weekday_no,slot_key,start_time,end_time),
 FOREIGN KEY(tenant_id,arrangement_id) REFERENCES eam_inspection_daily_arrangement(tenant_id,id) ON DELETE CASCADE,
 CHECK(weekday_no BETWEEN 0 AND 7), CHECK(unassigned IN(0,1))
) COMMENT='检查安排常规排班规则';
CREATE TABLE eam_inspection_daily_staff_rule_person (
 tenant_id BIGINT NOT NULL, arrangement_id BIGINT NOT NULL, rule_key VARCHAR(32) NOT NULL,
 user_id BIGINT NOT NULL COMMENT '实际用户关联ID', sort_no INT NOT NULL,
 PRIMARY KEY(tenant_id,arrangement_id,rule_key,user_id),
 FOREIGN KEY(tenant_id,arrangement_id,rule_key) REFERENCES eam_inspection_daily_staff_rule(tenant_id,arrangement_id,rule_key) ON DELETE CASCADE,
 FOREIGN KEY(user_id) REFERENCES eam_sys_user(id)
) COMMENT='常规排班候选人员';
CREATE TABLE eam_inspection_daily_staff_override (
 tenant_id BIGINT NOT NULL COMMENT '租户ID', arrangement_id BIGINT NOT NULL COMMENT '检查安排ID',
 rule_key VARCHAR(32) NOT NULL COMMENT '日期调整稳定标识', work_date DATE NOT NULL COMMENT '业务归属日期',
 slot_key VARCHAR(64) NOT NULL DEFAULT '' COMMENT '引用安排班次时段；空表示按日期时间覆盖',
 start_time TIME NOT NULL DEFAULT '00:00:00' COMMENT '人员覆盖开始时间',
 end_time TIME NOT NULL DEFAULT '00:00:00' COMMENT '覆盖结束，支持跨夜或全天',
 unassigned TINYINT NOT NULL DEFAULT 0 COMMENT '明确无人，不回退',
 PRIMARY KEY(tenant_id,arrangement_id,rule_key),
 UNIQUE KEY uk_daily_staff_override(tenant_id,arrangement_id,work_date,slot_key,start_time,end_time),
 FOREIGN KEY(tenant_id,arrangement_id) REFERENCES eam_inspection_daily_arrangement(tenant_id,id) ON DELETE CASCADE,
 CHECK(unassigned IN(0,1))
) COMMENT='检查安排指定日期排班调整';
CREATE TABLE eam_inspection_daily_staff_override_person (
 tenant_id BIGINT NOT NULL, arrangement_id BIGINT NOT NULL, rule_key VARCHAR(32) NOT NULL,
 user_id BIGINT NOT NULL COMMENT '实际用户关联ID', sort_no INT NOT NULL,
 PRIMARY KEY(tenant_id,arrangement_id,rule_key,user_id),
 FOREIGN KEY(tenant_id,arrangement_id,rule_key) REFERENCES eam_inspection_daily_staff_override(tenant_id,arrangement_id,rule_key) ON DELETE CASCADE,
 FOREIGN KEY(user_id) REFERENCES eam_sys_user(id)
) COMMENT='指定日期排班候选人员';

-- Retain legacy rows; reproduce their original union-of-users matching semantics.
INSERT INTO eam_inspection_daily_staff_override(tenant_id,arrangement_id,rule_key,work_date,slot_key,unassigned)
SELECT r.tenant_id,s.arrangement_id,MD5(CONCAT(s.arrangement_id,'/',r.roster_date,'/',r.shift_key)),r.roster_date,r.shift_key,
 IF(COUNT(u.user_id)=0,1,0)
FROM eam_inspection_daily_roster r
JOIN eam_inspection_daily_arrangement a ON a.tenant_id=r.tenant_id AND a.plan_id=r.plan_id AND a.frequency='shift'
JOIN eam_inspection_daily_arrangement_slot s ON s.tenant_id=a.tenant_id AND s.arrangement_id=a.id AND s.slot_key=r.shift_key
LEFT JOIN eam_inspection_daily_roster_user u ON u.tenant_id=r.tenant_id AND u.plan_id=r.plan_id AND u.roster_sort=r.sort_no
GROUP BY r.tenant_id,s.arrangement_id,r.roster_date,r.shift_key;
INSERT INTO eam_inspection_daily_staff_override_person(tenant_id,arrangement_id,rule_key,user_id,sort_no)
SELECT r.tenant_id,s.arrangement_id,MD5(CONCAT(s.arrangement_id,'/',r.roster_date,'/',r.shift_key)),u.user_id,MIN(u.sort_no)
FROM eam_inspection_daily_roster r
JOIN eam_inspection_daily_arrangement a ON a.tenant_id=r.tenant_id AND a.plan_id=r.plan_id AND a.frequency='shift'
JOIN eam_inspection_daily_arrangement_slot s ON s.tenant_id=a.tenant_id AND s.arrangement_id=a.id AND s.slot_key=r.shift_key
JOIN eam_inspection_daily_roster_user u ON u.tenant_id=r.tenant_id AND u.plan_id=r.plan_id AND u.roster_sort=r.sort_no
GROUP BY r.tenant_id,s.arrangement_id,r.roster_date,r.shift_key,u.user_id;
