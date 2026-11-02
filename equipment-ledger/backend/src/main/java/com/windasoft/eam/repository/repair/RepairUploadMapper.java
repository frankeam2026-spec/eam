package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.repair.RepairUploadRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RepairUploadMapper extends BaseMapper<RepairUploadRecord> {}
