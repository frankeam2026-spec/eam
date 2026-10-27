package com.windasoft.eam.common.workflow;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Closed expression language. Never delegates to EL, reflection, SQL or a scripting engine. */
public final class WorkflowExpression {
  private WorkflowExpression() {}

  public enum Type {
    NUMBER,
    TEXT,
    REFERENCE,
    BOOLEAN,
    COLLECTION,
    NULL
  }

  public record Variable(
      String key, String label, Type type, boolean available, boolean nullable) {}

  public record Expr(String op, String field, Object literal, Type type, List<Expr> children) {
    public Expr {
      children = List.copyOf(children);
    }
  }

  public record Result(boolean matched, Set<String> fields) {}

  public static Expr parse(String source, Map<String, Variable> variables) {
    if (source == null || source.isBlank() || source.length() > 8000)
      throw invalid("表达式不能为空且不能超过8000字符");
    var p = new Parser(source, variables);
    Expr result = p.or(0);
    p.expect("<END>");
    requireType(result, Type.BOOLEAN);
    checkDepth(result, 0);
    return result;
  }

  private static void checkDepth(Expr e, int depth) {
    if (depth > 64) throw invalid("表达式结构不能超过64层");
    for (var child : e.children()) checkDepth(child, depth + 1);
  }

  public static Result test(Expr expression, Map<String, Object> values) {
    Set<String> used = new LinkedHashSet<>();
    boolean matched = bool(evaluate(expression, values, null, used));
    return new Result(matched, Set.copyOf(used));
  }

  private static Object evaluate(
      Expr e, Map<String, Object> values, Map<String, Object> item, Set<String> used) {
    var c = e.children();
    return switch (e.op()) {
      case "LITERAL" -> e.literal();
      case "FIELD" -> {
        used.add(e.field());
        Map<String, Object> scope = e.field().startsWith("lines.") && item != null ? item : values;
        String key = scope == item ? e.field().substring(6) : e.field();
        if (!scope.containsKey(key)) throw invalid("变量未取得：" + e.field());
        Object value = scope.get(key);
        yield normalize(value, e.type());
      }
      case "AND" ->
          bool(evaluate(c.get(0), values, item, used))
              && bool(evaluate(c.get(1), values, item, used));
      case "OR" ->
          bool(evaluate(c.get(0), values, item, used))
              || bool(evaluate(c.get(1), values, item, used));
      case "NOT" -> !bool(evaluate(c.getFirst(), values, item, used));
      case "IS_NULL" -> evaluate(c.getFirst(), values, item, used) == null;
      case "IS_NOT_NULL" -> evaluate(c.getFirst(), values, item, used) != null;
      case "ANY", "ALL" -> {
        var rows = lines(values);
        boolean match = e.op().equals("ALL") && !rows.isEmpty();
        for (var row : rows) {
          boolean current = bool(evaluate(c.getFirst(), values, row, used));
          if (e.op().equals("ANY") && current) {
            match = true;
            break;
          }
          if (e.op().equals("ALL") && !current) {
            match = false;
            break;
          }
        }
        yield match;
      }
      case "SUM" -> {
        BigDecimal sum = BigDecimal.ZERO;
        for (var row : lines(values)) {
          Object value = evaluate(c.getFirst(), values, row, used);
          if (value == null) throw invalid("求和变量为空：" + c.getFirst().field());
          sum = sum.add((BigDecimal) value);
          if (sum.precision() > 38) throw invalid("求和结果超出允许精度");
        }
        yield sum;
      }
      case "IN", "NOT_IN" -> {
        Object left = required(evaluate(c.getFirst(), values, item, used));
        boolean found = false;
        for (int i = 1; i < c.size(); i++)
          if (compare(left, required(evaluate(c.get(i), values, item, used))) == 0) {
            found = true;
            break;
          }
        yield e.op().equals("IN") ? found : !found;
      }
      case "CONTAINS", "STARTS_WITH" -> {
        String left = (String) required(evaluate(c.get(0), values, item, used));
        String right = (String) required(evaluate(c.get(1), values, item, used));
        yield e.op().equals("CONTAINS") ? left.contains(right) : left.startsWith(right);
      }
      default -> {
        Object left = required(evaluate(c.get(0), values, item, used));
        Object right = required(evaluate(c.get(1), values, item, used));
        int comparison = compare(left, right);
        yield switch (e.op()) {
          case "==" -> comparison == 0;
          case "!=" -> comparison != 0;
          case ">" -> comparison > 0;
          case ">=" -> comparison >= 0;
          case "<" -> comparison < 0;
          case "<=" -> comparison <= 0;
          default -> throw invalid("不支持的操作符：" + e.op());
        };
      }
    };
  }

