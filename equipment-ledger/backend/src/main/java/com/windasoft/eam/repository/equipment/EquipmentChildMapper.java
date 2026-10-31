package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.equipment.EquipmentChild;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface EquipmentChildMapper extends BaseMapper<EquipmentChild> {
  int insertBatch(@Param("rows") List<EquipmentChild> rows);
}
