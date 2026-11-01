package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.equipment.EquipmentBom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface EquipmentBomMapper extends BaseMapper<EquipmentBom> {
  @org.apache.ibatis.annotations.Insert({
      "<script>",
      "INSERT INTO eam_equipment_bom (tenant_id, equipment_id, material_id, quantity, code, name, model, unit, sort_no) VALUES",
      "<foreach collection='rows' item='row' separator=','>",
      "(#{row.tenantId}, #{row.equipmentId}, #{row.materialId}, #{row.quantity}, #{row.code}, #{row.name}, #{row.model}, #{row.unit}, #{row.sortNo})",
      "</foreach>",
      "</script>"
  })
  int insertBatch(@Param("rows") List<EquipmentBom> rows);
}
