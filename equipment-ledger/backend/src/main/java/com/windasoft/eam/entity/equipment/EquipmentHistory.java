package com.windasoft.eam.entity.equipment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import lombok.Data;

/** 设备生命周期历史事件的持久化模型。 */
@Data
@TableName("eam_equipment_history")
public class EquipmentHistory {
  @TableId(type = IdType.INPUT)
  private Long id;
  private Long tenantId;
  private Long equipmentId;
  private String recordType;
  private String recordNo;
  private Timestamp occurredAt;
  private String description;
  private String operatorName;
  private String status;
  private String result;
}
