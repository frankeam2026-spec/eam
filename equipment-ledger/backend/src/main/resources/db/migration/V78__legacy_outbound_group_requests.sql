-- Historical merged outbound groups retain typed source links; their original snapshot is archived.
CREATE TABLE migration_v78_issue_group_archive AS SELECT tenant_id,id,request_ids FROM inv_issue_group;
ALTER TABLE migration_v78_issue_group_archive ADD PRIMARY KEY(tenant_id,id);
CREATE TABLE inv_issue_group_request (
 tenant_id BIGINT NOT NULL COMMENT '租户ID',group_id BIGINT NOT NULL COMMENT '历史合并出库组ID',
 requisition_id BIGINT NOT NULL COMMENT '来源领料申请ID',sort_no INT NOT NULL COMMENT '顺序',
 PRIMARY KEY(tenant_id,group_id,requisition_id),INDEX(tenant_id,requisition_id),
 FOREIGN KEY(group_id) REFERENCES inv_issue_group(id) ON DELETE CASCADE
) COMMENT='历史合并出库组来源申请';
INSERT INTO inv_issue_group_request SELECT g.tenant_id,g.id,j.request_id,MIN(j.n)
FROM inv_issue_group g JOIN JSON_TABLE(COALESCE(g.request_ids,JSON_ARRAY()),'$[*]'
COLUMNS(n FOR ORDINALITY,request_id BIGINT PATH '$' ERROR ON ERROR)) j GROUP BY g.tenant_id,g.id,j.request_id;
ALTER TABLE inv_issue_group DROP COLUMN request_ids;
