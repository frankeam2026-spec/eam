package com.windasoft.eam.controller.system;

import com.windasoft.eam.config.security.AuthFilter;
import com.windasoft.eam.dto.system.ChangePasswordForm;
import com.windasoft.eam.dto.system.LoginForm;
import com.windasoft.eam.service.system.AuthenticationService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthenticationService service;

  public AuthController(AuthenticationService service) {
    this.service = service;
  }

  @PostMapping("/login")
  public Map<String, Object> login(
      @Valid @RequestBody LoginForm form,
      HttpServletRequest request,
      HttpServletResponse response) {
    var result = service.login(form);
    response.addHeader(
        HttpHeaders.SET_COOKIE, cookie(result.token(), request.isSecure(), 8 * 3600));
    return Map.of("data", result.user());
  }

  @GetMapping("/me")
  public Map<String, Object> me() {
    return service.me();
  }

  @PostMapping("/logout")
  public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
    service.logout(token(request));
    response.addHeader(HttpHeaders.SET_COOKIE, cookie("", request.isSecure(), 0));
    return Map.of("data", Map.of("loggedOut", true));
  }

  @PostMapping("/password")
  public Map<String, Object> changePassword(
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
