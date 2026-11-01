package com.windasoft.eam.dto.equipment;

import java.math.BigDecimal;
import lombok.Data;

/** 设备已安装备件的统一只读响应；兼容维修旧备件与库存领用记录。 */
@Data
public class EquipmentInstalledSpareView {
  private String id;
  private String code;
  private String name;
  private String model;
  private BigDecimal quantity;
  private String status;
  private String installedAt;
}
