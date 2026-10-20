ALTER TABLE eam_maintenance_task
 ADD type_id BIGINT NULL,
 ADD KEY ix_maintenance_task_type(tenant_id,type_id),
 ADD CONSTRAINT fk_maintenance_task_type FOREIGN KEY(type_id) REFERENCES eam_maintenance_definition(id);
ALTER TABLE eam_inspection_definition
 ADD type_id BIGINT NULL,
 ADD KEY ix_inspection_plan_type(tenant_id,type_id),
 ADD CONSTRAINT fk_inspection_plan_type FOREIGN KEY(type_id) REFERENCES eam_inspection_definition(id);
ALTER TABLE eam_inspection_task
 ADD type_id BIGINT NULL,
 ADD KEY ix_inspection_task_type(tenant_id,type_id),
 ADD CONSTRAINT fk_inspection_task_type FOREIGN KEY(type_id) REFERENCES eam_inspection_definition(id);
