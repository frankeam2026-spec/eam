package com.windasoft.eam.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** 复用精确整数协议，额外核对Integer范围；不允许小数截断或溢出后改变状态/版本。 */
public final class ExactIntegerDeserializer extends JsonDeserializer<Integer> {
  private final ExactLongDeserializer longs = new ExactLongDeserializer();

  @Override
  public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    Long value = longs.deserialize(parser, context);
    if (value == null) {
      return null;
    }
    try {
      return Math.toIntExact(value);
    } catch (ArithmeticException outOfRange) {
      return (Integer) context.handleUnexpectedToken(Integer.class, parser);
    }
  }
}
