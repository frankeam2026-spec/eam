package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.dto.equipment.EquipmentInstalledSpareView;
import com.windasoft.eam.entity.equipment.EquipmentSpare;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EquipmentSpareMapper extends BaseMapper<EquipmentSpare> {
  /** 原操作由数据库生成时间，不能用应用服务器时钟替代；其余CRUD使用BaseMapper。 */
  @org.apache.ibatis.annotations.Insert(
      "INSERT INTO"
          + " eam_equipment_spare(id,tenant_id,equipment_id,stock_id,order_id,quantity,status,installed_at)"
          + " VALUES(#{id},#{tenantId},#{equipmentId},#{stockId},#{orderId},#{quantity},#{status},NOW())")
  int insertAtDatabaseTime(EquipmentSpare row);

  /** 跨表展示需要保留关联SQL，简单读写继续复用BaseMapper。 */
  @Select(
      "SELECT CAST(p.id AS CHAR)"
          + " id,s.code,s.name,s.model,p.quantity,p.status,DATE_FORMAT(p.installed_at,'%Y-%m-%d"
          + " %H:%i') installedAt FROM eam_equipment_spare p JOIN eam_spare_stock s ON"
          + " s.id=p.stock_id AND s.tenant_id=p.tenant_id WHERE p.tenant_id=#{tenant} AND"
          + " p.equipment_id=#{equipmentId} AND p.status='在用' ORDER BY p.installed_at DESC,p.id"
          + " DESC")
  List<EquipmentInstalledSpareView> installed(long tenant, long equipmentId);
}
