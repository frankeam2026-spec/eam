package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.workflow.WorkflowEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowEventMapper extends BaseMapper<WorkflowEvent> {}
