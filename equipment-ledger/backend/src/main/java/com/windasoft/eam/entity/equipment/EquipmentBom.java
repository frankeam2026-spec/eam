package com.windasoft.eam.entity.equipment;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 复合主键由数据库约束，读写须使用完整租户及业务键，禁止按单列主键CRUD。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("eam_equipment_bom")
public class EquipmentBom {
  private Long tenantId;
  private Long equipmentId;
  private Long materialId;
  private BigDecimal quantity;
  private String code;
  private String name;
  private String model;
  private String unit;
  private Integer sortNo;
}
