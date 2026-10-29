ALTER TABLE eam_knowledge_document ADD applies_to_all BOOLEAN NOT NULL DEFAULT FALSE COMMENT '明确选择公司内通用资料';
ALTER TABLE eam_ai_conversation ADD equipment_id BIGINT NULL COMMENT '明确绑定设备ID，使用时复核';
