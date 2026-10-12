CREATE INDEX idx_repair_assignee_queue
    ON eam_repair_order (tenant_id, assignee_user_id, deleted, status, reported_at, id);
CREATE INDEX idx_repair_reporter_queue
    ON eam_repair_order (tenant_id, reporter_user_id, deleted, status, reported_at, id);
CREATE INDEX idx_repair_tenant_queue
    ON eam_repair_order (tenant_id, deleted, status, reported_at, id);
