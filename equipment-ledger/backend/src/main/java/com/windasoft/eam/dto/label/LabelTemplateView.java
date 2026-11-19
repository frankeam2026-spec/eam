package com.windasoft.eam.dto.label;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

/** 布局和元素是设计器文档，其余响应字段使用固定类型。 */
public record LabelTemplateView(
    String id,
    String name,
    BigDecimal widthMm,
    BigDecimal heightMm,
    String remark,
    JsonNode elements,
    JsonNode layout,
    int rowVersion,
    String updatedAt) {

  public record EquipmentFields(
      String name, String code, String owner, String department, String model) {}

  public record PrintData(LabelTemplateView template, List<EquipmentFields> equipment) {}
}
