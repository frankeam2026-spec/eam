package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.system.UserEmailProfile;
import com.windasoft.eam.dto.system.UserNameOption;
import com.windasoft.eam.entity.system.SystemUser;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/** 系统用户仓储：按租户访问用户主体和邮箱版本，身份及角色权限在服务层校验。 */
@Repository
public class SystemUserRepository {
  private final SystemUserMapper mapper;

  public SystemUserRepository(SystemUserMapper mapper) {
    this.mapper = mapper;
  }

  public SystemUser find(long tenant, Long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getId, id));
  }

  public Page<SystemUser> ownerCandidates(long tenant, ListQuery query) {
    var where =
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getStatus, 1);
    if (!query.keyword().isBlank()) {
      where.and(
          w ->
              w.like(SystemUser::getDisplayName, query.keyword())
                  .or()
                  .like(SystemUser::getAccount, query.keyword()));
    }
    where.orderByAsc(SystemUser::getDisplayName).orderByAsc(SystemUser::getId);
    return mapper.selectPage(new Page<>(query.page(), query.size()), where);
  }

  public List<UserNameOption> nameOptions(long tenant) {
    return mapper.nameOptions(tenant);
  }

  public List<SystemUser> employees(long tenant) {
    return mapper.selectList(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getStatus, 1));
  }

  public List<SystemUser> users(long tenant) {
    return mapper.selectList(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .orderByAsc(SystemUser::getId));
  }

  public SystemUser user(long tenant, Long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getId, id));
  }

  public int insertUser(SystemUser x) {
    return mapper.insert(x);
  }

  public int updateUser(SystemUser x) {
    return mapper.updateById(x);
  }

  public int deleteUser(Long id) {
    return mapper.deleteById(id);
  }

  public long organizationUserCount(long tenant, Long id) {
    return mapper.selectCount(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getOrgId, id));
  }

  public long accountCount(long tenant, String account, Long current) {
    var q =
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getAccount, account.trim());
    if (current != null) q.ne(SystemUser::getId, current);
    return mapper.selectCount(q);
  }

  public Map<Long, UserEmailProfile> emailProfiles(long tenant, Collection<Long> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<Long, UserEmailProfile> result = new LinkedHashMap<>();
    for (var row : mapper.emailProfilesIncludingDeleted(tenant, List.copyOf(ids))) {
      result.put(row.id(), row);
    }
    return result;
  }

  public UserEmailProfile emailProfile(long tenant, long user) {
    var row = emailProfiles(tenant, List.of(user)).get(user);
    if (row == null) {
      throw new EmptyResultDataAccessException(1);
    }
    return row;
  }

  /** 邮箱使用独立版本原子更新；通用账号更新的FieldStrategy.NEVER不限制此显式更新。 */
  public int updateEmail(long tenant, long user, String email, int expectedVersion) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getId, user)
            .eq(SystemUser::getEmailVersion, expectedVersion)
            .set(SystemUser::getEmail, email)
            .setSql("email_version=email_version+1"));
  }

  /** 固定人员配置批量核对，仅取得身份与所属组织，不读取密码和邮箱。 */
  public List<SystemUser> activeIdentities(long tenant, List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<SystemUser>()
            .select(SystemUser::getId, SystemUser::getOrgId)
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getStatus, 1)
            .in(SystemUser::getId, ids));
  }

  /** 角色候选在数据库侧限定适用公司组织，避免逐角色逐用户查询。 */
  public List<SystemUser> activeRoleMembers(
      long tenant, List<Long> roles, List<Long> organizations) {
    if (roles.isEmpty() || organizations.isEmpty()) {
      return List.of();
    }
    return mapper.activeRoleMembers(tenant, roles, organizations);
  }

  public SystemUser activeUser(long tenant, long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getId, id)
            .eq(SystemUser::getStatus, 1));
  }

  /** 历史执行人可能已停用或删除；仅按租户和标识读取姓名，不因此恢复其业务操作资格。 */
  public String displayNameIncludingDeleted(long tenant, long id) {
    return mapper.displayNameIncludingDeleted(tenant, id);
  }
}
