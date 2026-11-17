package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.maintenance.MaintenanceTask;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MaintenanceTaskMapper extends BaseMapper<MaintenanceTask> {
  /**
   * 保留原生成原子INSERT IGNORE及唯一键幂等语义，影响行数为0时调用方不写人员、来源或通知。 BaseMapper没有等价的忽略重复插入能力，不能以先查后插替代数据库唯一约束。
   */
  @Insert(
      """
      INSERT IGNORE INTO eam_maintenance_task
        (id,tenant_id,code,plan_id,schedule_id,equipment_id,type_id,company_id,department_id,
         due_date,expires_date,snapshot)
      VALUES (#{id},#{tenantId},#{code},#{planId},#{scheduleId},#{equipmentId},#{typeId},
              #{companyId},#{departmentId},#{dueDate},#{expiresDate},#{snapshot})
      """)
  int insertIfAbsent(MaintenanceTask row);

  /** 全局MP逻辑删除会自动过滤deleted，无法保留原任务锁集合；此锁入口明确包括已删除记录。 */
  @Select("SELECT id FROM eam_maintenance_task WHERE tenant_id=#{tenant} AND id=#{task} FOR UPDATE")
  Long lockIncludingDeleted(@Param("tenant") long tenant, @Param("task") long task);

  /** 先锁原始表中全部匹配行，再由调用方忽略已删除记录；不通过关系视图加锁。 */
  @Select(
      """
      SELECT id,deleted FROM eam_maintenance_task
      WHERE tenant_id=#{tenant} AND expires_date<#{today} AND status=#{status} FOR UPDATE
      """)
  List<MaintenanceTask> lockExpiredIncludingDeleted(
      @Param("tenant") long tenant,
      @Param("today") LocalDate today,
      @Param("status") String status);
}
