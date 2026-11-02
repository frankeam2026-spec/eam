package com.windasoft.eam.repository.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class DailyArrangementRepositoryTest {
  @Test
  void operationTeamsExcludeSoftDeletedUsers() {
    var db = mock(JdbcTemplate.class);
    var repository = new DailyArrangementRepository(db);
    when(db.queryForList(anyString(), eq(Long.class), eq(10001L), eq(88001L)))
        .thenReturn(List.of(73001L));

    assertEquals(List.of(73001L), repository.teamMembers(10001L, 88001L));

    var sql = ArgumentCaptor.forClass(String.class);
    verify(db).queryForList(sql.capture(), eq(Long.class), eq(10001L), eq(88001L));
    assertTrue(sql.getValue().contains("u.status=1 AND u.deleted=0"));
  }
  @Test
  @SuppressWarnings("unchecked")
  void loadBatchesArrangementChildrenAndRosterMembersAcrossThePlan() {
    var db = mock(JdbcTemplate.class);
    var repository = new DailyArrangementRepository(db);
    when(db.queryForList(anyString(), any(Object[].class)))
        .thenAnswer(
            call -> {
              String sql = call.getArgument(0);
              if (sql.contains("FROM eam_inspection_daily_arrangement WHERE"))
                return List.of(
                    new java.util.HashMap<>(
                        java.util.Map.ofEntries(
                            java.util.Map.entry("id", 88001L),
                            java.util.Map.entry("name", "日班"),
                            java.util.Map.entry("anchorDate", "2026-11-02"),
                            java.util.Map.entry("startTime", "08:00:00"),
                            java.util.Map.entry("endTime", "16:00:00"),
                            java.util.Map.entry("followUpUserId", 73001L),
                            java.util.Map.entry("teamId", 81001L),
                            java.util.Map.entry("allowLate", 1))),
                    new java.util.HashMap<>(java.util.Map.of("id", 88002L, "name", "夜班")));
              if (sql.contains("FROM eam_inspection_daily_staff_rule_person"))
                return List.of(
                    java.util.Map.of(
                        "arrangementId", 88001L, "key", "regular", "userId", "73001"));
              if (sql.contains("FROM eam_inspection_daily_staff_rule WHERE"))
                return List.of(
                    java.util.Map.of(
                        "arrangementId", 88001L,
                        "key", "regular",
                        "slotId", "day",
                        "start", "08:00:00",
                        "end", "16:00:00",
                        "unassigned", 0,
                        "weekday", 1));
              if (sql.contains("FROM eam_inspection_daily_staff_override_person"))
                return List.of(
                    java.util.Map.of(
                        "arrangementId", 88001L, "key", "override", "userId", "73002"));
              if (sql.contains("FROM eam_inspection_daily_staff_override WHERE"))
                return List.of(
                    java.util.Map.of(
                        "arrangementId", 88001L,
                        "key", "override",
                        "slotId", "day",
                        "start", "08:00:00",
                        "end", "16:00:00",
                        "unassigned", 0,
                        "date", "2026-11-03"));
              if (sql.contains("FROM eam_inspection_daily_arrangement_day WHERE"))
                return List.of(java.util.Map.of("arrangementId", 88001L, "dayNo", 1));
              if (sql.contains("FROM eam_inspection_daily_arrangement_slot WHERE"))
                return List.of(
                    java.util.Map.of(
                        "arrangementId", 88001L,
                        "id", "day",
                        "name", "白班",
                        "start", "08:00:00",
                        "end", "16:00:00"));
              if (sql.contains("FROM eam_inspection_daily_arrangement_person WHERE"))
                return List.of(java.util.Map.of("arrangementId", 88001L, "value", "73001"));
              if (sql.contains("FROM eam_inspection_daily_arrangement_equipment WHERE"))
                return List.of(java.util.Map.of("arrangementId", 88001L, "value", "501"));
              if (sql.contains("FROM eam_inspection_daily_arrangement_project WHERE"))
                return List.of(
                    java.util.Map.of(
                        "arrangementId", 88001L, "standardId", "601", "itemId", "701"));
              return List.of();
            });

    var loaded = repository.load(10001L, 90001L);

    assertEquals(2, loaded.size());
    var arrangement = loaded.getFirst();
    assertEquals("88001", arrangement.get("id"));
    assertEquals(true, arrangement.get("enabled"));
    assertEquals(true, arrangement.get("allowLate"));
    assertEquals(List.of(1), arrangement.get("days"));
    assertEquals(List.of("73001"), arrangement.get("owners"));
    assertEquals(List.of("501"), arrangement.get("equipmentIds"));
    assertEquals(
        List.of(java.util.Map.of("standardId", "601", "itemId", "701")),
        arrangement.get("projectRefs"));
    var regular = ((List<java.util.Map<String, Object>>) arrangement.get("regularRoster")).getFirst();
    assertEquals(List.of("73001"), regular.get("users"));
    assertEquals(false, regular.get("unassigned"));
    var override = ((List<java.util.Map<String, Object>>) arrangement.get("dateRoster")).getFirst();
    assertEquals(List.of("73002"), override.get("users"));
    assertEquals("2026-11-03", override.get("date"));
    var secondArrangement = loaded.get(1);
    assertEquals("88002", secondArrangement.get("id"));
    assertEquals(List.of(), secondArrangement.get("regularRoster"));
    assertEquals(List.of(), secondArrangement.get("days"));

    // One plan read, four batched roster queries, and five batched child queries.
    verify(db, times(10)).queryForList(anyString(), any(Object[].class));
  }

}
