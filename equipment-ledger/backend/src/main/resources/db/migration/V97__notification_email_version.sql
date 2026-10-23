-- Scope concurrency protection to the new email maintenance capability.
ALTER TABLE eam_sys_user ADD COLUMN email_version INT NOT NULL DEFAULT 0 COMMENT '通知邮箱版本，防止并发覆盖';
