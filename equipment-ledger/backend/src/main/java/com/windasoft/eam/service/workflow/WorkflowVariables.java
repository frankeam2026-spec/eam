package com.windasoft.eam.service.workflow;

import static com.windasoft.eam.common.workflow.WorkflowExpression.*;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WorkflowVariables {
  public Map<String, Variable> catalog(
      com.windasoft.eam.dto.workflow.WorkflowDefinition definition) {
    Map<String, Variable> result = new LinkedHashMap<>(catalog(definition.businessType()));
    if (definition.nodes() != null)
      for (var node : definition.nodes())
        if (node != null && node.script() != null) {
          var script = node.script();
          if (!"SCRIPT".equals(node.type())
              || !"javascript".equals(script.language())
              || script.outputKey() == null
              || !script.outputKey().matches("computed_[A-Za-z][A-Za-z0-9_]{0,69}")
              || script.outputType() == null
              || !Set.of("NUMBER", "TEXT", "BOOLEAN").contains(script.outputType()))
            throw new IllegalArgumentException("脚本须使用JavaScript并声明computed_开头的数值/文本/布尔结果变量");
          if (result.putIfAbsent(
                  script.outputKey(),
                  new Variable(
                      script.outputKey(),
                      node.name() + " · 计算结果",
                      Type.valueOf(script.outputType()),
                      true,
                      false))
              != null) throw new IllegalArgumentException("脚本结果变量重复：" + script.outputKey());
        }
    return result;
  }

  public Map<String, Variable> catalog(String businessType) {
    if ("repair_report".equals(businessType)) {
      var result = new LinkedHashMap<String, Variable>();
      for (var v :
          List.of(
              new Variable("applicantId", "报修人", Type.REFERENCE, true, false),
              new Variable("applicantDepartmentId", "报修人所属部门", Type.REFERENCE, true, true),
              new Variable("companyId", "工单所属公司", Type.REFERENCE, true, false),
              new Variable("departmentId", "工单所属部门", Type.REFERENCE, true, false),
              new Variable("equipmentId", "设备", Type.REFERENCE, true, false),
              new Variable("categoryId", "设备分类", Type.REFERENCE, true, true),
              new Variable("faultTypeId", "故障类型", Type.REFERENCE, true, false),
              new Variable("severity", "故障等级", Type.TEXT, true, false))) result.put(v.key(), v);
      return Collections.unmodifiableMap(result);
    }
    if (!"requisition".equals(businessType)) throw new IllegalArgumentException("不支持的审批业务类型");
    Map<String, Variable> result = new LinkedHashMap<>();
    for (var v :
        List.of(
            new Variable("applicantId", "申请人", Type.REFERENCE, true, false),
            new Variable("applicantDepartmentId", "申请人所属部门", Type.REFERENCE, true, true),
            new Variable("companyId", "单据所属公司", Type.REFERENCE, true, false),
            new Variable("warehouseId", "仓库", Type.REFERENCE, true, false),
            new Variable("taskKind", "关联任务类型", Type.TEXT, true, false),
            new Variable("amount", "申请金额（尚未接入来源）", Type.NUMBER, false, true),
            new Variable("lines.materialId", "明细物料", Type.REFERENCE, true, false),
            new Variable("lines.code", "明细料号", Type.TEXT, true, false),
            new Variable("lines.quantity", "明细申请数量", Type.NUMBER, true, false)))
      result.put(v.key(), v);
    return Collections.unmodifiableMap(result);
  }
}
