package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.dto.repair.RepairInboxView;
import com.windasoft.eam.entity.repair.RepairInbox;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RepairInboxMapper extends BaseMapper<RepairInbox> {
  /** 原操作由数据库生成时间，不能用应用服务器时钟替代；其余CRUD使用BaseMapper。 */
  @org.apache.ibatis.annotations.Insert(
      "INSERT INTO"
          + " eam_repair_inbox(id,tenant_id,user_id,order_id,kind,title,is_todo,done,message_key,message_args,created_at)"
          + " VALUES(#{id},#{tenantId},#{userId},#{orderId},#{kind},#{title},#{isTodo},#{done},#{messageKey},#{messageArgs},NOW())")
  int insertAtDatabaseTime(RepairInbox row);

  /** 跨表展示需要保留关联SQL，简单读写继续复用BaseMapper。 */
  @Select(
      "SELECT CAST(i.id AS CHAR) id,CAST(i.order_id AS CHAR) orderId,i.kind,i.title,i.message_key"
          + " messageKey,i.message_args messageArgs,i.is_todo todo,i.done,i.read_at IS NOT NULL"
          + " isRead,DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time,r.order_no"
          + " orderNo,r.equipment_name equipmentName,r.status FROM eam_repair_inbox i JOIN"
          + " eam_repair_order r ON r.id=i.order_id AND r.tenant_id=i.tenant_id WHERE"
          + " i.tenant_id=#{tenant} AND i.user_id=#{userId} AND r.deleted=0 ORDER BY i.created_at"
          + " DESC,i.id DESC LIMIT #{limit}")
  List<RepairInboxView> inboxRows(long tenant, long userId, int limit);
}
