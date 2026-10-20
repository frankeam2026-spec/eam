CREATE TABLE migration_v74_eam_equipment_image_archive AS SELECT id,tenant_id,images_json original_value FROM eam_equipment;

ALTER TABLE migration_v74_eam_equipment_image_archive ADD PRIMARY KEY(id);

CREATE TABLE eam_equipment_image (tenant_id BIGINT NOT NULL,equipment_id BIGINT NOT NULL,sort_no INT NOT NULL,`file_id` VARCHAR(64) NULL COMMENT 'id',`name_snapshot` VARCHAR(255) NULL COMMENT 'name',PRIMARY KEY(tenant_id,equipment_id,sort_no),INDEX(tenant_id,file_id),FOREIGN KEY(equipment_id) REFERENCES eam_equipment(id) ON DELETE CASCADE);

INSERT INTO eam_equipment_image SELECT d.tenant_id,d.id,j.n,NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.id')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.name')),'null') FROM eam_equipment d JOIN JSON_TABLE(COALESCE(d.images_json,JSON_ARRAY()),'$[*]' COLUMNS(n FOR ORDINALITY,value JSON PATH '$')) j;

ALTER TABLE eam_equipment DROP COLUMN images_json;

CREATE TABLE migration_v74_eam_equipment_document_archive AS SELECT id,tenant_id,documents_json original_value FROM eam_equipment;

ALTER TABLE migration_v74_eam_equipment_document_archive ADD PRIMARY KEY(id);

CREATE TABLE eam_equipment_document (tenant_id BIGINT NOT NULL,equipment_id BIGINT NOT NULL,sort_no INT NOT NULL,`file_id` VARCHAR(64) NULL COMMENT 'id',`name_snapshot` VARCHAR(255) NULL COMMENT 'name',`content_type` VARCHAR(150) NULL COMMENT 'contentType',`size_bytes` BIGINT NULL COMMENT 'size',`url_snapshot` VARCHAR(1000) NULL COMMENT 'url',`original_name` VARCHAR(255) NULL COMMENT 'originalName',`document_code` VARCHAR(64) NULL COMMENT 'code',`document_type` VARCHAR(100) NULL COMMENT 'type',`security_level` VARCHAR(24) NULL COMMENT 'security',`quantity` DECIMAL(18,2) NULL COMMENT 'quantity',`remark` TEXT NULL COMMENT 'remark',PRIMARY KEY(tenant_id,equipment_id,sort_no),INDEX(tenant_id,file_id),FOREIGN KEY(equipment_id) REFERENCES eam_equipment(id) ON DELETE CASCADE);

INSERT INTO eam_equipment_document SELECT d.tenant_id,d.id,j.n,NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.id')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.name')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.contentType')),'null'),CAST(NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.size')),'null'),'') AS SIGNED),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.url')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.originalName')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.code')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.type')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.security')),'null'),CAST(NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.quantity')),'null'),'') AS DECIMAL(18,2)),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.remark')),'null') FROM eam_equipment d JOIN JSON_TABLE(COALESCE(d.documents_json,JSON_ARRAY()),'$[*]' COLUMNS(n FOR ORDINALITY,value JSON PATH '$')) j;

ALTER TABLE eam_equipment DROP COLUMN documents_json;

CREATE TABLE migration_v74_eam_repair_report_file_archive AS SELECT id,tenant_id,attachments_json original_value FROM eam_repair_order;

ALTER TABLE migration_v74_eam_repair_report_file_archive ADD PRIMARY KEY(id);

CREATE TABLE eam_repair_report_file (tenant_id BIGINT NOT NULL,order_id BIGINT NOT NULL,sort_no INT NOT NULL,`file_id` VARCHAR(64) NULL COMMENT 'id',`name_snapshot` VARCHAR(255) NULL COMMENT 'name',`content_type` VARCHAR(150) NULL COMMENT 'contentType',`size_bytes` BIGINT NULL COMMENT 'size',`url_snapshot` VARCHAR(1000) NULL COMMENT 'url',PRIMARY KEY(tenant_id,order_id,sort_no),INDEX(tenant_id,file_id),FOREIGN KEY(order_id) REFERENCES eam_repair_order(id) ON DELETE CASCADE);

INSERT INTO eam_repair_report_file SELECT d.tenant_id,d.id,j.n,NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.id')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.name')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.contentType')),'null'),CAST(NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.size')),'null'),'') AS SIGNED),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.url')),'null') FROM eam_repair_order d JOIN JSON_TABLE(COALESCE(d.attachments_json,JSON_ARRAY()),'$[*]' COLUMNS(n FOR ORDINALITY,value JSON PATH '$')) j;

ALTER TABLE eam_repair_order DROP COLUMN attachments_json;

CREATE TABLE migration_v74_eam_repair_process_file_archive AS SELECT id,tenant_id,process_attachments_json original_value FROM eam_repair_order;

ALTER TABLE migration_v74_eam_repair_process_file_archive ADD PRIMARY KEY(id);

