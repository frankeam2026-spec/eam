package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.equipment.EquipmentHistory;
import org.apache.ibatis.annotations.Mapper;

/** 使用 MyBatis-Plus 读取设备历史事件，不重复编写简单 SELECT。 */
@Mapper
public interface EquipmentHistoryMapper extends BaseMapper<EquipmentHistory> {
  /** 维修旧备件事件沿用数据库事件时间，其他调用方仍可显式提供历史时间。 */
  @org.apache.ibatis.annotations.Insert(
      "INSERT INTO"
          + " eam_equipment_history(id,tenant_id,equipment_id,record_type,record_no,occurred_at,description,operator_name,status,result)"
          + " VALUES(#{id},#{tenantId},#{equipmentId},#{recordType},#{recordNo},NOW(),#{description},#{operatorName},#{status},#{result})")
  int insertAtDatabaseTime(EquipmentHistory row);
}
