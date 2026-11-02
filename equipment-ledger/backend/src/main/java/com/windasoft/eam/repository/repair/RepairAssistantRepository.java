package com.windasoft.eam.repository.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.dto.knowledge.AiBusinessCatalog;
import com.windasoft.eam.dto.repair.RepairAssistantRequest;
import com.windasoft.eam.repository.common.AssistantReadSource;
import com.windasoft.eam.repository.common.SqlPredicate;
import com.windasoft.eam.repository.knowledge.AiBusinessFilters;
import com.windasoft.eam.repository.knowledge.AiBusinessQueryRepository;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** 维修AI查询执行边界：复用受控汇总执行器，SQL不经Service流出维修模块；动态列仅来自变量白名单。 */
@Repository
public class RepairAssistantRepository {
  private final AiBusinessQueryRepository executor;

  public RepairAssistantRepository(AiBusinessQueryRepository executor) {
    this.executor = executor;
  }

  private AssistantReadSource source(RepairQueryRepository.Query query) {
    return AssistantReadSource.wrapped(
        "CAST(id AS CHAR) id,order_no code,equipment_code equipmentCode,equipment_name"
            + " equipmentName,CAST(equipment_id AS CHAR)"
            + " equipmentId,status,severity,department,CAST(department_org_id AS CHAR)"
            + " departmentId,(SELECT o.name FROM eam_org_unit o WHERE"
            + " o.tenant_id=eam_repair_order.tenant_id AND o.id=company_org_id)"
            + " company,CAST(company_org_id AS CHAR) companyId,assignee owner,CAST(assignee_user_id"
            + " AS CHAR) ownerId,reporter,CAST(reporter_user_id AS CHAR) reporterId,fault_type"
            + " faultType,CAST(fault_type_id AS CHAR) faultTypeId,fault_cause"
            + " faultCause,CAST(fault_cause_id AS CHAR) faultCauseId,reported_at"
            + " reportedAt,claimed_at claimedAt,completed_at submittedAt,accepted_at"
            + " acceptedAt,downtime_minutes downtimeMinutes",
        "eam_repair_order",
        query.rows());
  }

  /** 授权条件由维修查询服务取得，所有模型过滤与设备、日期条件只会进一步缩小结果。 */
  public Map<String, Object> execute(
      RepairQueryRepository.Query query, RepairAssistantRequest request, long tenant, long user) {
    var plan = request.plan();
    var domain = AiBusinessCatalog.ALL.get("repair");
    var source = source(query);
    var filter =
        AiBusinessFilters.compile(plan.where(), domain, request.evaluatedAt(), user, tenant);
    if (request.equipmentId() != null) {
      filter = filter.and(executor.equipmentFilter(plan, tenant, request.equipmentId()));
    }
    if (request.start() != null) {
      filter = filter.and(executor.timeFilter(plan.timeField(), request.start(), request.end()));
    }
    checkNames(plan.where(), domain, source, filter);
    return executor.execute(source, filter, plan, request.page(), request.size());
  }

  private void checkNames(
      JsonNode node,
      AiBusinessCatalog.Domain domain,
      AssistantReadSource source,
      SqlPredicate filter) {
    if (node.isMissingNode() || node.isNull()) {
      return;
    }
    if (node.has("children")) {
      for (var child : node.path("children")) {
        checkNames(child, domain, source, filter);
      }
      return;
    }
    var field = domain.fields().get(node.path("field").asText());
    if (node.path("op").asText().equals("eq")
        && field != null
        && !field.identity().isEmpty()
        && executor.ambiguousIdentity(
            source,
            filter,
            node.path("field").asText(),
            field.identity(),
            node.path("values").get(0).asText())) {
      throw new IllegalArgumentException("所选" + field.label() + "存在同名对象，请补充公司、部门或编号限定，不能将同名对象合并");
    }
  }
}
