package com.windasoft.eam.dto.repair;

import java.util.LinkedHashMap;
import java.util.Map;

/** 报修审批的后台权威变量，空值保留；只在工作流变量注册边界转为名称索引。 */
public record RepairApprovalValues(
    Long applicantId,
    Long applicantDepartmentId,
    Long companyId,
    Long departmentId,
    Long equipmentId,
    Long categoryId,
    Long faultTypeId,
    String severity) {
  public Map<String, Object> variables() {
    var values = new LinkedHashMap<String, Object>();
    values.put("applicantId", applicantId);
    values.put("applicantDepartmentId", applicantDepartmentId);
    values.put("companyId", companyId);
    values.put("departmentId", departmentId);
    values.put("equipmentId", equipmentId);
    values.put("categoryId", categoryId);
    values.put("faultTypeId", faultTypeId);
    values.put("severity", severity);
    return values;
  }
}
