package com.windasoft.eam.service.label;

import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.dto.label.LabelPdfForm;
import com.windasoft.eam.dto.label.LabelTemplateForm;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.label.LabelTemplateRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LabelPrintService {
  private final LabelTemplateRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;
  private final LabelLayoutValidator validator;
  private final LabelTemplateService templates;
  private final com.windasoft.eam.service.printing.GridPdfService pdf;

  public LabelPrintService(
      LabelTemplateRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      LabelLayoutValidator validator,
      LabelTemplateService templates,
      com.windasoft.eam.service.printing.GridPdfService pdf) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
    this.validator = validator;
    this.templates = templates;
    this.pdf = pdf;
  }

  public byte[] pdf(Long id, LabelPdfForm f) throws java.io.IOException {
    permissions.require("equipmentTag", "打印");
    var t = templates.require(id);
    List<Map<String, String>> data =
        f.equipmentIds().stream()
            .distinct()
            .map(
                key -> {
                  var e = repository.equipment(auth.tenantId(), key);
                  if (e == null)
                    throw new com.windasoft.eam.common.LocalizedArgumentException(
                        "server.common.message0064");
                  permissions.checkEquipment(e, "查看");
                  return equipmentData(e);
                })
            .toList();
    List<List<Map<String, String>>> details = new ArrayList<>();
    if (t.getLayoutJson() != null && json.readTree(t.getLayoutJson()).path("repeat").isObject())
      for (Long key : f.equipmentIds().stream().distinct().toList()) {
        var e = repository.equipmentById(key);
        var records = repository.repairs(auth.tenantId(), e.getEquipmentCode());
        if (records.size() > 1000)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.common.message0065");
        details.add(
            records.stream()
                .filter(
                    r -> {
                      try {
                        permissions.checkRepair(r, "查看");
                        return true;
                      } catch (org.springframework.web.server.ResponseStatusException denied) {
                        return false;
                      }
                    })
                .map(
                    r ->
                        Map.of(
                            "repair_orderNo",
                            safe(r.getOrderNo()),
                            "repair_reportedAt",
                            r.getReportedAt() == null
                                ? ""
                                : r.getReportedAt().toString().replace('T', ' '),
                            "repair_description",
                            safe(r.getDescription()),
                            "repair_assignee",
                            safe(r.getAssignee()),
                            "repair_status",
                            safe(r.getStatus())))
                .toList());
      }
    return pdf.render(
        t.getWidthMm().doubleValue(),
        t.getHeightMm().doubleValue(),
        t.getLayoutJson() == null ? null : json.readTree(t.getLayoutJson()),
        json.readTree(t.getElementsJson()),
        data,
        f.copies(),
        details);
  }

  public byte[] preview(LabelTemplateForm f) throws java.io.IOException {
    permissions.require("equipmentTag", "查看");
    if (f.layout() != null && !f.layout().isNull())
      com.windasoft.eam.common.printing.GridValidator.validate(
          f.layout(), f.widthMm().doubleValue(), f.heightMm().doubleValue());
    else validator.validateElements(f);
    return pdf.render(
        f.widthMm().doubleValue(),
        f.heightMm().doubleValue(),
        f.layout(),
        f.elements(),
        List.of(
            Map.ofEntries(
                Map.entry("name", "数控车床"),
                Map.entry("code", "EQ-2026-0001"),
                Map.entry("owner", "张建国"),
                Map.entry("department", "机加工一部"),
                Map.entry("model", "CK6150"),
                Map.entry("category", "机床设备"),
                Map.entry("serial", "CK6150-2024-031"),
                Map.entry("brand", "沈阳机床"),
                Map.entry("functionLocation", "A车间"),
                Map.entry("assetNo", "ZC-0001"),
                Map.entry("unit", "台"),
                Map.entry("supplier", "设备供应商"),
                Map.entry("level", "A级"),
                Map.entry("inspectorName", "李明"),
                Map.entry("purchaseDate", "2024-01-15"),
                Map.entry("inServiceDate", "2024-02-01"),
                Map.entry("calibrationDate", "2026-10-10"),
                Map.entry("calibrationValidUntil", "2027-10-10"),
                Map.entry("company", "苏州风塔精密制造有限公司"),
                Map.entry("state", "正常"))),
        1,
        List.of(
            List.of(
                Map.of(
                    "repair_orderNo",
                    "WX-2026-0001",
                    "repair_reportedAt",
                    "2026-10-10 09:00",
                    "repair_description",
                    "主轴运行时有异响",
                    "repair_assignee",
                    "王师傅",
                    "repair_status",
                    "已完成"))));
  }

  private Map<String, String> equipmentData(Equipment e) {
    var d = new LinkedHashMap<String, String>();
    d.put("name", safe(e.getName()));
    d.put("code", safe(e.getEquipmentCode()));
    d.put("owner", safe(e.getOwnerName()));
    d.put("department", safe(e.getDepartment()));
    d.put("model", safe(e.getModel()));
    d.put("company", safe(e.getCompany()));
    d.put("category", safe(e.getCategory()));
    d.put("state", safe(e.getEquipmentStatus()));
    d.put("serial", safe(e.getSerialNo()));
    d.put("brand", safe(e.getBrand()));
    d.put("functionLocation", safe(e.getFunctionLocation()));
    d.put("assetNo", safe(e.getAssetNo()));
    d.put("unit", safe(e.getUnitName()));
    d.put("supplier", safe(e.getSupplier()));
    d.put("level", safe(e.getEquipmentLevel()));
    d.put("inspectorName", safe(e.getInspectorName()));
    d.put("purchaseDate", e.getPurchaseDate() == null ? "" : e.getPurchaseDate().toString());
    d.put("inServiceDate", e.getInServiceDate() == null ? "" : e.getInServiceDate().toString());
    d.put(
        "calibrationDate", e.getCalibrationDate() == null ? "" : e.getCalibrationDate().toString());
    d.put(
        "calibrationValidUntil",
        e.getCalibrationValidUntil() == null ? "" : e.getCalibrationValidUntil().toString());
    return d;
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
