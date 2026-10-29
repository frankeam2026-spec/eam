package com.windasoft.eam.repository.common;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import java.util.*;
import java.util.regex.Pattern;

/** Internal authorized SELECT, never populated from an HTTP request or model output. */
public record AssistantReadSource(String sql, List<Object> arguments) {
  public AssistantReadSource {
    arguments = List.copyOf(arguments);
  }

  public static AssistantReadSource selected(String select, String from, SqlPredicate predicate) {
    return new AssistantReadSource(
        "SELECT " + select + " " + from + " WHERE " + predicate.sql(), predicate.arguments());
  }

  public static AssistantReadSource wrapped(
      String select, String table, AbstractWrapper<?, ?, ?> wrapper) {
    String segment = wrapper.getSqlSegment();
    var matcher =
        Pattern.compile("#\\{ew\\.paramNameValuePairs\\.([A-Za-z0-9_]+)\\}").matcher(segment);
    var sql = new StringBuffer();
    var args = new ArrayList<Object>();
    while (matcher.find()) {
      if (!wrapper.getParamNameValuePairs().containsKey(matcher.group(1)))
        throw new IllegalStateException("Unbound authorized query parameter");
      args.add(wrapper.getParamNameValuePairs().get(matcher.group(1)));
      matcher.appendReplacement(sql, "?");
    }
    matcher.appendTail(sql);
    if (sql.indexOf("#{") >= 0)
      throw new IllegalStateException("Unsupported authorized query parameter");
    return new AssistantReadSource(
        "SELECT " + select + " FROM " + table + " WHERE deleted=0 AND (" + sql + ")", args);
  }
}
