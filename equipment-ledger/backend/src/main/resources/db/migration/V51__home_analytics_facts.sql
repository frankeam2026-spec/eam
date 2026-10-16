ALTER TABLE eam_home_template ADD overview_draft JSON NULL;
ALTER TABLE eam_repair_order
 ADD equipment_id BIGINT NULL, ADD company_org_id BIGINT NULL,
 ADD fault_type_id BIGINT NULL, ADD fault_cause_id BIGINT NULL,
 ADD accepted_at DATETIME(3) NULL, ADD accepted_owner_id BIGINT NULL,
 ADD acceptance_score INT NULL,
 ADD KEY ix_repair_report_analytics(tenant_id,deleted,reported_at,department_org_id),
 ADD KEY ix_repair_accept_analytics(tenant_id,deleted,accepted_at,assignee_user_id);
UPDATE eam_repair_order r JOIN (
 SELECT tenant_id,equipment_code,MIN(id) id,COUNT(*) n FROM eam_equipment GROUP BY tenant_id,equipment_code
) e ON e.tenant_id=r.tenant_id AND e.equipment_code=r.equipment_code AND e.n=1 SET r.equipment_id=e.id;
UPDATE eam_repair_order r JOIN (
 WITH RECURSIVE lineage AS (
 SELECT tenant_id,id origin,id,parent_id,org_type,0 depth FROM eam_org_unit
 UNION ALL SELECT p.tenant_id,l.origin,p.id,p.parent_id,p.org_type,l.depth+1
 FROM lineage l JOIN eam_org_unit p ON p.tenant_id=l.tenant_id AND p.id=l.parent_id WHERE l.org_type<>'公司' AND l.depth<64
 ) SELECT tenant_id,origin,MIN(id) company_id FROM lineage WHERE org_type='公司' GROUP BY tenant_id,origin
) c ON c.tenant_id=r.tenant_id AND c.origin=r.department_org_id SET r.company_org_id=c.company_id;
UPDATE eam_repair_order r JOIN (SELECT tenant_id,name,MIN(id) id FROM eam_base_item WHERE item_type='FAULT_TYPE' AND deleted=0 GROUP BY tenant_id,name HAVING COUNT(*)=1) b
 ON b.tenant_id=r.tenant_id AND b.name=r.fault_type SET r.fault_type_id=b.id;
UPDATE eam_repair_order r JOIN (SELECT tenant_id,name,MIN(id) id FROM eam_base_item WHERE item_type='FAULT_CAUSE' AND deleted=0 GROUP BY tenant_id,name HAVING COUNT(*)=1) b
 ON b.tenant_id=r.tenant_id AND b.name=r.fault_cause SET r.fault_cause_id=b.id;
UPDATE eam_repair_order r JOIN (
 SELECT tenant_id,order_id,created_at,details_json,ROW_NUMBER() OVER(PARTITION BY tenant_id,order_id ORDER BY created_at DESC,id DESC) rn
 FROM eam_repair_event WHERE action='验收通过'
) e ON e.tenant_id=r.tenant_id AND e.order_id=r.id AND e.rn=1
 SET r.accepted_at=e.created_at,r.accepted_owner_id=r.assignee_user_id,
 r.acceptance_score=CAST(JSON_UNQUOTE(JSON_EXTRACT(e.details_json,'$.score')) AS UNSIGNED)
 WHERE r.status='已完成';
ALTER TABLE eam_equipment_anomaly ADD company_org_id BIGINT NULL, ADD department_org_id BIGINT NULL,
 ADD KEY ix_anomaly_analysis(tenant_id,status,discovered_at,equipment_id);
UPDATE eam_equipment_anomaly a JOIN eam_equipment e ON e.tenant_id=a.tenant_id AND e.id=a.equipment_id
 SET a.company_org_id=e.company_org_id,a.department_org_id=e.department_org_id;
