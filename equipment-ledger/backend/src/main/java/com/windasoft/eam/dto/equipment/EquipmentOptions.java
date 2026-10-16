package com.windasoft.eam.dto.equipment;

import java.util.List;

public record EquipmentOptions(List<Department> departments, List<Employee> users) {
  public record Department(
      boolean disabled, String id, String name, String type, String parentId) {}

  public record Employee(String id, String name, String account) {}
}
