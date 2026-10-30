package com.windasoft.eam.entity.equipment;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 复合主键由数据库约束，读写须使用完整租户及业务键，禁止按单列主键CRUD。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("eam_equipment_child")
public class EquipmentChild {
  private Long tenantId;
  private Long parentId;
  private Long childId;
  private Integer sortNo;
}
