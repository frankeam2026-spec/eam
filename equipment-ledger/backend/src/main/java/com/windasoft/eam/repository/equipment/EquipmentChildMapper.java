package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.equipment.EquipmentChild;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface EquipmentChildMapper extends BaseMapper<EquipmentChild> {
  @org.apache.ibatis.annotations.Insert({
      "<script>",
      "INSERT INTO eam_equipment_child (tenant_id, parent_id, child_id, sort_no) VALUES",
      "<foreach collection='rows' item='row' separator=','>",
      "(#{row.tenantId}, #{row.parentId}, #{row.childId}, #{row.sortNo})",
      "</foreach>",
      "</script>"
  })
  int insertBatch(@Param("rows") List<EquipmentChild> rows);
}
