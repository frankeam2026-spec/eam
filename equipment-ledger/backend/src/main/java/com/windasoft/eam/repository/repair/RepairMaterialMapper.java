package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.dto.repair.RepairReadViews;
import com.windasoft.eam.entity.repair.RepairMaterial;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RepairMaterialMapper extends BaseMapper<RepairMaterial> {
  /** 跨表展示需要保留关联SQL，简单读写继续复用BaseMapper。 */
  @Select(
      "SELECT CAST(m.id AS CHAR) id,CAST(m.stock_id AS CHAR)"
          + " stockId,s.code,s.name,s.model,m.quantity,m.issued_quantity"
          + " issuedQuantity,m.installed_quantity installedQuantity,m.status FROM"
          + " eam_repair_material m JOIN eam_spare_stock s ON s.id=m.stock_id AND"
          + " s.tenant_id=m.tenant_id WHERE m.tenant_id=#{tenant} AND m.order_id=#{orderId} ORDER"
          + " BY m.created_at DESC,m.id DESC")
  List<RepairReadViews.Material> materials(long tenant, long orderId);
}
