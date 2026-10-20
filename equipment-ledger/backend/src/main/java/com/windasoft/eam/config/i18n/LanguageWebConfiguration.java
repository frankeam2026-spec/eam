package com.windasoft.eam.config.i18n;

import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.service.i18n.LanguageService;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class LanguageWebConfiguration implements WebMvcConfigurer {
  private final LanguageService languages;

  public LanguageWebConfiguration(LanguageService languages) {
    this.languages = languages;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new HandlerInterceptor() {
          @Override
          public boolean preHandle(
              HttpServletRequest req, HttpServletResponse res, Object handler) {
            if (req.getRequestURI().startsWith("/api/v1/")) {
              String locale = languages.currentLocale();
              MessageContext.setLocale(locale);
              MessageContext.set(languages.messages(locale));
            }
            return true;
          }

          @Override
          public void afterCompletion(
              HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
            MessageContext.clear();
          }
        });
  }
}
