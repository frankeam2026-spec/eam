package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.dto.experience.HomeContracts;
import com.windasoft.eam.entity.system.SystemRole;
import java.util.Collection;
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

  /**
   * 首页历史模板按status筛选，包含已逻辑删除角色；标准BaseMapper会追加deleted=0，不能等价替代。
   * wanted派生表保留数据库排序规则的匹配语义，同时返回请求编码以便一次装配多个预置角色。
   */
  @Select(
      """
      <script>
      SELECT wanted.code,r.id FROM eam_sys_role r JOIN (
      <foreach collection="codes" item="code" separator=" UNION ALL ">SELECT #{code} code</foreach>
      ) wanted ON wanted.code=r.code WHERE r.tenant_id=#{tenant} AND r.status=1
      </script>
      """)
  List<HomeContracts.PresetRole> homePresetRoles(
      @Param("tenant") long tenant, @Param("codes") Collection<String> codes);

  /** 沿用首页旧数据对逻辑删除角色的兼容口径，按整组ID读取，不逐角色查询。 */
  @Select(
      """
      <script>SELECT id FROM eam_sys_role WHERE tenant_id=#{tenant} AND status=1 AND id IN
      <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach></script>
      """)
  List<Long> homeActiveRoleIds(@Param("tenant") long tenant, @Param("ids") Collection<Long> ids);

  /** 首页适用模板历史角色关系只限定有效状态，不改变其他授权入口的deleted过滤。 */
  @Select(
      "SELECT ur.role_id FROM eam_sys_user_role ur JOIN eam_sys_role r ON r.tenant_id=ur.tenant_id"
          + " AND r.id=ur.role_id WHERE ur.tenant_id=#{tenant} AND ur.user_id=#{user} AND"
          + " r.status=1")
  List<Long> homeUserRoleIds(@Param("tenant") long tenant, @Param("user") long user);
}
