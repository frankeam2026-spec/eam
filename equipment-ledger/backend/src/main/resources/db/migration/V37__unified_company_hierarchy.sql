-- Preserve organization IDs and historical ownership; group becomes a company display identity.
ALTER TABLE eam_org_unit ADD COLUMN display_type VARCHAR(20) NULL;
UPDATE eam_org_unit SET display_type='集团',org_type='公司' WHERE org_type='集团';
ALTER TABLE eam_org_unit ADD COLUMN active_root_tenant BIGINT GENERATED ALWAYS AS
  (CASE WHEN deleted=0 AND parent_id IS NULL THEN tenant_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_org_single_root(active_root_tenant);
CREATE TABLE eam_org_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL, org_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
  action VARCHAR(20) NOT NULL, before_json JSON NULL, after_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_org_event(tenant_id,org_id,created_at)
);
