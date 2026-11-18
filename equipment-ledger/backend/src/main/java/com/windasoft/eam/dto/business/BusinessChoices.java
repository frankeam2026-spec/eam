package com.windasoft.eam.dto.business;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 保留原enabled协议：数据库来源使用数字，内置选项使用布尔；只允许下列两种固定结构。 */
public final class BusinessChoices {
  private BusinessChoices() {}

  public sealed interface Choice permits StoredChoice, InlineChoice {
    String id();

    String name();
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record StoredChoice(String id, String name, int enabled, Integer sortOrder)
      implements Choice {}

  public record InlineChoice(String id, String name, boolean enabled, int sortOrder)
      implements Choice {}

  public record ChoicePage(List<Choice> data, long total) {}

  /** 内部引用列表只供资格校验和名称匹配，不作为混合格式的HTTP响应。 */
  public record ReferenceOption(String id, String name, boolean enabled, int sortOrder) {}

  public record LockedOption(String id, String name, boolean enabled) {}
}
