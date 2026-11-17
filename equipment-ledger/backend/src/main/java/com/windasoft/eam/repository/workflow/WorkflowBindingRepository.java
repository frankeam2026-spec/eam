package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.windasoft.eam.dto.workflow.WorkflowEffectiveBinding;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.Setting;
import com.windasoft.eam.entity.workflow.WorkflowBinding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 公司业务流程绑定主仓储；绑定切换和流程发布由模板事务协调。 */
@Repository
public class WorkflowBindingRepository {
  private final WorkflowBindingMapper mapper;
  private final JdbcTemplate db;

  public WorkflowBindingRepository(WorkflowBindingMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  public Setting approvalSetting(long tenant, long company) {
    var rows =
        db.query(
            """
            SELECT requires_approval AS required,row_version AS rowVersion,
            (enabled=1 AND version_id IS NOT NULL) AS templateAvailable
            FROM wf_binding WHERE tenant_id=? AND company_id=? AND business_type='repair_report'
            """,
            (r, n) ->
                new Setting(
                    WorkflowTemplateViews.flag(r.getObject("required")),
                    r.getLong("rowVersion"),
                    WorkflowTemplateViews.flag(r.getObject("templateAvailable"))),
            tenant,
            company);
    return rows.isEmpty()
        ? new Setting(BooleanNode.FALSE, -1L, BooleanNode.FALSE)
        : rows.getFirst();
  }

  /** -1只代表尚无绑定的旧并发协议；初始化后仍以版本条件原子写入。 */
  public void approvalSetting(long tenant, long company, boolean required, long version) {
    ensure(tenant, company, "repair_report");
    int changed =
        mapper.update(
            null,
            new LambdaUpdateWrapper<WorkflowBinding>()
                .eq(WorkflowBinding::getTenantId, tenant)
                .eq(WorkflowBinding::getCompanyId, company)
                .eq(WorkflowBinding::getBusinessType, "repair_report")
                .eq(WorkflowBinding::getRowVersion, version == -1 ? 0 : version)
                .set(WorkflowBinding::getRequiresApproval, required ? 1 : 0)
                .setSql("row_version=row_version+1"));
    if (changed != 1) {
      throw new IllegalArgumentException("审批设置已变化，请刷新后重试");
    }
  }

  public void ensure(long tenant, long company, String business) {
    var row = new WorkflowBinding();
    row.setTenantId(tenant);
    row.setCompanyId(company);
    row.setBusinessType(business);
    row.setRequiresApproval(business.equals("repair_report") ? 0 : 1);
    mapper.insertIfAbsent(row);
  }

  public WorkflowBinding find(long tenant, long company, String business, boolean lock) {
    return mapper.selectOne(
        new LambdaQueryWrapper<WorkflowBinding>()
            .eq(WorkflowBinding::getTenantId, tenant)
            .eq(WorkflowBinding::getCompanyId, company)
            .eq(WorkflowBinding::getBusinessType, business)
            .last(lock ? "FOR UPDATE" : ""));
  }

  /** 执行器需要固定版本与引擎定义，联查不改变缺失版本或关闭绑定的语义。 */
  public WorkflowEffectiveBinding effective(long tenant, long company, String business) {
    var rows =
        db.query(
            """
SELECT b.version_id,b.enabled,v.template_id,v.engine_definition_id,v.execution_version
FROM wf_binding b LEFT JOIN wf_version v ON v.tenant_id=b.tenant_id AND v.id=b.version_id
WHERE b.tenant_id=? AND b.company_id=? AND b.business_type=?
""",
            (r, n) ->
                new WorkflowEffectiveBinding(
                    r.getObject("version_id", Long.class),
                    r.getObject("template_id", Long.class),
                    r.getString("engine_definition_id"),
                    r.getObject("execution_version", Integer.class),
                    r.getInt("enabled")),
            tenant,
            company,
            business);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public void activate(long tenant, long company, String business, long version) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<WorkflowBinding>()
            .eq(WorkflowBinding::getTenantId, tenant)
            .eq(WorkflowBinding::getCompanyId, company)
            .eq(WorkflowBinding::getBusinessType, business)
            .set(WorkflowBinding::getVersionId, version)
            .set(WorkflowBinding::getEnabled, 1)
            .setSql("row_version=row_version+1"));
  }

  /** 只停用当前仍指向此模板的绑定；其他模板并发替代后不能被旧模板操作关闭。 */
  public void disableTemplate(long tenant, long template) {
    db.update(
        """
        UPDATE wf_binding b JOIN wf_version v ON v.tenant_id=b.tenant_id AND v.id=b.version_id
        SET b.enabled=0,b.row_version=b.row_version+1
        WHERE b.tenant_id=? AND v.template_id=?
        """,
        tenant,
        template);
  }
}
