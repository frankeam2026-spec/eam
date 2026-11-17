package com.windasoft.eam.dto.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.windasoft.eam.common.workflow.WorkflowExpression.Expr;
import com.windasoft.eam.common.workflow.WorkflowExpression.Type;
import java.time.LocalDateTime;
import java.util.List;

/** 模板管理的固定查询协议；列表与选择器沿用各自已有分页形状。 */
public final class WorkflowTemplateViews {
  private WorkflowTemplateViews() {}

  public record TemplateRow(
      String id,
      String name,
      String companyId,
      String companyName,
      String businessType,
      long rowVersion,
      LocalDateTime updatedAt,
      int active) {}

  public record TemplatePage(List<TemplateRow> data, long total, int page, int size) {}

  public record Company(String id, String name) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Choice(String id, String name, String code) {}

  public record Count(long total) {}

  public record ChoicePage(List<Choice> data, Count page) {}

  public record Version(
      String id, int versionNo, String state, Long publishedBy, LocalDateTime publishedAt) {}

  /** 历史协议未配置行返回布尔值，已有行沿用JDBC的布尔/数字标量；仅这两个字段保留受限联合类型。 */
  public record Setting(JsonNode required, long rowVersion, JsonNode templateAvailable) {}

  public record ScriptResult(JsonNode value) {}

  public record Expression(
      String op, String field, JsonNode literal, Type type, List<Expression> children) {
    public static Expression from(Expr expression) {
      Object value = expression.literal();
      JsonNode literal =
          value == null
              ? JsonNodeFactory.instance.nullNode()
              : value instanceof Boolean b
                  ? BooleanNode.valueOf(b)
                  : JsonNodeFactory.instance.textNode(value.toString());
      return new Expression(
          expression.op(),
          expression.field(),
          literal,
          expression.type(),
          expression.children().stream().map(Expression::from).toList());
    }
  }

  /** 从JDBC标量进入固定设置结构，不允许把任意对象变为JSON负载。 */
  public static JsonNode flag(Object value) {
    if (value instanceof Boolean b) {
      return BooleanNode.valueOf(b);
    }
    if (value instanceof Number n) {
      return IntNode.valueOf(n.intValue());
    }
    throw new IllegalStateException("审批设置标量类型不正确");
  }
}
