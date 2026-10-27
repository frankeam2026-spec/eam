ALTER TABLE inv_requisition ADD COLUMN approval_gate VARCHAR(16) NOT NULL DEFAULT 'REQUIRED' COMMENT 'REQUIRED需审批；LEGACY明确历史免审批资格';
UPDATE inv_requisition SET approval_gate='LEGACY' WHERE status<>'草稿';
