-- Existing material values are retained and become group-maintained dictionary entries.
INSERT INTO eam_base_item(id,tenant_id,item_type,code,name,parent_id,decimal_precision,sort_no,status,deleted)
SELECT (SELECT COALESCE(MAX(id),0) FROM eam_base_item) + ROW_NUMBER() OVER(ORDER BY v.tenant_id,v.name),v.tenant_id,'MATERIAL_CATEGORY',CONCAT('MC-',v.tenant_id,'-',ROW_NUMBER() OVER(ORDER BY v.tenant_id,v.name)),v.name,NULL,0,0,1,0
FROM (SELECT DISTINCT tenant_id,category name FROM inv_material WHERE category<>'' AND CHAR_LENGTH(category)<=100) v
WHERE NOT EXISTS(SELECT 1 FROM eam_base_item b WHERE b.tenant_id=v.tenant_id AND b.item_type='MATERIAL_CATEGORY' AND b.name=v.name AND b.deleted=0);
INSERT INTO eam_base_item(id,tenant_id,item_type,code,name,parent_id,decimal_precision,sort_no,status,deleted)
SELECT (SELECT COALESCE(MAX(id),0) FROM eam_base_item) + ROW_NUMBER() OVER(ORDER BY v.tenant_id,v.name),v.tenant_id,'UNIT',CONCAT('MU-',v.tenant_id,'-',ROW_NUMBER() OVER(ORDER BY v.tenant_id,v.name)),v.name,NULL,6,0,1,0
FROM (SELECT DISTINCT tenant_id,unit name FROM inv_material WHERE unit<>'') v
WHERE NOT EXISTS(SELECT 1 FROM eam_base_item b WHERE b.tenant_id=v.tenant_id AND b.item_type='UNIT' AND b.name=v.name AND b.deleted=0);
INSERT INTO inv_bin(id,tenant_id,warehouse_id,code,name,enabled)
SELECT GREATEST((SELECT COALESCE(MAX(id),0) FROM inv_bin),(SELECT COALESCE(MAX(id),0) FROM inv_warehouse)) + ROW_NUMBER() OVER(ORDER BY w.id),w.tenant_id,w.id,'DEFAULT','默认库位',1
FROM inv_warehouse w WHERE NOT EXISTS(SELECT 1 FROM inv_bin b WHERE b.tenant_id=w.tenant_id AND b.warehouse_id=w.id);

UPDATE inv_inbox SET kind='待出库' WHERE route_kind='requisitions' AND kind='待发料';
