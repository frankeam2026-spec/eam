package com.windasoft.eam.dto.equipment;

import java.util.List;

public record EquipmentPage(List<EquipmentView> data, PageInfo page) {
  public record PageInfo(long number, long size, long total) {}
}
