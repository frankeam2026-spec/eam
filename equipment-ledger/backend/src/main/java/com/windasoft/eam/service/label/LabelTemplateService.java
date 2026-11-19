package com.windasoft.eam.service.label;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.label.LabelPrintForm;
import com.windasoft.eam.dto.label.LabelTemplateForm;
import com.windasoft.eam.dto.label.LabelTemplateView;
import com.windasoft.eam.dto.label.LabelTemplateView.EquipmentFields;
import com.windasoft.eam.dto.label.LabelTemplateView.PrintData;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.label.LabelTemplate;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.label.LabelTemplateRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class LabelTemplateService {
  private final LabelTemplateRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;
  private final LabelLayoutValidator validator;
  private final EquipmentQueryRepository equipment;

  public LabelTemplateService(
      LabelTemplateRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      LabelLayoutValidator validator,
      EquipmentQueryRepository equipment) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
    this.validator = validator;
    this.equipment = equipment;
  }

  public List<LabelTemplateView> list() {
    permissions.require("equipmentTag", PermissionAction.VIEW);
    return repository.list(auth.tenantId()).stream().map(this::view).toList();
  }

  public LabelTemplateView create(LabelTemplateForm f) {
    permissions.require("equipmentTag", PermissionAction.CREATE);
    var row = new LabelTemplate();
    row.setTenantId(auth.tenantId());
    row.setDeleted(0);
    row.setRowVersion(0);
    assign(row, f);
    repository.insert(row);
    return view(require(row.getId()));
  }

  public LabelTemplateView update(Long id, LabelTemplateForm f) {
    permissions.require("equipmentTag", PermissionAction.EDIT);
    var row = require(id);
    assign(row, f);
    if (f.rowVersion() == null) throw new LocalizedArgumentException("server.common.message0066");
    row.setRowVersion(f.rowVersion());
    if (repository.update(row) != 1)
      throw new LocalizedArgumentException("server.common.message0067");
    return view(require(id));
  }

  public void delete(Long id) {
    permissions.require("equipmentTag", PermissionAction.DELETE);
    require(id);
    repository.delete(auth.tenantId(), id);
  }

  public PrintData printData(Long id, LabelPrintForm form) {
    permissions.require("equipmentTag", PermissionAction.PRINT);
    var template = require(id);
    var rows =
        authorizedEquipment(form.equipmentIds()).stream()
            .map(
                row ->
                    new EquipmentFields(
                        safe(row.getName()),
                        safe(row.getEquipmentCode()),
                        safe(row.getOwnerName()),
                        safe(row.getDepartment()),
                        safe(row.getModel())))
            .toList();
    return new PrintData(view(template), rows);
  }

  /** 设备批量读取后按请求顺序逐一授权；重复设备仍只打印一次。 */
  List<Equipment> authorizedEquipment(List<Long> requestedIds) {
    var ids = requestedIds.stream().distinct().toList();
    var byId =
        equipment.findFacts(auth.tenantId(), ids).stream()
            .collect(Collectors.toMap(Equipment::getId, Function.identity()));
    return ids.stream()
        .map(
            id -> {
              var row = byId.get(id);
              if (row == null) {
                throw new LocalizedArgumentException("server.common.message0064");
              }
              permissions.checkEquipment(row, PermissionAction.VIEW);
              return row;
            })
        .toList();
  }

  public LabelTemplate require(Long id) {
    var row = repository.find(auth.tenantId(), id);
    if (row == null) throw new LocalizedArgumentException("server.common.message0068");
    return row;
  }

  private void assign(LabelTemplate row, LabelTemplateForm f) {
    validator.validate(f);
    if (repository.nameCount(auth.tenantId(), f.name().trim(), row.getId()) > 0)
      throw new LocalizedArgumentException("server.common.message0069");
    row.setName(f.name().trim());
    row.setWidthMm(f.widthMm());
    row.setHeightMm(f.heightMm());
    row.setRemark(f.remark() == null ? "" : f.remark());
    row.setElementsJson(f.elements() == null ? "[]" : f.elements().toString());
    row.setLayoutJson(f.layout() == null || f.layout().isNull() ? null : f.layout().toString());
  }

  private LabelTemplateView view(LabelTemplate row) {
    try {
      return new LabelTemplateView(
          String.valueOf(row.getId()),
          row.getName(),
          row.getWidthMm(),
          row.getHeightMm(),
          safe(row.getRemark()),
          json.readTree(row.getElementsJson()),
          row.getLayoutJson() == null ? null : json.readTree(row.getLayoutJson()),
          row.getRowVersion(),
          row.getUpdatedAt() == null ? "" : row.getUpdatedAt().toString());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("模板内容读取失败", e);
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
