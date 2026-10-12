-- Convert generated standard codes; preserve codes entered manually.
CREATE TEMPORARY TABLE standard_monthly_numbers AS
SELECT id, tenant_id, DATE_FORMAT(created_at,'%Y%m') month_key,
       ROW_NUMBER() OVER (PARTITION BY tenant_id,DATE_FORMAT(created_at,'%Y%m') ORDER BY created_at,id) sequence_no
FROM eam_maintenance_definition WHERE kind='standards' AND code REGEXP '^MS-[0-9]{10,}$';

UPDATE eam_maintenance_definition d JOIN standard_monthly_numbers n ON n.id=d.id
SET d.code=CONCAT('MS-',n.month_key,'-',LPAD(n.sequence_no,GREATEST(4,LENGTH(n.sequence_no)),'0')),
    d.payload=JSON_SET(d.payload,'$.code',CONCAT('MS-',n.month_key,'-',LPAD(n.sequence_no,GREATEST(4,LENGTH(n.sequence_no)),'0')));

INSERT INTO sys_number_counter(rule_code,period_key,next_value)
SELECT CONCAT('standard-',tenant_id),month_key,MAX(sequence_no)
FROM standard_monthly_numbers GROUP BY tenant_id,month_key
ON DUPLICATE KEY UPDATE next_value=GREATEST(next_value,VALUES(next_value));
DROP TEMPORARY TABLE standard_monthly_numbers;
