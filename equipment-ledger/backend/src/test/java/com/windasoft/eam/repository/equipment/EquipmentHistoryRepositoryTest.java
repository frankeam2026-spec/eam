package com.windasoft.eam.repository.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.equipment.EquipmentHistory;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EquipmentHistoryRepositoryTest {
  @BeforeAll
  static void initializeEquipmentHistoryTableMetadata() {
    var assistant =
        new MapperBuilderAssistant(new MybatisConfiguration(), "equipment-history-test");
    assistant.setCurrentNamespace(EquipmentHistoryMapper.class.getName());
    TableInfoHelper.initTableInfo(assistant, EquipmentHistory.class);
  }

  @Test
  void inspectionRecordLookupIsScopedByTenantEquipmentAndRecordNumber() {
    var mapper = mock(EquipmentHistoryMapper.class);
    when(mapper.selectCount(any()))
        .thenAnswer(
            call -> {
              var query = (LambdaQueryWrapper<EquipmentHistory>) call.getArgument(0);
              assertTrue(query.getSqlSegment().contains("tenant_id"));
              assertTrue(query.getSqlSegment().contains("equipment_id"));
              assertTrue(query.getSqlSegment().contains("record_type"));
              assertTrue(query.getSqlSegment().contains("record_no"));
              assertTrue(
                  query
                      .getParamNameValuePairs()
                      .values()
                      .containsAll(List.of(7L, 11L, "INSPECTION", "INS-7")));
              return 1L;
            });

    assertTrue(new EquipmentHistoryRepository(mapper).hasInspectionRecord(7L, 11L, "INS-7"));
  }

  @Test
  void inspectionResultUpdateChangesOnlyTheScopedHistoryResult() {
    var mapper = mock(EquipmentHistoryMapper.class);
    when(mapper.update(isNull(), any(Wrapper.class)))
        .thenAnswer(
            call -> {
              var update = (LambdaUpdateWrapper<EquipmentHistory>) call.getArgument(1);
              assertTrue(update.getSqlSet().contains("result"));
              assertTrue(update.getSqlSegment().contains("tenant_id"));
              assertTrue(update.getSqlSegment().contains("equipment_id"));
              assertTrue(update.getSqlSegment().contains("record_type"));
              assertTrue(update.getSqlSegment().contains("record_no"));
              assertTrue(
                  update
                      .getParamNameValuePairs()
                      .values()
                      .containsAll(List.of("12", 7L, 11L, "INSPECTION", "INS-7")));
              return 1;
            });

    assertEquals(
        1,
        new EquipmentHistoryRepository(mapper)
            .updateInspectionResult(7L, 11L, "INS-7", "12"));
  }

  @Test
  void inspectionInsertUsesEquipmentHistoryEntityAndDatabaseEventTime() {
    var mapper = mock(EquipmentHistoryMapper.class);
    doAnswer(
            call -> {
              EquipmentHistory row = call.getArgument(0);
              assertEquals(101L, row.getId());
              assertEquals(7L, row.getTenantId());
              assertEquals(11L, row.getEquipmentId());
              assertEquals("INSPECTION", row.getRecordType());
              assertEquals("INS-7", row.getRecordNo());
              assertEquals("周检计划", row.getDescription());
              assertEquals("巡检员", row.getOperatorName());
              assertEquals("已完成", row.getStatus());
              assertEquals("12", row.getResult());
              return 1;
            })
        .when(mapper)
        .insertAtDatabaseTime(any(EquipmentHistory.class));

    new EquipmentHistoryRepository(mapper)
        .insertInspection(101L, 7L, 11L, "INS-7", "周检计划", "巡检员", "已完成", "12");
  }
}
