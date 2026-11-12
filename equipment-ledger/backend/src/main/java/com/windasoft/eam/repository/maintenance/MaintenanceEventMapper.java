package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.maintenance.MaintenanceEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaintenanceEventMapper extends BaseMapper<MaintenanceEvent> {}
