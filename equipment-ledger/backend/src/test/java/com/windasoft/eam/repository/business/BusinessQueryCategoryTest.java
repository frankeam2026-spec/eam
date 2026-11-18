package com.windasoft.eam.repository.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class BusinessQueryCategoryTest {
  @Test
  void nullableCategoryKeepsEmptyStringAndLargeId() throws Exception {
    var db = mock(JdbcTemplate.class);
    var row = mock(ResultSet.class);
    long id = 9007199254740993L;
    when(row.getLong("id")).thenReturn(id);
    when(row.getString("category")).thenReturn(null);
    doAnswer(
            call -> {
              assertEquals(List.of(8L, id), Arrays.asList((Object[]) call.getRawArguments()[2]));
              ((RowCallbackHandler) call.getArgument(1)).processRow(row);
              return null;
            })
        .when(db)
        .query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    assertEquals(Map.of(id, ""), new BusinessQueryRepository(db).repairCategories(8, List.of(id)));
  }

  @Test
  void emptyLookupDoesNotIssueSql() {
    var db = mock(JdbcTemplate.class);
    assertEquals(Map.of(), new BusinessQueryRepository(db).repairCategories(8, List.of()));
    verifyNoInteractions(db);
  }
}
