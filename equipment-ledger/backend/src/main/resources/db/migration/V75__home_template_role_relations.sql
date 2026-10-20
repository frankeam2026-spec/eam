CREATE TABLE migration_v75_eam_home_template_archive AS SELECT tenant_id,id,role_ids FROM eam_home_template;

CREATE TABLE eam_home_template_role (tenant_id BIGINT NOT NULL,template_id BIGINT NOT NULL,role_id BIGINT NOT NULL,sort_no INT NOT NULL,PRIMARY KEY(tenant_id,template_id,role_id),INDEX(tenant_id,role_id),FOREIGN KEY(template_id) REFERENCES eam_home_template(id) ON DELETE CASCADE);

INSERT INTO eam_home_template_role SELECT d.tenant_id,d.id,j.role_id,MIN(j.n) FROM eam_home_template d JOIN JSON_TABLE(COALESCE(d.role_ids,JSON_ARRAY()),'$[*]' COLUMNS(n FOR ORDINALITY,role_id BIGINT PATH '$' ERROR ON ERROR)) j GROUP BY d.tenant_id,d.id,j.role_id;

ALTER TABLE eam_home_template DROP COLUMN role_ids;

CREATE VIEW v_home_template AS SELECT d.*,COALESCE((SELECT JSON_ARRAYAGG(CAST(r.role_id AS CHAR)) OVER(ORDER BY r.sort_no ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM eam_home_template_role r WHERE r.tenant_id=d.tenant_id AND r.template_id=d.id LIMIT 1),JSON_ARRAY()) role_ids FROM eam_home_template d;

CREATE TABLE migration_v75_eam_home_template_version_archive AS SELECT tenant_id,template_id,version,role_ids FROM eam_home_template_version;

CREATE TABLE eam_home_template_version_role (tenant_id BIGINT NOT NULL,template_id BIGINT NOT NULL,version INT NOT NULL,role_id BIGINT NOT NULL,sort_no INT NOT NULL,PRIMARY KEY(tenant_id,template_id,version,role_id),INDEX(tenant_id,role_id),FOREIGN KEY(tenant_id,template_id,version) REFERENCES eam_home_template_version(tenant_id,template_id,version) ON DELETE CASCADE);

INSERT INTO eam_home_template_version_role SELECT d.tenant_id,d.template_id,d.version,j.role_id,MIN(j.n) FROM eam_home_template_version d JOIN JSON_TABLE(COALESCE(d.role_ids,JSON_ARRAY()),'$[*]' COLUMNS(n FOR ORDINALITY,role_id BIGINT PATH '$' ERROR ON ERROR)) j GROUP BY d.tenant_id,d.template_id,d.version,j.role_id;

ALTER TABLE eam_home_template_version DROP COLUMN role_ids;

CREATE VIEW v_home_template_version AS SELECT d.*,COALESCE((SELECT JSON_ARRAYAGG(CAST(r.role_id AS CHAR)) OVER(ORDER BY r.sort_no ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM eam_home_template_version_role r WHERE r.tenant_id=d.tenant_id AND r.template_id=d.template_id AND r.version=d.version LIMIT 1),JSON_ARRAY()) role_ids FROM eam_home_template_version d;