CREATE TABLE eam_repair_process_file (tenant_id BIGINT NOT NULL,order_id BIGINT NOT NULL,sort_no INT NOT NULL,`file_id` VARCHAR(64) NULL COMMENT 'id',`name_snapshot` VARCHAR(255) NULL COMMENT 'name',`content_type` VARCHAR(150) NULL COMMENT 'contentType',`size_bytes` BIGINT NULL COMMENT 'size',`url_snapshot` VARCHAR(1000) NULL COMMENT 'url',PRIMARY KEY(tenant_id,order_id,sort_no),INDEX(tenant_id,file_id),FOREIGN KEY(order_id) REFERENCES eam_repair_order(id) ON DELETE CASCADE);

INSERT INTO eam_repair_process_file SELECT d.tenant_id,d.id,j.n,NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.id')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.name')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.contentType')),'null'),CAST(NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.size')),'null'),'') AS SIGNED),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.url')),'null') FROM eam_repair_order d JOIN JSON_TABLE(COALESCE(d.process_attachments_json,JSON_ARRAY()),'$[*]' COLUMNS(n FOR ORDINALITY,value JSON PATH '$')) j;

ALTER TABLE eam_repair_order DROP COLUMN process_attachments_json;

CREATE TABLE migration_v74_eam_anomaly_attachment_archive AS SELECT id,tenant_id,attachments original_value FROM eam_equipment_anomaly;

ALTER TABLE migration_v74_eam_anomaly_attachment_archive ADD PRIMARY KEY(id);

CREATE TABLE eam_anomaly_attachment (tenant_id BIGINT NOT NULL,anomaly_id BIGINT NOT NULL,sort_no INT NOT NULL,`file_id` VARCHAR(64) NULL COMMENT 'id',`name_snapshot` VARCHAR(255) NULL COMMENT 'name',`content_type` VARCHAR(150) NULL COMMENT 'contentType',`size_bytes` BIGINT NULL COMMENT 'size',`url_snapshot` VARCHAR(1000) NULL COMMENT 'url',PRIMARY KEY(tenant_id,anomaly_id,sort_no),INDEX(tenant_id,file_id),FOREIGN KEY(anomaly_id) REFERENCES eam_equipment_anomaly(id) ON DELETE CASCADE);

INSERT INTO eam_anomaly_attachment SELECT d.tenant_id,d.id,j.n,NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.id')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.name')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.contentType')),'null'),CAST(NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.size')),'null'),'') AS SIGNED),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.url')),'null') FROM eam_equipment_anomaly d JOIN JSON_TABLE(COALESCE(d.attachments,JSON_ARRAY()),'$[*]' COLUMNS(n FOR ORDINALITY,value JSON PATH '$')) j;

ALTER TABLE eam_equipment_anomaly DROP COLUMN attachments;

CREATE TABLE migration_v74_eam_repair_plan_file_archive AS SELECT id,tenant_id,attachments original_value FROM eam_single_repair_plan;

ALTER TABLE migration_v74_eam_repair_plan_file_archive ADD PRIMARY KEY(id);

CREATE TABLE eam_repair_plan_file (tenant_id BIGINT NOT NULL,plan_id BIGINT NOT NULL,sort_no INT NOT NULL,`file_id` VARCHAR(64) NULL COMMENT 'id',`name_snapshot` VARCHAR(255) NULL COMMENT 'name',`content_type` VARCHAR(150) NULL COMMENT 'contentType',`size_bytes` BIGINT NULL COMMENT 'size',`url_snapshot` VARCHAR(1000) NULL COMMENT 'url',PRIMARY KEY(tenant_id,plan_id,sort_no),INDEX(tenant_id,file_id),FOREIGN KEY(plan_id) REFERENCES eam_single_repair_plan(id) ON DELETE CASCADE);

INSERT INTO eam_repair_plan_file SELECT d.tenant_id,d.id,j.n,NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.id')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.name')),'null'),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.contentType')),'null'),CAST(NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.size')),'null'),'') AS SIGNED),NULLIF(JSON_UNQUOTE(JSON_EXTRACT(j.value,'$.url')),'null') FROM eam_single_repair_plan d JOIN JSON_TABLE(COALESCE(d.attachments,JSON_ARRAY()),'$[*]' COLUMNS(n FOR ORDINALITY,value JSON PATH '$')) j;

ALTER TABLE eam_single_repair_plan DROP COLUMN attachments;

CREATE VIEW v_anomaly AS SELECT d.*,COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('id',r.`file_id`,'name',r.`name_snapshot`,'contentType',r.`content_type`,'size',r.`size_bytes`,'url',r.`url_snapshot`)) OVER(ORDER BY r.sort_no ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM eam_anomaly_attachment r WHERE r.tenant_id=d.tenant_id AND r.anomaly_id=d.id LIMIT 1),JSON_ARRAY()) attachments FROM eam_equipment_anomaly d;

CREATE VIEW v_single_repair_plan AS SELECT d.*,COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('id',r.`file_id`,'name',r.`name_snapshot`,'contentType',r.`content_type`,'size',r.`size_bytes`,'url',r.`url_snapshot`)) OVER(ORDER BY r.sort_no ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM eam_repair_plan_file r WHERE r.tenant_id=d.tenant_id AND r.plan_id=d.id LIMIT 1),JSON_ARRAY()) attachments FROM eam_single_repair_plan d;
