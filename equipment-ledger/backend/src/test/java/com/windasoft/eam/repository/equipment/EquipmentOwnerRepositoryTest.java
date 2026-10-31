package com.windasoft.eam.repository.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.windasoft.eam.entity.equipment.EquipmentOwner;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class EquipmentOwnerRepositoryTest {
  @Test
  void insertAndDeleteUseMyBatisPlusMapper() {
    var mapper = mock(EquipmentOwnerMapper.class);
    var repository = new EquipmentOwnerRepository(mapper);
    var owner = new EquipmentOwner(10001L, 501L, 701L, "负责人", 2);

    repository.add(owner);
    repository.clear(10001L, 501L);

    verify(mapper).insert(owner);
    verify(mapper).delete(any());
  }

  @Test
  void readUsesMyBatisPlusMapper() {
    var mapper = mock(EquipmentOwnerMapper.class);
    var repository = new EquipmentOwnerRepository(mapper);
    repository.list(10001L, 501L);

    verify(mapper).selectList(any());
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
