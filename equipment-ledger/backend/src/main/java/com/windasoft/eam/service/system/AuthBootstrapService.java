package com.windasoft.eam.service.system;

import com.windasoft.eam.repository.system.AuthenticationRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthBootstrapService {
  private final AuthenticationRepository repository;

  public AuthBootstrapService(AuthenticationRepository repository) {
    this.repository = repository;
  }

  public void initialize(long tenantId, String password) {

    if (password.isBlank()) return;
    if (password.length() < 6 || password.length() > 72)
      throw new IllegalStateException("EAM_BOOTSTRAP_ADMIN_PASSWORD 长度需为 6 至 72 位");
    var admin = repository.bootstrapAdmin(tenantId);
    if (admin != null && admin.getPasswordHash() == null) {
      admin.setPasswordHash(new BCryptPasswordEncoder().encode(password));
      repository.updateUser(admin);
    }
  }
}
