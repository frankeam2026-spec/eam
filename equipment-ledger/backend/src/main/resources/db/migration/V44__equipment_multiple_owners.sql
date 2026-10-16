CREATE TABLE eam_equipment_owner (
 tenant_id BIGINT NOT NULL,
 equipment_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 owner_name VARCHAR(100) NOT NULL,
 sort_no INT NOT NULL DEFAULT 0,
 PRIMARY KEY (tenant_id,equipment_id,user_id),
 INDEX idx_equipment_owner_user (tenant_id,user_id,equipment_id)
);
INSERT INTO eam_equipment_owner(tenant_id,equipment_id,user_id,owner_name)
 SELECT e.tenant_id,e.id,e.owner_user_id,COALESCE(u.display_name,e.owner_name,'')
 FROM eam_equipment e LEFT JOIN eam_sys_user u ON u.id=e.owner_user_id AND u.tenant_id=e.tenant_id
 WHERE e.owner_user_id IS NOT NULL AND e.deleted=0;
ALTER TABLE eam_equipment MODIFY COLUMN owner_name VARCHAR(6000) NULL;
