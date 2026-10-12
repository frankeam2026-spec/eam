ALTER TABLE eam_org_unit
  ADD COLUMN active_group_tenant BIGINT GENERATED ALWAYS AS
    (CASE WHEN org_type = '集团' AND deleted = 0 THEN tenant_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_org_single_active_group (active_group_tenant);
