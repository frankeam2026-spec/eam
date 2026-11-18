package com.windasoft.eam.service.system;

import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.dto.system.NotificationContracts.Channel;
import com.windasoft.eam.dto.system.NotificationContracts.DeliveryFilter;
import com.windasoft.eam.dto.system.NotificationViews;
import com.windasoft.eam.entity.system.NotifyChannel;
import com.windasoft.eam.repository.system.NotificationRepository;
import com.windasoft.eam.repository.system.NotifyChannelRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationChannelService {
  private final NotificationRepository repo;
  private final NotifyChannelRepository configuration;
  private final NotificationSecret secrets;
  private final PermissionService permissions;
  private final AuthContext auth;

  public NotificationChannelService(
      NotificationRepository repo,
      NotifyChannelRepository configuration,
      NotificationSecret secrets,
      PermissionService permissions,
      AuthContext auth) {
    this.repo = repo;
    this.configuration = configuration;
    this.secrets = secrets;
    this.permissions = permissions;
    this.auth = auth;
  }

  public NotificationViews.Channel settings() {
    permissions.require("notificationChannel", "查看");
    var c = configuration.find(auth.tenantId(), false);
    return new NotificationViews.Channel(
        c.getRowVersion(),
        c.getSystemEnabled() == 1,
        c.getEmailEnabled() == 1,
        c.getSmtpHost(),
        c.getSmtpPort(),
        c.getSmtpSecurity(),
        c.getSmtpUsername(),
        c.getSenderAddress(),
        c.getPublicUrl(),
        c.getSmtpSecret() != null && !c.getSmtpSecret().isBlank(),
        "NOT_INTEGRATED");
  }

  @Transactional
  public void save(Channel c) {
    permissions.require("notificationChannel", "编辑");
    NotificationPolicyService.require(c.systemEnabled(), "站内消息为公共通知基础渠道，须保持启用");
    NotificationPolicyService.require(
        c.smtpSecurity() != null && Set.of("NONE", "STARTTLS", "SSL").contains(c.smtpSecurity()),
        "SMTP安全方式无效");
    NotificationPolicyService.require(
        !c.emailEnabled()
            || (c.smtpHost() != null
                && !c.smtpHost().isBlank()
                && c.senderAddress() != null
                && !c.senderAddress().isBlank()),
        "启用邮件必须配置SMTP主机和发件邮箱");
    NotificationPolicyService.require(
        c.publicUrl() == null
            || c.publicUrl().isBlank()
            || c.publicUrl().matches("https?://[^\\s]+"),
        "系统访问地址必须是HTTP或HTTPS地址");
    var old = configuration.find(auth.tenantId(), true);
    VersionGuard.check(old.getRowVersion(), c.rowVersion());
    String secret = Objects.toString(old.getSmtpSecret(), "");
    if (c.password() != null && !c.password().isEmpty()) secret = secrets.encrypt(c.password());
    configuration.save(auth.tenantId(), c, secret);
  }

  public void test(String address) {
    permissions.require("notificationChannel", "编辑");
    var c = configuration.find(auth.tenantId(), false);
    send(c, address, false, "设备管理系统邮件测试", "邮件渠道测试成功。");
  }

  public NotificationViews.Rows<NotificationViews.Delivery> deliveries(
      String status, String channel, String q, int page, int size) {
    permissions.require("notificationDelivery", "查看");
    return repo.deliveryPage(auth.tenantId(), status, channel, q, page, size);
  }

  public NotificationViews.Rows<NotificationViews.Delivery> deliveries(
      String status,
      String channel,
      String q,
      LocalDate from,
      LocalDate to,
      String kind,
      Long recipient,
      boolean showInactive,
      int page,
      int size) {
    permissions.require("notificationDelivery", "查看");
    var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    if (from == null) from = today.minusDays(6);
    if (to == null) to = today;
    NotificationPolicyService.require(!to.isBefore(from) && to.getYear() < 9999, "发送记录起止日期无效");
    NotificationPolicyService.require(
        kind.isEmpty() || NotificationPolicyService.KINDS.contains(kind), "提醒业务类型无效");
    NotificationPolicyService.require(
        channel.isEmpty() || Set.of("SYSTEM", "EMAIL", "APP").contains(channel), "通知渠道无效");
    NotificationPolicyService.require(
        status.isEmpty()
            || Set.of(
                    "PENDING",
                    "SENDING",
                    "SENT",
                    "FAILED",
                    "UNKNOWN",
                    "MISSING_ADDRESS",
                    "DISABLED",
                    "NOT_INTEGRATED",
                    "CANCELLED")
                .contains(status),
        "发送状态无效");
    return repo.deliveryPage(
        auth.tenantId(),
        new DeliveryFilter(
            status,
            channel,
            q,
            from.atStartOfDay(),
            to.plusDays(1).atStartOfDay(),
            kind,
            recipient,
            showInactive),
        page,
        size);
  }

  public NotificationViews.Rows<NotificationViews.Recipient> deliveryRecipients(
      String q, int page, int size) {
    permissions.require("notificationDelivery", "查看");
    return repo.deliveryRecipients(auth.tenantId(), q, page, size);
  }

  @Transactional
  public void retry(long id, int attempts) {
    permissions.require("notificationDelivery", "重试");
    VersionGuard.updated(repo.retry(auth.tenantId(), id, attempts));
  }

  public void send(
      NotifyChannel config, String address, boolean cc, String subject, String content) {
    var sender = new JavaMailSenderImpl();
    sender.setHost(Objects.toString(config.getSmtpHost(), ""));
    sender.setPort(config.getSmtpPort());
    sender.setUsername(Objects.toString(config.getSmtpUsername(), ""));
    sender.setPassword(secrets.decrypt(Objects.toString(config.getSmtpSecret(), "")));
    sender.setDefaultEncoding("UTF-8");
    var props = sender.getJavaMailProperties();
    props.put("mail.smtp.auth", !sender.getUsername().isBlank());
    props.put("mail.smtp.connectiontimeout", 5000);
    props.put("mail.smtp.timeout", 5000);
    props.put("mail.smtp.writetimeout", 5000);
    String security = Objects.toString(config.getSmtpSecurity(), "STARTTLS");
    props.put("mail.smtp.starttls.enable", security.equals("STARTTLS"));
    props.put("mail.smtp.starttls.required", security.equals("STARTTLS"));
    props.put("mail.smtp.ssl.enable", security.equals("SSL"));
    props.put("mail.smtp.ssl.checkserveridentity", true);
    var mail = new SimpleMailMessage();
    mail.setFrom(Objects.toString(config.getSenderAddress()));
    if (cc) mail.setCc(address);
    else mail.setTo(address);
    mail.setSubject(subject);
    mail.setText(content);
    sender.send(mail);
  }
}
