package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Aggregate;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.All;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Comparison;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Condition;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.DateRange;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.DateTimeValue;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.DateValue;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Field;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.FieldSort;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Filter;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Identifier;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Junction;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Kind;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Logical;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Operation;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Reference;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.ResultSort;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Text;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Value;
import com.windasoft.eam.dto.equipment.EquipmentAssistantResult.Count;
import com.windasoft.eam.dto.equipment.EquipmentAssistantResult.Data;
import com.windasoft.eam.dto.equipment.EquipmentAssistantResult.Dimension;
import com.windasoft.eam.dto.equipment.EquipmentAssistantResult.GroupRow;
import com.windasoft.eam.dto.equipment.EquipmentAssistantResult.Grouped;
import com.windasoft.eam.dto.equipment.EquipmentAssistantResult.ListData;
import com.windasoft.eam.dto.equipment.EquipmentAssistantResult.Row;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.Direction;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.common.SqlListFilters;
import com.windasoft.eam.repository.common.SqlPredicate;
import com.windasoft.eam.repository.common.SqlScopes;
import com.windasoft.eam.repository.system.AuthorizationQueryPredicates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 设备助手查询的SQL执行边界，复用台账的字段表达式及授权谓词。 */
@Repository
public class EquipmentAssistantRepository {
  private final EquipmentAssistantMapper equipment;

  public EquipmentAssistantRepository(EquipmentAssistantMapper equipment) {
    this.equipment = equipment;
  }

  public Optional<Field> ambiguousName(
      long tenant,
      long user,
      PermissionScope scope,
      EquipmentAssistantQuery plan,
      LocalDateTime now,
      DateRange range) {
    var conditions = new ArrayList<Condition>();
    equalNames(plan.where(), conditions);
    for (var condition : conditions) {
      var q = base(tenant, user, scope, plan, now, range);
      q.select("DISTINCT " + expression(condition.field().identity()))
          .eq(expression(condition.field()), ((Text) condition.values().getFirst()).value())
          .last("LIMIT 2");
      if (equipment.rows(q).size() > 1) {
        return Optional.of(condition.field());
      }
    }
    return Optional.empty();
  }

  public Data execute(
      long tenant,
      long user,
      PermissionScope scope,
      EquipmentAssistantQuery plan,
      LocalDateTime now,
      DateRange range) {
    var q = base(tenant, user, scope, plan, now, range);
    long records = equipment.count(q.clone());
    String metric =
        plan.aggregate() == Aggregate.COUNT
            ? "COUNT(*)"
            : "COUNT(DISTINCT "
                + expression(
                    plan.measure().identity() == null ? plan.measure() : plan.measure().identity())
                + ")";
    if (plan.operation() == Operation.COUNT) {
      long value = records;
      if (plan.aggregate() == Aggregate.DISTINCT) {
        value = number(equipment.rows(q.select(metric + " AS metric")).getFirst(), "metric");
      }
      return new Count(value, records);
    }
    long offset = ((long) plan.page() - 1) * plan.size();
    int limit =
        plan.topN() == 0
            ? plan.size()
            : (int) Math.max(0, Math.min(plan.size(), plan.topN() - offset));
    // 页大小可因排行上限缩短，偏移仍按原请求计算，不能跳回上一页。
    var page =
        new Page<Map<String, Object>>(plan.page(), limit) {
          @Override
          public long offset() {
            return offset;
          }
        };
    if (plan.operation() == Operation.LIST) {
      var selects = new ArrayList<String>();
      selects.add(expression(Field.ID) + " AS id");
      for (var field : EquipmentAssistantQuery.LIST_FIELDS) {
        selects.add(expression(field) + " AS `" + field.key() + "`");
      }
      q.select(selects.toArray(String[]::new));
      Field sort = plan.sortBy() instanceof FieldSort(Field field1) ? field1 : Field.CREATED_AT;
      boolean asc = plan.direction() == Direction.ASC;
      q.orderBy(true, asc, orderExpression(sort)).orderBy(true, asc, orderExpression(Field.ID));
      page.setSearchCount(false);
      page.setTotal(records);
      var rows = equipment.page(page, q).getRecords().stream().map(this::row).toList();
      return new ListData(rows, total(records, plan.topN()), records);
    }
    var selects = new ArrayList<String>();
    var groups = new ArrayList<String>();
    if (plan.operation() == Operation.TREND) {
      String format =
          switch (plan.bucket()) {
            case DAY -> "%Y-%m-%d";
            case MONTH -> "%Y-%m";
            case YEAR -> "%Y";
          };
      String expression = "DATE_FORMAT(" + expression(plan.timeField()) + ",'" + format + "')";
      selects.add(expression + " AS period");
      groups.add("period");
    }
    for (int i = 0; i < plan.groupBy().size(); i++) {
      var field = plan.groupBy().get(i);
      String value = "COALESCE(" + expression(field) + ",'')";
      selects.add(value + " AS dimension" + i);
      groups.add("dimension" + i);
      if (field.identity() != null) {
        String identity = orderExpression(field.identity());
        selects.add(identity + " AS identity" + i);
        groups.add("identity" + i);
      }
    }
    selects.add("COUNT(*) AS rowcount");
    selects.add(metric + " AS aggregatevalue");
    q.select(selects.toArray(String[]::new)).groupBy(groups);
    String sort = "aggregatevalue";
    boolean asc = plan.direction() == Direction.ASC;
    if (plan.sortBy() instanceof FieldSort(Field field1)) {
      sort = "dimension" + plan.groupBy().indexOf(field1);
    } else if (plan.sortBy() instanceof ResultSort value) {
      sort =
          switch (value) {
            case COUNT -> "rowcount";
            case VALUE -> "aggregatevalue";
            case PERIOD -> "period";
          };
    } else if (plan.operation() == Operation.TREND) {
      sort = "period";
      asc = true;
    }
    q.orderBy(true, asc, sort);
    for (int i = 0; i < plan.groupBy().size(); i++) {
      q.orderByAsc("dimension" + i);
      if (plan.groupBy().get(i).identity() != null) {
        q.orderByAsc("identity" + i);
      }
    }
    var selected = equipment.page(page, q);
    var rows = selected.getRecords().stream().map(raw -> group(raw, plan, records)).toList();
    return new Grouped(rows, total(selected.getTotal(), plan.topN()), records);
  }

