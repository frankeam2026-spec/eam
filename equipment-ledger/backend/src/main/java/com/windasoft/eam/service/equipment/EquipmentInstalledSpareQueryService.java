package com.windasoft.eam.service.equipment;

import com.windasoft.eam.dto.equipment.EquipmentInstalledSpareView;
import com.windasoft.eam.repository.inventory.InventoryUsageRepository;
import com.windasoft.eam.repository.repair.RepairWorkflowRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备已安装备件查询用例。
 *
 * <p>维修域保留旧备件记录，库存域负责新库存使用记录；此处统一投影并合并两个来源，
 * 避免设备台账 Repository 直接访问其他领域的数据表。
 */
@Service
public class EquipmentInstalledSpareQueryService {
  private final RepairWorkflowRepository repairWorkflow;
  private final InventoryUsageRepository inventoryUsage;

  public EquipmentInstalledSpareQueryService(
      RepairWorkflowRepository repairWorkflow, InventoryUsageRepository inventoryUsage) {
    this.repairWorkflow = repairWorkflow;
    this.inventoryUsage = inventoryUsage;
  }

  @Transactional(readOnly = true)
  public List<EquipmentInstalledSpareView> installed(long tenant, long equipmentId) {
    var result = new ArrayList<EquipmentInstalledSpareView>();
    for (var row : repairWorkflow.installed(tenant, equipmentId)) {
      result.add(legacyView(row));
    }
    result.addAll(inventoryUsage.installedForEquipment(tenant, equipmentId));
    return List.copyOf(result);
  }

  private static EquipmentInstalledSpareView legacyView(Map<String, Object> row) {
    var view = new EquipmentInstalledSpareView();
    view.setId(string(row.get("id")));
    view.setCode(string(row.get("code")));
    view.setName(string(row.get("name")));
    view.setModel(string(row.get("model")));
    view.setQuantity(decimal(row.get("quantity")));
    view.setStatus(string(row.get("status")));
    view.setInstalledAt(string(row.get("installedAt")));
    return view;
  }

  private static String string(Object value) {
    return value == null ? null : value.toString();
  }

  private static BigDecimal decimal(Object value) {
    if (value == null) return null;
    if (value instanceof BigDecimal decimal) return decimal;
    return new BigDecimal(value.toString());
  }
}
