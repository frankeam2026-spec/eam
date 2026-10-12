-- Short monthly business numbers; internal IDs and relationships remain unchanged.
CREATE TEMPORARY TABLE maintenance_monthly_numbers AS
SELECT id, tenant_id, DATE_FORMAT(created_at, '%Y%m') month_key,
       ROW_NUMBER() OVER (PARTITION BY tenant_id, DATE_FORMAT(created_at, '%Y%m') ORDER BY created_at,id) sequence_no
FROM eam_maintenance_task;

UPDATE eam_maintenance_task SET code=CONCAT('MIG-',id);
UPDATE eam_maintenance_task t JOIN maintenance_monthly_numbers n ON n.id=t.id
SET t.code=CONCAT('BY-',n.month_key,'-',LPAD(n.sequence_no,GREATEST(4,LENGTH(n.sequence_no)),'0'));

INSERT INTO sys_number_counter(rule_code,period_key,next_value)
SELECT CONCAT('maintenance-',tenant_id),month_key,MAX(sequence_no)
FROM maintenance_monthly_numbers GROUP BY tenant_id,month_key
ON DUPLICATE KEY UPDATE next_value=GREATEST(next_value,VALUES(next_value));
DROP TEMPORARY TABLE maintenance_monthly_numbers;

CREATE TEMPORARY TABLE repair_monthly_numbers AS
SELECT id, tenant_id, DATE_FORMAT(reported_at, '%Y%m') month_key,
       ROW_NUMBER() OVER (PARTITION BY tenant_id, DATE_FORMAT(reported_at, '%Y%m') ORDER BY reported_at,id) sequence_no
FROM eam_repair_order;

UPDATE eam_repair_order SET order_no=CONCAT('MIG-',id);
UPDATE eam_repair_order r JOIN repair_monthly_numbers n ON n.id=r.id
SET r.order_no=CONCAT('wx-',n.month_key,'-',LPAD(n.sequence_no,GREATEST(4,LENGTH(n.sequence_no)),'0'));

INSERT INTO sys_number_counter(rule_code,period_key,next_value)
SELECT CONCAT('repair-',tenant_id),month_key,MAX(sequence_no)
FROM repair_monthly_numbers GROUP BY tenant_id,month_key
ON DUPLICATE KEY UPDATE next_value=GREATEST(next_value,VALUES(next_value));
DROP TEMPORARY TABLE repair_monthly_numbers;
