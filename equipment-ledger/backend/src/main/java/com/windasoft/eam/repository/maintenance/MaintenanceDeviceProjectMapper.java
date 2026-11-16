package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.maintenance.MaintenanceDeviceProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MaintenanceDeviceProjectMapper extends BaseMapper<MaintenanceDeviceProject> {
  /** 只有启用安排阻止删除标准项目；需要关联安排状态，保留原只读JOIN口径。 */
  @Select(
      """
SELECT COUNT(*) FROM eam_maintenance_device_project r
JOIN eam_maintenance_plan_device d ON d.tenant_id=r.tenant_id AND d.id=r.device_id
WHERE r.tenant_id=#{tenant} AND r.standard_id=#{standard} AND r.item_key=#{item} AND d.enabled=1
""")
  long activeReferences(
      @Param("tenant") long tenant, @Param("standard") long standard, @Param("item") String item);
}
