package com.windasoft.eam.service.equipment;

import com.windasoft.eam.dto.equipment.EquipmentHistoryView;
import com.windasoft.eam.dto.equipment.EquipmentView;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.equipment.EquipmentHistory;
import com.windasoft.eam.repository.equipment.EquipmentHistoryRepository;
import com.windasoft.eam.repository.repair.RepairQueryRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 设备履历用例：协调维修、生命周期事件和已安装备件三个数据来源。 */
@Service
public class EquipmentHistoryQueryService {
  private final RepairQueryRepository repairs;
  private final EquipmentHistoryRepository history;
  private final EquipmentInstalledSpareService installedSpares;
  private final AuthContext auth;
  private final PermissionService permissions;

  public EquipmentHistoryQueryService(
      RepairQueryRepository repairs,
      EquipmentHistoryRepository history,
      EquipmentInstalledSpareService installedSpares,
      AuthContext auth,
      PermissionService permissions) {
    this.repairs = repairs;
    this.history = history;
    this.installedSpares = installedSpares;
    this.auth = auth;
    this.permissions = permissions;
  }

  @Transactional(readOnly = true)
  public EquipmentHistoryView history(Equipment equipment, EquipmentView equipmentView) {
    var repairHistory =
        repairs.historyByEquipment(
                auth.tenantId(), equipment.getId(), equipment.getEquipmentCode())
            .stream()
            .filter(
                row -> {
                  try {
                    permissions.checkRepair(row, "查看");
                    return true;
                  } catch (ResponseStatusException denied) {
                    return false;
                  }
                })
            .map(
                row ->
                    Map.<String, Object>ofEntries(
                        Map.entry("number", safe(row.getOrderNo())),
                        Map.entry(
                            "time",
                            row.getReportedAt() == null ? "" : row.getReportedAt().toString()),
                        Map.entry("description", safe(row.getDescription())),
                        Map.entry("person", safe(row.getAssignee())),
                        Map.entry("status", safe(row.getStatus())),
                        Map.entry("result", safe(row.getRepairAction()))))
            .toList();

    var records =
        history.list(auth.tenantId(), equipment.getId()).stream()
            .map(EquipmentHistoryQueryService::historyRow)
            .toList();

    return new EquipmentHistoryView(
        installedSpares.list(auth.tenantId(), equipment.getId()),
        equipmentView,
        repairHistory,
        records.stream().filter(row -> "MAINTENANCE".equals(row.get("type"))).toList(),
        records.stream().filter(row -> "INSPECTION".equals(row.get("type"))).toList(),
        records.stream().filter(row -> "SPARE_CHANGE".equals(row.get("type"))).toList());
  }

  private static Map<String, Object> historyRow(EquipmentHistory event) {
    // 保留历史 API 已发布的字段与时间戳类型，避免存储重构改变响应契约。
    var row = new LinkedHashMap<String, Object>();
    row.put("type", event.getRecordType());
    row.put("number", event.getRecordNo());
    row.put("time", event.getOccurredAt());
    row.put("description", event.getDescription());
    row.put("person", event.getOperatorName());
    row.put("status", event.getStatus());
    row.put("result", event.getResult());
    return row;
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
