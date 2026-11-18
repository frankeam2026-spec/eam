package com.windasoft.eam.dto.business;

import com.windasoft.eam.common.LocalizedArgumentException;
import java.util.Locale;

/** 业务配置选择器沿用1..100条的有效范围，不能套用普通列表20/50/100或静默夹取非法值。 */
public record BusinessChoiceQuery(String keyword, int page, int size) {
  public static final int MAX_SIZE = 100;
  private static final int MAX_KEYWORD_LENGTH = 200;

  public BusinessChoiceQuery {
    if (keyword == null || keyword.length() > MAX_KEYWORD_LENGTH)
      throw new LocalizedArgumentException("server.business.message0041");
    if (page < 1 || size < 1 || size > MAX_SIZE)
      throw new LocalizedArgumentException("server.common.message0003");
  }

  public String likePattern() {
    return "%"
        + keyword.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
        + "%";
  }
}
