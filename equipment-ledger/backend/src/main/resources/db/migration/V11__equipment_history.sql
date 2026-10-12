CREATE TABLE eam_equipment_history (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 equipment_id BIGINT NOT NULL,
 record_type VARCHAR(30) NOT NULL,
 record_no VARCHAR(64) NOT NULL DEFAULT '',
 occurred_at DATETIME NOT NULL,
 description TEXT,
 operator_name VARCHAR(100) NOT NULL DEFAULT '',
 status VARCHAR(30) NOT NULL DEFAULT '',
 result TEXT,
 KEY idx_history_equipment (tenant_id,equipment_id,record_type,occurred_at),
 CONSTRAINT fk_history_equipment FOREIGN KEY (equipment_id) REFERENCES eam_equipment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
