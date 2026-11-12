package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.equipment.EquipmentHistory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 使用 MyBatis-Plus 读取设备历史事件，不重复编写简单 SELECT。 */
@Mapper
public interface EquipmentHistoryMapper extends BaseMapper<EquipmentHistory> {
  /** 履历创建沿用数据库事件时间，不以应用服务器的默认时区替代原 NOW() 语义。 */
  @Insert(
      "INSERT INTO"
          + " eam_equipment_history(id,tenant_id,equipment_id,record_type,record_no,occurred_at,description,operator_name,status,result)"
          + " VALUES(#{id},#{tenantId},#{equipmentId},#{recordType},#{recordNo},NOW(),#{description},#{operatorName},#{status},#{result})")
  int insertAtDatabaseTime(EquipmentHistory row);
}
