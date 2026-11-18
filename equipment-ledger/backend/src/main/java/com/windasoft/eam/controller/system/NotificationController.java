package com.windasoft.eam.controller.system;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.SuccessResponse;
import com.windasoft.eam.dto.system.NotificationContracts;
import com.windasoft.eam.dto.system.NotificationViews;
import com.windasoft.eam.service.system.NotificationChannelService;
import com.windasoft.eam.service.system.NotificationPolicyService;
import com.windasoft.eam.service.system.NotificationRetentionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 配置入口保留原响应外层与分页兼容，权限和业务校验由对应Service执行。 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationPolicyService policies;
  private final NotificationChannelService channels;
  private final NotificationRetentionService retention;

  public NotificationController(
      NotificationPolicyService policies,
      NotificationChannelService channels,
      NotificationRetentionService retention) {
    this.policies = policies;
    this.channels = channels;
    this.retention = retention;
  }

  static int size(int n) {
    return Math.max(1, Math.min(n, 100));
  }

  @GetMapping("/policies")
  public NotificationViews.Rows<NotificationViews.PolicyRow> list(
      @RequestParam(defaultValue = "") String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return policies.list(kind, q, Math.max(1, page), size(size));
  }

  @GetMapping("/policies/choices")
  public NotificationViews.Choices<NotificationViews.Choice> choices(
      @RequestParam String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int size) {
    return policies.choices(kind, q, Math.max(1, page), size(size));
  }

  @GetMapping("/policies/{id}")
  public DataResponse<NotificationViews.Policy> detail(@PathVariable long id) {
    return new DataResponse<>(policies.detail(id));
  }

  @PostMapping("/policies")
  public DataResponse<NotificationViews.Policy> save(
      @Valid @RequestBody NotificationContracts.Policy body) {
    return new DataResponse<>(policies.save(body));
  }

  @GetMapping("/defaults")
  public DataResponse<List<NotificationViews.Default>> defaults() {
    return new DataResponse<>(policies.defaults());
  }

  @PutMapping("/defaults/{kind}")
  public DataResponse<List<NotificationViews.Default>> defaultPolicy(
      @PathVariable String kind, @Valid @RequestBody NotificationContracts.DefaultPolicy body) {
    policies.setDefault(kind, body);
    return defaults();
  }

  @GetMapping("/users")
  public NotificationViews.Rows<NotificationViews.User> users(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int size) {
    return policies.users(q, Math.max(1, page), size(size));
  }

  @GetMapping("/channels")
  public DataResponse<NotificationViews.Channel> channels() {
    return new DataResponse<>(channels.settings());
  }

  @PutMapping("/channels")
  public DataResponse<NotificationViews.Channel> channels(
      @Valid @RequestBody NotificationContracts.Channel body) {
    channels.save(body);
    return channels();
  }

  @PostMapping("/channels/test-email")
  public SuccessResponse test(@Valid @RequestBody NotificationContracts.TestEmail body) {
    channels.test(body.address());
    return new SuccessResponse(true);
  }

  @GetMapping("/deliveries")
  public NotificationViews.Rows<NotificationViews.Delivery> deliveries(
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String channel,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "") String businessKind,
      @RequestParam(required = false) Long recipientId,
      @RequestParam(defaultValue = "false") boolean showInactive,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return channels.deliveries(
        status,
        channel,
        q,
        from,
        to,
        businessKind,
        recipientId,
        showInactive,
        Math.max(1, page),
        size(size));
  }

  @GetMapping("/deliveries/recipients")
  public NotificationViews.Rows<NotificationViews.Recipient> recipients(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int size) {
    return channels.deliveryRecipients(q, Math.max(1, page), size(size));
  }

  @GetMapping("/deliveries/retention")
  public DataResponse<NotificationViews.Retention> retention() {
    return new DataResponse<>(retention.settings());
  }

  @PutMapping("/deliveries/retention")
  public DataResponse<NotificationViews.Retention> retention(
      @Valid @RequestBody NotificationContracts.Retention body) {
    return new DataResponse<>(retention.save(body));
  }

  @PostMapping("/deliveries/{id}/retry")
  public SuccessResponse retry(
      @PathVariable long id, @Valid @RequestBody NotificationContracts.Retry body) {
    channels.retry(id, body.attempts());
    return new SuccessResponse(true);
  }
}
