package com.windasoft.eam.service.equipment;

import com.windasoft.eam.dto.equipment.EquipmentInstalledSpareView;
import com.windasoft.eam.repository.equipment.EquipmentSpareRepository;
import com.windasoft.eam.repository.inventory.InventoryUsageRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 统一协调维修旧备件与库存安装记录，不将跨领域组合放入 Repository。 */
/** 已安装备件查询：汇总设备在用备件信息，区分安装记录与库存领用记录。 */
@Service
public class EquipmentInstalledSpareService {
  private final EquipmentSpareRepository repairWorkflow;
  private final InventoryUsageRepository inventoryUsage;

  public EquipmentInstalledSpareService(
      EquipmentSpareRepository repairWorkflow, InventoryUsageRepository inventoryUsage) {
    this.repairWorkflow = repairWorkflow;
    this.inventoryUsage = inventoryUsage;
  }

  public List<EquipmentInstalledSpareView> list(long tenant, long equipmentId) {
    var result = new ArrayList<EquipmentInstalledSpareView>();
    for (var row : repairWorkflow.installed(tenant, equipmentId)) {
      result.add(row);
    }
    result.addAll(inventoryUsage.installedForEquipment(tenant, equipmentId));
    return List.copyOf(result);
  }
}
