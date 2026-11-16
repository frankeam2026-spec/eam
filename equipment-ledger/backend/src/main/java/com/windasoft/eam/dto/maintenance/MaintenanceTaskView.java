package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** 任务固定协议独立建模。historicalSnapshot只透传数据库保存的旧版本生成文档， 不接受HTTP输入，不承载主体、动作、执行结果、扩展字段或权限字段。 */
@Data
public class MaintenanceTaskView {
  private String id;
  private String code;
  private String status;
  private String planId;
  private String equipmentId;
  private String typeId;
  private String companyId;
  private String departmentId;
  private String ownerId;
  private int rowVersion;
  private String dueDate;
  private String expiresDate;
  private String createdAt;
  private String actualStartAt;
  private String actualEndAt;
  private MaintenanceTaskResultView result;
  private boolean canExecute;
  private boolean canAccept;
  private boolean canDelete;
  private boolean canPostpone;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String department;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String owner;

  @JsonUnwrapped private MaintenanceCustomFields custom;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private MaintenanceTaskStatistics statistics;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<MaintenanceTaskEventView> events;

  @JsonIgnore private ObjectNode historicalSnapshot;

  /** 只由查询装配器提供已移除固定字段的历史属性，保留未知历史键和明确null的原始语义。 */
  @JsonAnyGetter
  public Map<String, JsonNode> historicalProperties() {
    var values = new LinkedHashMap<String, JsonNode>();
    if (historicalSnapshot != null) {
      historicalSnapshot
          .fields()
          .forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue()));
    }
    return values;
  }
}
