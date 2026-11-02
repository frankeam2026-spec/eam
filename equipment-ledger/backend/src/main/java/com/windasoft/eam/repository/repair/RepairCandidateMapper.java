package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.dto.repair.RepairReadViews;
import com.windasoft.eam.entity.repair.RepairCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RepairCandidateMapper extends BaseMapper<RepairCandidate> {
  /** 原操作由数据库生成时间，不能用应用服务器时钟替代；其余CRUD使用BaseMapper。 */
  @org.apache.ibatis.annotations.Insert(
      "INSERT INTO eam_repair_candidate(id,tenant_id,order_id,user_id,status,created_at)"
          + " VALUES(#{id},#{tenantId},#{orderId},#{userId},#{status},NOW())")
  int insertAtDatabaseTime(RepairCandidate row);

  /** 跨表展示需要保留关联SQL，简单读写继续复用BaseMapper。 */
  @Select(
      "SELECT CAST(c.user_id AS CHAR) id,u.display_name name,c.status FROM eam_repair_candidate c"
          + " JOIN eam_sys_user u ON c.user_id=u.id AND c.tenant_id=u.tenant_id WHERE"
          + " c.tenant_id=#{tenant} AND c.order_id=#{orderId}")
  List<RepairReadViews.Candidate> candidates(long tenant, long orderId);
}
