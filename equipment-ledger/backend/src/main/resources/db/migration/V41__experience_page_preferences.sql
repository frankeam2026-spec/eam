CREATE TABLE eam_experience_page_preference (
 tenant_id BIGINT NOT NULL,user_id BIGINT NOT NULL,page_key VARCHAR(40) NOT NULL,
 preferences JSON NOT NULL,updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(tenant_id,user_id,page_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
