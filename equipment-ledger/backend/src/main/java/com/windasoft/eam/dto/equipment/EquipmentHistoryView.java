package com.windasoft.eam.dto.equipment;

import java.util.List;
import java.util.Map;

public record EquipmentHistoryView(
    List<EquipmentInstalledSpareView> installedSpares,
    EquipmentView data,
    List<Map<String, Object>> repairs,
    List<Map<String, Object>> maintenance,
    List<Map<String, Object>> inspections,
    List<Map<String, Object>> spareChanges) {}
