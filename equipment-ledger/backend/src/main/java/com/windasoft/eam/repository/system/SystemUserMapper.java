package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.dto.system.UserNameOption;
import com.windasoft.eam.entity.system.SystemUser;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {
  /** 用户选择需同租户关联组织名；不能用逐用户读取组织代替此只读JOIN，组织停用/删除不改变历史展示。 */
  @Select(
      """
      SELECT CAST(u.id AS CHAR) id,u.display_name name,u.account,COALESCE(o.name,'') department
      FROM eam_sys_user u LEFT JOIN eam_org_unit o ON o.id=u.org_id AND o.tenant_id=u.tenant_id
      WHERE u.tenant_id=#{tenant} AND u.deleted=0 AND u.status=1 ORDER BY u.display_name,u.id
      """)
  List<UserNameOption> nameOptions(@Param("tenant") long tenant);

  /** 用户、成员关系、有效角色需联查；租户和公司组织范围均在SQL内限定。 */
  @Select(
      """
<script>
SELECT DISTINCT u.id,u.org_id FROM eam_sys_user u
JOIN eam_sys_user_role r ON r.tenant_id=u.tenant_id AND r.user_id=u.id
JOIN eam_sys_role s ON s.tenant_id=r.tenant_id AND s.id=r.role_id
WHERE u.tenant_id=#{tenant} AND u.deleted=0 AND u.status=1 AND s.deleted=0 AND s.status=1
AND r.role_id IN
<foreach collection="roles" item="role" open="(" separator="," close=")">#{role}</foreach>
AND u.org_id IN
<foreach collection="organizations" item="org" open="(" separator="," close=")">#{org}</foreach>
</script>
""")
  List<SystemUser> activeRoleMembers(
      @Param("tenant") long tenant,
      @Param("roles") List<Long> roles,
      @Param("organizations") List<Long> organizations);

  /** 历史业务显示姓名包含已删除用户，不能使用会自动追加逻辑删除条件的 BaseMapper 查询。 */
  @Select("SELECT display_name FROM eam_sys_user WHERE tenant_id=#{tenant} AND id=#{id}")
  String displayNameIncludingDeleted(@Param("tenant") long tenant, @Param("id") long id);
}
