package com.windasoft.eam.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.windasoft.eam.common.ApiExceptionHandler;
import com.windasoft.eam.controller.system.NotificationController;
import com.windasoft.eam.dto.system.NotificationContracts.Channel;
import com.windasoft.eam.repository.system.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class NotificationChannelValidationTest {
  @Test
  void missingSecurityReturnsHttpFieldError() throws Exception {
    var channels = mock(NotificationChannelService.class);
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new NotificationController(
                    mock(NotificationPolicyService.class),
                    channels,
                    mock(NotificationRetentionService.class)))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    mvc.perform(
            put("/api/v1/notifications/channels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"rowVersion\":0,\"systemEnabled\":true,\"emailEnabled\":false,\"smtpPort\":25}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors.smtpSecurity").isString());
    verifyNoInteractions(channels);
  }

  @Test
  void missingSecurityIsRejectedBeforeConfigurationReadOrWrite() {
    var repository = mock(NotificationRepository.class);
    var service =
        new NotificationChannelService(
            repository,
            mock(NotificationSecret.class),
            mock(PermissionService.class),
            mock(AuthContext.class));
    var input = new Channel(0, true, false, null, 25, null, null, null, null, null);
    var failure = assertThrows(ResponseStatusException.class, () -> service.save(input));
    assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    verifyNoInteractions(repository);
  }
}