  private static long total(long total, int top) {
    return top == 0 ? total : Math.min(total, top);
  }

  private QueryWrapper<Equipment> base(
      long tenant,
      long user,
      PermissionScope scope,
      EquipmentAssistantQuery plan,
      LocalDateTime now,
      DateRange range) {
    var q = new QueryWrapper<Equipment>();
    q.setEntityClass(Equipment.class);
    q.eq("eam_equipment.tenant_id", tenant).eq("eam_equipment.deleted", 0);
    AuthorizationQueryPredicates.equipment(q.lambda(), scope, tenant, user);
    bind(q, filter(plan.where(), tenant, user, now));
    if (plan.equipmentId() != null) {
      q.eq(expression(Field.ID), plan.equipmentId());
    }
    if (range != null) {
      q.ge(expression(plan.timeField()), range.start().atStartOfDay())
          .lt(expression(plan.timeField()), range.end().plusDays(1).atStartOfDay());
    }
    return q;
  }

  private static void bind(QueryWrapper<Equipment> q, SqlPredicate predicate) {
    var sql = new StringBuilder();
    int index = 0;
    for (char c : predicate.sql().toCharArray()) {
      sql.append(c == '?' ? "{" + (index++) + "}" : String.valueOf(c));
    }
    // 整体括号不可省略：顶层OR不能绕过前面的租户、删除标记和授权条件。
    q.apply("(" + sql + ")", predicate.arguments().toArray());
  }

