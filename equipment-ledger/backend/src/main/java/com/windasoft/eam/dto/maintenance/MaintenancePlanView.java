package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

/** 计划关系投影的固定字段；框架映射在Repository结束，不把通用payload带入Service。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MaintenancePlanView {
  private String id;
  private String code;
  private String name;
  private String departmentId;
  private String creatorId;
  private Integer rowVersion;
  private String createdAt;
  private boolean enabled;
  private String remark;
  private String calculation;
  private Integer advanceDays;
  private Integer remindDays;
  private String autoFill;
  private boolean scanStart;
  private String reviewMode;
  private String reviewRule;
  private List<String> items;
  private List<String> equipmentIds;
  private List<String> categoryIds;
  private List<String> categoryNames;
  private List<String> legacyCategoryNames;
  private List<String> owners;
  private List<String> reviewers;
  private List<MaintenancePlanDeviceView> details;
}
