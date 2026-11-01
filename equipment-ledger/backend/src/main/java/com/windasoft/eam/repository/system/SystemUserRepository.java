package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import com.windasoft.eam.entity.system.SystemUser;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;

/** 系统用户自身数据的租户限定读取。 */
@Repository
public class SystemUserRepository {
  private final SystemUserMapper mapper;
  private final JdbcTemplate db;

  public SystemUserRepository(SystemUserMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  public SystemUser find(long tenant, Long id) {
    return mapper.selectOne(new LambdaQueryWrapper<SystemUser>()
        .eq(SystemUser::getTenantId, tenant).eq(SystemUser::getId, id));
  }

  public Page<SystemUser> ownerCandidates(
      long tenant, com.windasoft.eam.dto.common.ListQuery query) {
    var where = new LambdaQueryWrapper<SystemUser>()
        .eq(SystemUser::getTenantId, tenant)
        .eq(SystemUser::getStatus, 1);
    if (!query.keyword().isBlank()) {
      where.and(w -> w.like(SystemUser::getDisplayName, query.keyword())
          .or().like(SystemUser::getAccount, query.keyword()));
    }
    where.orderByAsc(SystemUser::getDisplayName).orderByAsc(SystemUser::getId);
    return mapper.selectPage(new Page<>(query.page(), query.size()), where);
  }

  public List<SystemUser> employees(long tenant) {
    return mapper.selectList(new LambdaQueryWrapper<SystemUser>()
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
  public Map<Long, Map<String, Object>> emailProfiles(
      long tenant, java.util.Collection<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    var args = new java.util.ArrayList<Object>();
    args.add(tenant);
    args.addAll(ids);
    var result = new java.util.HashMap<Long, Map<String, Object>>();
    for (var row : db.queryForList(
        "SELECT id,email,email_version FROM eam_sys_user WHERE tenant_id=? AND id IN ("
            + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ")",
        args.toArray())) {
      result.put(((Number) row.get("id")).longValue(), row);
    }
    return result;
  }

  public Map<String, Object> emailProfile(long tenant, long user) {
    return db.queryForMap(
        "SELECT email,email_version FROM eam_sys_user WHERE tenant_id=? AND id=?",
        tenant,
        user);
  }

  public int updateEmail(long tenant, long user, String email, int expectedVersion) {
    return db.update(
        "UPDATE eam_sys_user SET email=?,email_version=email_version+1"
            + " WHERE tenant_id=? AND id=? AND email_version=? AND deleted=0",
        email,
        tenant,
        user,
        expectedVersion);
  }
}
