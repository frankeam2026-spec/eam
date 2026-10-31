package com.windasoft.eam.repository.equipment;

import org.springframework.stereotype.Repository;

/** 设备编号计数器数据访问；编号生成与冲突处理仍由 Service 编排。 */
@Repository
public class EquipmentNumberRepository {
  private final NumberCounterMapper counter;

  public EquipmentNumberRepository(NumberCounterMapper counter) {
    this.counter = counter;
  }

  public void nextEquipmentNumber(String year, long minimum) {
    counter.nextEquipmentNumber(year, minimum);
  }

  public long currentValue() {
    return counter.currentValue();
  }
}
