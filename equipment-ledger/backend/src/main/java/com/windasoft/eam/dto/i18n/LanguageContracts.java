package com.windasoft.eam.dto.i18n;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactIntegerDeserializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 语言包词条键是动态协议；版本、管理元数据与校验结果均为固定结构。 */
public final class LanguageContracts {
  private LanguageContracts() {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record LanguageView(
      String locale,
      String nativeName,
      boolean builtin,
      boolean enabled,
      int rowVersion,
      LocalDateTime updatedAt,
      double coverage) {}

  public record Resource(String locale, String version, Map<String, String> messages) {}

  public record Preference(String locale, String effectiveLocale) {}

  public record LanguagePack(
      int formatVersion,
      String locale,
      String nativeName,
      String direction,
      Map<String, String> messages) {}

  public record Validation(
      boolean valid,
      List<String> invalid,
      List<String> unknown,
      List<String> missing,
      long added,
      long changed,
      int accepted,
      int total) {}

  public record Publish(
      @NotNull @Min(0) @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer rowVersion,
      @NotNull @JsonProperty("package") JsonNode languagePackage) {}

  public record State(
      @NotNull @Min(0) @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer rowVersion,
      @NotNull Boolean enabled) {}

  public record Version(
      @NotNull @Min(0) @JsonDeserialize(using = ExactIntegerDeserializer.class)
          Integer rowVersion) {}
}
