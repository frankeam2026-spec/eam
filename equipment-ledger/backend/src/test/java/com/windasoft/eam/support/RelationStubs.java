package com.windasoft.eam.support;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.repair.RepairRelationsRepository;
import com.windasoft.eam.repository.system.RoleRelationsRepository;

/** Unit fixtures already carry hydrated values; SQL hydration is covered by MySQL integration. */
public final class RelationStubs {
  private RelationStubs() {}

  public static RoleRelationsRepository roles() {
    var r = mock(RoleRelationsRepository.class);
    when(r.roles(anyList())).thenAnswer(c -> c.getArgument(0));
    when(r.grants(anyList())).thenAnswer(c -> c.getArgument(0));
    when(r.role(any())).thenAnswer(c -> c.getArgument(0));
    return r;
  }

  public static RepairRelationsRepository repair() {
    var r = mock(RepairRelationsRepository.class);
    when(r.hydrate(anyList())).thenAnswer(c -> c.getArgument(0));
    when(r.hydrate(any(RepairOrder.class))).thenAnswer(c -> c.getArgument(0));
    return r;
  }
}
