ALTER TABLE eam_equipment ADD COLUMN function_location_id BIGINT NULL,
 ADD INDEX ix_equipment_location(tenant_id,company_org_id,function_location_id);

-- Convert only a unique match within the equipment's company; preserve unmatched historical text.
WITH RECURSIVE location_paths AS (
 SELECT id,tenant_id,company_org_id,name,CAST(name AS CHAR(1200)) path,0 depth
 FROM eam_base_item WHERE item_type='LOCATION' AND deleted=0 AND (parent_id IS NULL OR parent_id=0)
 UNION ALL
 SELECT c.id,c.tenant_id,c.company_org_id,c.name,CONCAT(p.path,'·',c.name),p.depth+1
 FROM eam_base_item c JOIN location_paths p ON c.parent_id=p.id AND c.tenant_id=p.tenant_id
 WHERE c.item_type='LOCATION' AND c.deleted=0 AND p.depth<50
), matches AS (
 SELECT e.id,MIN(l.id) location_id FROM eam_equipment e JOIN location_paths l
 ON l.tenant_id=e.tenant_id AND l.company_org_id=e.company_org_id
 AND (REGEXP_REPLACE(TRIM(e.function_location),'[[:space:]]*·[[:space:]]*','·')=l.path OR TRIM(e.function_location)=l.name)
 WHERE e.deleted=0 GROUP BY e.id HAVING COUNT(*)=1
)
UPDATE eam_equipment e JOIN matches m ON m.id=e.id SET e.function_location_id=m.location_id;
