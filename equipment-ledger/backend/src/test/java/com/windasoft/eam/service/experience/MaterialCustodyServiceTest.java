package com.windasoft.eam.service.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.experience.MaterialCustodyRepository;
import com.windasoft.eam.service.inventory.InventoryMutex;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class MaterialCustodyServiceTest {
  @Test
  void taskCompletionDoesNotUseTaskVersionAsCustodyVersionButDirectEditingStillChecksIt() {
    var repository = mock(MaterialCustodyRepository.class);
    var auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(1L);
    when(auth.userId()).thenReturn(9L);
    var user = new SystemUser();
    user.setDisplayName("办理人");
    when(auth.user()).thenReturn(user);
    var permissions = mock(PermissionService.class);
    when(permissions.scope(anyString(), anyString()))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    var service =
        new MaterialCustodyService(
            repository, auth, permissions, new ObjectMapper(), mock(InventoryMutex.class));
    var allocation =
        Map.<String, Object>of(
            "id", 10L, "holderId", 9L, "rowVersion", 7L, "remaining", new BigDecimal("2.00"));
    when(repository.lockAllocation(1L, 10L)).thenReturn(List.of(allocation));
    when(repository.lockTaskAllocations(1L, "保养", 20L)).thenReturn(List.of(allocation));
    when(repository.findHolder(1L, 9L)).thenReturn(List.of(Map.of("display_name", "保管人")));
    // 单独编辑必须匹配余料版本7，不能因任务入口修正而放宽。
    assertEquals(
        409,
        assertThrows(
                ResponseStatusException.class,
                () -> service.save(10L, Map.of("rowVersion", 3, "remark", "说明")))
            .getStatusCode()
            .value());
    verify(repository, never())
        .saveCustody(anyLong(), eq(10L), eq(9L), eq("保管人"), eq("说明"), anyLong());
    // 任务自身可为版本3；完成入口不接收/传播这个与余料版本无关的值。
    service.finishTask("保养", 20L, "说明", null);
    verify(repository).saveCustody(1L, 10L, 9L, "保管人", "说明", 9L);
    verify(repository).lockTaskAllocations(1L, "保养", 20L);
  }
}