  private static Object required(Object v) {
    if (v == null) throw invalid("条件所需变量为空，请先使用 IS NULL 或 IS NOT NULL 判断");
    return v;
  }

  private static boolean bool(Object v) {
    if (!(v instanceof Boolean b)) throw invalid("条件必须得到布尔值");
    return b;
  }

  private static Object normalize(Object v, Type type) {
    if (v == null) return null;
    try {
      return switch (type) {
        case NUMBER -> decimal(v.toString());
        case REFERENCE -> Long.valueOf(v.toString());
        case TEXT -> {
          if (!(v instanceof String)) throw invalid("文本变量类型错误");
          yield v;
        }
        case BOOLEAN -> {
          if (!(v instanceof Boolean)) throw invalid("布尔变量类型错误");
          yield v;
        }
        default -> throw invalid("不支持此变量类型的直接比较");
      };
    } catch (NumberFormatException ex) {
      throw invalid("数值或关联ID格式错误");
    }
  }

  private static int compare(Object a, Object b) {
    if (a instanceof BigDecimal x && b instanceof BigDecimal y) return x.compareTo(y);
    if (a instanceof Long x && b instanceof Long y) return x.compareTo(y);
    if (a instanceof String x && b instanceof String y) return x.compareTo(y);
    if (a instanceof Boolean x && b instanceof Boolean y) return x.compareTo(y);
    throw invalid("比较值类型不一致");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> lines(Map<String, Object> values) {
    Object value = values.get("lines");
    if (!(value instanceof List<?> rows)
        || rows.size() > 500
        || rows.stream().anyMatch(v -> !(v instanceof Map))) throw invalid("明细变量未取得或超出500行");
    return (List<Map<String, Object>>) value;
  }

  private static BigDecimal decimal(String text) {
    BigDecimal n = new BigDecimal(text);
    if (n.scale() > 12 || n.precision() > 38) throw invalid("数值最多38位有效数字、12位小数");
    return n;
  }

  private static IllegalArgumentException invalid(String message) {
    return new IllegalArgumentException(message);
  }

  private static void requireType(Expr e, Type type) {
    if (e.type() != type) throw invalid("表达式类型应为 " + type);
  }

  private static Expr node(String op, Type type, Expr... children) {
    return new Expr(op, null, null, type, List.of(children));
  }

  private record Token(String text, boolean quoted) {}

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z_0-9.]*");
  private static final Pattern NUMBER = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?");

  private static final class Parser {
    final List<Token> tokens = new ArrayList<>();
    final Map<String, Variable> variables;
    int at, collectionDepth;

    Parser(String text, Map<String, Variable> variables) {
      this.variables = Map.copyOf(variables);
      for (int i = 0; i < text.length(); ) {
        char ch = text.charAt(i);
        if (Character.isWhitespace(ch)) {
          i++;
          continue;
        }
        if (ch == '"') {
          StringBuilder value = new StringBuilder();
          boolean closed = false;
          i++;
          while (i < text.length()) {
            char s = text.charAt(i++);
            if (s == '"') {
              closed = true;
              break;
            }
            if (s == '\\') {
              if (i >= text.length()) throw invalid("字符串未结束");
              s = text.charAt(i++);
              if (s != '"' && s != '\\') throw invalid("字符串只支持引号和反斜线转义");
            }
            value.append(s);
          }
          if (!closed || value.length() > 1000) throw invalid("字符串未结束或超过1000字符");
          tokens.add(new Token(value.toString(), true));
        } else if ("(),".indexOf(ch) >= 0) {
          tokens.add(new Token(String.valueOf(ch), false));
          i++;
        } else if ("<>=!".indexOf(ch) >= 0) {
          String op = String.valueOf(ch);
          i++;
          if (i < text.length() && text.charAt(i) == '=') {
            op += "=";
            i++;
          }
          if (!Set.of("<", ">", "<=", ">=", "==", "!=").contains(op)) throw invalid("无效比较符号");
          tokens.add(new Token(op, false));
        } else {
          int start = i;
          while (i < text.length()
              && (Character.isLetterOrDigit(text.charAt(i)) || "_.-".indexOf(text.charAt(i)) >= 0))
            i++;
          if (start == i) throw invalid("不支持的字符：" + ch);
          tokens.add(new Token(text.substring(start, i), false));
        }
        if (tokens.size() > 1024) throw invalid("表达式过于复杂");
      }
      tokens.add(new Token("<END>", false));
    }

    boolean take(String value) {
      Token token = tokens.get(at);
      if (!token.quoted && token.text.equalsIgnoreCase(value)) {
        at++;
        return true;
      }
      return false;
    }

    void expect(String value) {
      if (!take(value)) throw invalid("预期 " + value + "，位置 " + at);
    }

