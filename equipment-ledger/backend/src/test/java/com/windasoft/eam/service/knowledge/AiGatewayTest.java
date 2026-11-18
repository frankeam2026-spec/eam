package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.knowledge.AiMonthlyUsage;
import com.windasoft.eam.entity.knowledge.AiSetting;
import com.windasoft.eam.repository.knowledge.AiCallRepository;
import com.windasoft.eam.repository.knowledge.AiMonthlyUsageRepository;
import com.windasoft.eam.repository.knowledge.AiSettingRepository;
import com.windasoft.eam.service.system.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.*;

class AiGatewayTest {
  @Test
  void acceptsOnlyTheExactTokenPlanCompatibilityEndpoint() {
    assertDoesNotThrow(
        () -> AiGateway.address("https://token-plan.maas.qianwenaiapi.com/compatible-mode/v1"));
    assertDoesNotThrow(
        () -> AiGateway.address("https://token-plan.maas.qianwenaiapi.com/compatible-mode/v1/"));
    for (String url :
        List.of(
            "http://token-plan.maas.qianwenaiapi.com/compatible-mode/v1",
            "https://token-plan.maas.qianwenaiapi.com.evil.test/compatible-mode/v1",
            "https://other.maas.qianwenaiapi.com/compatible-mode/v1",
            "https://key@token-plan.maas.qianwenaiapi.com/compatible-mode/v1",
            "https://token-plan.maas.qianwenaiapi.com/compatible-mode/v1?key=x",
            "https://token-plan.maas.qianwenaiapi.com/compatible-mode/v1#x",
            "https://token-plan.maas.qianwenaiapi.com:8443/compatible-mode/v1",
            "https://token-plan.maas.qianwenaiapi.com/other"))
      assertThrows(IllegalArgumentException.class, () -> AiGateway.address(url), url);
  }

  @Test
  void pricesUseDecimalAndRoundConservatively() {
    assertEquals(
        new BigDecimal("0.006000"),
        AiGateway.cost(5000, 1000, new BigDecimal("0.8"), new BigDecimal("2")));
    assertEquals(
        new BigDecimal("0.000001"), AiGateway.cost(1, 0, new BigDecimal("0.01"), BigDecimal.ZERO));
  }

  @Test
  void rejectsCredentialUrlsAndForeignHosts() {
    for (String url :
        List.of(
            "http://a.aliyuncs.com/v1",
            "https://a.aliyuncs.com.evil.test/v1",
            "https://user:password@a.aliyuncs.com/v1",
            "https://a.aliyuncs.com/v1?key=x"))
      assertThrows(IllegalArgumentException.class, () -> AiGateway.address(url));
  }

  @Test
  void hardBudgetRejectsBeforeReservationOrNetwork() {
    var repo = mock(AiSettingRepository.class);
    var usages = mock(AiMonthlyUsageRepository.class);
    var calls = mock(AiCallRepository.class);
    var auth = mock(AuthContext.class);
    var permission = mock(PermissionService.class);
    var secrets = mock(NotificationSecret.class);
    var tx = mock(TransactionTemplate.class);
    when(auth.tenantId()).thenReturn(10L);
    when(auth.userId()).thenReturn(20L);
    var config = new AiSetting();
    config.setEnabled(true);
    config.setBaseUrl("https://test.aliyuncs.com/v1");
    config.setChatModel("test");
    config.setInputPrice(BigDecimal.ONE);
    config.setOutputPrice(BigDecimal.ONE);
    config.setRowVersion(1L);
    config.setMonthlyLimit(BigDecimal.ONE);
    when(repo.settings(eq(10L), anyBoolean())).thenReturn(config);
    var usage = new AiMonthlyUsage();
    usage.setEstimatedSpent(BigDecimal.ONE);
    usage.setReservedAmount(BigDecimal.ZERO);
    when(usages.usage(eq(10L), anyString(), eq(true))).thenReturn(usage);
    doAnswer(
            i -> {
              ((Consumer<TransactionStatus>) i.getArgument(0))
                  .accept(mock(TransactionStatus.class));
              return null;
            })
        .when(tx)
        .executeWithoutResult(any());
    var service =
        new AiGateway(repo, usages, calls, auth, permission, secrets, tx, new ObjectMapper());
    assertThrows(
        IllegalStateException.class,
        () ->
            service.post(
                "/chat/completions", Map.of("max_tokens", 1, "messages", List.of()), false));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.post(
                "/chat/completions", Map.of("max_tokens", 2200, "messages", List.of()), false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.post(
                "/chat/completions",
                Map.of("max_tokens", AiGateway.MAX_OUTPUT_TOKENS + 1, "messages", List.of()),
                false));
    verify(usages, never()).reserve(anyLong(), anyString(), any());
    verify(calls, never()).insert(any());
    verifyNoInteractions(secrets);
  }

  @Test
  void missingEmbeddingModelReportsTheExactField() {
    var c =
        new AiGateway.Settings(
            false,
            "https://test.aliyuncs.com/v1",
            "",
            "qwen-plus",
            "",
            BigDecimal.ZERO,
            80,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0,
            "",
            "",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    assertEquals(
        "请填写向量模型版本",
        assertThrows(IllegalArgumentException.class, () -> AiGateway.validate(c)).getMessage());
  }

  @Test
  void providerAuthenticationFailureIsActionableWithoutEchoingTheResponse() {
    var error = AiGateway.rejected(401);
    assertTrue(error.getReason().contains("HTTP 401"));
    assertTrue(error.getReason().contains("密钥"));
    assertEquals(502, error.getStatusCode().value());
  }
}
