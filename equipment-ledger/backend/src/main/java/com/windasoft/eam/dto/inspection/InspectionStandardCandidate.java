package com.windasoft.eam.dto.inspection;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.windasoft.eam.dto.planning.PlanningCandidate.Standard;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 保留点巡检标准候选项的字段和空值，供计划编辑回填标准及项目。 */
public record InspectionStandardCandidate(
    String id,
    String code,
    String name,
    String departmentId,
    int rowVersion,
    String createdAt,
    @JsonUnwrapped Content content,
    String departmentName,
    String typeName,
    String personMode,
    LocalDate startDate,
    LocalDate endDate,
    long itemCount,
    long equipmentCount,
    long shiftCount,
    String periodLabel,
    LocalDateTime nextAt)
    implements Standard {

  /** 现有标准关系视图投影；计划兼容数组对标准固定为空。 */
  public record Content(
      boolean enabled,
      String remark,
      String requirement,
      List<String> owners,
      List<String> reviewers,
      List<Item> items,
      List<String> equipmentIds,
      List<String> categoryIds,
      List<String> categoryNames,
      List<String> legacyCategoryNames,
      List<String> details,
      List<Integer> restDays,
      List<String> holidays,
      List<Shift> shifts,
      List<RosterEntry> roster) {}

  public record Item(
      String id,
      String code,
      String name,
      String content,
      String typeId,
      String valueType,
      String lower,
      String upper,
      boolean required,
      boolean photoRequired,
      String remark,
      List<String> options,
      List<String> normalOptions,
      List<Reference> references) {}

  public record Reference(String id, String name, String contentType, Long size, String url) {}

  public record Shift(String id, String name, String start, String end) {}

  public record RosterEntry(String key, String date, String shiftId, List<String> users) {}
}
