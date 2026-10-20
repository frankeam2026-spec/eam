CREATE TABLE migration_v66_repair_collaborators_archive (
 tenant_id BIGINT NOT NULL,order_id BIGINT NOT NULL,original_collaborators JSON NULL,
 PRIMARY KEY(tenant_id,order_id)
) COMMENT='V66维修协同人员原始归档，仅供核对和回退';
INSERT INTO migration_v66_repair_collaborators_archive SELECT tenant_id,id,collaborators_json FROM eam_repair_order WHERE collaborators_json IS NOT NULL;
CREATE TABLE eam_repair_collaborator (
 tenant_id BIGINT NOT NULL COMMENT '租户ID',order_id BIGINT NOT NULL COMMENT '维修工单ID',user_id BIGINT NOT NULL COMMENT '协同人员ID',name_snapshot VARCHAR(100) NOT NULL DEFAULT '' COMMENT '登记时姓名',sort_no INT NOT NULL COMMENT '顺序',
 PRIMARY KEY(tenant_id,order_id,user_id),INDEX ix_repair_collaborator_user(tenant_id,user_id,order_id),FOREIGN KEY(order_id) REFERENCES eam_repair_order(id) ON DELETE CASCADE
) COMMENT='维修协同人员关系';
INSERT INTO eam_repair_collaborator SELECT r.tenant_id,r.id,j.user_id,MAX(COALESCE(j.name_snapshot,'')),MIN(j.sort_no) FROM eam_repair_order r JOIN JSON_TABLE(COALESCE(r.collaborators_json,JSON_ARRAY()),'$[*]' COLUMNS(sort_no FOR ORDINALITY,user_id BIGINT PATH '$.id' ERROR ON ERROR,name_snapshot VARCHAR(100) PATH '$.name')) j GROUP BY r.tenant_id,r.id,j.user_id;
ALTER TABLE eam_repair_order DROP COLUMN collaborators_json;
