package com.windasoft.eam.repository.repair;

import com.windasoft.eam.domain.repair.RepairOrderStatus;
import com.windasoft.eam.dto.repair.RepairApprovalValues;
import com.windasoft.eam.dto.repair.RepairReadViews;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 维修工作流跨表只读投影：审批变量及派工负载；单对象写入归各自主仓储。 */
@Repository
public class RepairWorkflowRepository {
  private final RepairWorkflowMapper mapper;
  private final JdbcTemplate db;

  public RepairWorkflowRepository(RepairWorkflowMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  /** 审批变量从工单、设备及申请人真实关联取得，不信任前端提交的组织值。 */
  public RepairApprovalValues approvalValues(long tenant, long id) {
    return db.queryForObject(
        "SELECT r.reporter_user_id applicantId,u.org_id applicantDepartmentId,r.company_org_id"
            + " companyId,r.department_org_id departmentId,r.equipment_id equipmentId,e.category_id"
            + " categoryId,r.fault_type_id faultTypeId,r.severity FROM eam_repair_order r JOIN"
            + " eam_equipment e ON e.tenant_id=r.tenant_id AND e.id=r.equipment_id AND e.deleted=0"
            + " JOIN eam_sys_user u ON u.tenant_id=r.tenant_id AND u.id=r.reporter_user_id AND"
            + " u.deleted=0 WHERE r.tenant_id=? AND r.id=? AND r.deleted=0",
        (rs, index) ->
            new RepairApprovalValues(
                rs.getObject("applicantId", Long.class),
                rs.getObject("applicantDepartmentId", Long.class),
                rs.getObject("companyId", Long.class),
                rs.getObject("departmentId", Long.class),
                rs.getObject("equipmentId", Long.class),
                rs.getObject("categoryId", Long.class),
                rs.getObject("faultTypeId", Long.class),
                rs.getString("severity")),
        tenant,
        id);
  }

  public List<RepairReadViews.DispatchUser> users(long tenant) {
    return mapper.users(
        tenant,
        RepairOrderStatus.PROCESSING.code(),
        RepairOrderStatus.SUSPENDED.code(),
        RepairOrderStatus.PENDING_ACCEPTANCE.code());
  }
}
