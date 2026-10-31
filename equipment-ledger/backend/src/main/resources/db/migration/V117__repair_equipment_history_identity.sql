CREATE INDEX ix_repair_equipment_history_id
    ON eam_repair_order (tenant_id, equipment_id, reported_at, id);

CREATE INDEX ix_repair_equipment_history_code
    ON eam_repair_order (tenant_id, equipment_code, reported_at, id);
