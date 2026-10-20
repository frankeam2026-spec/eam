CREATE TABLE eam_inspection_daily_submission (
 id BIGINT NOT NULL PRIMARY KEY COMMENT '提交ID',tenant_id BIGINT NOT NULL,task_id BIGINT NOT NULL,
 submitted_by BIGINT NOT NULL,submitted_at DATETIME(6) NOT NULL COMMENT '实际提交时间',
 description TEXT NULL COMMENT '本次提交说明',
 FOREIGN KEY(task_id) REFERENCES eam_inspection_task(id),KEY ix_daily_submission(tenant_id,task_id,submitted_at,id)
) COMMENT='日常点检历次提交，打回后不覆盖';
CREATE TABLE eam_inspection_daily_submission_item (
 tenant_id BIGINT NOT NULL,submission_id BIGINT NOT NULL,item_key VARCHAR(255) NOT NULL,
 value_text TEXT NOT NULL COMMENT '本次提交时的结果原值',abnormal TINYINT NOT NULL,skipped TINYINT NOT NULL,
 resolved TINYINT NOT NULL,measure TEXT NULL,remark TEXT NULL,
 PRIMARY KEY(tenant_id,submission_id,item_key),FOREIGN KEY(submission_id) REFERENCES eam_inspection_daily_submission(id)
) COMMENT='不可变的历史提交项目结果';
CREATE TABLE eam_inspection_daily_submission_file (
 tenant_id BIGINT NOT NULL,submission_id BIGINT NOT NULL,item_key VARCHAR(255) NOT NULL COMMENT '空字符串代表整次提交附件',
 sort_no INT NOT NULL,file_id VARCHAR(64) NOT NULL COMMENT '原附件ID',
 PRIMARY KEY(tenant_id,submission_id,item_key,sort_no),FOREIGN KEY(submission_id) REFERENCES eam_inspection_daily_submission(id)
) COMMENT='历史提交附件引用';
CREATE TABLE eam_inspection_daily_submission_review (
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,submission_id BIGINT NOT NULL,
 reviewer_id BIGINT NOT NULL,decision VARCHAR(16) NOT NULL COMMENT 'accept/reject',
 description TEXT NULL,reviewed_at DATETIME(6) NOT NULL,
 FOREIGN KEY(submission_id) REFERENCES eam_inspection_daily_submission(id),
 UNIQUE KEY uk_daily_submission_review(tenant_id,submission_id)
) COMMENT='日常点检每次提交对应的审核结果';
