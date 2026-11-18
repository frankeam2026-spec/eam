package com.windasoft.eam.controller.system;

import com.windasoft.eam.config.security.AuthFilter;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.system.AuthenticatedUserView;
import com.windasoft.eam.dto.system.ChangePasswordForm;
import com.windasoft.eam.dto.system.CurrentUserView;
import com.windasoft.eam.dto.system.LoginForm;
import com.windasoft.eam.dto.system.LogoutResult;
import com.windasoft.eam.dto.common.SuccessResponse;
import com.windasoft.eam.service.system.AuthenticationService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证接口：提供登录与会话管理入口，密码和会话校验由认证服务处理。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthenticationService service;

  public AuthController(AuthenticationService service) {
    this.service = service;
  }

  @PostMapping("/login")
  public DataResponse<AuthenticatedUserView> login(
      @Valid @RequestBody LoginForm form,
      HttpServletRequest request,
      HttpServletResponse response) {
    var result = service.login(form);
    response.addHeader(
        HttpHeaders.SET_COOKIE, cookie(result.token(), request.isSecure(), 8 * 3600));
    return new DataResponse<>(result.user());
  }

  @GetMapping("/me")
  public CurrentUserView me() {
    return service.me();
  }

  @PostMapping("/logout")
  public DataResponse<LogoutResult> logout(
      HttpServletRequest request, HttpServletResponse response) {
    service.logout(token(request));
    response.addHeader(HttpHeaders.SET_COOKIE, cookie("", request.isSecure(), 0));
    return new DataResponse<>(new LogoutResult(true));
  }

  @PostMapping("/password")
  public SuccessResponse changePassword(
      @Valid @RequestBody ChangePasswordForm form, HttpServletRequest request) {
    return service.changePassword(form, token(request));
  }

  private String token(HttpServletRequest request) {
    String token = null;
    if (request.getCookies() != null)
      for (var c : request.getCookies())
        if (AuthFilter.COOKIE.equals(c.getName())) token = c.getValue();
    return token;
  }

  private String cookie(String value, boolean secure, int maxAge) {
    return AuthFilter.COOKIE
        + "="
        + value
        + "; HttpOnly; SameSite=Strict; Path=/api/v1; Max-Age="
        + maxAge
        + (secure ? "; Secure" : "");
  }
}
