package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.workflow.WorkflowValue;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowValueMapper extends BaseMapper<WorkflowValue> {
  /** 计算结果可能覆盖本轮同名变量，沿用唯一键原子upsert，不能先查后写。 */
  @Insert(
      """
INSERT INTO wf_value(id,tenant_id,instance_id,field_key,line_no,value_type,is_null,number_value,text_value,boolean_value)
VALUES(#{id},#{tenantId},#{instanceId},#{fieldKey},0,#{valueType},0,#{numberValue},#{textValue},#{booleanValue})
ON DUPLICATE KEY UPDATE is_null=0,value_type=VALUES(value_type),number_value=VALUES(number_value),
text_value=VALUES(text_value),boolean_value=VALUES(boolean_value)
""")
  int upsertComputed(WorkflowValue row);
}
