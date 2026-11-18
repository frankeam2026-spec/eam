package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.entity.system.SystemRole;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemRoleMapper extends BaseMapper<SystemRole> {
  @Select(
      "SELECT DISTINCT r.name FROM eam_sys_role r JOIN eam_sys_user_role ur ON"
          + " ur.tenant_id=r.tenant_id AND ur.role_id=r.id WHERE ur.tenant_id=#{tenant} AND"
          + " ur.user_id=#{user} AND r.deleted=0 AND r.status=1 ORDER BY r.name")
  List<String> names(@Param("tenant") long tenant, @Param("user") long user);
}
