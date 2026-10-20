package com.windasoft.eam.dto.common;

import java.util.*;

/** Protocol adapter; field names, SQL expressions and permissions are registered by each module. */
public record ListQuery(
    int page,
    int size,
    String keyword,
    String conditions,
    String filters,
    String sortBy,
    String direction,
    String queue,
    String organization) {
  public static ListQuery from(Map<String, String> parameters) {
    try {
      int page = Integer.parseInt(parameters.getOrDefault("page", "1"));
      int size = Integer.parseInt(parameters.getOrDefault("size", "10"));
      String keyword = parameters.getOrDefault("q", parameters.getOrDefault("keyword", "")).trim();
      String conditions =
          parameters.getOrDefault("conditions", parameters.getOrDefault("advanced", "[]"));
      String filters = parameters.getOrDefault("filters", "{}");
      String direction = parameters.getOrDefault("direction", "desc");
      if (page < 1
          || size < 1
          || size > 200
          || keyword.length() > 200
          || conditions.length() > 256000
          || filters.length() > 16000
          || !Set.of("asc", "desc").contains(direction))
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0003");
      return new ListQuery(
          page,
          size,
          keyword,
          conditions,
          filters,
          parameters.getOrDefault("sortBy", ""),
          direction,
          parameters.getOrDefault("queue", "全部"),
          parameters.getOrDefault("organization", ""));
    } catch (NumberFormatException e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0004");
    }
  }

  public ListQuery at(int number, int pageSize) {
    return new ListQuery(
        number, pageSize, keyword, conditions, filters, sortBy, direction, queue, organization);
  }
}
