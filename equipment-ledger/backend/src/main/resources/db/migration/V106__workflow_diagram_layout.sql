-- Editable visual artifact only. Execution and authorization use the relational workflow model.
ALTER TABLE wf_version ADD COLUMN diagram_xml MEDIUMTEXT NULL
  COMMENT 'BPMN画布布局产物，仅供编辑展示，不直接部署执行';
