-- Seed a usable editable template once during database initialization / upgrade.
-- Existing tenants with active templates keep their own list unchanged.
-- Flyway applies this migration once: later deletion is intentional and is not undone on reads/startup.
INSERT INTO eam_label_template
  (id, tenant_id, name, width_mm, height_mm, remark, elements_json, layout_json, row_version, deleted)
SELECT existing.max_id + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id),
       tenants.tenant_id,
       '标准设备标签（演示）', 80.00, 50.00,
       '80×50mm通用设备标签，包含设备信息和二维码；可直接编辑、复制和打印。',
       JSON_ARRAY(),
       CAST('{"version":1,"columns":[25,25,26],"rows":[8,8,8,8,7,7],"cells":[{"rowSpan":1,"colSpan":3,"content":"{name}","kind":"text","image":"","fontSize":14,"bold":true,"italic":false,"underline":false,"align":"center","vertical":"middle","color":"#111827","background":"#f1f5f9","border":false,"overflow":"shrink","row":0,"col":0},{"rowSpan":1,"colSpan":2,"content":"设备编号：{code}","kind":"text","image":"","fontSize":9,"bold":false,"italic":false,"underline":false,"align":"left","vertical":"middle","color":"#111827","background":"#ffffff","border":false,"overflow":"shrink","row":1,"col":0},{"rowSpan":1,"colSpan":2,"content":"规格型号：{model}","kind":"text","image":"","fontSize":9,"bold":false,"italic":false,"underline":false,"align":"left","vertical":"middle","color":"#111827","background":"#ffffff","border":false,"overflow":"shrink","row":2,"col":0},{"rowSpan":1,"colSpan":2,"content":"所属部门：{department}","kind":"text","image":"","fontSize":9,"bold":false,"italic":false,"underline":false,"align":"left","vertical":"middle","color":"#111827","background":"#ffffff","border":false,"overflow":"shrink","row":3,"col":0},{"rowSpan":3,"colSpan":1,"content":"{code}","kind":"qrcode","image":"","fontSize":9,"bold":false,"italic":false,"underline":false,"align":"center","vertical":"middle","color":"#111827","background":"#ffffff","border":false,"overflow":"shrink","row":1,"col":2},{"rowSpan":1,"colSpan":2,"content":"负责人：{owner}","kind":"text","image":"","fontSize":9,"bold":false,"italic":false,"underline":false,"align":"left","vertical":"middle","color":"#111827","background":"#ffffff","border":false,"overflow":"shrink","row":4,"col":0},{"rowSpan":1,"colSpan":1,"content":"扫码识别设备","kind":"text","image":"","fontSize":8,"bold":false,"italic":false,"underline":false,"align":"center","vertical":"middle","color":"#111827","background":"#ffffff","border":false,"overflow":"shrink","row":4,"col":2},{"rowSpan":1,"colSpan":3,"content":"存放位置：{functionLocation}","kind":"text","image":"","fontSize":9,"bold":false,"italic":false,"underline":false,"align":"left","vertical":"middle","color":"#111827","background":"#ffffff","border":false,"overflow":"shrink","row":5,"col":0}],"margin":{"top":2,"right":2,"bottom":2,"left":2},"repeatHeader":0,"pageNumbers":false,"area":null}' AS JSON),
       0, 0
FROM (SELECT DISTINCT tenant_id FROM eam_org_unit) tenants
CROSS JOIN (SELECT COALESCE(MAX(id), 0) AS max_id FROM eam_label_template) existing
WHERE NOT EXISTS (
  SELECT 1 FROM eam_label_template active
  WHERE active.tenant_id = tenants.tenant_id AND active.deleted = 0
);
