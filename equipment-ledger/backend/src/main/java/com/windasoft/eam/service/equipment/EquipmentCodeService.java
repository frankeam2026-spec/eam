package com.windasoft.eam.service.equipment;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.repository.equipment.EquipmentNumberRepository;
import com.windasoft.eam.repository.equipment.EquipmentWriteRepository;
import com.windasoft.eam.service.system.AuthContext;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** 设备编号校验与生成；由调用方的业务事务保证计数器及设备写入一致性。 */
@Service
public class EquipmentCodeService {
  private final EquipmentWriteRepository equipment;
  private final EquipmentNumberRepository counter;
  private final AuthContext auth;

  public EquipmentCodeService(
      EquipmentWriteRepository equipment, EquipmentNumberRepository counter, AuthContext auth) {
    this.equipment = equipment;
    this.counter = counter;
    this.auth = auth;
  }

  public void assertAvailable(String code, Long currentId) {
    if (equipment.countCode(auth.tenantId(), code, currentId) > 0) {
      throw new LocalizedArgumentException("server.equipment.message0045");
    }
  }

  public String nextCode() {
    String year = String.valueOf(LocalDate.now().getYear());
    long minimum = 0;
    while (true) {
      counter.nextEquipmentNumber(year, minimum);
      long sequence = counter.currentValue();
      String code = "EQ-" + year + "-" + String.format("%04d", sequence);
      if (equipment.countCode(auth.tenantId(), code, null) == 0) {
        return code;
      }
      // 当前事务中修复缺失或落后的计数器，不得复用已占用的设备编号。
      minimum = Math.max(sequence, equipment.maxEquipmentNumber(year));
    }
  }
}
