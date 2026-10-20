ALTER TABLE eam_import_batch
 ADD COLUMN confirm_version INT NULL COMMENT '首次确认请求携带的批次版本' AFTER confirm_key,
 ADD UNIQUE KEY ux_import_confirm(tenant_id,actor_id,confirm_key);
