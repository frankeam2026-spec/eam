package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.system.SystemUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {
  /** 历史业务显示姓名包含已删除用户，不能使用会自动追加逻辑删除条件的 BaseMapper 查询。 */
  @Select("SELECT display_name FROM eam_sys_user WHERE tenant_id=#{tenant} AND id=#{id}")
  String displayNameIncludingDeleted(@Param("tenant") long tenant, @Param("id") long id);
}
