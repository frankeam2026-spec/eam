package com.windasoft.eam.dto.knowledge;

import java.util.Set;

/** 助手业务查询的分页契约，复用于统一入口和领域请求；不适用于普通列表、选择器或导出批次。 */
public final class AiQueryLimits {
  public static final int MAX_PAGE_NUMBER = 1_000_000;
  private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);

  private AiQueryLimits() {}

  public static boolean acceptsPage(int page, int size) {
    return page >= 1 && page <= MAX_PAGE_NUMBER && PAGE_SIZES.contains(size);
  }
}
