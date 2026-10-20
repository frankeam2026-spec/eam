package com.windasoft.eam.dto.imports;

import com.windasoft.eam.dto.equipment.EquipmentForm;
import com.windasoft.eam.entity.equipment.Equipment;
import java.util.*;

/** Trusted, validated new-record input. Never accepted directly from an HTTP request. */
public record PreparedEquipmentImport(
    int row,
    EquipmentForm form,
    Equipment entity,
    String parentCode,
    Map<String, List<Map<String, Object>>> custom,
    Map<Long, String> ownerNames) {}
