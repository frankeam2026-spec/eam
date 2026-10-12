-- Preserve posted issue/allocation identities; extend the issue header into a unified document.
ALTER TABLE inv_issue MODIFY requisition_id BIGINT NULL,
 ADD status VARCHAR(20) NOT NULL DEFAULT '已出库',
 ADD source_kind VARCHAR(20) NOT NULL DEFAULT '历史领料',
 ADD row_version INT NOT NULL DEFAULT 0,
 ADD deleted TINYINT NOT NULL DEFAULT 0,
 ADD payload JSON NULL,
 ADD confirmed_at DATETIME(3) NULL;
UPDATE inv_issue SET confirmed_at=created_at,status=IF(reversed=1,'已冲销','已出库');
ALTER TABLE inv_issue_allocation MODIFY requisition_line_id BIGINT NULL,
 MODIFY reservation_id BIGINT NULL, ADD outbound_line_id BIGINT NULL;
ALTER TABLE inv_usage MODIFY requisition_id BIGINT NULL;
CREATE TABLE inv_outbound_line (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,issue_id BIGINT NOT NULL,line_no INT NOT NULL,
 material_id BIGINT NOT NULL,quantity DECIMAL(20,6) NOT NULL,
 requisition_id BIGINT NULL,requisition_line_id BIGINT NULL,
 task_kind VARCHAR(20) NOT NULL DEFAULT '',task_id BIGINT NULL,task_code VARCHAR(64) NOT NULL DEFAULT '',
 department_id BIGINT NULL,recipient_id BIGINT NULL,recipient_name VARCHAR(100) NOT NULL DEFAULT '',
 UNIQUE KEY uq_outbound_line(tenant_id,issue_id,line_no),
 KEY ix_outbound_task(tenant_id,task_kind,task_id),CHECK(quantity>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE inv_issue ADD KEY ix_outbound_list(tenant_id,deleted,status,created_at);
-- Migrate only existing issue authority, preserving each role's exact scope. No direct issue grant.
SET @outbound_permission_id=(SELECT COALESCE(MAX(id),0) FROM eam_sys_role_menu_permission);
INSERT INTO eam_sys_role_menu_permission(id,tenant_id,role_id,menu_key,actions_json,data_scopes_json,org_ids_json)
 SELECT (@outbound_permission_id:=@outbound_permission_id+1),tenant_id,role_id,'inventoryOutbound',
 JSON_ARRAY('查看','预留','释放预留','确认出库','关闭剩余需求',
 IF(JSON_CONTAINS(actions_json,JSON_QUOTE('导出')),'导出',NULL),
 IF(JSON_CONTAINS(actions_json,JSON_QUOTE('查看金额')),'查看金额',NULL)),data_scopes_json,org_ids_json
 FROM eam_sys_role_menu_permission WHERE menu_key='inventoryRequisition'
 AND JSON_CONTAINS(actions_json,JSON_QUOTE('出库'));
