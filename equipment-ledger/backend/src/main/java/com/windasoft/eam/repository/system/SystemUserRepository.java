package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.windasoft.eam.dto.business.BusinessChoiceQuery;
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

  /** 普通资料变更不能携带整行读出的凭据或独立版本管理的邮箱。 */
  public void updateProfile(SystemUser user) {
    mapper.update(
        null,
        accountRow(user)
            .set(SystemUser::getAccount, user.getAccount())
            .set(SystemUser::getDisplayName, user.getDisplayName())
            .set(SystemUser::getEmployeeNo, user.getEmployeeNo())
            .set(SystemUser::getPhone, user.getPhone())
            .set(SystemUser::getOrgId, user.getOrgId())
            .set(SystemUser::getStatus, user.getStatus())
            .set(SystemUser::getUpdatedAt, user.getUpdatedAt()));
  }

  public void updateStatus(SystemUser user) {
    mapper.update(
        null,
        accountRow(user)
            .set(SystemUser::getStatus, user.getStatus())
            .set(SystemUser::getUpdatedAt, user.getUpdatedAt()));
  }

  public void updateDepartment(SystemUser user) {
    mapper.update(
        null,
        accountRow(user)
            .set(SystemUser::getOrgId, user.getOrgId())
            .set(SystemUser::getUpdatedAt, user.getUpdatedAt()));
  }

  private LambdaUpdateWrapper<SystemUser> accountRow(SystemUser user) {
    return new LambdaUpdateWrapper<SystemUser>()
        .eq(SystemUser::getTenantId, user.getTenantId())
        .eq(SystemUser::getId, user.getId());
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
  public Page<SystemUser> notificationChoices(long tenant, String q, int page, int size) {
    return mapper.selectPage(
        new Page<>(page, size),
        new LambdaQueryWrapper<SystemUser>()
            .select(SystemUser::getId, SystemUser::getDisplayName, SystemUser::getEmail)
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getStatus, 1)
            .and(
                w ->
                    w.apply("LOCATE({0},display_name)>0", q).or().apply("LOCATE({0},account)>0", q))
            .orderByAsc(SystemUser::getDisplayName, SystemUser::getId));
  }

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

  public SystemUser activeUser(long tenant, String account) {
    var query =
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getAccount, account)
            .eq(SystemUser::getStatus, 1);
    return mapper.selectOne(query);
  }

  /** 改密先锁本租户用户，密码写入和会话撤销由认证服务的同一事务协调。 */
  public SystemUser lockUser(long tenant, long user) {
    return mapper.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getId, user)
            .last("FOR UPDATE"));
  }

  /** 只更新密码；保留旧改密的更新时间语义，不覆盖邮箱、资料或其他并发更新字段。 */
  public void updatePassword(SystemUser user) {
    mapper.update(
        null,
        accountRow(user)
            .set(SystemUser::getPasswordHash, user.getPasswordHash())
            .set(SystemUser::getUpdatedAt, user.getUpdatedAt()));
  }

  public SystemUser bootstrapAdmin(long tenant) {
    return mapper.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getAccount, "admin"));
  }

  /** 多实例初始化只填充空密码，不覆盖已由用户修改或另一个实例初始化的密码。 */
  public void initializePassword(long tenant, long user, String hash) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getId, user)
            .isNull(SystemUser::getPasswordHash)
            .set(SystemUser::getPasswordHash, hash)
            .setSql("updated_at=updated_at"));
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

  /** 内部完整引用列表与分页有不同历史口径：全范围可保留组织已失效的启用用户，分页仍限定启用组织集合。 */
  public List<SystemUser> businessReferences(
      long tenant, boolean all, Collection<Long> organizations, Long ownUser) {
    return mapper.selectList(referenceScope(tenant, all, organizations, ownUser));
  }

  /** 公司/部门授权与本人回退在SQL内取并集；不能先分页再到Java中过滤。 */
  public Page<SystemUser> businessChoices(
      long tenant, BusinessChoiceQuery query, Collection<Long> organizations, Long ownUser) {
    var where =
        referenceScope(tenant, false, organizations, ownUser)
            .apply("LOWER(display_name) LIKE {0} ESCAPE '!'", query.likePattern());
    return mapper.selectPage(new Page<>(query.page(), query.size()), where);
  }

  private QueryWrapper<SystemUser> referenceScope(
      long tenant, boolean all, Collection<Long> organizations, Long ownUser) {
    var where =
        new QueryWrapper<SystemUser>()
            .select("id", "display_name", "org_id", "status")
            .eq("tenant_id", tenant)
            .eq("status", 1)
            .orderByAsc("display_name", "CAST(id AS CHAR)");
    if (!all) {
      if (organizations.isEmpty()) {
        if (ownUser == null) where.apply("0=1");
        else where.eq("id", ownUser);
      } else
        where.and(
            scope -> {
              scope.in("org_id", organizations);
              if (ownUser != null) scope.or().eq("id", ownUser);
            });
    }
    return where;
  }

  public SystemUser referenceForShare(long tenant, long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .select(
                SystemUser::getId,
                SystemUser::getDisplayName,
                SystemUser::getOrgId,
                SystemUser::getStatus)
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getId, id)
            .last("FOR SHARE"));
  }
}
