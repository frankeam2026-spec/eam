CREATE TABLE eam_home_overview_layout (
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  template_version INT NOT NULL,
  payload JSON NOT NULL,
  row_version INT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (tenant_id, user_id, template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
