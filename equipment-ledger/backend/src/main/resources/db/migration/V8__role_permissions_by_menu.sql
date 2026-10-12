CREATE TABLE eam_sys_role_menu_permission (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  menu_key VARCHAR(60) NOT NULL,
  actions_json JSON NOT NULL,
  data_scopes_json JSON NOT NULL,
  org_ids_json JSON NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_menu (tenant_id, role_id, menu_key),
  KEY idx_role_menu_role (tenant_id, role_id),
  CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES eam_sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
