package com.windasoft.eam.dto.planning;

import java.util.List;

public final class PlanningCandidate {
  private PlanningCandidate() {}

  /** 两类标准保留各自的项目及回填字段，仅按标准编号做合并。 */
  public interface Standard {
    String id();
  }

  public record Resolved(List<Equipment> equipment, List<User> users, List<Standard> standards) {}

  public record ResolveRequest(
      List<String> equipmentIds, List<String> userIds, List<String> standardIds) {}

  public record DefaultsRequest(List<String> equipmentIds) {}

  public record Equipment(
      String id,
      String code,
      String name,
      String model,
      String functionLocation,
      String category,
      String categoryId,
      String department,
      String companyId) {}

  public record User(String id, String name, String account, String department) {}
}
