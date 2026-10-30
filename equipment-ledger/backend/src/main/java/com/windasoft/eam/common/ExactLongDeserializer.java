package com.windasoft.eam.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** 兼容整数及十进制字符串编号，禁止 Jackson 将小数截断为整数。 */
public final class ExactLongDeserializer extends JsonDeserializer<Long> {
  @Override
  public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    String value = parser.getValueAsString();
    try {
      if (value == null || !value.trim().matches("[+-]?[0-9]+")) {
        throw new NumberFormatException();
      }
      return Long.parseLong(value.trim());
    } catch (NumberFormatException invalid) {
      return (Long) context.handleUnexpectedToken(Long.class, parser);
    }
  }
}
