package com.windasoft.eam.service.workflow;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.common.workflow.WorkflowExpression.Variable;
import com.windasoft.eam.entity.workflow.WorkflowScriptRun;
import com.windasoft.eam.entity.workflow.WorkflowValue;
import com.windasoft.eam.repository.workflow.WorkflowScriptRunRepository;
import com.windasoft.eam.repository.workflow.WorkflowValueRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 目录约束的动态变量边界：固定存储列使用Entity，变量名和值仍由业务适配器与受限脚本提供。 */
@Service
@RequiredArgsConstructor
public class WorkflowValueService {
  private final WorkflowValueRepository values;
  private final WorkflowScriptRunRepository runs;

  @Transactional(propagation = Propagation.MANDATORY)
  public void snapshot(
      long tenant, long instance, Map<String, Variable> catalog, Map<String, Object> data) {
    for (var variable : catalog.values()) {
      if (!variable.available()) {
        continue;
      }
      if (variable.key().startsWith("lines.")) {
        var lines = (List<?>) data.get("lines");
        for (int i = 0; i < lines.size(); i++) {
          values.insert(
              value(
                  tenant,
                  instance,
                  variable.key(),
                  i + 1,
                  variable.type().name(),
                  ((Map<?, ?>) lines.get(i)).get(variable.key().substring(6))));
        }
      } else {
        values.insert(
            value(
                tenant,
                instance,
                variable.key(),
                0,
                variable.type().name(),
                data.get(variable.key())));
      }
    }
  }

  public Map<String, Object> values(long tenant, long instance) {
    Map<String, Object> result = new LinkedHashMap<>();
    List<Map<String, Object>> lines = new ArrayList<>();
    for (var row : values.list(tenant, instance)) {
      Object item =
          Boolean.TRUE.equals(row.getIsNull())
              ? null
              : switch (row.getValueType()) {
                case "NUMBER" -> row.getNumberValue();
                case "REFERENCE" -> row.getReferenceValue();
                case "BOOLEAN" -> row.getBooleanValue();
                default -> row.getTextValue();
              };
      if (row.getLineNo() == 0) {
        result.put(row.getFieldKey(), item);
      } else {
        while (lines.size() < row.getLineNo()) {
          lines.add(new LinkedHashMap<>());
        }
        lines.get(row.getLineNo() - 1).put(row.getFieldKey().substring(6), item);
      }
    }
    result.put("lines", lines);
    return result;
  }

  /** 计算结果覆盖与执行审计同事务提交；任一步失败须让Flowable命令一起回滚。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void scriptResult(
      long tenant,
      long instance,
      String node,
      String execution,
      String hash,
      LocalDateTime started,
      String key,
      String type,
      Object computed) {
    var result = value(tenant, instance, key, 0, type, computed);
    values.upsertComputed(result);
    var run = new WorkflowScriptRun();
    run.setId(IdWorker.getId());
    run.setTenantId(tenant);
    run.setInstanceId(instance);
    run.setNodeKey(node);
    run.setExecutionId(execution);
    run.setSourceHash(hash);
    run.setStartedAt(started);
    run.setValueType(type);
    run.setNumberValue(result.getNumberValue());
    run.setTextValue(result.getTextValue());
    run.setBooleanValue(result.getBooleanValue());
    runs.insert(run);
  }

  private WorkflowValue value(
      long tenant, long instance, String key, int line, String type, Object item) {
    var row = new WorkflowValue();
    row.setId(IdWorker.getId());
    row.setTenantId(tenant);
    row.setInstanceId(instance);
    row.setFieldKey(key);
    row.setLineNo(line);
    row.setValueType(type);
    row.setIsNull(item == null);
    switch (type) {
      case "NUMBER" -> row.setNumberValue((BigDecimal) item);
      case "TEXT" -> row.setTextValue((String) item);
      case "REFERENCE" -> row.setReferenceValue(item == null ? null : ((Number) item).longValue());
      case "BOOLEAN" -> row.setBooleanValue((Boolean) item);
      default -> {}
    }
    return row;
  }
}
