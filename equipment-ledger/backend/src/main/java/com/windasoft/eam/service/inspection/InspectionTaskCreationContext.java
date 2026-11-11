package com.windasoft.eam.service.inspection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** 单次任务的时间与安排上下文，不写入计划或任务快照；周期巡检和旧班次没有检查安排。 */
record InspectionTaskCreationContext(
    LocalDateTime due,
    String shiftId,
    String shiftName,
    LocalDateTime shiftEnd,
    DailyArrangement daily) {

  /** 安排配置仍沿用现有人员解析契约，业务日期和排班班次不再通过隐藏字符串字段传递。 */
  record DailyArrangement(
      Map<String, Object> definition, LocalDate businessDate, String rosterShiftId) {}

  static InspectionTaskCreationContext scheduled(
      LocalDateTime due, String shiftId, String shiftName, LocalDateTime shiftEnd) {
    return new InspectionTaskCreationContext(due, shiftId, shiftName, shiftEnd, null);
  }

  static InspectionTaskCreationContext arranged(
      Map<String, Object> arrangement, DailyInspectionSchedule.Occurrence occurrence) {
    return new InspectionTaskCreationContext(
        occurrence.start(),
        occurrence.key(),
        occurrence.name(),
        occurrence.end(),
        new DailyArrangement(arrangement, occurrence.businessDate(), occurrence.rosterShiftId()));
  }
}
