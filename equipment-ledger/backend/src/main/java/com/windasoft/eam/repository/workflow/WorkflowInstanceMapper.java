package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.workflow.WorkflowInstance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowInstanceMapper extends BaseMapper<WorkflowInstance> {}
