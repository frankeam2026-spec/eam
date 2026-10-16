CREATE TABLE eam_experience_inbox (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,object_kind VARCHAR(20) NOT NULL,object_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,kind VARCHAR(40) NOT NULL,title VARCHAR(250) NOT NULL,
 is_todo TINYINT NOT NULL,done TINYINT NOT NULL DEFAULT 0,read_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_experience_notice(tenant_id,object_kind,object_id,user_id,kind),
 KEY ix_experience_inbox(tenant_id,user_id,is_todo,done,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
