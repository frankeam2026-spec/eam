package com.windasoft.eam.dto.system;

import java.util.Set;

public record PermissionScope(
    boolean all, Long companyId, Set<Long> departmentIds, boolean owner, Set<Long> companyIds) {
  public PermissionScope(boolean all, Long companyId, Set<Long> departmentIds, boolean owner) {
    this(all, companyId, departmentIds, owner, companyId == null ? Set.of() : Set.of(companyId));
  }

  public boolean matches(Long company, Long department, Long ownerId, long userId) {
    return all
        || company != null && companyIds.contains(company)
        || department != null && departmentIds.contains(department)
        || owner && ownerId != null && ownerId == userId;
  }
}
