ALTER TABLE eam_equipment ADD COLUMN images_json JSON NULL;
UPDATE eam_equipment
SET images_json = JSON_ARRAY(JSON_OBJECT('id', image_file_id, 'name', COALESCE(image_name, '')))
WHERE image_file_id IS NOT NULL AND image_file_id <> '';
