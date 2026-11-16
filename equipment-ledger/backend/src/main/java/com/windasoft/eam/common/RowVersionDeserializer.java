package com.windasoft.eam.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** 保持旧版本字段只接受JSON整数的契约；不让Jackson把字符串或小数悄悄转换为版本。 */
public final class RowVersionDeserializer extends JsonDeserializer<Integer> {
  @Override
  public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT)
      return (Integer) context.handleUnexpectedToken(Integer.class, parser);
    long value = parser.getLongValue();
    if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
      return (Integer) context.handleWeirdNumberValue(Integer.class, value, "版本超出整数范围");
    return (int) value;
  }
}
