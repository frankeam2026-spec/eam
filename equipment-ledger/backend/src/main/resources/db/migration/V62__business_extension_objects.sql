-- Separate schemas share the same typed values, draft/publish and version protocol.
INSERT INTO eam_business_schema(tenant_id,object_key,draft,published)
SELECT t.tenant_id,o.object_key,JSON_OBJECT('fields',JSON_ARRAY()),JSON_OBJECT('fields',JSON_ARRAY())
FROM (SELECT DISTINCT tenant_id FROM eam_org_unit) t
CROSS JOIN (SELECT 'repair_process' object_key UNION ALL SELECT 'inventory_outbound' UNION ALL SELECT 'inventory_receipt' UNION ALL SELECT 'maintenance_plan' UNION ALL SELECT 'maintenance_task' UNION ALL SELECT 'inspection_daily_plan' UNION ALL SELECT 'inspection_daily_task' UNION ALL SELECT 'inspection_periodic_plan' UNION ALL SELECT 'inspection_periodic_task') o
WHERE NOT EXISTS(SELECT 1 FROM eam_business_schema s WHERE s.tenant_id=t.tenant_id AND s.object_key=o.object_key);
