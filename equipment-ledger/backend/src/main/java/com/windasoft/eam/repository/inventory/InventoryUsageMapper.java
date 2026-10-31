package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.dto.equipment.EquipmentInstalledSpareView;
import com.windasoft.eam.entity.inventory.InventoryUsage;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** inv_usage 单表 Mapper；复杂只读投影由对应 XML 查询定义。 */
@Mapper
public interface InventoryUsageMapper extends BaseMapper<InventoryUsage> {
  List<EquipmentInstalledSpareView> installedForEquipment(
      @Param("tenant") long tenant, @Param("equipmentId") long equipmentId);
}
