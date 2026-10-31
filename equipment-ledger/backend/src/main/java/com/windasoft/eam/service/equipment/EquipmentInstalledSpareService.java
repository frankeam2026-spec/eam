package com.windasoft.eam.service.equipment;

import com.windasoft.eam.dto.equipment.EquipmentInstalledSpareView;
import com.windasoft.eam.repository.inventory.InventoryUsageRepository;
import com.windasoft.eam.repository.repair.RepairWorkflowRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 统一协调维修旧备件与库存安装记录，不将跨领域组合放入 Repository。 */
@Service
public class EquipmentInstalledSpareService {
  private final RepairWorkflowRepository repairWorkflow;
  private final InventoryUsageRepository inventoryUsage;

  public EquipmentInstalledSpareService(
      RepairWorkflowRepository repairWorkflow, InventoryUsageRepository inventoryUsage) {
    this.repairWorkflow = repairWorkflow;
    this.inventoryUsage = inventoryUsage;
  }

  public List<EquipmentInstalledSpareView> list(long tenant, long equipmentId) {
    var result = new ArrayList<EquipmentInstalledSpareView>();
    for (var row : repairWorkflow.installed(tenant, equipmentId)) {
      result.add(legacyView(row));
    }
    result.addAll(inventoryUsage.installedForEquipment(tenant, equipmentId));
    return List.copyOf(result);
  }

  private static EquipmentInstalledSpareView legacyView(Map<String, Object> row) {
    var view = new EquipmentInstalledSpareView();
    view.setId(text(row.get("id")));
    view.setCode(text(row.get("code")));
    view.setName(text(row.get("name")));
    view.setModel(text(row.get("model")));
    view.setQuantity(decimal(row.get("quantity")));
    view.setStatus(nullableText(row.get("status")));
    view.setInstalledAt(nullableText(row.get("installedAt")));
    return view;
  }

  private static String nullableText(Object value) {
    return Objects.toString(value, null);
  }

  private static BigDecimal decimal(Object value) {
    if (value == null) return null;
    if (value instanceof BigDecimal amount) return amount;
    if (value instanceof Number number) return new BigDecimal(number.toString());
    return new BigDecimal(value.toString());
  }
}
