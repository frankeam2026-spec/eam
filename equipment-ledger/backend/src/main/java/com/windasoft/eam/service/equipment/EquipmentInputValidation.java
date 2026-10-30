package com.windasoft.eam.service.equipment;

import com.windasoft.eam.dto.equipment.EquipmentForm;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 手工录入和文件导入共用的基础字段校验，不产生业务写入。 */
@Service
public class EquipmentInputValidation {
  private final Validator validator;
  private final Set<String> states;

  public EquipmentInputValidation(
      Validator validator, @Value("${eam.equipment.states}") String states) {
    this.validator = validator;
    var allowed = new LinkedHashSet<String>();
    for (String state : states.split(",")) {
      if (!state.isBlank()) {
        allowed.add(state.trim());
      }
    }
    this.states = Collections.unmodifiableSet(allowed);
  }

  public Map<String, String> errors(EquipmentForm f) {
    var errors = new LinkedHashMap<String, String>();
    for (var v : validator.validate(f)) {
      errors.put(
          v.getPropertyPath().toString(),
          com.windasoft.eam.common.MessageContext.annotation(v.getMessage()));
    }
    text(errors, "name", f.name(), 100);
    text(errors, "code", f.code(), 32);
    text(errors, "assetNo", f.assetNo(), 80);
    text(errors, "tagCode", f.tagCode(), 100);
    text(errors, "model", f.model(), 120);
    text(errors, "serial", f.serial(), 120);
    text(errors, "brand", f.brand(), 100);
    text(errors, "supplier", f.supplier(), 160);
    text(errors, "remark", f.remark(), 1000);
    text(errors, "technicalParams", f.technicalParams(), 2000);
    text(errors, "inspectorName", f.inspectorName(), 100);
    text(errors, "level", f.level(), 30);
    if (!states.contains(f.state())) {
      errors.put(
          "state",
          com.windasoft.eam.common.MessageContext.text(
              "server.equipment.message0054", String.join("、", states)));
    }
    if (f.amount() != null
        && (f.amount().stripTrailingZeros().scale() > 2
            || f.amount().precision() - (long) f.amount().scale() > 16)) {
      errors.put(
          "amount", com.windasoft.eam.common.MessageContext.text("server.equipment.message0055"));
    }
    yesNo(errors, "specialEquipment", f.specialEquipment());
    yesNo(errors, "metrologyEquipment", f.metrologyEquipment());
    yesNo(errors, "depreciationEnabled", f.depreciationEnabled());
    return errors;
  }

  public Map<String, String> entityErrors(com.windasoft.eam.entity.equipment.Equipment e) {
    var errors = new LinkedHashMap<String, String>();
    resolvedText(errors, "department", e.getDepartmentPath(), 1200);
    resolvedText(errors, "company", e.getCompany(), 120);
    resolvedText(errors, "category", e.getCategory(), 100);
    resolvedText(errors, "unit", e.getUnitName(), 40);
    resolvedText(errors, "useState", e.getUseStatus(), 40);
    resolvedText(errors, "source", e.getSource(), 40);
    resolvedText(errors, "owner", e.getOwnerName(), 6000);
    resolvedText(errors, "functionLocation", e.getFunctionLocation(), 1200);
    return errors;
  }

  public void validateEntity(com.windasoft.eam.entity.equipment.Equipment e) {
    var errors = entityErrors(e);
    if (!errors.isEmpty()) {
      var entry = errors.entrySet().iterator().next();
      throw new com.windasoft.eam.common.FieldValidationException(entry.getKey(), entry.getValue());
    }
  }

  public void validate(EquipmentForm form) {
    var errors = errors(form);
    if (!errors.isEmpty()) {
      var e = errors.entrySet().iterator().next();
      throw new com.windasoft.eam.common.FieldValidationException(e.getKey(), e.getValue());
    }
  }

  public Set<String> states() {
    return states;
  }

  private static void text(Map<String, String> errors, String field, String value, int limit) {
    if (value != null && value.codePointCount(0, value.length()) > limit) {
      errors.put(
          field,
          com.windasoft.eam.common.MessageContext.text("server.equipment.message0053", limit));
    }
  }

  private static void resolvedText(
      Map<String, String> errors, String field, String value, int limit) {
    if (value != null && value.codePointCount(0, value.length()) > limit) {
      errors.put(
          field,
          com.windasoft.eam.common.MessageContext.text("server.equipment.message0056", limit));
    }
  }

  private static void yesNo(Map<String, String> errors, String field, String value) {
    if (value != null && !value.isBlank() && !Set.of("是", "否").contains(value)) {
      errors.put(field, com.windasoft.eam.common.MessageContext.text("server.imports.message0022"));
    }
  }
}
