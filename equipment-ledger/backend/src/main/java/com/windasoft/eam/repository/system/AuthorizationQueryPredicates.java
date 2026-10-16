package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.repair.RepairOrder;
import java.util.*;

/** Generates SQL predicates from already-authorized scopes, without granting access. */
public final class AuthorizationQueryPredicates {
  private AuthorizationQueryPredicates() {}

  public static void equipment(
      LambdaQueryWrapper<Equipment> query, PermissionScope scope, long tenant, long user) {
    if (scope.all()) return;
    if (scope.companyIds().isEmpty() && scope.departmentIds().isEmpty() && !scope.owner()) {
      query.eq(Equipment::getId, -1L);
      return;
    }
    query.and(
        w -> {
          boolean added = false;
          if (!scope.companyIds().isEmpty()) {
            w.in(Equipment::getCompanyOrgId, scope.companyIds());
            added = true;
          }
          if (!scope.departmentIds().isEmpty()) {
            if (added) w.or();
            w.in(Equipment::getDepartmentOrgId, scope.departmentIds());
            added = true;
          }
          if (scope.owner()) {
            if (added) w.or();
            w.and(
                owned ->
                    owned
                        .eq(Equipment::getOwnerUserId, user)
                        .or()
                        .apply(
                            "id IN (SELECT equipment_id FROM eam_equipment_owner WHERE"
                                + " tenant_id={0} AND user_id={1})",
                            tenant, user));
          }
        });
  }

  public static void repair(
      LambdaQueryWrapper<RepairOrder> query, PermissionScope scope, long tenant, long user) {
    final PermissionScope effective = scope;
    if (scope.all()) return;
    if (scope.departmentIds().isEmpty() && !scope.owner()) {
      query.eq(RepairOrder::getId, -1L);
      return;
    }
    query.and(
        w -> {
          boolean added = false;
          if (!effective.departmentIds().isEmpty()) {
            w.in(RepairOrder::getDepartmentOrgId, effective.departmentIds());
            added = true;
          }
          if (effective.owner()) {
            if (added) w.or();
            w.eq(RepairOrder::getReporterUserId, user)
                .or()
                .eq(RepairOrder::getAssigneeUserId, user)
                .or()
                .apply(
                    "id IN (SELECT order_id FROM eam_repair_candidate WHERE tenant_id={0} AND"
                        + " user_id={1} AND status='待接单')",
                    tenant, user);
          }
        });
  }

  public static void acceptance(
      LambdaQueryWrapper<RepairOrder> query, PermissionScope scope, long user) {
    if (scope.all()) return;
    final Set<Long> departments = scope.departmentIds();
    query.and(
        w -> {
          w.eq(RepairOrder::getReporterUserId, user);
          if (!departments.isEmpty()) w.or().in(RepairOrder::getDepartmentOrgId, departments);
        });
  }
}