    Expr or(int depth) {
      if (depth > 32) throw invalid("表达式嵌套不能超过32层");
      Expr left = and(depth);
      while (take("OR")) {
        requireType(left, Type.BOOLEAN);
        Expr r = and(depth);
        requireType(r, Type.BOOLEAN);
        left = node("OR", Type.BOOLEAN, left, r);
      }
      return left;
    }

    Expr and(int depth) {
      Expr left = condition(depth);
      while (take("AND")) {
        requireType(left, Type.BOOLEAN);
        Expr r = condition(depth);
        requireType(r, Type.BOOLEAN);
        left = node("AND", Type.BOOLEAN, left, r);
      }
      return left;
    }

    Expr condition(int depth) {
      if (depth > 32) throw invalid("表达式嵌套不能超过32层");
      if (take("NOT")) {
        Expr e = condition(depth + 1);
        requireType(e, Type.BOOLEAN);
        return node("NOT", Type.BOOLEAN, e);
      }
      if (take("(")) {
        Expr e = or(depth + 1);
        expect(")");
        return e;
      }
      for (String quantifier : List.of("ANY", "ALL"))
        if (take(quantifier)) {
          if (collectionDepth != 0) throw invalid("明细量词不能相互嵌套");
          expect("(");
          expect("lines");
          expect(",");
          collectionDepth++;
          Expr predicate = or(depth + 1);
          collectionDepth--;
          expect(")");
          requireType(predicate, Type.BOOLEAN);
          return node(quantifier, Type.BOOLEAN, predicate);
        }
      Expr left = operand();
      if (take("IS")) {
        boolean not = take("NOT");
        expect("NULL");
        return node(not ? "IS_NOT_NULL" : "IS_NULL", Type.BOOLEAN, left);
      }
      boolean notIn = take("NOT");
      if (notIn || take("IN")) {
        if (notIn) expect("IN");
        expect("(");
        List<Expr> items = new ArrayList<>();
        items.add(left);
        do {
          items.add(coerce(left, operand()));
        } while (take(","));
        expect(")");
        return new Expr(notIn ? "NOT_IN" : "IN", null, null, Type.BOOLEAN, items);
      }
      for (String op : List.of("==", "!=", ">=", "<=", ">", "<", "CONTAINS", "STARTS_WITH"))
        if (take(op)) {
          Expr right = coerce(left, operand());
          if (Set.of(">", ">=", "<", "<=").contains(op)) requireType(left, Type.NUMBER);
          if (Set.of("CONTAINS", "STARTS_WITH").contains(op)) requireType(left, Type.TEXT);
          return node(op, Type.BOOLEAN, left, right);
        }
      requireType(left, Type.BOOLEAN);
      return left;
    }

    Expr operand() {
      if (take("SUM")) {
        if (collectionDepth != 0) throw invalid("SUM不能嵌套在明细量词中");
        expect("(");
        collectionDepth++;
        Expr e = operand();
        collectionDepth--;
        expect(")");
        requireType(e, Type.NUMBER);
        if (!e.op().equals("FIELD") || !e.field().startsWith("lines."))
          throw invalid("SUM只支持明细数值字段");
        return node("SUM", Type.NUMBER, e);
      }
      Token token = tokens.get(at++);
      if (token.quoted) return new Expr("LITERAL", null, token.text, Type.TEXT, List.of());
      if (NUMBER.matcher(token.text).matches())
        return new Expr("LITERAL", null, decimal(token.text), Type.NUMBER, List.of());
      if (token.text.equalsIgnoreCase("true") || token.text.equalsIgnoreCase("false"))
        return new Expr("LITERAL", null, Boolean.valueOf(token.text), Type.BOOLEAN, List.of());
      if (!IDENTIFIER.matcher(token.text).matches()) throw invalid("无效变量：" + token.text);
      Variable v = variables.get(token.text);
      if (v == null || !v.available) throw invalid("变量不存在或尚未接入：" + token.text);
      if (v.key.startsWith("lines.") && collectionDepth == 0) throw invalid("明细字段必须放在ANY、ALL或SUM中");
      return new Expr("FIELD", v.key, null, v.type, List.of());
    }

    Expr coerce(Expr left, Expr right) {
      if (left.type == Type.REFERENCE && right.op.equals("LITERAL") && right.type == Type.NUMBER) {
        try {
          return new Expr(
              "LITERAL",
              null,
              ((BigDecimal) right.literal).longValueExact(),
              Type.REFERENCE,
              List.of());
        } catch (ArithmeticException ex) {
          throw invalid("关联ID必须是整数");
        }
      }
      if (left.type != right.type) throw invalid("条件两侧类型不一致");
      return right;
    }
  }
}
