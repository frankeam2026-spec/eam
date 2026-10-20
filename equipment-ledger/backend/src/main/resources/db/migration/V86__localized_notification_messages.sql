ALTER TABLE eam_repair_inbox ADD COLUMN message_key VARCHAR(190) NULL COMMENT '新消息的稳定翻译键；历史为空保持原文', ADD COLUMN message_args JSON NULL COMMENT '消息插值参数快照；仅日志描述，不作为业务数据源';
ALTER TABLE eam_maintenance_inbox ADD COLUMN message_key VARCHAR(190) NULL COMMENT '新消息的稳定翻译键；历史为空保持原文', ADD COLUMN message_args JSON NULL COMMENT '消息插值参数快照；仅日志描述，不作为业务数据源';
ALTER TABLE eam_inspection_inbox ADD COLUMN message_key VARCHAR(190) NULL COMMENT '新消息的稳定翻译键；历史为空保持原文', ADD COLUMN message_args JSON NULL COMMENT '消息插值参数快照；仅日志描述，不作为业务数据源';
ALTER TABLE inv_inbox ADD COLUMN message_key VARCHAR(190) NULL COMMENT '新消息的稳定翻译键；历史为空保持原文', ADD COLUMN message_args JSON NULL COMMENT '消息插值参数快照；仅日志描述，不作为业务数据源';
ALTER TABLE eam_experience_inbox ADD COLUMN message_key VARCHAR(190) NULL COMMENT '新消息的稳定翻译键；历史为空保持原文', ADD COLUMN message_args JSON NULL COMMENT '消息插值参数快照；仅日志描述，不作为业务数据源';
