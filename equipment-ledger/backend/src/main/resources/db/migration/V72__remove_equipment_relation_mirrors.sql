-- Child/BOM IDs have been canonical in their relation tables since V46.
-- Unresolved historical associations remain in eam_equipment_relation_legacy.
CREATE TABLE migration_v72_equipment_relation_archive AS
SELECT id,tenant_id,children_json,related_spares_json FROM eam_equipment;
ALTER TABLE migration_v72_equipment_relation_archive ADD PRIMARY KEY(id);
ALTER TABLE eam_equipment DROP COLUMN children_json,DROP COLUMN related_spares_json;
