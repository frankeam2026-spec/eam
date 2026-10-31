package com.windasoft.eam.entity.equipment;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 设备负责人关联表。复合主键为 tenant_id、equipment_id、user_id，不使用单列主键 CRUD。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("eam_equipment_owner")
public class EquipmentOwner {
  private Long tenantId;
  private Long equipmentId;
  private Long userId;
  private String ownerName;
  private Integer sortNo;
}
