package com.windasoft.eam.dto.repair;

import java.math.BigDecimal;

/** 维修只读关联投影，沿用既有接口字段与字符串ID。 */
public final class RepairReadViews {
  private RepairReadViews() {}

  public record Candidate(String id, String name, String status) {}

  public record Stock(String id, String code, String name, String model, BigDecimal quantity) {}

  public record Material(
      String id,
      String stockId,
      String code,
      String name,
      String model,
      BigDecimal quantity,
      BigDecimal issuedQuantity,
      BigDecimal installedQuantity,
      String status) {}

  public record DispatchUser(
      String id, String name, String account, String department, Long unfinished) {}
}
