package com.windasoft.eam.dto.equipment;

/** 明确操作结果类型，保留原有 JSON 字段及响应结构。 */
public final class EquipmentMutationResult {
  private EquipmentMutationResult() {}

  public record Deleted(boolean deleted) {}

  public record Count(int count) {}
}
