package com.windasoft.eam.service.equipment;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Aggregate;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.All;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Bucket;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Comparison;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Condition;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.DateRange;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Field;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.FieldSort;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Filter;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Identifier;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Junction;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Logical;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Operation;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Period;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Reference;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.ResultSort;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.SortBy;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Text;
import com.windasoft.eam.dto.equipment.EquipmentAssistantResult;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.Direction;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.equipment.EquipmentAssistantRepository;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class EquipmentAssistantServiceTest {
  final EquipmentAssistantRepository queries = mock(EquipmentAssistantRepository.class);
  final EquipmentQueryRepository ledger = mock(EquipmentQueryRepository.class);
  final PermissionService permissions = mock(PermissionService.class);
  final AuthContext auth = mock(AuthContext.class);
  EquipmentQueryService service;

  @BeforeEach
  void setup() {
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(73001L);
    when(permissions.equipment("查看")).thenReturn(new PermissionScope(true, null, Set.of(), false));
    when(queries.ambiguousName(anyLong(), anyLong(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(queries.execute(anyLong(), anyLong(), any(), any(), any(), any()))
        .thenReturn(new EquipmentAssistantResult.Count(0, 0));
    service = new EquipmentQueryService(ledger, auth, permissions, new ObjectMapper(), queries,
        mock(com.windasoft.eam.repository.equipment.EquipmentOwnerRepository.class));
  }

  EquipmentAssistantQuery query(
      Operation operation,
      Filter filter,
      List<Field> groups,
      Aggregate aggregate,
      Field measure,
      SortBy sort,
      Field time,
      Period period,
      DateRange range,
      int top,
      int page,
      int size) {
    return new EquipmentAssistantQuery(
        operation,
        filter,
        groups,
        aggregate,
        measure,
        sort,
        Direction.DESC,
        time,
        period,
        range,
        Bucket.MONTH,
        top,
        null,
        page,
        size);
  }

  EquipmentAssistantQuery count(Filter filter) {
    return query(
        Operation.COUNT,
        filter,
        List.of(),
        Aggregate.COUNT,
        null,
        null,
        Field.CREATED_AT,
        Period.ALL,
        null,
        0,
        1,
        20);
  }

  @Test
  void validTypedPlanUsesTrustedScopeAndKeepsSqlInTheRepository() {
    var request = count(new Condition(Field.OWNER_ID, Comparison.EQ, List.of(Reference.ME)));
    assertInstanceOf(EquipmentAssistantResult.Count.class, service.queryAssistant(request).data());
    verify(permissions).require("equipment", "查看");
    var scope = permissions.equipment("查看");
    verify(queries).execute(eq(10001L), eq(73001L), eq(scope), same(request), any(), isNull());
  }

  @Test
  void independentlyRejectsInvalidCapabilitiesWithoutRelyingOnAiParsing() {
    var requests =
        List.of(
            query(
                Operation.LIST,
                new All(),
                List.of(),
                Aggregate.DISTINCT,
                Field.ID,
                null,
                Field.CREATED_AT,
                Period.ALL,
                null,
                0,
                1,
                20),
            query(
                Operation.GROUP,
                new All(),
                List.of(Field.CREATED_AT),
                Aggregate.COUNT,
                null,
                null,
                Field.CREATED_AT,
                Period.ALL,
                null,
                0,
                1,
                20),
            query(
                Operation.GROUP,
                new All(),
                List.of(Field.CATEGORY),
                Aggregate.COUNT,
                null,
                new FieldSort(Field.MODEL),
                Field.CREATED_AT,
                Period.ALL,
                null,
                0,
                1,
                20),
            query(
                Operation.LIST,
                new All(),
                List.of(),
                Aggregate.COUNT,
                null,
                ResultSort.VALUE,
                Field.CREATED_AT,
                Period.ALL,
                null,
                0,
                1,
                20),
            query(
                Operation.COUNT,
                new All(),
                List.of(),
                Aggregate.COUNT,
                null,
                null,
                Field.CREATED_AT,
                Period.ALL,
                null,
                101,
                1,
                20),
            query(
                Operation.COUNT,
                new All(),
                List.of(),
                Aggregate.COUNT,
                null,
                null,
                Field.CREATED_AT,
                Period.ALL,
                null,
                0,
                0,
                20),
            query(
                Operation.COUNT,
                new All(),
                List.of(),
                Aggregate.COUNT,
                null,
                null,
                Field.CREATED_AT,
                Period.ALL,
                null,
                0,
                1,
                10),
            query(
                Operation.COUNT,
                new All(),
                List.of(),
                Aggregate.COUNT,
                null,
                null,
                Field.NAME,
                Period.ALL,
                null,
                0,
                1,
                20),
            query(
                Operation.TREND,
                new All(),
                List.of(),
                Aggregate.COUNT,
                null,
                null,
                null,
                Period.ALL,
                null,
                0,
                1,
                20),
            query(
                Operation.COUNT,
                new All(),
                List.of(),
                Aggregate.COUNT,
                null,
                null,
                Field.CREATED_AT,
                Period.CUSTOM,
                new DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)),
                0,
                1,
                20));
    for (var request : requests) {
      assertThrows(LocalizedArgumentException.class, () -> service.queryAssistant(request));
    }
    verifyNoInteractions(queries);
  }

  @Test
  void valueTypesReferencesAndLogicalComplexityAreValidated() {
    var filters =
        List.<Filter>of(
            new Condition(Field.OWNER_ID, Comparison.EQ, List.of(new Identifier(-1L))),
            new Condition(Field.CATEGORY_ID, Comparison.EQ, List.of(Reference.ME)),
            new Condition(Field.CREATED_AT, Comparison.CONTAINS, List.of(new Text("2026"))),
            new Condition(Field.NAME, Comparison.EQ, List.of(new Identifier(1L))),
            new Condition(Field.NAME, Comparison.EMPTY, List.of(new Text("x"))),
            new Junction(Logical.NOT, List.of(new All(), new All())),
            new Junction(Logical.OR, List.of()),
            new Condition(Field.NAME, Comparison.EQ, null));
    for (var filter : filters) {
      assertThrows(LocalizedArgumentException.class, () -> service.queryAssistant(count(filter)));
    }
    Filter deep = new Condition(Field.NAME, Comparison.EQ, List.of(new Text("x")));
    for (int i = 0; i < 6; i++) {
      deep = new Junction(Logical.NOT, List.of(deep));
    }
    var request = count(deep);
    assertThrows(LocalizedArgumentException.class, () -> service.queryAssistant(request));
    verifyNoInteractions(queries);
  }

  @Test
  void permissionFailureDoesNotReachTheRepository() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
        .when(permissions)
        .require("equipment", "查看");
    assertThrows(ResponseStatusException.class, () -> service.queryAssistant(count(new All())));
    verifyNoInteractions(queries);
  }

  @Test
  void ambiguousNamesAreRejectedByTheDeviceServiceBeforeAggregation() {
    when(queries.ambiguousName(anyLong(), anyLong(), any(), any(), any(), any()))
        .thenReturn(Optional.of(Field.NAME));
    assertThrows(
        LocalizedArgumentException.class,
        () ->
            service.queryAssistant(
                count(new Condition(Field.NAME, Comparison.EQ, List.of(new Text("同名设备"))))));
    verify(queries, never()).execute(anyLong(), anyLong(), any(), any(), any(), any());
  }
}
