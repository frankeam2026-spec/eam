ALTER TABLE eam_base_item ADD COLUMN company_org_id BIGINT NULL COMMENT '存放地点所属公司';
ALTER TABLE eam_base_item ADD KEY idx_base_company (tenant_id,item_type,company_org_id,deleted);
ALTER TABLE eam_base_item ADD CONSTRAINT fk_base_company FOREIGN KEY (company_org_id) REFERENCES eam_org_unit(id);
-- 原始车间、工位位置属于苏州公司，保留原有 ID 和层级。
UPDATE eam_base_item b JOIN eam_org_unit o ON o.tenant_id=b.tenant_id AND o.id=71002 AND o.org_type='公司'
SET b.company_org_id=o.id WHERE b.item_type='LOCATION' AND b.company_org_id IS NULL;
