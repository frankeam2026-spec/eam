package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.workflow.WorkflowScriptRun;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowScriptRunMapper extends BaseMapper<WorkflowScriptRun> {
  /** finished_at无默认值，必须保持原数据库NOW(3)而非应用时钟。 */
  @Insert(
      """
INSERT INTO wf_script_run(id,tenant_id,instance_id,node_key,execution_id,source_hash,started_at,finished_at,value_type,number_value,text_value,boolean_value)
VALUES(#{id},#{tenantId},#{instanceId},#{nodeKey},#{executionId},#{sourceHash},#{startedAt},NOW(3),#{valueType},#{numberValue},#{textValue},#{booleanValue})
""")
  int insertAtDatabaseTime(WorkflowScriptRun row);
}
