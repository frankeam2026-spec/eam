ALTER TABLE eam_language_message
 ADD COLUMN module_key VARCHAR(100) NOT NULL DEFAULT 'common' COMMENT '词条所属模块；由稳定词条前缀确定';
UPDATE eam_language_message SET module_key=SUBSTRING_INDEX(message_key,'.',2);
CREATE INDEX ix_language_message_module ON eam_language_message(tenant_id,locale,module_key);
