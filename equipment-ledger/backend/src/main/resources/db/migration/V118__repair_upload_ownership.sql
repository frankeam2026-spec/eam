CREATE TABLE eam_repair_upload (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全局上传文件ID',
 tenant_id BIGINT NOT NULL COMMENT '上传租户',
 uploaded_by BIGINT NOT NULL COMMENT '上传用户',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间',
 PRIMARY KEY(id),
 KEY ix_repair_upload_owner(tenant_id,uploaded_by,created_at)
) ENGINE=InnoDB COMMENT='维修上传文件原始归属；历史文件沿用已有授权关系，不推测上传人';