  private static SqlPredicate filter(Filter filter, long tenant, long user, LocalDateTime now) {
    if (filter instanceof All) {
      return SqlPredicate.of("1=1");
    }
    if (filter instanceof Junction(Logical operator1, List<Filter> children)) {
      if (operator1 == Logical.NOT) {
        var value = filter(children.getFirst(), tenant, user, now);
        return new SqlPredicate("NOT (" + value.sql() + ")", value.arguments());
      }
      var result = SqlPredicate.of(operator1 == Logical.AND ? "1=1" : "1=0");
      for (var child : children) {
        var value = filter(child, tenant, user, now);
        result = operator1 == Logical.AND ? result.and(value) : result.or(value);
      }
      return result;
    }
    var condition = (Condition) filter;
    String column = expression(condition.field());
    if (condition.operator() == Comparison.EMPTY || condition.operator() == Comparison.NOT_EMPTY) {
      boolean empty = condition.operator() == Comparison.EMPTY;
      if (condition.field().kind() == Kind.TEXT) {
        return SqlPredicate.of(
            "("
                + column
                + (empty ? " IS NULL OR " : " IS NOT NULL AND ")
                + column
                + (empty ? "=''" : "<>''")
                + ")");
      }
      return SqlPredicate.of(column + (empty ? " IS NULL" : " IS NOT NULL"));
    }
    var values =
        condition.values().stream().map(v -> value(v, condition.field(), user, now)).toList();
    if (condition.field() == Field.OWNER_ID
        && (condition.operator() == Comparison.EQ
            || condition.operator() == Comparison.NE
            || condition.operator() == Comparison.IN)) {
      var primary = SqlScopes.in("COALESCE(" + column + ",0)", values);
      var ids = SqlScopes.in("p.user_id", values);
      var args = new ArrayList<Object>();
      args.add(tenant);
      args.addAll(ids.arguments());
      var relation =
          new SqlPredicate(
              "EXISTS(SELECT 1 FROM eam_equipment_owner p WHERE p.tenant_id=? AND"
                  + " p.equipment_id=eam_equipment.id AND "
                  + ids.sql()
                  + ")",
              args);
      var result = primary.or(relation);
      return condition.operator() == Comparison.NE
          ? new SqlPredicate("NOT (" + result.sql() + ")", result.arguments())
          : result;
    }
    if (condition.operator() == Comparison.IN) {
      return SqlScopes.in(column, values);
    }
    if (condition.operator() == Comparison.CONTAINS) {
      return SqlListFilters.contains(column, values.getFirst().toString());
    }
    String operator =
        switch (condition.operator()) {
          case EQ -> "=";
          case NE -> "<>";
          case GT -> ">";
          case GTE -> ">=";
          case LT -> "<";
          case LTE -> "<=";
          default -> throw new IllegalStateException("未处理的设备筛选操作");
        };
    return new SqlPredicate(column + operator + "?", values);
  }

  private static Object value(Value value, Field field, long user, LocalDateTime now) {
    return switch (value) {
      case Text v -> v.value();
      case Identifier v -> v.value();
      case DateValue v -> v.value();
      case DateTimeValue v -> v.value();
      case Reference reference ->
          switch (reference) {
            case ME -> user;
            case TODAY ->
                field.kind() == Kind.DATETIME
                    ? now.toLocalDate().atStartOfDay()
                    : now.toLocalDate();
            case NOW -> now;
          };
    };
  }

  private static void equalNames(Filter filter, List<Condition> result) {
    if (filter instanceof Junction group) {
      for (var child : group.children()) {
        equalNames(child, result);
      }
    } else if (filter instanceof Condition condition
        && condition.operator() == Comparison.EQ
        && condition.field().identity() != null) {
      result.add(condition);
    }
  }

  private static String expression(Field field) {
    return switch (field) {
      case ID -> "eam_equipment.id";
      case CATEGORY_ID -> "eam_equipment.category_id";
      case COMPANY_ID -> "eam_equipment.company_org_id";
      case DEPARTMENT_ID -> "eam_equipment.department_org_id";
      case OWNER_ID -> "eam_equipment.owner_user_id";
      case STATUS -> EquipmentQueryFields.expression("state");
      case LOCATION -> EquipmentQueryFields.expression("functionLocation");
      case WARRANTY_UNTIL -> EquipmentQueryFields.expression("warranty");
      default -> EquipmentQueryFields.expression(field.key());
    };
  }

  private static String orderExpression(Field field) {
    // 原助手按字符串ID排序，兼容不同长度历史编号的并列顺序。
    String expression = expression(field);
    return field.kind() == Kind.ID ? "CAST(" + expression + " AS CHAR)" : expression;
  }

  // MyBatis的动态投影只在此处转换，Service不会接收Map行或SQL。
  private Row row(Map<String, Object> raw) {
    return new Row(
        identifier(raw, "id"),
        text(raw, "code"),
        text(raw, "name"),
        text(raw, "category"),
        text(raw, "status"),
        text(raw, "useState"),
        text(raw, "department"),
        text(raw, "model"));
  }

  private GroupRow group(Map<String, Object> raw, EquipmentAssistantQuery plan, long records) {
    var dimensions = new ArrayList<Dimension>();
    for (int i = 0; i < plan.groupBy().size(); i++) {
      dimensions.add(
          new Dimension(
              plan.groupBy().get(i), text(raw, "dimension" + i), identifier(raw, "identity" + i)));
    }
    long count = number(raw, "rowcount");
    var percent =
        records == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(records), 2, RoundingMode.HALF_UP);
    return new GroupRow(
        text(raw, "period"), dimensions, count, number(raw, "aggregatevalue"), percent);
  }

  private static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  private static Long identifier(Map<String, Object> row, String key) {
    var value = row.get(key);
    return value == null ? null : Long.valueOf(value.toString());
  }

  private static String text(Map<String, Object> row, String key) {
    var value = row.get(key);
    return value == null ? null : value.toString();
  }
}
