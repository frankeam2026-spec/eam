-- Engineering query plans from isolated 10005-row fixtures showed filesorts for these lists.
-- Keep existing indexes/checksums; add only indexes for current list predicates and stable order.
CREATE INDEX ix_maintenance_definition_page ON eam_maintenance_definition(tenant_id,kind,created_at,id);
CREATE INDEX ix_maintenance_definition_org_page ON eam_maintenance_definition(tenant_id,kind,department_id,created_at,id);
CREATE INDEX ix_inspection_task_page ON eam_inspection_task(tenant_id,kind,deleted,due_at,id);
CREATE INDEX ix_receipt_page ON inv_receipt(tenant_id,warehouse_id,deleted,created_at,id);
CREATE INDEX ix_user_org_page ON eam_sys_user(tenant_id,org_id,deleted,id);
