package com.windasoft.eam.dto.experience;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.windasoft.eam.common.ExactIntegerDeserializer;
import com.windasoft.eam.common.ExactLongDeserializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;

/** 画布与页面偏好是受现有校验器限制的动态JSON；版本、角色和展示元数据均明确建模。 */
public final class HomeContracts {
  private HomeContracts() {}

  @Data
  public static class TemplateForm {
    private String name;
    private JsonNode draft = JsonNodeFactory.instance.objectNode();
    private JsonNode overviewDraft;

    @JsonDeserialize(contentUsing = ExactLongDeserializer.class)
    private List<Long> roleIds = List.of();

    @JsonDeserialize(using = ExactIntegerDeserializer.class)
    private Integer priority = 100;

    @JsonDeserialize(using = ExactIntegerDeserializer.class)
    private Integer rowVersion;

    /** 保留旧输入的区别：省略优先级为100，显式null按0处理。 */
    public void setPriority(Integer value) {
      this.priority = value == null ? 0 : value;
    }
  }

  public record Version(
      @NotNull @Min(0) @JsonDeserialize(using = ExactIntegerDeserializer.class)
          Integer rowVersion) {}

  public record Identity(
      @NotNull @JsonDeserialize(using = ExactLongDeserializer.class) Long templateId) {}

  public record LayoutForm(
      @NotNull @JsonDeserialize(using = ExactLongDeserializer.class) Long templateId,
      @NotNull @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer templateVersion,
      @NotNull @Min(-1) @JsonDeserialize(using = ExactIntegerDeserializer.class) Integer rowVersion,
      @NotNull JsonNode layout) {}

  public record PresetRole(String code, Long id) {}

  public record Recent(String url) {}

  public record RoleChoice(String id, String name) {}

  public record TemplateView(
      String id,
      @JsonProperty("tenant_id") String tenantId,
      String name,
      @JsonProperty("preset_key") String presetKey,
      JsonNode draft,
      int priority,
      @JsonProperty("published_version") int publishedVersion,
      int enabled,
      @JsonProperty("row_version") int rowVersion,
      @JsonProperty("created_by") String createdBy,
      @JsonProperty("created_at") LocalDateTime createdAt,
      @JsonProperty("updated_at") LocalDateTime updatedAt,
      @JsonProperty("overview_draft") String overviewDraftJson,
      @JsonProperty("role_ids") String roleIdsJson,
      List<String> roleIds,
      JsonNode overviewDraft) {}

  public record Published(
      Long id, String name, int publishedVersion, String payload, int priority) {}

  public record Summary(String id, String name, int version) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Selected(
      String id,
      String name,
      @JsonProperty("published_version") Integer publishedVersion,
      Integer priority,
      int version,
      JsonNode layout) {}

  public record State(
      List<Summary> templates,
      Selected selected,
      JsonNode layout,
      int rowVersion,
      int templateVersion,
      boolean updateAvailable,
      Map<String, Set<String>> permissions) {}

  public record Overview(
      List<Summary> templates,
      Summary selected,
      JsonNode layout,
      JsonNode defaultLayout,
      List<String> catalog,
      int rowVersion,
      int templateVersion,
      boolean updateAvailable) {}
}
