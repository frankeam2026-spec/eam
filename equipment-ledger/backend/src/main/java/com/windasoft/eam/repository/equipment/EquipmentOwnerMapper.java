package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.equipment.EquipmentOwner;
import org.apache.ibatis.annotations.Mapper;

/** 设备负责人关系表的数据映射，复合主键由数据库维护。 */
@Mapper
public interface EquipmentOwnerMapper extends BaseMapper<EquipmentOwner> {}
