package com.windasoft.eam.controller.system;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.DeletionResult;
import com.windasoft.eam.dto.system.BrandingAssetView;
import com.windasoft.eam.dto.system.BrandingImage;
import com.windasoft.eam.dto.system.PersonalizationForm;
import com.windasoft.eam.dto.system.PersonalizationView;
import com.windasoft.eam.dto.system.PublicBrandingView;
import com.windasoft.eam.service.system.PersonalizationService;
import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PersonalizationContractTest {
  @Test
  void allPersonalizationResponsesHaveExplicitTypes() {
    for (var method : PersonalizationController.class.getDeclaredMethods())
      if (Modifier.isPublic(method.getModifiers()))
        ContractTypes.assertExplicit(method.getGenericReturnType());
  }

  @Test
  void originalHttpEnvelopesAndImageHeadersRemainCompatible() throws Exception {
    var service = mock(PersonalizationService.class);
    var mvc = MockMvcBuilders.standaloneSetup(new PersonalizationController(service)).build();
    var privateView =
        new PersonalizationView(
            "测试系统", "/logo", "/background", "center", "", "", 2, "2026-11-18T08:00");
    when(service.get()).thenReturn(privateView);
    when(service.save(any())).thenReturn(privateView);
    when(service.publicConfig(7L))
        .thenReturn(new PublicBrandingView("测试系统", "/logo", "/background", "center"));
    mvc.perform(get("/api/v1/branding/public").param("tenantId", "7"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
{"data":{"systemName":"测试系统","logoUrl":"/logo","backgroundUrl":"/background","backgroundPosition":"center"}}
""",
                    true));
    String expected =
        """
{"data":{"systemName":"测试系统","logoUrl":"/logo","backgroundUrl":"/background","backgroundPosition":"center","logoFileId":"","backgroundFileId":"","rowVersion":2,"updatedAt":"2026-11-18T08:00"}}
""";
    mvc.perform(get("/api/v1/personalization"))
        .andExpect(status().isOk())
        .andExpect(content().json(expected, true));
    mvc.perform(
            put("/api/v1/personalization")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"systemName":"测试系统","backgroundPosition":"center","rowVersion":"1"}
                    """))
        .andExpect(status().isOk())
        .andExpect(content().json(expected, true));
    when(service.upload(any(), any())).thenReturn(new BrandingAssetView("a", "/asset/a"));
    mvc.perform(
            multipart("/api/v1/personalization/assets")
                .file(new MockMultipartFile("file", "a.png", "image/png", new byte[] {1}))
                .param("kind", "logo"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"data\":{\"id\":\"a\",\"url\":\"/asset/a\"}}", true));
    when(service.discardUpload("a")).thenReturn(new DeletionResult(true));
    mvc.perform(delete("/api/v1/personalization/assets/a"))
        .andExpect(content().json("{\"data\":{\"deleted\":true}}", true));
    when(service.publicAsset(7L, "a"))
        .thenReturn(new BrandingImage(new byte[] {1, 2}, "image/png"));
    mvc.perform(get("/api/v1/branding/public/assets/7/a"))
        .andExpect(content().bytes(new byte[] {1, 2}))
        .andExpect(content().contentType("image/png"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }

  @Test
  void rowVersionCannotBeTruncated() {
    assertThrows(
        JsonProcessingException.class,
        () ->
            new ObjectMapper()
                .readValue(
                    "{\"systemName\":\"t\",\"backgroundPosition\":\"center\",\"rowVersion\":0.5}",
                    PersonalizationForm.class));
  }
}
