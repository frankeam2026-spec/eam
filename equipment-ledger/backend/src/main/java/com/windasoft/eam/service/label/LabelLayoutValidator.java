package com.windasoft.eam.service.label;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.printing.GridValidator;
import com.windasoft.eam.dto.label.LabelTemplateForm;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LabelLayoutValidator {
  public void validate(LabelTemplateForm form) {
    if (form.layout() != null && !form.layout().isNull()) {
      GridValidator.validate(
          form.layout(), form.widthMm().doubleValue(), form.heightMm().doubleValue());
      return;
    }
    validateElements(form);
  }

  private void validateElements(LabelTemplateForm f) {
    var elements = f.elements();
    if (elements == null
        || !elements.isArray()
        || elements.size() > 100
        || elements.toString().length() > 3000000)
      throw new LocalizedArgumentException("server.common.message0057");
    Set<String> ids = new HashSet<>();
    for (var e : elements) {
      if (!ids.add(e.path("id").asText()) || e.path("id").asText().isBlank())
        throw new LocalizedArgumentException("server.common.message0058");
      if (!Set.of("name", "code", "owner", "department", "model", "text", "image")
          .contains(e.path("type").asText()))
        throw new LocalizedArgumentException("server.common.message0059");
      double x = number(e, "x", 0, 300),
          y = number(e, "y", 0, 300),
          w = number(e, "width", 1, 300),
          h = number(e, "height", 1, 300);
      if (x + w > f.widthMm().doubleValue() + 0.01 || y + h > f.heightMm().doubleValue() + 0.01)
        throw new LocalizedArgumentException("server.common.message0060");
      number(e, "fontSize", 6, 72);
      if (e.path("prefix").asText().length() > 100 || e.path("text").asText().length() > 1000)
        throw new LocalizedArgumentException("server.common.message0061");
      String image = e.path("image").asText();
      if (!image.isEmpty() && !image.matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+"))
        throw new LocalizedArgumentException("server.common.message0062");
    }
  }

  private double number(JsonNode e, String key, double min, double max) {
    var n = e.path(key);
    double value = n.asDouble(Double.NaN);
    if (!n.isNumber() || !Double.isFinite(value) || value < min || value > max)
      throw new LocalizedArgumentException("server.common.message0063");
    return value;
  }
}
