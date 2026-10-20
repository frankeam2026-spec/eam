package com.windasoft.eam.config.security;

import com.windasoft.eam.service.system.AuthContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthFilter extends OncePerRequestFilter {
  public static final String COOKIE = "EAM_SESSION";
  private final com.windasoft.eam.repository.system.AuthenticationRepository repository;
  private final org.springframework.core.env.Environment environment;

  @org.springframework.beans.factory.annotation.Value("${eam.security.demo-tenant-id:10001}")
  private Long demoTenant;

  public AuthFilter(
      com.windasoft.eam.repository.system.AuthenticationRepository repository,
      org.springframework.core.env.Environment environment) {
    this.repository = repository;
    this.environment = environment;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/") || "OPTIONS".equals(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!safeMethod(request.getMethod())) {
      String origin = request.getHeader("Origin");
      String expected = request.getScheme() + "://" + request.getHeader("Host");
      if (origin != null && !origin.equals(expected)) {
        response.sendError(403, "请求来源不允许");
        return;
      }
    }
    if ("GET".equals(request.getMethod())
        && (request.getRequestURI().equals("/api/v1/i18n/languages")
            || request.getRequestURI().matches("/api/v1/i18n/resources/[a-zA-Z0-9-]+")
            || request.getRequestURI().equals("/api/v1/branding/public")
            || request
                .getRequestURI()
                .matches("/api/v1/branding/public/assets/[0-9]+/[0-9a-f-]{36}"))) {
      // Public translation resources may use an existing session to select the tenant,
      // but stay readable when anonymous or when that session has expired.
      if (request.getRequestURI().startsWith("/api/v1/i18n/") && request.getCookies() != null)
        for (Cookie cookie : request.getCookies())
          if (COOKIE.equals(cookie.getName())
              && cookie.getValue().matches("[A-Za-z0-9_-]{40,80}")) {
            var session = repository.activeSession(hash(cookie.getValue()), LocalDateTime.now());
            if (session != null) {
              var user = repository.activeUserById(session.getTenantId(), session.getUserId());
              if (user != null) request.setAttribute(AuthContext.ATTRIBUTE, user);
            }
            break;
          }
      chain.doFilter(request, response);
      return;
    }
    if (request.getRequestURI().equals("/api/v1/auth/login")) {
      chain.doFilter(request, response);
      return;
    }
    if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local-dev"))) {
      var user = repository.activeUser(demoTenant, "admin");
      if (user == null) {
        reject(request, response);
        return;
      }
      request.setAttribute(AuthContext.ATTRIBUTE, user);
      chain.doFilter(request, response);
      return;
    }
    String token = null;
    if (request.getCookies() != null)
      for (Cookie cookie : request.getCookies())
        if (COOKIE.equals(cookie.getName())) token = cookie.getValue();
    if (token == null || !token.matches("[A-Za-z0-9_-]{40,80}")) {
      reject(request, response);
      return;
    }
    if (!safeMethod(request.getMethod()) && request.getHeader("Origin") == null) {
      response.sendError(403, "缺少请求来源");
      return;
    }
    var session = repository.activeSession(hash(token), LocalDateTime.now());
    if (session == null) {
      reject(request, response);
      return;
    }
    var user = repository.activeUserById(session.getTenantId(), session.getUserId());
    if (user == null) {
      reject(request, response);
      return;
    }
    request.setAttribute(AuthContext.ATTRIBUTE, user);
    chain.doFilter(request, response);
  }

  public static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean safeMethod(String method) {
    return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
  }

  private String publicMessage(HttpServletRequest request, String key) {
    String locale = "zh-CN";
    try {
      for (var range :
          java.util.Locale.LanguageRange.parse(
              java.util.Objects.toString(request.getHeader("Accept-Language"), "zh-CN"))) {
        String tag = java.util.Locale.forLanguageTag(range.getRange()).toLanguageTag();
        if (com.windasoft.eam.common.BuiltinMessages.NAMES.containsKey(tag)) {
          locale = tag;
          break;
        }
      }
    } catch (IllegalArgumentException ignored) {
    }
    return com.windasoft.eam.common.BuiltinMessages.get(locale).getOrDefault(key, "请求无法完成");
  }

  private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setStatus(401);
    response.setContentType("application/json;charset=UTF-8");
    response
        .getWriter()
        .write(
            new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(
                    java.util.Map.of(
                        "code",
                        "UNAUTHENTICATED",
                        "message",
                        publicMessage(request, "auth.error.signInRequired"))));
  }
}
