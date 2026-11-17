package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.workflow.WorkflowBinding;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowBindingMapper extends BaseMapper<WorkflowBinding> {
  /** 唯一复合键上的原子初始化；先查再插不能抵挡同公司并发发布。 */
  @Insert(
      """
      INSERT IGNORE INTO wf_binding(tenant_id,company_id,business_type,requires_approval)
      VALUES(#{tenantId},#{companyId},#{businessType},#{requiresApproval})
      """)
  int insertIfAbsent(WorkflowBinding row);
}
