package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import com.windasoft.eam.service.repair.RepairQueryService;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class AiRepairQueryServiceTest {
  final RepairQueryService repairs=mock(RepairQueryService.class);
  final PermissionService permissions=mock(PermissionService.class);
  AiRepairQueryService service(){return new AiRepairQueryService(repairs,mock(EquipmentQueryService.class),permissions);}
  @Test void monthQueriesKeepRollingAndCalendarMonthDistinct(){
    var today=LocalDate.of(2026,10,29);
    assertEquals(LocalDate.of(2026,9,29),AiRepairQueryService.interpret("最近一个月的维修记录",today,null).from());
    var previous=AiRepairQueryService.interpret("请查询上个月我负责的已完成维修记录",today,null);
    assertEquals(LocalDate.of(2026,9,1),previous.from());assertEquals(LocalDate.of(2026,9,30),previous.to());assertEquals("owned",previous.relation());assertTrue(previous.completed());
  }
  @Test void unsupportedFiltersAreNotSilentlyDropped() throws Exception {
    when(permissions.allowed("repair",PermissionAction.VIEW)).thenReturn(true);
    assertNotNull(service().tryAnswer("张三最近一个月的维修记录",null));verifyNoInteractions(repairs);
    assertNull(service().tryAnswer("如何填写维修记录",null));
    assertNull(service().tryAnswer("本月维修记录统计",null));
  }
  @Test void missingBusinessPermissionDoesNotReadRecords() throws Exception {
    assertNotNull(service().tryAnswer("最近一个月的维修记录",null));verifyNoInteractions(repairs);
  }
  @Test void reusesAuthorizedDatabasePagingAndReturnsOnlyProjectedFields() throws Exception {
    var row=new RepairOrder();row.setId(9007199254740993L);row.setOrderNo("TEST-1");row.setEquipmentName("Test equipment");
    var page=new Page<RepairOrder>(1,20,1);page.setRecords(List.of(row));
    var today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
    var from = today.minusMonths(1);
    when(repairs.pageForAssistant(from, today, "owned", false, null, null, 1, 20))
        .thenReturn(page);
    var result = service().query(
        new AiRepairQueryService.Criteria(from, today, "owned", false, null), 1, 20);
    verify(repairs).pageForAssistant(from, today, "owned", false, null, null, 1, 20);
    verify(permissions).require("repair", PermissionAction.VIEW);
    verify(permissions).require("knowledge", PermissionAction.VIEW);
    var record = new ObjectMapper().valueToTree(result).path("data").get(0);
    assertEquals("9007199254740993", record.path("id").asText());
    assertEquals("reportedAt", result.dateField());
    assertFalse(record.has("customFields"));
  }
}
