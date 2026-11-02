package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.repair.RepairEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RepairEventMapper extends BaseMapper<RepairEvent> {
  /** 原操作由数据库生成时间，不能用应用服务器时钟替代；其余CRUD使用BaseMapper。 */
  @org.apache.ibatis.annotations.Insert(
      "INSERT INTO"
          + " eam_repair_event(id,tenant_id,order_id,action,actor_id,actor_name,details_json,created_at)"
          + " VALUES(#{id},#{tenantId},#{orderId},#{action},#{actorId},#{actorName},#{detailsJson},NOW())")
  int insertAtDatabaseTime(RepairEvent row);
}
