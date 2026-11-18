package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.system.AuthSession;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

/** 认证会话主仓储，只保存令牌摘要；用户和组织读写各归对应主仓储。 */
@Repository
public class AuthenticationRepository {
  private final AuthSessionMapper sessions;

  public AuthenticationRepository(AuthSessionMapper sessions) {
    this.sessions = sessions;
  }

  public AuthSession activeSession(String hash, LocalDateTime now) {
    return sessions.selectOne(
        new LambdaQueryWrapper<AuthSession>()
            .eq(AuthSession::getTokenHash, hash)
            .gt(AuthSession::getExpiresAt, now));
  }

  public void insertSession(AuthSession session) {
    sessions.insert(session);
  }

  public void deleteToken(String hash) {
    sessions.delete(new LambdaQueryWrapper<AuthSession>().eq(AuthSession::getTokenHash, hash));
  }

  public void revokeOtherSessions(long tenant, long user, String hash) {
    var where =
        new LambdaQueryWrapper<AuthSession>()
            .eq(AuthSession::getTenantId, tenant)
            .eq(AuthSession::getUserId, user);
    if (hash != null) where.ne(AuthSession::getTokenHash, hash);
    sessions.delete(where);
  }

  public void revokeSessions(long tenant, Long user) {
    sessions.delete(
        new LambdaQueryWrapper<AuthSession>()
            .eq(AuthSession::getTenantId, tenant)
            .eq(AuthSession::getUserId, user));
  }
}