ALTER TABLE inv_usage ADD actor_id BIGINT NULL, ADD attribution VARCHAR(24) NOT NULL DEFAULT 'legacy_unassigned',
 ADD cost_amount DECIMAL(20,2) NULL, ADD currency VARCHAR(3) NULL,
 ADD KEY ix_usage_analytics(tenant_id,created_at,equipment_id,material_id);
UPDATE inv_usage SET attribution='equipment' WHERE equipment_id IS NOT NULL;
UPDATE inv_usage u JOIN (SELECT tenant_id,display_name,MIN(id) id FROM eam_sys_user WHERE deleted=0 GROUP BY tenant_id,display_name HAVING COUNT(*)=1) a
 ON a.tenant_id=u.tenant_id AND a.display_name=u.actor_name SET u.actor_id=a.id;
UPDATE inv_usage u JOIN inv_issue_allocation a ON a.tenant_id=u.tenant_id AND a.id=u.allocation_id
 LEFT JOIN inv_outbound_line ol ON ol.tenant_id=a.tenant_id AND ol.id=a.outbound_line_id
 LEFT JOIN inv_requisition q ON q.tenant_id=u.tenant_id AND q.id=u.requisition_id
 LEFT JOIN eam_repair_order r ON r.tenant_id=u.tenant_id AND r.id=COALESCE(ol.task_id,q.task_id) AND COALESCE(ol.task_kind,q.task_kind)='维修'
 LEFT JOIN eam_maintenance_task m ON m.tenant_id=u.tenant_id AND m.id=COALESCE(ol.task_id,q.task_id) AND COALESCE(ol.task_kind,q.task_kind)='保养'
 LEFT JOIN eam_inspection_task it ON it.tenant_id=u.tenant_id AND it.id=COALESCE(ol.task_id,q.task_id) AND COALESCE(ol.task_kind,q.task_kind)='点巡检' AND it.equipment_id>0
 SET u.equipment_id=COALESCE(r.equipment_id,m.equipment_id,it.equipment_id),u.attribution='equipment'
 WHERE u.equipment_id IS NULL AND COALESCE(r.equipment_id,m.equipment_id,it.equipment_id) IS NOT NULL;
-- Each allocation is valued cumulatively, retaining its posted net cost and rounding only to cents.
UPDATE inv_usage u JOIN (
 SELECT x.id,x.tenant_id,
 ROUND(x.budget*x.cumulative/x.available_quantity,2)-ROUND(x.budget*(x.cumulative-x.quantity)/x.available_quantity,2) cost
 FROM (
 SELECT u.id,u.tenant_id,u.quantity,a.amount-a.returned_amount budget,a.quantity-a.returned_quantity available_quantity,
 SUM(u.quantity) OVER(PARTITION BY u.tenant_id,u.allocation_id ORDER BY u.created_at,u.id) cumulative
 FROM inv_usage u JOIN inv_issue_allocation a ON a.tenant_id=u.tenant_id AND a.id=u.allocation_id
 WHERE a.quantity>a.returned_quantity AND a.amount>=a.returned_amount
 ) x WHERE x.cumulative<=x.available_quantity
) v ON v.tenant_id=u.tenant_id AND v.id=u.id SET u.cost_amount=v.cost WHERE u.cost_amount IS NULL;
UPDATE inv_usage u JOIN inv_issue_allocation a ON a.tenant_id=u.tenant_id AND a.id=u.allocation_id
 JOIN inv_cost_layer c ON c.tenant_id=a.tenant_id AND c.id=a.cost_layer_id
 LEFT JOIN inv_receipt_line l ON l.tenant_id=c.tenant_id AND l.id=c.source_line_id
 SET u.currency=l.currency WHERE u.currency IS NULL AND l.currency IS NOT NULL;
CREATE INDEX ix_repair_event_analysis ON eam_repair_event(tenant_id,action,order_id,created_at,id);
CREATE INDEX ix_maintenance_event_analysis ON eam_maintenance_event(tenant_id,task_id,action,created_at,id);
CREATE INDEX ix_inspection_event_analysis ON eam_inspection_event(tenant_id,task_id,action,created_at,id);
