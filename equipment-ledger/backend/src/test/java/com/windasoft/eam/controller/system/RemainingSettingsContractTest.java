package com.windasoft.eam.controller.system;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.windasoft.eam.controller.i18n.LanguageController;
import com.windasoft.eam.dto.i18n.LanguageContracts;
import com.windasoft.eam.dto.system.CurrentUserView;
import com.windasoft.eam.service.i18n.LanguageService;
import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RemainingSettingsContractTest {
  /** 仅三个现有动态字典字段允许Map；其他返回字段仍穿透公共门禁，不放行任意payload。 */
  private void check(Type type) {
    if (type instanceof ParameterizedType p) {
      for (var arg : p.getActualTypeArguments()) check(arg);
      return;
    }
    if (type == LanguageContracts.Resource.class
        || type == LanguageContracts.LanguagePack.class
        || type == CurrentUserView.class) {
      for (var field : ((Class<?>) type).getRecordComponents()) {
        if (field.getName().equals("messages"))
          assertEquals("java.util.Map<java.lang.String, java.lang.String>", field.getGenericType().getTypeName());
        else if (field.getName().equals("permissions"))
          assertEquals("java.util.Map<java.lang.String, java.util.Set<java.lang.String>>", field.getGenericType().getTypeName());
        else ContractTypes.assertExplicit(field.getGenericType());
      }
      return;
    }
    ContractTypes.assertExplicit(type);
  }

  @Test
  void fixedSettingsContractsAreExplicit() {
    for (var type :
        new Class<?>[] {
          PersonalSettingsController.class, AuthController.class, LanguageController.class
        })
      for (var method : type.getDeclaredMethods())
        if (Modifier.isPublic(method.getModifiers())) check(method.getGenericReturnType());
  }

  @Test
  void resourceKeepsEtagAndNotModified() throws Exception {
    var service = mock(LanguageService.class);
    when(service.resource("fr-FR"))
        .thenReturn(new LanguageContracts.Resource("fr-FR", "revision-1", Map.of("a", "b")));
    var mvc = MockMvcBuilders.standaloneSetup(new LanguageController(service)).build();
    mvc.perform(get("/api/v1/i18n/resources/fr-FR"))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"fr-FR-revision-1\""))
        .andExpect(
            content()
                .json(
                    "{\"data\":{\"locale\":\"fr-FR\",\"version\":\"revision-1\",\"messages\":{\"a\":\"b\"}}}",
                    true));
    mvc.perform(get("/api/v1/i18n/resources/fr-FR").header("If-None-Match", "\"fr-FR-revision-1\""))
        .andExpect(status().isNotModified())
        .andExpect(content().string(""));
  }

  @Test
  void invalidVersionAndMissingStateDoNotReachService() throws Exception {
    var service = mock(LanguageService.class);
    var mvc = MockMvcBuilders.standaloneSetup(new LanguageController(service)).build();
    for (String body : new String[] {"{\"rowVersion\":1.5,\"enabled\":true}", "{\"rowVersion\":1}"})
      mvc.perform(
              patch("/api/v1/system/languages/fr-FR/state")
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}
