package com.windasoft.eam.service.system;

import com.windasoft.eam.entity.system.SystemUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
/** 请求身份上下文：提供可信的用户及租户身份，客户端参数不能替代服务端身份。 */
public class AuthContext {
  public static final String ATTRIBUTE = AuthContext.class.getName() + ".user";
  private final HttpServletRequest request;

  public AuthContext(HttpServletRequest request) {
    this.request = request;
  }

  public SystemUser user() {
    var systemUser = SystemExecution.current();
    if (systemUser != null) return systemUser;
    var user = (SystemUser) request.getAttribute(ATTRIBUTE);
    if (user == null) throw new IllegalStateException("缺少登录身份");
    return user;
  }

  public long userId() {
    return user().getId();
  }

  public long tenantId() {
    return user().getTenantId();
  }
}
