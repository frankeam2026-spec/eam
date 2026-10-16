package com.windasoft.eam.service.label;

import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.dto.label.LabelPrintForm;
import com.windasoft.eam.dto.label.LabelTemplateForm;
import com.windasoft.eam.entity.label.LabelTemplate;
import com.windasoft.eam.repository.label.LabelTemplateRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LabelTemplateService {
  private final LabelTemplateRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;
  private final LabelLayoutValidator validator;

  public LabelTemplateService(
      LabelTemplateRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      LabelLayoutValidator validator) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
    this.validator = validator;
  }

  public Map<String, Object> list() {
    permissions.require("equipmentTag", "查看");
    return Map.of("data", repository.list(auth.tenantId()).stream().map(this::view).toList());
  }

  public Map<String, Object> create(LabelTemplateForm f) {
    permissions.require("equipmentTag", "新增");
    var row = new LabelTemplate();
    row.setTenantId(auth.tenantId());
    row.setDeleted(0);
    row.setRowVersion(0);
    assign(row, f);
    repository.insert(row);
    return Map.of("data", view(require(row.getId())));
  }

  public Map<String, Object> update(Long id, LabelTemplateForm f) {
    permissions.require("equipmentTag", "编辑");
    var row = require(id);
    assign(row, f);
    if (f.rowVersion() == null) throw new IllegalArgumentException("缺少版本号");
    row.setRowVersion(f.rowVersion());
    if (repository.update(row) != 1) throw new IllegalArgumentException("模板已被修改，请重新打开");
    return Map.of("data", view(require(id)));
  }

  public Map<String, Object> delete(Long id) {
    permissions.require("equipmentTag", "删除");
    require(id);
    repository.delete(id);
    return Map.of("data", true);
  }

  public Map<String, Object> printData(Long id, LabelPrintForm f) {
    permissions.require("equipmentTag", "打印");
    var template = require(id);
    var rows =
        f.equipmentIds().stream()
            .distinct()
            .map(
                key -> {
                  var e = repository.equipment(auth.tenantId(), key);
                  if (e == null) throw new IllegalArgumentException("设备不存在");
                  permissions.checkEquipment(e, "查看");
                  return Map.of(
                      "name",
                      safe(e.getName()),
                      "code",
                      safe(e.getEquipmentCode()),
                      "owner",
                      safe(e.getOwnerName()),
                      "department",
                      safe(e.getDepartment()),
                      "model",
                      safe(e.getModel()));
                })
            .toList();
    return Map.of("template", view(template), "equipment", rows);
  }

  public LabelTemplate require(Long id) {
    var row = repository.find(auth.tenantId(), id);
    if (row == null) throw new IllegalArgumentException("模板不存在");
    return row;
  }

  private void assign(LabelTemplate row, LabelTemplateForm f) {
    if (f.layout() != null && !f.layout().isNull())
      com.windasoft.eam.common.printing.GridValidator.validate(
          f.layout(), f.widthMm().doubleValue(), f.heightMm().doubleValue());
    else validator.validateElements(f);
    if (repository.nameCount(auth.tenantId(), f.name().trim(), row.getId()) > 0)
      throw new IllegalArgumentException("模板名称已存在");
    row.setName(f.name().trim());
    row.setWidthMm(f.widthMm());
    row.setHeightMm(f.heightMm());
    row.setRemark(f.remark() == null ? "" : f.remark());
    row.setElementsJson(f.elements() == null ? "[]" : f.elements().toString());
    row.setLayoutJson(f.layout() == null || f.layout().isNull() ? null : f.layout().toString());
  }

  private Map<String, Object> view(LabelTemplate row) {
    try {
      var result = new LinkedHashMap<String, Object>();
      result.put("id", String.valueOf(row.getId()));
      result.put("name", row.getName());
      result.put("widthMm", row.getWidthMm());
      result.put("heightMm", row.getHeightMm());
      result.put("remark", safe(row.getRemark()));
      result.put("elements", json.readTree(row.getElementsJson()));
      result.put("layout", row.getLayoutJson() == null ? null : json.readTree(row.getLayoutJson()));
      result.put("rowVersion", row.getRowVersion());
      result.put("updatedAt", row.getUpdatedAt() == null ? "" : row.getUpdatedAt().toString());
      return result;
    } catch (Exception e) {
      throw new IllegalStateException("模板内容读取失败", e);
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
