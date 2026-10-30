package com.windasoft.eam.dto.equipment;

import java.util.Objects;

/** BOM物料选择器参数，保留原接口的页码和页大小收敛规则。 */
public record EquipmentBomMaterialQuery(String keyword, int page, int size) {
  public EquipmentBomMaterialQuery {
    Objects.requireNonNull(keyword);
    page = Math.max(1, page);
    size = Math.max(1, Math.min(100, size));
  }
}
