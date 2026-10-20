package com.windasoft.eam.service.repair;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepairQueryServiceTest {
  private RepairQueryService service;
  private RepairOrderMapper mapper;
  private PermissionService permissions;

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "query-test"), RepairOrder.class);
    mapper = mock(RepairOrderMapper.class);
    AuthContext auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(10L);
    when(mapper.selectPage(any(Page.class), any())).thenAnswer(call -> call.getArgument(0));
    when(mapper.selectCount(any())).thenReturn(0L);
    permissions = mock(PermissionService.class);
    service =
        new RepairQueryService(
            new com.windasoft.eam.repository.repair.RepairQueryRepository(
                mapper, new ObjectMapper(), com.windasoft.eam.support.RelationStubs.repair()),
            auth,
            permissions,
            new ObjectMapper());
    var repo = mock(com.windasoft.eam.repository.business.BusinessConfigRepository.class);
    when(repo.schema(anyLong(), anyString(), anyBoolean()))
        .thenReturn(Map.of("published", "{\"fields\":[]}"));
    var config =
        new com.windasoft.eam.service.business.BusinessConfigService(
            repo, new ObjectMapper(), auth, permissions);
    var query =
        new com.windasoft.eam.service.business.BusinessQueryService(
            config,
            repo,
            mock(com.windasoft.eam.service.business.BusinessReferenceService.class),
            auth,
            new ObjectMapper(),
            new com.windasoft.eam.repository.business.BusinessQueryRepository(
                mock(org.springframework.jdbc.core.JdbcTemplate.class)));
    var custom = mock(com.windasoft.eam.service.business.BusinessValueService.class);
    when(custom.batch(anyString(), anyCollection())).thenReturn(Map.of());
    when(custom.repairCategories(anyCollection())).thenReturn(Map.of());
    org.springframework.test.util.ReflectionTestUtils.setField(service, "customQueries", query);
    org.springframework.test.util.ReflectionTestUtils.setField(service, "custom", custom);
  }

  @Test
  void listHasBoundedPageAndAllQueueCounts() {
    var result = service.list(Map.of("page", "2", "size", "20"));
    assertEquals(2, result.page().getCurrent());
    assertEquals(20, result.page().getSize());
    assertEquals(7, result.counts().size());
  }

  @Test
  void oversizedPageIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> service.list(Map.of("size", "10000")));
  }

  @Test
  void unknownFilterIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.list(Map.of("filters", "{\"tenantId\":\"20002\"}")));
  }

  @Test
  void unknownSortIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> service.list(Map.of("sortBy", "unknown")));
  }

  @Test
  void unknownQueueIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> service.list(Map.of("queue", "unknown")));
  }

  @Test
  void unknownExportFieldIsRejectedBeforeWriting() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.validateExport(Map.of("columns", "tenantId")));
  }

  @Test
  void exportAppliesBothViewAndExportScopes() {
    service.validateExport(Map.of());
    verify(permissions).filterRepair(any());
    verify(permissions).filterRepair(any(), eq("导出"));
  }

  @Test
  void csvQuotesAndEscapesText() {
    assertEquals("\"a\"\"b,c\"", RepairQueryService.csv("a\"b,c"));
  }

  @Test
  void csvNeutralizesFormulaIncludingMultiline() {
    assertEquals("\"'=1+1\ntext\"", RepairQueryService.csv("=1+1\ntext"));
  }

  @Test
  void nullCsvIsEmptyCell() {
    assertEquals("\"\"", RepairQueryService.csv(null));
  }

  @Test
  void advancedAllowsHiddenFieldsAndRanges() {
    assertDoesNotThrow(
        () ->
            service.list(
                Map.of(
                    "advanced",
                    "[{\"field\":\"repairAction\",\"operator\":\"contains\",\"values\":[\"更换\"]},{\"field\":\"downtimeMinutes\",\"operator\":\"range\",\"values\":[\"10\",\"60\"]},{\"field\":\"status\",\"operator\":\"in\",\"values\":[\"处理中\",\"挂起\"]}]")));
  }

  @Test
  void advancedRejectsSecurityFields() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.list(
                Map.of(
                    "advanced",
                    "[{\"field\":\"tenantId\",\"operator\":\"eq\",\"values\":[\"20002\"]}]")));
  }

  @Test
  void advancedRejectsReversedRange() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.list(
                Map.of(
                    "advanced",
                    "[{\"field\":\"reportedAt\",\"operator\":\"range\",\"values\":[\"2026-10-10T12:00\",\"2026-10-01T12:00\"]}]")));
  }

  @Test
  void advancedRejectsWrongOperatorForType() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.list(
                Map.of(
                    "advanced",
                    "[{\"field\":\"status\",\"operator\":\"contains\",\"values\":[\"待\"]}]")));
  }

  @Test
  void advancedRejectsMalformedExportBeforeWriting() {
    assertThrows(
        IllegalArgumentException.class, () -> service.validateExport(Map.of("advanced", "null")));
  }
}
