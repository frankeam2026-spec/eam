ALTER TABLE eam_equipment
 DROP INDEX idx_equipment_function_location,
 ADD COLUMN category_id BIGINT NULL,
 ADD COLUMN unit_id BIGINT NULL,
 ADD COLUMN use_status_id BIGINT NULL,
 ADD COLUMN source_id BIGINT NULL,
 ADD COLUMN department_path VARCHAR(1200) NULL,
 MODIFY COLUMN function_location VARCHAR(1200) NULL,
 ADD INDEX idx_equipment_function_location(function_location(191));

UPDATE eam_equipment e JOIN (SELECT tenant_id,name,MIN(id) id FROM eam_base_item WHERE item_type='CATEGORY' AND deleted=0 GROUP BY tenant_id,name HAVING COUNT(*)=1) d ON d.tenant_id=e.tenant_id AND d.name=e.category SET e.category_id=d.id;
UPDATE eam_equipment e JOIN (SELECT tenant_id,name,MIN(id) id FROM eam_base_item WHERE item_type='UNIT' AND deleted=0 GROUP BY tenant_id,name HAVING COUNT(*)=1) d ON d.tenant_id=e.tenant_id AND d.name=e.unit_name SET e.unit_id=d.id;
UPDATE eam_equipment e JOIN (SELECT tenant_id,name,MIN(id) id FROM eam_base_item WHERE item_type='USE_STATUS' AND deleted=0 GROUP BY tenant_id,name HAVING COUNT(*)=1) d ON d.tenant_id=e.tenant_id AND d.name=e.use_status SET e.use_status_id=d.id;
UPDATE eam_equipment e JOIN (SELECT tenant_id,name,MIN(id) id FROM eam_base_item WHERE item_type='SOURCE' AND deleted=0 GROUP BY tenant_id,name HAVING COUNT(*)=1) d ON d.tenant_id=e.tenant_id AND d.name=e.source SET e.source_id=d.id;
UPDATE eam_equipment e JOIN (SELECT tenant_id,display_name,MIN(id) id FROM eam_sys_user WHERE deleted=0 GROUP BY tenant_id,display_name HAVING COUNT(*)=1) u ON u.tenant_id=e.tenant_id AND u.display_name=e.owner_name SET e.owner_user_id=u.id WHERE e.owner_user_id IS NULL;

WITH RECURSIVE org_paths AS (
 SELECT id,tenant_id,CAST(name AS CHAR(1200)) path,0 depth FROM eam_org_unit WHERE parent_id IS NULL OR parent_id=0
 UNION ALL
 SELECT c.id,c.tenant_id,CONCAT(p.path,' · ',c.name),p.depth+1 FROM eam_org_unit c JOIN org_paths p ON c.tenant_id=p.tenant_id AND c.parent_id=p.id WHERE p.depth<50
)
UPDATE eam_equipment e JOIN org_paths p ON p.tenant_id=e.tenant_id AND p.id=e.department_org_id SET e.department_path=p.path;
