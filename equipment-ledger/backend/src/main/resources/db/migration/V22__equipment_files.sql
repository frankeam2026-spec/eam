CREATE TABLE eam_equipment_file (
  id CHAR(36) PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  extension VARCHAR(12) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_equipment_file_owner (tenant_id,user_id)
);
