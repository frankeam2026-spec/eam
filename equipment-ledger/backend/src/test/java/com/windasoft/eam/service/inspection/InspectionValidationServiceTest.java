package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.dto.system.PermissionScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InspectionValidationServiceTest {
  final InspectionRepository repository = mock(InspectionRepository.class);
  final InspectionProjectService projects =
      new InspectionProjectService(repository, new ObjectMapper(), auth, permissions);
  final AuthContext auth = mock(AuthContext.class);
  final PermissionService permissions = mock(PermissionService.class);

  InspectionValidationService service() {
    return new InspectionValidationService(repository, projects, auth, permissions);
  }

  Map<String, Object> item(String valueType) {
    var item = new LinkedHashMap<String, Object>();
    item.put("id", "101");
    item.put("code", "TEMP");
    item.put("name", "温度");
    item.put("content", "设备运行温度");
    item.put("typeId", "7");
    item.put("valueType", valueType);
    item.put("options", List.of("正常", "异常"));
    item.put("normalOptions", List.of("正常"));
    item.put("module", "legacy");
    return item;
  }

  void enabledType() {
    var row =
        Map.<String, Object>of(
            "id", 7L,
            "kind", "types",
            "name", "温度检查",
            "payload", "{\"enabled\":true}");
    when(repository.loadDefinition(1L, "types", 7L, true)).thenReturn(List.of(row));
  }

  @Test
  void validItemsAreNormalizedAndLegacyModuleIsRemoved() {
    enabledType();
    var item = item("选项");

    service().validateItems(1L, List.of(item));

    assertFalse(item.containsKey("module"));
    assertEquals("7", item.get("typeId"));
    assertEquals("温度检查", item.get("typeName"));
    verify(repository).loadDefinition(1L, "types", 7L, true);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void disabledCycleTypeCannotBeUsedByInspectionStandard() {
    var row =
        Map.<String, Object>of(
            "id", 7L,
            "kind", "types",
            "name", "停用类型",
            "payload", "{\"enabled\":false}");
    when(repository.loadDefinition(1L, "types", 7L, true)).thenReturn(List.of(row));

    assertThrows(IllegalArgumentException.class, () -> service().validateItems(1L, List.of(item("文本"))));
  }

  @Test
  void numericInspectionRangeCannotHaveLowerBoundAboveUpperBound() {
    enabledType();
    var item = item("数值");
    item.put("lower", "10");
    item.put("upper", "1");

    assertThrows(IllegalArgumentException.class, () -> service().validateItems(1L, List.of(item)));
  }

  @Test
  void equipmentValidationRejectsObjectsOutsidePermissionScope() {
    when(auth.userId()).thenReturn(99L);
    when(permissions.inspection("daily-plans", "新增"))
        .thenReturn(new PermissionScope(false, null, Set.of(), false));
    var equipment =
        Map.<String, Object>of(
            "company_org_id", 1L,
            "department_org_id", 2L,
            "owner_user_id", 3L);

    assertThrows(
        ResponseStatusException.class,
        () -> service().checkEquipment(equipment, "daily-plans", "新增"));
  }
}
