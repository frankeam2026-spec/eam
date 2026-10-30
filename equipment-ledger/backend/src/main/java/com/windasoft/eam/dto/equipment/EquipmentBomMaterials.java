package com.windasoft.eam.dto.equipment;

import java.util.List;

/** 兼容物料选择器原有响应，page仅包含total。 */
public record EquipmentBomMaterials(List<Material> data, Page page) {
  public EquipmentBomMaterials {
    data = List.copyOf(data);
  }

  public record Page(long total) {}

  public record Material(
      String id,
      String code,
      String name,
      String model,
      String unit,
      String category,
      boolean enabled) {}
}
