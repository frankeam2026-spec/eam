package com.windasoft.eam.dto.system;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class NotificationContracts {
  private NotificationContracts() {}
  public record Rule(Long id, @NotBlank String scene, @Min(0) @Max(525600) int value,
      @NotBlank String unit, boolean enabled, @NotNull List<Long> recipients, @NotNull List<Long> cc) {}
  public record Policy(Long id, Integer rowVersion, @NotBlank @Size(max=150) String name,
      @NotBlank String businessKind, boolean enabled, @NotNull @Size(max=100) List<@Valid Rule> rules) {}
  public record DefaultPolicy(Long policyId, @Min(0) int rowVersion) {}
  public record Channel(@Min(0) int rowVersion, boolean systemEnabled, boolean emailEnabled,
      @Size(max=255) String smtpHost, @Min(1) @Max(65535) int smtpPort, String smtpSecurity,
      @Size(max=255) String smtpUsername, @Size(max=1000) String password,
      @Email @Size(max=254) String senderAddress, @Size(max=500) String publicUrl) {}
  public record TestEmail(@NotBlank @Email String address) {}
  public record Retry(@Min(0) int attempts) {}
  public record Retention(@Min(0) int rowVersion, @Min(1) @Max(3650) int successDays,
      @Min(1) @Max(3650) int exceptionDays) {}
  public record DeliveryFilter(String status, String channel, String query,
      java.time.LocalDateTime from, java.time.LocalDateTime until, String businessKind,
      Long recipientId, boolean showInactive) {}
}
