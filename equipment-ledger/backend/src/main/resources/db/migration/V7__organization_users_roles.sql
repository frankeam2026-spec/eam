CREATE TABLE eam_org_unit (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  code VARCHAR(60) NOT NULL,
  org_type VARCHAR(20) NOT NULL,
  parent_id BIGINT NULL,
  manager VARCHAR(80) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_org_tenant_code (tenant_id, code, deleted),
  KEY idx_org_tenant_parent (tenant_id, parent_id, status),
  CONSTRAINT fk_org_parent FOREIGN KEY (parent_id) REFERENCES eam_org_unit(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eam_sys_user (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  account VARCHAR(80) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  employee_no VARCHAR(60) NULL,
  phone VARCHAR(32) NULL,
  org_id BIGINT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_tenant_account (tenant_id, account, deleted),
  KEY idx_user_tenant_org (tenant_id, org_id, status),
  CONSTRAINT fk_user_org FOREIGN KEY (org_id) REFERENCES eam_org_unit(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eam_sys_role (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  code VARCHAR(60) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(255) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  data_scope VARCHAR(32) NOT NULL DEFAULT 'DEPARTMENT',
  org_ids_json JSON NULL,
  permissions_json JSON NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_tenant_code (tenant_id, code, deleted),
  KEY idx_role_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eam_sys_user_role (
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, user_id, role_id),
  KEY idx_user_role_role (tenant_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES eam_sys_user(id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES eam_sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO eam_org_unit (id,tenant_id,name,code,org_type,parent_id,manager,status) VALUES
(71001,10001,'风塔集团','FT-GROUP','集团',NULL,'林晓雯',1),
(71002,10001,'苏州风塔精密制造有限公司','FT-SZ','公司',71001,'周海',1),
(71003,10001,'机加工一部','MACH-01','部门',71002,'张建国',1),
(71004,10001,'机加工二部','MACH-02','部门',71002,'陈伟',1),
(71005,10001,'质量部','QUALITY','部门',71002,'刘晓梅',1),
(71006,10001,'无锡风塔装备有限公司','FT-WX','公司',71001,'王志强',1),
(71007,10001,'生产部','PROD','部门',71006,'赵敏',1);

INSERT INTO eam_sys_role (id,tenant_id,code,name,description,status,data_scope,org_ids_json,permissions_json) VALUES
(72001,10001,'SYS_ADMIN','系统管理员','系统配置与组织权限管理',1,'全部数据','[]','["org.查看","org.新增","org.编辑","org.删除","user.查看","user.新增","user.编辑","user.停用","role.查看","role.新增","role.编辑","role.删除"]'),
(72002,10001,'EQUIP_MANAGER','设备管理员','设备台账与维修业务管理',1,'本公司及下级','["71002"]','["equipment.查看","equipment.新增","equipment.编辑","equipment.删除","equipment.导出","repair.查看","repair.报修","repair.派工","repair.处理"]'),
(72003,10001,'DEPT_MANAGER','部门负责人','查看本部门设备与工单',1,'本部门','["71003"]','["equipment.查看","repair.查看","repair.报修","repair.验收"]');

INSERT INTO eam_sys_user (id,tenant_id,account,display_name,employee_no,phone,org_id,status) VALUES
(73001,10001,'admin','林晓雯','FT0001','13800008001',71003,1),
(73002,10001,'zhangjianguo','张建国','FT0018','13800008018',71003,1),
(73003,10001,'chenwei','陈伟','FT0026','13800008026',71004,1),
(73004,10001,'liuxiaomei','刘晓梅','FT0031','13800008031',71005,1);

INSERT INTO eam_sys_user_role (tenant_id,user_id,role_id) VALUES
(10001,73001,72001),(10001,73002,72002),(10001,73002,72003),(10001,73003,72003),(10001,73004,72002);
