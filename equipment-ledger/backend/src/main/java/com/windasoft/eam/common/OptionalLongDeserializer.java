package com.windasoft.eam.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** 旧表单的可选ID允许空串表示未选择；非空输入复用精确整数解析，不能把小数截断或静默置空。 */
public final class OptionalLongDeserializer extends JsonDeserializer<Long> {
  private final ExactLongDeserializer exact = new ExactLongDeserializer();

  @Override
  public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.hasToken(JsonToken.VALUE_STRING) && parser.getText().isBlank()) return null;
    return exact.deserialize(parser, context);
  }
}
