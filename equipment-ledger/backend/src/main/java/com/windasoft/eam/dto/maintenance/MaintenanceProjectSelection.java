package com.windasoft.eam.dto.maintenance;

import java.util.List;

/** 显式项目选择与旧整标准选择语义不同，不能以空项目集合推断选择方式。 */
public record MaintenanceProjectSelection(
    long typeId, long standardId, boolean explicit, List<MaintenanceProjectReference> references) {
  public MaintenanceProjectSelection {
    references = List.copyOf(references);
  }
}
