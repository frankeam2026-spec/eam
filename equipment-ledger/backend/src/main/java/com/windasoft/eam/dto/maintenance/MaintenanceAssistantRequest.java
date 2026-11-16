package com.windasoft.eam.dto.maintenance;
import com.windasoft.eam.dto.knowledge.AiBusinessPlan;
import com.windasoft.eam.dto.knowledge.AiQueryLimits;
import java.time.LocalDate;
import java.time.LocalDateTime;
/** 查询请求只携带受控计划，授权范围不能由模型传入。 */
public record MaintenanceAssistantRequest(AiBusinessPlan plan, Long equipmentId, int page, int size,
 LocalDateTime evaluatedAt, LocalDate start, LocalDate end) {
 public static final String TASK_DOMAIN="maintenanceTask";
 public static final String PLAN_DOMAIN="maintenancePlan";
 public MaintenanceAssistantRequest {
  if(plan==null || !(TASK_DOMAIN.equals(plan.domain())||PLAN_DOMAIN.equals(plan.domain()))
   || !AiQueryLimits.acceptsPage(page,size)||evaluatedAt==null || (start==null)!=(end==null)
   || (start!=null&&(start.isAfter(end)||plan.timeField().isBlank()))) throw new IllegalArgumentException("保养查询计划无效");
 }
 public String kind(){return TASK_DOMAIN.equals(plan.domain())?"tasks":"plans";}
}
