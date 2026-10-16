-- Opening, returned and transferred layers also retain a posted accounting currency.
-- Use the recorded layer currency; no exchange or inferred currency conversion.
UPDATE inv_usage u JOIN inv_issue_allocation a ON a.tenant_id=u.tenant_id AND a.id=u.allocation_id
 JOIN inv_cost_layer c ON c.tenant_id=a.tenant_id AND c.id=a.cost_layer_id
 SET u.currency=c.currency WHERE u.currency IS NULL AND c.currency IS NOT NULL;

ALTER TABLE inv_usage
 ADD equipment_code_snapshot VARCHAR(64) NULL,
 ADD equipment_name_snapshot VARCHAR(150) NULL,
 ADD company_id_snapshot BIGINT NULL,
 ADD department_id_snapshot BIGINT NULL;
-- New usages capture these values when written. Older names are not retroactively invented.
