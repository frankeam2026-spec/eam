package com.windasoft.eam.service.equipment;

import com.windasoft.eam.dto.equipment.EquipmentForm;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Shared side-effect-free primitive validation for manual entry and file imports. */
@Service
public class EquipmentInputValidation {
  private final Validator validator;
  private final Set<String> states;

  public EquipmentInputValidation(
      Validator validator, @Value("${eam.equipment.states}") String states) {
    this.validator = validator;
    this.states = new HashSet<>(Arrays.asList(states.split(",")));
  }

  public Map<String, String> errors(EquipmentForm f) {
    var errors = new LinkedHashMap<String, String>();
    for (var v : validator.validate(f))
      errors.put(
          v.getPropertyPath().toString(),
          com.windasoft.eam.common.MessageContext.annotation(v.getMessage()));
    Map<String, Object> strings = new LinkedHashMap<>();
    strings.put("name", f.name());
    strings.put("code", f.code());
    strings.put("assetNo", f.assetNo());
    strings.put("tagCode", f.tagCode());
    strings.put("model", f.model());
    strings.put("serial", f.serial());
    strings.put("brand", f.brand());
    strings.put("supplier", f.supplier());
    strings.put("remark", f.remark());
    strings.put("technicalParams", f.technicalParams());
    strings.put("inspectorName", f.inspectorName());
    strings.put("level", f.level());
    Map<String, Integer> limits =
        Map.ofEntries(
            Map.entry("name", 100),
            Map.entry("code", 32),
            Map.entry("assetNo", 80),
            Map.entry("tagCode", 100),
            Map.entry("model", 120),
            Map.entry("serial", 120),
            Map.entry("brand", 100),
            Map.entry("supplier", 160),
            Map.entry("remark", 1000),
            Map.entry("technicalParams", 2000),
            Map.entry("inspectorName", 100),
            Map.entry("level", 30));
    strings.forEach(
        (key, value) -> {
          if (value != null
              && value.toString().codePointCount(0, value.toString().length()) > limits.get(key))
            errors.put(
                key,
                com.windasoft.eam.common.MessageContext.text(
                    "server.equipment.message0053", limits.get(key)));
        });
    if (!states.contains(f.state()))
      errors.put(
          "state",
          com.windasoft.eam.common.MessageContext.text(
              "server.equipment.message0054", String.join("、", states)));
    if (f.amount() != null
        && (f.amount().stripTrailingZeros().scale() > 2
            || f.amount().precision() - (long) f.amount().scale() > 16))
      errors.put(
          "amount", com.windasoft.eam.common.MessageContext.text("server.equipment.message0055"));
    for (var entry :
        Map.of(
                "specialEquipment",
                Objects.toString(f.specialEquipment(), ""),
                "metrologyEquipment",
                Objects.toString(f.metrologyEquipment(), ""),
                "depreciationEnabled",
                Objects.toString(f.depreciationEnabled(), ""))
            .entrySet())
      if (!entry.getValue().isBlank() && !Set.of("是", "否").contains(entry.getValue()))
        errors.put(
            entry.getKey(),
            com.windasoft.eam.common.MessageContext.text("server.imports.message0022"));
    return errors;
  }

  public Map<String, String> entityErrors(com.windasoft.eam.entity.equipment.Equipment e) {
    var errors = new LinkedHashMap<String, String>();
    Map<String, String> strings =
        Map.ofEntries(
            Map.entry("department", Objects.toString(e.getDepartmentPath(), "")),
            Map.entry("company", Objects.toString(e.getCompany(), "")),
            Map.entry("category", Objects.toString(e.getCategory(), "")),
            Map.entry("unit", Objects.toString(e.getUnitName(), "")),
            Map.entry("useState", Objects.toString(e.getUseStatus(), "")),
            Map.entry("source", Objects.toString(e.getSource(), "")),
            Map.entry("owner", Objects.toString(e.getOwnerName(), "")),
            Map.entry("functionLocation", Objects.toString(e.getFunctionLocation(), "")));
    Map<String, Integer> limits =
        Map.of(
            "department",
            1200,
            "company",
            120,
            "category",
            100,
            "unit",
            40,
            "useState",
            40,
            "source",
            40,
            "owner",
            6000,
            "functionLocation",
            1200);
    strings.forEach(
        (field, value) -> {
          if (value.codePointCount(0, value.length()) > limits.get(field))
            errors.put(
                field,
                com.windasoft.eam.common.MessageContext.text(
                    "server.equipment.message0056", limits.get(field)));
        });
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
    return Set.copyOf(states);
  }
}
