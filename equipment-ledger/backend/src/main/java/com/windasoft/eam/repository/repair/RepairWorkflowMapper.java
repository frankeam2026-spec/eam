package com.windasoft.eam.repository.repair;

import com.windasoft.eam.dto.repair.RepairReadViews;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 仅保留派工负载与个人关联的跨表只读查询，不再写入其他业务对象。 */
@Mapper
public interface RepairWorkflowMapper {
  @Select(
      "SELECT (EXISTS(SELECT 1 FROM eam_repair_order WHERE tenant_id=#{tenant} AND deleted=0 AND"
          + " (reporter_user_id=#{userId} OR assignee_user_id=#{userId})) OR EXISTS(SELECT 1 FROM"
          + " eam_repair_candidate WHERE tenant_id=#{tenant} AND user_id=#{userId} AND"
          + " status=#{candidateStatus}))")
  boolean personalAccess(long tenant, long userId, String candidateStatus);

  @Select(
      "SELECT CAST(u.id AS CHAR) id,u.display_name name,u.account,COALESCE(o.name,'')"
          + " department,(SELECT COUNT(*) FROM eam_repair_order r WHERE r.tenant_id=u.tenant_id AND"
          + " r.deleted=0 AND r.assignee_user_id=u.id AND r.status IN"
          + " (#{processing},#{suspended},#{pendingAcceptance})) unfinished FROM eam_sys_user u"
          + " LEFT JOIN eam_org_unit o ON o.id=u.org_id AND o.tenant_id=u.tenant_id WHERE"
          + " u.tenant_id=#{tenant} AND u.status=1 AND u.deleted=0 ORDER BY"
          + " unfinished,u.display_name,u.id")
  List<RepairReadViews.DispatchUser> users(
      long tenant, String processing, String suspended, String pendingAcceptance);
}
