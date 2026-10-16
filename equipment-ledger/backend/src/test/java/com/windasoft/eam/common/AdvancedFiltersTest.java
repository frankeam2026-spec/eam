package com.windasoft.eam.common;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdvancedFiltersTest {
  private final ObjectMapper json = new ObjectMapper();
  private final Map<String, AdvancedFilters.Field<RepairOrder>> fields = Map.of(
      "faultType", new AdvancedFilters.Field<>(RepairOrder::getFaultType, AdvancedFilters.Kind.TEXT),
      "reportedAt", new AdvancedFilters.Field<>(RepairOrder::getReportedAt, AdvancedFilters.Kind.DATE));

  @BeforeEach
  void metadata() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "filter-test"), RepairOrder.class);
  }

  @Test
  void treeScopeUsesBoundParametersAndIgnoresDisplaySelection() {
    var query = new LambdaQueryWrapper<RepairOrder>();
    AdvancedFilters.apply(query, """
        [{"field":"faultType","operator":"in","values":["机械","轴承"],"selectedNodeIds":["1"]}]
        """, fields, json);
    assertTrue(query.getSqlSegment().contains(" IN "));
    assertEquals(2, query.getParamNameValuePairs().size());
    assertTrue(query.getParamNameValuePairs().containsValue("轴承"));
  }

  @Test
  void dateOnlyUpperBoundIsNextDayExclusive() {
    var query = new LambdaQueryWrapper<RepairOrder>();
    AdvancedFilters.apply(query, """
        [{"field":"reportedAt","operator":"range","values":["2026-10-15","2026-10-15"]}]
        """, fields, json);
    assertTrue(query.getSqlSegment().contains(" < "));
    assertTrue(query.getParamNameValuePairs().containsValue(LocalDateTime.of(2026, 10, 16, 0, 0)));
  }

  @Test
  void sharedRangesCoverWholeDayAndSingleNumericBounds() {
    assertTrue(AdvancedFilters.matchesRange("2026-10-15T23:59:59.999999", "2026-10-15", "2026-10-15", false));
    assertFalse(AdvancedFilters.matchesRange("2026-10-16T00:00:00", "2026-10-15", "2026-10-15", false));
    assertTrue(AdvancedFilters.matchesRange("0.00", "", "10", true));
    assertTrue(AdvancedFilters.matchesRange("10.00", "2", "", true));
    assertFalse(AdvancedFilters.matchesRange("2026-10-15T13:00:01", "2026-10-15T12:00:00", "2026-10-15T13:00:00", false));
  }

  @Test
  void textContainsTreatsWildcardCharactersAsText() {
    var query = new LambdaQueryWrapper<RepairOrder>();
    AdvancedFilters.apply(query,
        "[{\"field\":\"faultType\",\"operator\":\"contains\",\"values\":[\"50%_\"]}]", fields, json);
    query.getSqlSegment();
    assertTrue(query.getParamNameValuePairs().containsValue("%50\\%\\_%"));
  }

  @Test
  void exactMatchRemainsAvailableAndUnlistedFieldsAreRejected() {
    var query = new LambdaQueryWrapper<RepairOrder>();
    assertDoesNotThrow(() -> AdvancedFilters.apply(query,
        "[{\"field\":\"faultType\",\"operator\":\"eq\",\"values\":[\"机械\"]}]", fields, json));
    assertThrows(IllegalArgumentException.class, () -> AdvancedFilters.apply(new LambdaQueryWrapper<>(),
        "[{\"field\":\"tenantId\",\"operator\":\"in\",\"values\":[\"1\"]}]", fields, json));
  }
}
