package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.windasoft.eam.entity.equipment.Equipment;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 聚合投影仅在设备Repository内部转换，保留原查询的8秒执行限制。 */
@Mapper
public interface EquipmentAssistantMapper {
  long count(@Param("ew") Wrapper<Equipment> query);

  List<Map<String, Object>> rows(@Param("ew") Wrapper<Equipment> query);

  IPage<Map<String, Object>> page(
      IPage<Map<String, Object>> page, @Param("ew") Wrapper<Equipment> query);
}
