ALTER TABLE wf_binding ADD COLUMN requires_approval TINYINT NOT NULL DEFAULT 1 COMMENT '公司业务审批开关，与模板enabled独立',
 ADD CONSTRAINT ck_wf_binding_required CHECK (requires_approval IN (0,1));
ALTER TABLE eam_repair_order
 ADD COLUMN approval_gate VARCHAR(16) NOT NULL DEFAULT 'LEGACY' COMMENT 'LEGACY历史免审批/EXEMPT提交时未启用/REQUIRED需审批',
 ADD COLUMN approval_ready_at DATETIME(3) NULL COMMENT '允许派工时间；审批通过时起算等待派工及接单',
 ADD CONSTRAINT ck_repair_approval_gate CHECK (approval_gate IN ('LEGACY','EXEMPT','REQUIRED'));
ALTER TABLE wf_todo ADD COLUMN source_kind VARCHAR(16) NOT NULL DEFAULT 'INVENTORY' COMMENT 'INVENTORY或REPAIR，精确定位既有业务待办',
 ADD CONSTRAINT ck_wf_todo_source CHECK (source_kind IN ('INVENTORY','REPAIR'));
