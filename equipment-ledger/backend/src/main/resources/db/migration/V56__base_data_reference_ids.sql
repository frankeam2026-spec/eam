-- Dictionary references retain stable IDs and display snapshots.
ALTER TABLE inv_material
 ADD unit_id BIGINT NULL, ADD category_id BIGINT NULL,
 ADD KEY ix_material_unit_id(tenant_id,unit_id), ADD KEY ix_material_category_id(tenant_id,category_id),
 ADD CONSTRAINT fk_material_unit_dictionary FOREIGN KEY(unit_id) REFERENCES eam_base_item(id),
 ADD CONSTRAINT fk_material_category_dictionary FOREIGN KEY(category_id) REFERENCES eam_base_item(id);
ALTER TABLE eam_repair_order
 ADD processing_result_id BIGINT NULL,
 ADD KEY ix_repair_processing_result(tenant_id,processing_result_id),
 ADD CONSTRAINT fk_repair_result_dictionary FOREIGN KEY(processing_result_id) REFERENCES eam_base_item(id);
ALTER TABLE eam_single_repair_plan
 ADD fault_type_id BIGINT NULL, ADD KEY ix_repair_plan_fault_type(tenant_id,fault_type_id),
 ADD CONSTRAINT fk_repair_plan_fault_type_dictionary FOREIGN KEY(fault_type_id) REFERENCES eam_base_item(id);

-- The resolved result is an immutable system dictionary entry, never a virtual text option.
INSERT INTO eam_base_item(id,tenant_id,item_type,code,name,parent_id,decimal_precision,sort_no,status,deleted)
SELECT (SELECT COALESCE(MAX(id),0) FROM eam_base_item)+ROW_NUMBER() OVER(ORDER BY t.tenant_id),
 t.tenant_id,'PROCESS_RESULT','SYS_RESOLVED','已解决',NULL,0,-100,1,0
FROM (SELECT DISTINCT tenant_id FROM eam_org_unit UNION SELECT DISTINCT tenant_id FROM eam_base_item) t
WHERE NOT EXISTS(SELECT 1 FROM eam_base_item b WHERE b.tenant_id=t.tenant_id AND b.item_type='PROCESS_RESULT' AND b.code='SYS_RESOLVED' AND b.deleted=0);

-- Only unique same-tenant/type names can be mapped. Unresolved text stays intact.
UPDATE inv_material m JOIN (SELECT tenant_id,name,MIN(id) id FROM eam_base_item WHERE item_type='UNIT' AND deleted=0 GROUP BY tenant_id,name HAVING COUNT(*)=1) b
 ON b.tenant_id=m.tenant_id AND b.name=m.unit SET m.unit_id=b.id WHERE m.unit_id IS NULL;
UPDATE inv_material m JOIN (SELECT tenant_id,name,MIN(id) id FROM eam_base_item WHERE item_type='MATERIAL_CATEGORY' AND deleted=0 GROUP BY tenant_id,name HAVING COUNT(*)=1) b
 ON b.tenant_id=m.tenant_id AND b.name=m.category SET m.category_id=b.id WHERE m.category_id IS NULL;
UPDATE eam_repair_order r JOIN (SELECT tenant_id,name,MIN(id) id FROM eam_base_item WHERE item_type='FAULT_TYPE' AND deleted=0 GROUP BY tenant_id,name HAVING COUNT(*)=1) b
 ON b.tenant_id=r.tenant_id AND b.name=r.fault_type SET r.fault_type_id=b.id WHERE r.fault_type_id IS NULL;
UPDATE eam_repair_order r JOIN (SELECT tenant_id,name,MIN(id) id FROM eam_base_item WHERE item_type='FAULT_CAUSE' AND deleted=0 GROUP BY tenant_id,name HAVING COUNT(*)=1) b
 ON b.tenant_id=r.tenant_id AND b.name=r.fault_cause SET r.fault_cause_id=b.id WHERE r.fault_cause_id IS NULL;
UPDATE eam_repair_order r JOIN eam_base_item b ON b.tenant_id=r.tenant_id AND b.item_type='PROCESS_RESULT' AND b.code='SYS_RESOLVED' AND b.deleted=0
 SET r.processing_result_id=b.id WHERE r.processing_result='已解决' AND r.processing_result_id IS NULL;
UPDATE eam_repair_order r JOIN (SELECT tenant_id,name,MIN(id) id FROM eam_base_item WHERE item_type='PROCESS_RESULT' AND deleted=0 GROUP BY tenant_id,name HAVING COUNT(*)=1) b
 ON b.tenant_id=r.tenant_id AND b.name=r.processing_result SET r.processing_result_id=b.id WHERE r.processing_result_id IS NULL;
UPDATE eam_single_repair_plan r JOIN (SELECT tenant_id,name,MIN(id) id FROM eam_base_item WHERE item_type='FAULT_TYPE' AND deleted=0 GROUP BY tenant_id,name HAVING COUNT(*)=1) b
 ON b.tenant_id=r.tenant_id AND b.name=r.fault_type SET r.fault_type_id=b.id WHERE r.fault_type_id IS NULL;
