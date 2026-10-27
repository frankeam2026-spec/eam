package com.windasoft.eam.dto.workflow;

import com.windasoft.eam.common.workflow.WorkflowExpression.Expr;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Browser contract: identifiers and expression numbers must retain their exact decimal text. */
public final class WorkflowResponse {
  private WorkflowResponse() {}

  public static Object exact(Object value) {
    if (value instanceof Expr expression) {
      return new Expr(
          expression.op(),
          expression.field(),
          expression.literal() instanceof Number
              ? expression.literal().toString()
              : expression.literal(),
          expression.type(),
          expression.children().stream().map(child -> (Expr) exact(child)).toList());
    }
    if (value instanceof List<?> rows) return rows.stream().map(WorkflowResponse::exact).toList();
    if (value instanceof Map<?, ?> row) {
      Map<String, Object> result = new LinkedHashMap<>();
      row.forEach(
          (key, item) -> {
            String name = key.toString();
            boolean identifier = name.equals("id") || name.endsWith("Id") || name.endsWith("_id");
            result.put(name, identifier && item instanceof Number ? item.toString() : exact(item));
          });
      return result;
    }
    return value;
  }
}
