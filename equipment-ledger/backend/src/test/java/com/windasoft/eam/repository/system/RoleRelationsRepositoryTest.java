package com.windasoft.eam.repository.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.system.RoleMenuPermission;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RoleRelationsRepositoryTest {
  @Test
  void groupsGrantRelationsWithoutMixingGrantsAndKeepsRowOrder() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForList(anyString(), any(Object[].class)))
        .thenAnswer(invocation -> {
          String sql = invocation.getArgument(0);
          if (sql.contains("eam_role_menu_action")) {
            return List.of(
                Map.of("grant_id", 11L, "action_name", "查看"),
                Map.of("grant_id", 12L, "action_name", "编辑"),
                Map.of("grant_id", 11L, "action_name", "删除"));
          }
          if (sql.contains("eam_role_menu_scope")) {
            return List.of(Map.of("grant_id", 11L, "scope_code", "all"));
          }
          if (sql.contains("eam_role_menu_target")) {
            return List.of(Map.of("grant_id", 12L, "target_id", 100L));
          }
          throw new AssertionError(sql);
        });
    var first = new RoleMenuPermission();
    first.setId(11L);
    first.setTenantId(7L);
    var second = new RoleMenuPermission();
    second.setId(12L);
    second.setTenantId(7L);

    var result = new RoleRelationsRepository(db, new ObjectMapper()).grants(List.of(first, second));

    assertEquals(List.of(first, second), result);
    assertEquals("[\"查看\",\"删除\"]", first.getActionsJson());
    assertEquals("[\"all\"]", first.getDataScopesJson());
    assertEquals("[]", first.getOrgIdsJson());
    assertEquals("[\"编辑\"]", second.getActionsJson());
    assertEquals("[]", second.getDataScopesJson());
    assertEquals("[100]", second.getOrgIdsJson());
  }
}
