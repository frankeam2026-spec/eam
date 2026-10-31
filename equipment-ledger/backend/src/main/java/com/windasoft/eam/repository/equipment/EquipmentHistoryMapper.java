package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.equipment.EquipmentHistory;
import org.apache.ibatis.annotations.Mapper;

/** 使用 MyBatis-Plus 读取设备历史事件，不重复编写简单 SELECT。 */
@Mapper
public interface EquipmentHistoryMapper extends BaseMapper<EquipmentHistory> {}
