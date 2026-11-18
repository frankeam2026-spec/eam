package com.windasoft.eam.service.system;

import com.windasoft.eam.repository.system.SystemUserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthBootstrapService {
  private final SystemUserRepository repository;

  public AuthBootstrapService(SystemUserRepository repository) {
    this.repository = repository;
  }

  /** 启动参数只用于首次空密码初始化，条件更新防止多实例或用户改密后被覆盖。 */
  public void initialize(long tenantId, String password) {

    if (password.isBlank()) return;
    if (password.length() < 6 || password.length() > 72)
      throw new IllegalStateException("EAM_BOOTSTRAP_ADMIN_PASSWORD 长度需为 6 至 72 位");
    var admin = repository.bootstrapAdmin(tenantId);
    if (admin != null && admin.getPasswordHash() == null) {
      repository.initializePassword(
          tenantId, admin.getId(), new BCryptPasswordEncoder().encode(password));
    }
  }
}
