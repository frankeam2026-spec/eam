package com.windasoft.eam.service.system;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.domain.system.PermissionMenu;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 权限编码不能因 Java 类型化而影响既有角色配置、前端操作和授权持久化。 */
class PermissionCodesTest {
  @Test
  void keepsRoleAndRepairPermissionCodes() {
    assertEquals("role", PermissionMenu.ROLE.code());
    assertEquals("repair", PermissionMenu.REPAIR.code());
    assertEquals("inventoryQuery", PermissionMenu.INVENTORY_QUERY.code());
    assertEquals("查看", PermissionAction.VIEW.code());
    assertEquals("授权", PermissionAction.AUTHORIZE.code());
    assertEquals("派工", PermissionAction.DISPATCH.code());
    assertEquals("冻结", PermissionAction.FREEZE.code());
    assertEquals("查看金额", PermissionAction.VIEW_AMOUNT.code());
  }

  @Test
  void permissionActionsDoNotContainDuplicatePersistentCodes() {
    Set<String> codes =
        Arrays.stream(PermissionAction.values())
            .map(PermissionAction::code)
            .collect(Collectors.toSet());
    assertEquals(PermissionAction.values().length, codes.size());
  }
}
