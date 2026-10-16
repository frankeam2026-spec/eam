package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.windasoft.eam.entity.system.AuthSession;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.entity.system.SystemUser;
import org.springframework.stereotype.Repository;

@Repository
public class AuthenticationRepository {
  private final SystemUserMapper users;
  private final AuthSessionMapper sessions;
  private final OrgUnitMapper orgs;

  public AuthenticationRepository(
      SystemUserMapper users, AuthSessionMapper sessions, OrgUnitMapper orgs) {
    this.users = users;
    this.sessions = sessions;
    this.orgs = orgs;
  }

  public AuthSession activeSession(String hash, java.time.LocalDateTime now) {
    return sessions.selectOne(
        new LambdaQueryWrapper<AuthSession>()
            .eq(AuthSession::getTokenHash, hash)
            .gt(AuthSession::getExpiresAt, now));
  }

  public SystemUser activeUserById(long tenant, Long id) {
    return users.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getId, id)
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getStatus, 1));
  }

  public SystemUser activeUser(long tenant, String account) {
    return users.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getAccount, account)
            .eq(SystemUser::getStatus, 1));
  }

  public SystemUser lockUser(long tenant, long user) {
    return users.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getId, user)
            .last("FOR UPDATE"));
  }

  public void updateUser(SystemUser user) {
    users.updateById(user);
  }

  public void insertSession(AuthSession session) {
    sessions.insert(session);
  }

  public void deleteToken(String hash) {
    sessions.delete(new QueryWrapper<AuthSession>().eq("token_hash", hash));
  }

  public void revokeOtherSessions(long tenant, long user, String hash) {
    var q = new QueryWrapper<AuthSession>().eq("tenant_id", tenant).eq("user_id", user);
    if (hash != null) q.ne("token_hash", hash);
    sessions.delete(q);
  }

  public OrgUnit organization(long tenant, Long id) {
    return orgs.selectOne(
        new LambdaQueryWrapper<OrgUnit>().eq(OrgUnit::getTenantId, tenant).eq(OrgUnit::getId, id));
  }

  public SystemUser bootstrapAdmin(long tenant) {
    return users.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getAccount, "admin"));
  }
}
