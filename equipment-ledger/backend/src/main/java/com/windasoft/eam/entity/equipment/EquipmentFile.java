package com.windasoft.eam.entity.equipment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 设备上传文件元数据；文件内容由独立存储处理。 */
@Data
@TableName("eam_equipment_file")
public class EquipmentFile {
  @TableId(type = IdType.INPUT)
  private String id;
  private Long tenantId;
  private Long userId;
  private String name;
  private String extension;
  private String contentType;
  private Long size;
}
