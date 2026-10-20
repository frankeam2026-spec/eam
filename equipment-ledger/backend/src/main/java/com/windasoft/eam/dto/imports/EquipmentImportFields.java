package com.windasoft.eam.dto.imports;

import java.util.*;

public final class EquipmentImportFields {
  private EquipmentImportFields() {}

  public static final Map<String, String> STAGING =
      Collections.unmodifiableMap(
          new LinkedHashMap<>(
              Map.ofEntries(
                  Map.entry("code", "code"),
                  Map.entry("name", "name"),
                  Map.entry("category", "category"),
                  Map.entry("model", "model"),
                  Map.entry("serial", "serial"),
                  Map.entry("company", "company"),
                  Map.entry("department", "department"),
                  Map.entry("owner", "owner"),
                  Map.entry("brand", "brand"),
                  Map.entry("state", "state"),
                  Map.entry("useState", "use_state"),
                  Map.entry("source", "source"),
                  Map.entry("acquiredAt", "acquired_at"),
                  Map.entry("amount", "amount"),
                  Map.entry("warranty", "warranty"),
                  Map.entry("remark", "remark"),
                  Map.entry("assetNo", "asset_no"),
                  Map.entry("tagCode", "tag_code"),
                  Map.entry("supplier", "supplier"),
                  Map.entry("unit", "unit"),
                  Map.entry("purchaseDate", "purchase_date"),
                  Map.entry("inServiceDate", "in_service_date"),
                  Map.entry("retirementDate", "retirement_date"),
                  Map.entry("level", "level"),
                  Map.entry("functionLocation", "function_location"),
                  Map.entry("specialEquipment", "special_equipment"),
                  Map.entry("metrologyEquipment", "metrology_equipment"),
                  Map.entry("depreciationEnabled", "depreciation_enabled"),
                  Map.entry("technicalParams", "technical_params"),
                  Map.entry("calibrationDate", "calibration_date"),
                  Map.entry("calibrationValidUntil", "calibration_valid_until"),
                  Map.entry("inspectorName", "inspector_name"),
                  Map.entry("departmentOrgId", "department_org_id"),
                  Map.entry("locationId", "location_id"),
                  Map.entry("categoryId", "category_id"),
                  Map.entry("unitId", "unit_id"),
                  Map.entry("useStateId", "use_state_id"),
                  Map.entry("sourceId", "source_id"),
                  Map.entry("parentCode", "parent_code"))));

  public static List<ImportColumn> core() {
    return List.of(
        new ImportColumn(
            "code",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0078"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0001")),
        new ImportColumn(
            "name",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0081"),
            true,
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0081")),
        new ImportColumn(
            "department",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0098"),
            true,
            com.windasoft.eam.common.MessageContext.text("output.import.help0002")),
        new ImportColumn(
            "category",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0390"),
            true,
            com.windasoft.eam.common.MessageContext.text("output.import.help0003")),
        new ImportColumn(
            "functionLocation",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0108"),
            true,
            com.windasoft.eam.common.MessageContext.text("output.import.help0004")),
        new ImportColumn(
            "unit",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0095"),
            true,
            com.windasoft.eam.common.MessageContext.text("output.import.help0005")),
        new ImportColumn(
            "state",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0111"),
            true,
            com.windasoft.eam.common.MessageContext.text("output.import.help0006")),
        new ImportColumn(
            "useState",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0112"),
            true,
            com.windasoft.eam.common.MessageContext.text("output.import.help0007")),
        new ImportColumn(
            "owner",
            com.windasoft.eam.common.MessageContext.text("output.import.label0001"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0008")),
        new ImportColumn(
            "parentCode",
            com.windasoft.eam.common.MessageContext.text("output.import.label0002"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0009")),
        new ImportColumn(
            "assetNo",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0085"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0010")),
        new ImportColumn(
            "serial",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text1666"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0010")),
        new ImportColumn(
            "tagCode",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0089"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0011")),
        new ImportColumn(
            "model",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0091"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0011")),
        new ImportColumn(
            "brand",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0093"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0011")),
        new ImportColumn(
            "source",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0117"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0012")),
        new ImportColumn(
            "supplier",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0118"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0011")),
        new ImportColumn(
            "amount",
            com.windasoft.eam.common.MessageContext.text("output.import.label0003"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0013")),
        new ImportColumn(
            "purchaseDate",
            com.windasoft.eam.common.MessageContext.text("output.import.label0004"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0014")),
        new ImportColumn(
            "acquiredAt",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0122"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0014")),
        new ImportColumn(
            "inServiceDate",
            com.windasoft.eam.common.MessageContext.text("output.import.label0005"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0014")),
        new ImportColumn(
            "warranty",
            com.windasoft.eam.common.MessageContext.text("output.import.label0006"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0014")),
        new ImportColumn(
            "retirementDate",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0126"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0014")),
        new ImportColumn(
            "level",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0113"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0015")),
        new ImportColumn(
            "specialEquipment",
            com.windasoft.eam.common.MessageContext.text("output.import.label0007"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0016")),
        new ImportColumn(
            "metrologyEquipment",
            com.windasoft.eam.common.MessageContext.text("output.import.label0008"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0016")),
        new ImportColumn(
            "depreciationEnabled",
            com.windasoft.eam.common.MessageContext.text("output.import.label0009"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0016")),
        new ImportColumn(
            "calibrationDate",
            com.windasoft.eam.common.MessageContext.text("output.import.label0010"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0014")),
        new ImportColumn(
            "calibrationValidUntil",
            com.windasoft.eam.common.MessageContext.text("output.import.label0011"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0014")),
        new ImportColumn(
            "inspectorName",
            com.windasoft.eam.common.MessageContext.text("output.import.label0012"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0011")),
        new ImportColumn(
            "technicalParams",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0128"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0011")),
        new ImportColumn(
            "remark",
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0130"),
            false,
            com.windasoft.eam.common.MessageContext.text("output.import.help0011")));
  }
}
