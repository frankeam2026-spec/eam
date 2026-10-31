package com.windasoft.eam.repository.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.windasoft.eam.entity.equipment.EquipmentOwner;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EquipmentOwnerRepositoryTest {
  @Test
  void insertUsesMyBatisPlusAndDeleteUsesTenantScopedCondition() {
    var mapper = mock(EquipmentOwnerMapper.class);
    var repository = new EquipmentOwnerRepository(mapper);
    var owner = new EquipmentOwner(10001L, 501L, 701L, "负责人", 2);

    repository.add(owner);
    repository.clear(10001L, 501L);

    verify(mapper).insert(owner);
    var deleted = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).delete(deleted.capture());
    String sql = deleted.getValue().getSqlSegment();
    assertTrue(sql.contains("tenant_id"));
    assertTrue(sql.contains("equipment_id"));
    assertEquals(2, deleted.getValue().getParamNameValuePairs().size());
    assertTrue(deleted.getValue().getParamNameValuePairs().containsValue(10001L));
    assertTrue(deleted.getValue().getParamNameValuePairs().containsValue(501L));
  }

  @Test
  void readsKeepTenantEquipmentAndStableOwnerOrder() {
    var mapper = mock(EquipmentOwnerMapper.class);
    var repository = new EquipmentOwnerRepository(mapper);
    repository.list(10001L, 501L);

    var query = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).selectList(query.capture());
    var segment = query.getValue().getSqlSegment();
    assertTrue(segment.contains("tenant_id"));
    assertTrue(segment.contains("equipment_id"));
    assertTrue(segment.contains("sort_no"));
    assertTrue(segment.contains("user_id"));
    assertTrue(query.getValue().getParamNameValuePairs().containsValue(10001L));
    assertTrue(query.getValue().getParamNameValuePairs().containsValue(501L));
  }

  @Test
  void anEmptyPageDoesNotQueryTheDatabase() {
    var mapper = mock(EquipmentOwnerMapper.class);
    var repository = new EquipmentOwnerRepository(mapper);
    assertEquals(List.of(), repository.listByEquipmentIds(10001L, List.of()));
    verifyNoInteractions(mapper);
  }

  @Test
  void batchOwnersUseAtMostOneQueryPerFiveHundredEquipmentIds() {
    var mapper = mock(EquipmentOwnerMapper.class);
    var repository = new EquipmentOwnerRepository(mapper);
    when(mapper.selectList(any())).thenReturn(List.of());
    var ids = LongStream.rangeClosed(1, 501).boxed().toList();

    assertEquals(List.of(), repository.listByEquipmentIds(10001L, ids));
    verify(mapper, times(2)).selectList(any());
  }
}
