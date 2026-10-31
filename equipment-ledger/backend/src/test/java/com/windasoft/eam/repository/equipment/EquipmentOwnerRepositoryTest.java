package com.windasoft.eam.repository.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.windasoft.eam.entity.equipment.EquipmentOwner;
import java.util.List;
import java.util.stream.LongStream;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EquipmentOwnerRepositoryTest {
  @BeforeAll
  static void initializeMapping() {
    // 单元测试使用模拟 Mapper，需要单独初始化 Lambda 字段映射缓存。
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""), EquipmentOwner.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ArgumentCaptor<Wrapper<EquipmentOwner>> ownerCondition() {
    return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
  }

  private static LambdaQueryWrapper<?> lambdaCondition(Wrapper<EquipmentOwner> wrapper) {
    return (LambdaQueryWrapper<?>) wrapper;
  }

  @Test
  void insertAndDeleteUseMapperWithTenantAndEquipmentCondition() {
    var mapper = mock(EquipmentOwnerMapper.class);
    var repository = new EquipmentOwnerRepository(mapper);
    var owner = new EquipmentOwner(10001L, 501L, 701L, "负责人", 2);

    repository.add(owner);
    repository.clear(10001L, 501L);

    verify(mapper).insert(owner);
    var deleted = ownerCondition();
    verify(mapper).delete(deleted.capture());
    var condition = lambdaCondition(deleted.getValue());
    assertTrue(condition.getSqlSegment().contains("tenant_id"));
    assertTrue(condition.getSqlSegment().contains("equipment_id"));
    assertEquals(2, condition.getParamNameValuePairs().size());
    assertTrue(condition.getParamNameValuePairs().containsValue(10001L));
    assertTrue(condition.getParamNameValuePairs().containsValue(501L));
  }

  @Test
  void readIncludesTenantEquipmentAndStableOwnerOrder() {
    var mapper = mock(EquipmentOwnerMapper.class);
    var repository = new EquipmentOwnerRepository(mapper);
    repository.list(10001L, 501L);

    var queried = ownerCondition();
    verify(mapper).selectList(queried.capture());
    var condition = lambdaCondition(queried.getValue());
    assertTrue(condition.getSqlSegment().contains("tenant_id"));
    assertTrue(condition.getSqlSegment().contains("equipment_id"));
    assertTrue(condition.getSqlSegment().contains("ORDER BY sort_no ASC,user_id ASC"));
    assertEquals(2, condition.getParamNameValuePairs().size());
    assertTrue(condition.getParamNameValuePairs().containsValue(10001L));
    assertTrue(condition.getParamNameValuePairs().containsValue(501L));
  }

  @Test
  void anEmptyPageDoesNotQueryTheDatabase() {
    var mapper = mock(EquipmentOwnerMapper.class);
    var repository = new EquipmentOwnerRepository(mapper);
    assertEquals(List.of(), repository.listByEquipmentIds(10001L, List.of()));
    verifyNoInteractions(mapper);
  }

  @Test
  void batchReadsAreTenantScopedAndLimitedToFiveHundredIds() {
    var mapper = mock(EquipmentOwnerMapper.class);
    var repository = new EquipmentOwnerRepository(mapper);
    when(mapper.selectList(any())).thenReturn(List.of());
    var ids = LongStream.rangeClosed(1, 501).boxed().toList();

    assertEquals(List.of(), repository.listByEquipmentIds(10001L, ids));
    var queried = ownerCondition();
    verify(mapper, times(2)).selectList(queried.capture());
    var chunks = queried.getAllValues();
    assertEquals(2, chunks.size());
    var first = lambdaCondition(chunks.get(0));
    var second = lambdaCondition(chunks.get(1));
    for (var condition : List.of(first, second)) {
      // IN 列表的参数在 SQL 片段解析时展开；先解析再检查绑定值。
      String sql = condition.getSqlSegment();
      assertTrue(sql.contains("tenant_id"));
      assertTrue(sql.contains("equipment_id IN"));
      assertTrue(sql.contains("ORDER BY equipment_id ASC,sort_no ASC,user_id ASC"));
      assertTrue(condition.getParamNameValuePairs().containsValue(10001L));
    }
    assertEquals(501, first.getParamNameValuePairs().size());
    assertEquals(2, second.getParamNameValuePairs().size());
    assertTrue(first.getParamNameValuePairs().containsValue(1L));
    assertTrue(first.getParamNameValuePairs().containsValue(500L));
    assertTrue(second.getParamNameValuePairs().containsValue(501L));
  }
}
