-- Preserve historical location text as the equipment's functional location before removing location.
UPDATE eam_equipment
SET function_location = location
WHERE (function_location IS NULL OR function_location = '')
  AND location IS NOT NULL AND location <> '';

UPDATE eam_equipment
SET equipment_status = CASE equipment_status
    WHEN '在用' THEN '正常'
    WHEN '闲置' THEN '正常'
    WHEN '维修中' THEN '故障'
    WHEN '待保养' THEN '带病运行'
    WHEN '正常' THEN '正常'
    WHEN '带病运行' THEN '带病运行'
    WHEN '故障' THEN '故障'
    ELSE '正常'
END;

ALTER TABLE eam_equipment
    MODIFY COLUMN equipment_status VARCHAR(24) NOT NULL DEFAULT '正常',
    DROP COLUMN location,
    DROP COLUMN certificate_status,
    DROP COLUMN certificate_until;
