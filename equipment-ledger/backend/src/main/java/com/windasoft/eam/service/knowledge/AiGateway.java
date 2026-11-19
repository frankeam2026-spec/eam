package com.windasoft.eam.service.knowledge;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactIntegerDeserializer;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.knowledge.AiCallView;
import com.windasoft.eam.dto.knowledge.AiCallsView;
import com.windasoft.eam.dto.knowledge.AiSettingsView;
import com.windasoft.eam.entity.knowledge.AiCall;
import com.windasoft.eam.entity.knowledge.AiSetting;
import com.windasoft.eam.repository.knowledge.AiCallRepository;
import com.windasoft.eam.repository.knowledge.AiMonthlyUsageRepository;
import com.windasoft.eam.repository.knowledge.AiSettingRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.NotificationSecret;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiGateway {
  public static final int MAX_OUTPUT_TOKENS = 2400;
  private final AiSettingRepository configuration;
  private final AiMonthlyUsageRepository usages;
  private final AiCallRepository calls;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final NotificationSecret secrets;
  private final TransactionTemplate tx;
  private final ObjectMapper json;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public AiGateway(
      AiSettingRepository configuration,
      AiMonthlyUsageRepository usages,
      AiCallRepository calls,
      AuthContext auth,
      PermissionService permissions,
      NotificationSecret secrets,
      TransactionTemplate tx,
      ObjectMapper json) {
    this.configuration = configuration;
    this.usages = usages;
    this.calls = calls;
    this.auth = auth;
    this.permissions = permissions;
    this.secrets = secrets;
    this.tx = tx;
    this.json = json;
  }

  static String month() {
    return YearMonth.now(ZoneId.of("Asia/Shanghai")).toString();
  }

  public String model(boolean embedding) {
    var value = configuration.settings(auth.tenantId(), false);
    return embedding ? value.getEmbeddingModel() : value.getChatModel();
  }

  public boolean enabled() {
    return configuration.settings(auth.tenantId(), false).getEnabled();
  }

  public record Settings(
      boolean enabled,
      String baseUrl,
      String apiKey,
      String chatModel,
      String embeddingModel,
      BigDecimal monthlyLimit,
      @JsonDeserialize(using = ExactIntegerDeserializer.class) int warningPercent,
      BigDecimal inputPrice,
      BigDecimal outputPrice,
      BigDecimal embeddingPrice,
      @JsonDeserialize(using = ExactLongDeserializer.class) long rowVersion,
      String visionModel,
      String audioModel,
      BigDecimal visionInputPrice,
      BigDecimal visionOutputPrice,
      BigDecimal audioMinutePrice) {
    public Settings {
      visionModel = Objects.toString(visionModel, "");
      audioModel = Objects.toString(audioModel, "");
      visionInputPrice = visionInputPrice == null ? BigDecimal.ZERO : visionInputPrice;
      visionOutputPrice = visionOutputPrice == null ? BigDecimal.ZERO : visionOutputPrice;
      audioMinutePrice = audioMinutePrice == null ? BigDecimal.ZERO : audioMinutePrice;
    }
  }

  static void address(String address) {
    URI uri;
    try {
      uri = URI.create(address);
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("请填写百炼官方HTTPS兼容接口地址");
    }
    String host = Objects.toString(uri.getHost(), "").toLowerCase(Locale.ROOT);
    boolean tokenPlan =
        host.equals("token-plan.maas.qianwenaiapi.com")
            && Set.of("/compatible-mode/v1", "/compatible-mode/v1/")
                .contains(Objects.toString(uri.getPath(), ""))
            && (uri.getPort() == -1 || uri.getPort() == 443);
    if (!"https".equals(uri.getScheme())
        || !(host.endsWith(".aliyuncs.com") || tokenPlan)
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null) throw new IllegalArgumentException("请填写百炼官方HTTPS兼容接口地址");
  }

  /** 配置与预算固定投影；密钥只输出存在标志，禁止将持久化实体或密文返回客户端。 */
  public AiSettingsView settings() {
    permissions.require("aiSetting", PermissionAction.VIEW);
    var c = configuration.settings(auth.tenantId(), false);
    var u = usages.usage(auth.tenantId(), month(), false);
    return new AiSettingsView(
        c.getEnabled(),
        c.getBaseUrl(),
        c.getChatModel(),
        c.getEmbeddingModel(),
        c.getApiSecret() != null && !c.getApiSecret().isBlank(),
        c.getRowVersion(),
        c.getMonthlyLimit(),
        c.getWarningPercent(),
        c.getInputPrice(),
        c.getOutputPrice(),
        c.getEmbeddingPrice(),
        u.getEstimatedSpent(),
        u.getReservedAmount(),
        month(),
        c.getMonthlyLimit().signum() > 0
            && u.getEstimatedSpent()
                    .add(u.getReservedAmount())
                    .multiply(BigDecimal.valueOf(100))
                    .compareTo(
                        c.getMonthlyLimit().multiply(BigDecimal.valueOf(c.getWarningPercent())))
                >= 0,
        c.getVisionModel(),
        c.getAudioModel(),
        c.getVisionInputPrice(),
        c.getVisionOutputPrice(),
        c.getAudioMinutePrice());
  }

  static void validate(Settings c) {
    if (c.baseUrl() == null || c.baseUrl().isBlank())
      throw new IllegalArgumentException("请填写百炼接口地址");
    if (c.baseUrl().length() > 500) throw new IllegalArgumentException("百炼接口地址不能超过500个字符");
    address(c.baseUrl());
    for (var model :
        List.of(
            Map.entry("文本模型版本", Objects.toString(c.chatModel(), "")),
            Map.entry("向量模型版本", Objects.toString(c.embeddingModel(), "")))) {
      if (model.getValue().isBlank()) throw new IllegalArgumentException("请填写" + model.getKey());
      if (!model.getValue().matches("[A-Za-z0-9_.:-]{1,128}"))
        throw new IllegalArgumentException(model.getKey() + "格式无效，请填写模型标识，不要填写显示名称或空格");
    }
    if (c.warningPercent() < 1 || c.warningPercent() > 100)
      throw new IllegalArgumentException("预警比例须为1至100的整数");
    if (c.monthlyLimit() == null) throw new IllegalArgumentException("请填写月度硬限额");
    if (c.inputPrice() == null) throw new IllegalArgumentException("请填写文本输入价格");
    if (c.outputPrice() == null) throw new IllegalArgumentException("请填写文本输出价格");
    if (c.embeddingPrice() == null) throw new IllegalArgumentException("请填写向量输入价格");
  }

  public void save(Settings c) {
    permissions.require("aiSetting", PermissionAction.EDIT);
    validate(c);
    for (var price :
        List.of(
            c.monthlyLimit(),
            c.inputPrice(),
            c.outputPrice(),
            c.embeddingPrice(),
            c.visionInputPrice(),
            c.visionOutputPrice(),
            c.audioMinutePrice()))
      if (price.signum() < 0 || price.compareTo(new BigDecimal("1000000")) > 0 || price.scale() > 6)
        throw new IllegalArgumentException("预算与价格须为非负数，最多6位小数");
    for (String mediaModel : List.of(c.visionModel(), c.audioModel()))
      if (!mediaModel.isBlank() && !mediaModel.matches("[A-Za-z0-9_.:-]{1,128}"))
        throw new IllegalArgumentException("媒体模型名称无效");
    if (c.enabled()
        && (c.monthlyLimit().signum() == 0
            || c.inputPrice().signum() == 0
            || c.outputPrice().signum() == 0
            || c.embeddingPrice().signum() == 0))
      throw new IllegalArgumentException("启用前须填写预算和各项价格");
    tx.executeWithoutResult(
        s -> {
          var old = configuration.settings(auth.tenantId(), true);
          String secret = Objects.toString(old.getApiSecret(), "");
          if (c.apiKey() != null && !c.apiKey().isBlank()) {
            if (c.apiKey().length() > 1000) throw new IllegalArgumentException("密钥长度无效");
            secret = secrets.encrypt(c.apiKey());
          }
          if (c.enabled() && secret.isBlank()) throw new IllegalArgumentException("请填写模型API密钥");
          var value = new AiSetting();
          value.setTenantId(auth.tenantId());
          value.setEnabled(c.enabled());
          value.setBaseUrl(c.baseUrl());
          value.setApiSecret(secret);
          value.setChatModel(c.chatModel());
          value.setEmbeddingModel(c.embeddingModel());
          value.setMonthlyLimit(c.monthlyLimit());
          value.setWarningPercent(c.warningPercent());
          value.setInputPrice(c.inputPrice());
          value.setOutputPrice(c.outputPrice());
          value.setEmbeddingPrice(c.embeddingPrice());
          value.setVisionModel(c.visionModel());
          value.setAudioModel(c.audioModel());
          value.setVisionInputPrice(c.visionInputPrice());
          value.setVisionOutputPrice(c.visionOutputPrice());
          value.setAudioMinutePrice(c.audioMinutePrice());
          configuration.save(value, c.rowVersion());
        });
  }

  public AiCallsView calls(int page, int size) {
    permissions.require("aiSetting", PermissionAction.VIEW);
    if (page < 1 || !Set.of(20, 50, 100).contains(size))
      throw new IllegalArgumentException("分页参数无效");
    var rows = calls.page(auth.tenantId(), page, size);
    return new AiCallsView(
        rows.getTotal(),
        rows.getRecords().stream()
            .map(
                r ->
                    new AiCallView(
                        String.valueOf(r.getId()),
                        r.getPurpose(),
                        r.getModelName(),
                        r.getStatus(),
                        r.getInputTokens(),
                        r.getOutputTokens(),
                        r.getEstimatedCost(),
                        r.getElapsedMs(),
                        r.getCreatedAt()))
            .toList());
  }

  public JsonNode post(String path, Map<String, Object> body, boolean embedding) throws Exception {
    return post(path, body, embedding ? "EMBEDDING" : "CHAT", 0);
  }

  public JsonNode post(String path, Map<String, Object> body, String purpose, double seconds)
      throws Exception {
    boolean embedding = purpose.equals("EMBEDDING"),
        vision = purpose.equals("VISION"),
        audio = purpose.equals("AUDIO");
    if (audio && (!Double.isFinite(seconds) || seconds <= 0 || seconds > 60))
      throw new IllegalArgumentException("录音时长无效");
    if (!Set.of("CHAT", "EMBEDDING", "VISION", "AUDIO").contains(purpose))
      throw new IllegalArgumentException("调用用途无效");
    permissions.require("knowledge", PermissionAction.VIEW);
    long tenant = auth.tenantId(),
        user = auth.userId(),
        id = IdWorker.getId(),
        started = System.nanoTime();
    String month = month();
    var c = configuration.settings(tenant, false);
    if (!c.getEnabled()) throw new IllegalStateException("小卓尚未启用，请联系管理员");
    String base = c.getBaseUrl().toString();
    address(base);
    String model =
        (embedding
            ? c.getEmbeddingModel()
            : vision ? c.getVisionModel() : audio ? c.getAudioModel() : c.getChatModel());
    if (model.isBlank()) throw new IllegalStateException("请先配置对应的媒体模型");
    var payload = new LinkedHashMap<>(body);
    payload.put("model", model);
    byte[] bytes = json.writeValueAsBytes(payload);
    if (bytes.length > (vision || audio ? 4_100_000 : 100_000))
      throw new IllegalArgumentException("本次问题和资料过长，请缩小范围");
    BigDecimal
        inputPrice =
            (embedding
                ? c.getEmbeddingPrice()
                : vision ? c.getVisionInputPrice() : c.getInputPrice()),
        outputPrice =
            embedding ? BigDecimal.ZERO : (vision ? c.getVisionOutputPrice() : c.getOutputPrice());
    if ((vision && (inputPrice.signum() <= 0 || outputPrice.signum() <= 0))
        || (audio && c.getAudioMinutePrice().signum() <= 0))
      throw new IllegalStateException("请先配置媒体模型价格");
    int maxOutput = embedding ? 0 : ((Number) payload.getOrDefault("max_tokens", 1200)).intValue();
    if (maxOutput < 0 || maxOutput > MAX_OUTPUT_TOKENS)
      throw new IllegalArgumentException("输出Token上限无效");
    BigDecimal reserved =
        audio
            ? c.getAudioMinutePrice()
                .multiply(BigDecimal.valueOf(seconds))
                .divide(BigDecimal.valueOf(60), 6, RoundingMode.CEILING)
            : cost(bytes.length, maxOutput, inputPrice, outputPrice);
    tx.executeWithoutResult(
        s -> {
          var current = configuration.settings(tenant, true);
          if (!current.getEnabled() || !current.getRowVersion().equals(c.getRowVersion()))
            throw new IllegalStateException("AI配置已改变，请重试");
          recoverExpired(tenant);
          var usage = usages.usage(tenant, month, true);
          if (usage
                  .getEstimatedSpent()
                  .add(usage.getReservedAmount())
                  .add(reserved)
                  .compareTo(current.getMonthlyLimit())
              > 0) throw new IllegalStateException("本月AI预算已达上限，请联系管理员");
          reserve(id, tenant, user, month, purpose, model, reserved);
        });
    Long input = null, output = null;
    BigDecimal actual = reserved;
    boolean success = false;
    try {
      String key = secrets.decrypt(Objects.toString(c.getApiSecret(), ""));
      var request =
          HttpRequest.newBuilder(URI.create(base.replaceAll("/+$", "") + path))
              .timeout(Duration.ofSeconds(60))
              .header("Authorization", "Bearer " + key)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
              .build();
      var response = AiHttpResponse.send(http, request, Duration.ofSeconds(60), 2_000_000);
      byte[] responseBytes = response.body();
      if (response.statusCode() != 200) throw rejected(response.statusCode());
      if (responseBytes.length > 2_000_000) throw new IllegalStateException("模型响应超过大小限制");
      var result = json.readTree(responseBytes);
      var usage = result.path("usage");
      if (usage.path("prompt_tokens").canConvertToLong()
          && usage.path("completion_tokens").canConvertToLong()) {
        input = usage.path("prompt_tokens").asLong();
        output = usage.path("completion_tokens").asLong();
      } else if (embedding && usage.path("total_tokens").canConvertToLong()) {
        input = usage.path("total_tokens").asLong();
        output = 0L;
      }
      if (!audio && input != null && input >= 0 && output >= 0)
        actual = cost(input, output, inputPrice, outputPrice);
      success = true;
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("模型调用已中断");
    } finally {
      BigDecimal charge = actual;
      Long in = input, out = output;
      boolean ok = success;
      long elapsed = (System.nanoTime() - started) / 1_000_000;
      tx.executeWithoutResult(
          s -> {
            configuration.settings(tenant, true);
            usages.usage(tenant, month, true);
            finish(id, tenant, month, reserved, charge, in, out, elapsed, ok);
          });
    }
  }

  /** 维持原预算更新→调用插入顺序，外层配置/账期锁及事务覆盖两张表。 */
  private void reserve(
      long id,
      long tenant,
      long user,
      String month,
      String purpose,
      String model,
      BigDecimal amount) {
    usages.reserve(tenant, month, amount);
    var call = new AiCall();
    call.setId(id);
    call.setTenantId(tenant);
    call.setUserId(user);
    call.setBillingMonth(month);
    call.setPurpose(purpose);
    call.setModelName(model);
    call.setStatus("RUNNING");
    call.setReservedAmount(amount);
    calls.insert(call);
  }

  /** 状态条件更新只有一个胜者，只有胜者才能释放预占并计费；恢复和正常完成复用同一规则。 */
  private void finish(
      long id,
      long tenant,
      String month,
      BigDecimal reserved,
      BigDecimal cost,
      Long input,
      Long output,
      long elapsed,
      boolean success) {
    if (calls.finish(id, tenant, cost, input, output, elapsed, success))
      usages.settle(tenant, month, reserved, cost);
  }

  private void recoverExpired(long tenant) {
    for (var call : calls.expired(tenant))
      finish(
          call.getId(),
          tenant,
          call.getBillingMonth(),
          call.getReservedAmount(),
          call.getReservedAmount(),
          null,
          null,
          600000,
          false);
  }

  static ResponseStatusException rejected(int status) {
    String reason =
        switch (status) {
          case 401 -> "模型密钥校验未通过（HTTP 401），请检查API密钥与接口所属地域、业务空间";
          case 403 -> "模型服务拒绝访问（HTTP 403），请检查业务空间及模型调用权限";
          case 404 -> "模型或接口未找到（HTTP 404），请检查接口地址与模型标识";
          case 429 -> "模型服务限流或额度不足（HTTP 429），请检查服务商额度后重试";
          case 400 -> "模型服务拒绝了请求参数（HTTP 400），请检查模型标识和接口兼容性";
          default -> "模型服务暂时不可用（HTTP " + status + "），请稍后重试";
        };
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason);
  }

  static BigDecimal cost(long input, long output, BigDecimal inputPrice, BigDecimal outputPrice) {
    return inputPrice
        .multiply(BigDecimal.valueOf(input))
        .add(outputPrice.multiply(BigDecimal.valueOf(output)))
        .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.CEILING);
  }
}
