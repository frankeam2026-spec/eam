package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 保养附件登记信息；UUID 沿用原存储标识，文件内容由独立存储组件维护。 */
@Data
@TableName("eam_maintenance_file")
public class MaintenanceFile {
  @TableId(type = IdType.INPUT)
  private String id;

  private Long tenantId;
  private Long userId;
  private String name;
  private String extension;
  private String contentType;
  private Long size;
}
