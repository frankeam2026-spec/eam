package com.windasoft.eam.dto.system;

import java.time.LocalDateTime;
import java.util.List;

public final class NotificationViews {
  private NotificationViews() {}

  public record Rows<T>(List<T> data, long total) {}

  public record Total(long total) {}

  public record Choices<T>(List<T> data, Total page) {}

  public record PolicyRow(
      String id,
      String name,
      String businessKind,
      int enabled,
      int rowVersion,
      LocalDateTime effectiveAt) {}

  public record Choice(String id, String name, int enabled) {}

  public record User(String id, String name, String email) {}

  public record Person(String ruleId, String userId, String recipientKind, String name) {}

  public record Rule(
      String id,
      String scene,
      int value,
      String unit,
      boolean enabled,
      LocalDateTime effectiveAt,
      List<String> recipients,
      List<String> cc) {}

  public record Policy(
      String id,
      String name,
      String businessKind,
      boolean enabled,
      int rowVersion,
      List<Rule> rules,
      List<Person> people) {}

  public record Default(String businessKind, String policyId, int rowVersion) {}

  public record Channel(
      int rowVersion,
      boolean systemEnabled,
      boolean emailEnabled,
      String smtpHost,
      int smtpPort,
      String smtpSecurity,
      String smtpUsername,
      String senderAddress,
      String publicUrl,
      boolean hasPassword,
      String appStatus) {}

  public record Retention(int rowVersion, int successDays, int exceptionDays) {}

  public record Delivery(
      String id,
      String channel,
      String status,
      int attempts,
      String lastError,
      LocalDateTime sentAt,
      String subject,
      String module,
      String businessKind,
      String objectId,
      String recipientKind,
      String recipient,
      LocalDateTime createdAt,
      LocalDateTime readAt) {}

  public record Recipient(String id, String name) {}
}
