package com.windasoft.eam.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AuthBootstrap implements CommandLineRunner {
  private final com.windasoft.eam.service.system.AuthBootstrapService service;
  private final long tenantId;
  private final String password;

  public AuthBootstrap(
      com.windasoft.eam.service.system.AuthBootstrapService service,
      @Value("${eam.security.demo-tenant-id:10001}") long tenantId,
      @Value("${eam.security.bootstrap-admin-password:}") String password) {
    this.service = service;
    this.tenantId = tenantId;
    this.password = password;
  }

  @Override
  public void run(String... args) {
    service.initialize(tenantId, password);
  }
}
