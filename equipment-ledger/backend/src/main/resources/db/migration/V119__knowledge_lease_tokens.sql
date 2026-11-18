-- 租约标识独立于可重置的尝试次数。旧任务保留原状态及到期时间。
ALTER TABLE eam_knowledge_version
  ADD COLUMN parse_lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL
  COMMENT '解析任务当前租约标识，领取或重新排队时更换';
ALTER TABLE eam_knowledge_index_job
  ADD COLUMN lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL
  COMMENT '索引任务当前租约标识，领取或重新排队时更换';
