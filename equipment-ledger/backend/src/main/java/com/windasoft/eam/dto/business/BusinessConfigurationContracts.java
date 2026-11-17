package com.windasoft.eam.dto.business;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactIntegerDeserializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 配置画布和字段定义保留经过现有规则校验的动态JSON；版本、操作和审计外层固定建模。 */
public final class BusinessConfigurationContracts {
  private BusinessConfigurationContracts() {}

  public record ObjectOption(String key, String name) {}

  public record SchemaView(JsonNode draft, JsonNode published, int version, int publishedVersion) {}

  public record RuntimeView(JsonNode configuration, int version) {}

  public record SaveDraftRequest(
      @NotNull JsonNode configuration,
      @NotNull @Min(0) @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer version) {}

  public record VersionRequest(
      @NotNull @Min(0) @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer version) {}

  public record RestoreRequest(
      @NotNull @Min(0) @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer target,
      @NotNull @Min(0) @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer version) {}

  /** 历史接口使用下划线字段、数值actor_id与JDBC时间格式，本批保持原序列化。 */
  public record VersionView(
      int version,
      @JsonProperty("actor_id") Long actorId,
      @JsonProperty("created_at") LocalDateTime createdAt) {}

  /** details沿用数据库JSON文本的原响应格式，不在此更改历史审计协议。 */
  public record EventView(String actor, String action, String details, LocalDateTime time) {}
}
