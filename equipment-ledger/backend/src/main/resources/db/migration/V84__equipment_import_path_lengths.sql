ALTER TABLE eam_equipment_import_row
 MODIFY COLUMN category TEXT NULL COMMENT '待新增设备类别路径',
 MODIFY COLUMN department TEXT NULL COMMENT '待新增设备部门全路径';
