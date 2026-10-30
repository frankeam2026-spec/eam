package com.windasoft.eam.dto.inventory;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.FieldValidationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Editable draft fields only. Read-only fields sent by existing clients are ignored. */
@JsonIgnoreProperties({
  "id",
  "tenantId",
  "code",
  "companyId",
  "taskCode",
  "applicantId",
  "applicantName",
  "status",
  "createdAt",
  "deleted",
  "approvalGate",
  "warehouseName",
  "reservations",
  "issues",
  "events",
  "workflow"
})
public record RequisitionDraftForm(
    TaskKind taskKind,
    @JsonDeserialize(using = ExactId.class) Long taskId,
    @JsonDeserialize(using = ExactId.class) Long warehouseId,
    String remark,
    List<Line> lines,
    @JsonDeserialize(using = ExactVersion.class) Integer rowVersion) {
  public RequisitionDraftForm {
    remark = remark == null ? "" : remark.trim();
    lines = lines == null ? null : Collections.unmodifiableList(new ArrayList<>(lines));
  }

  @JsonAnySetter
  public void rejectUnknown(String field, JsonNode ignored) {
    throw new FieldValidationException(
        field, com.windasoft.eam.common.MessageRef.of("server.common.message0072"));
  }

  public enum TaskKind {
    REPAIR("维修"),
    MAINTENANCE("保养"),
    INSPECTION("点巡检");
    private final String value;

    TaskKind(String value) {
      this.value = value;
    }

    @JsonValue
    public String value() {
      return value;
    }

    @JsonCreator
    public static TaskKind from(String value) {
      for (var kind : values()) {
        if (kind.value.equals(value == null ? null : value.trim())) {
          return kind;
        }
      }
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0018");
    }
  }

  @JsonIgnoreProperties({
    "id",
    "tenantId",
    "requisitionId",
    "lineNo",
    "issuedQuantity",
    "materialCode",
    "materialName",
    "materialModel",
    "unit",
    "reservedQuantity"
  })
  public record Line(@JsonDeserialize(using = ExactId.class) Long materialId, BigDecimal quantity) {
    @JsonAnySetter
    public void rejectUnknown(String field, JsonNode ignored) {
      throw new FieldValidationException(
          field, com.windasoft.eam.common.MessageRef.of("server.common.message0072"));
    }
  }

  /** Kept at the service boundary too, so internal callers cannot bypass validation. */
  public void validate(boolean update) {
    check(taskKind != null, "taskKind", "server.inventory.message0018");
    check(taskId != null && taskId > 0, "taskId", "server.inventory.message0211");
    check(warehouseId != null && warehouseId > 0, "warehouseId", "server.inspection.message0013");
    check(remark.length() <= 1000, "remark", "server.inventory.message0091");
    if (update) {
      check(rowVersion != null && rowVersion >= 0, "rowVersion", "server.common.message0018");
    }
    check(
        lines != null && !lines.isEmpty() && lines.size() <= 500,
        "lines",
        "server.inventory.message0223");
    var materials = new HashSet<Long>();
    for (int i = 0; i < lines.size(); i++) {
      var line = lines.get(i);
      String field = "lines[" + i + "]";
      check(line != null, field, "server.inventory.message0222");
      check(
          line.materialId() != null && line.materialId() > 0,
          field + ".materialId",
          "server.inspection.message0013");
      check(
          materials.add(line.materialId()), field + ".materialId", "server.inventory.message0224");
      var qty = line.quantity();
      check(
          qty != null
              && qty.signum() > 0
              && qty.scale() <= 6
              && qty.precision() - qty.scale() <= 14,
          field + ".quantity",
          "server.inventory.message0225");
    }
  }

  private static void check(boolean valid, String field, String message) {
    if (!valid) {
      throw new FieldValidationException(field, new com.windasoft.eam.common.MessageRef(message));
    }
  }

  /** Jackson otherwise silently truncates a floating-point ID to a long. */
  public static class ExactId extends JsonDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser p, DeserializationContext c) throws IOException {
      String text = p.getValueAsString();
      try {
        if (text == null || !text.trim().matches("[+-]?[0-9]+")) {
          throw new NumberFormatException();
        }
        return Long.parseLong(text.trim());
      } catch (NumberFormatException invalid) {
        return (Long) c.handleUnexpectedToken(Long.class, p);
      }
    }
  }

  public static class ExactVersion extends JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser p, DeserializationContext c) throws IOException {
      if (!p.currentToken().isNumeric() || !p.currentToken().name().equals("VALUE_NUMBER_INT")) {
        return (Integer) c.handleUnexpectedToken(Integer.class, p);
      }
      return p.getIntValue();
    }
  }
}
