package com.windasoft.eam.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

/** Applies allowlisted business conditions; client input never supplies SQL columns. */
public final class AdvancedFilters {
    private AdvancedFilters() {}
    public enum Kind { TEXT, CHOICE, DATE, NUMBER }
    public record Field<T>(SFunction<T, ?> column, Kind kind) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(String field, String operator, List<String> values) {}

    public static <T> void apply(LambdaQueryWrapper<T> query, String raw,
            Map<String, Field<T>> fields, ObjectMapper json) {
        if (raw == null || raw.isBlank()) return;
        if (raw.length() > 256000) throw new IllegalArgumentException("高级筛选条件过长");
        List<Condition> conditions;
        try {
            conditions = json.readValue(raw, new TypeReference<List<Condition>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("高级筛选格式无效");
        }
        if (conditions == null || conditions.size() > fields.size()) {
            throw new IllegalArgumentException("高级筛选数量无效");
        }
        var seen = new HashSet<String>();
        for (Condition c : conditions) {
            if (c == null || !fields.containsKey(c.field()) || !seen.add(c.field())
                    || c.operator() == null || c.values() == null || c.values().size() > 2000) {
                throw new IllegalArgumentException("高级筛选字段无效");
            }
            Field<T> field = fields.get(c.field());
            for (String value : c.values()) {
                if (value == null || value.length() > 200) throw new IllegalArgumentException("高级筛选值无效");
            }
            var column = field.column();
            switch (c.operator()) {
                case "empty" -> {
                    if (field.kind() == Kind.TEXT || field.kind() == Kind.CHOICE)
                        query.and(w -> w.isNull(column).or().eq(column, ""));
                    else query.isNull(column);
                }
                case "notEmpty" -> {
                    query.isNotNull(column);
                    if (field.kind() == Kind.TEXT || field.kind() == Kind.CHOICE) query.ne(column, "");
                }
                case "contains", "eq" -> {
                    if (field.kind() != Kind.TEXT || c.values().size() != 1 || c.values().get(0).isBlank())
                        throw new IllegalArgumentException("文本筛选条件无效");
                    if (c.operator().equals("contains")) query.like(column, c.values().get(0).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"));
                    else query.eq(column, c.values().get(0));
                }
                case "in" -> {
                    if ((field.kind() != Kind.CHOICE && field.kind() != Kind.TEXT) || c.values().isEmpty() || c.values().stream().anyMatch(String::isBlank))
                        throw new IllegalArgumentException("多选筛选条件无效");
                    query.in(column, c.values());
                }
                case "range" -> {
                    if ((field.kind() != Kind.DATE && field.kind() != Kind.NUMBER)
                            || c.values().isEmpty() || c.values().size() > 2)
                        throw new IllegalArgumentException("范围筛选条件无效");
                    String lower = c.values().get(0);
                    String upper = c.values().size() == 2 ? c.values().get(1) : "";
                    if (lower.isBlank() && upper.isBlank()) throw new IllegalArgumentException("范围不能为空");
                    Comparable<?> min = lower.isBlank() ? null : value(lower, field.kind());
                    Comparable<?> max = upper.isBlank() ? null : value(upper, field.kind());
                    if (min != null && max != null && compare(min, max) > 0)
                        throw new IllegalArgumentException("起始值不能大于结束值");
                    if (min != null) query.ge(column, min);
                    if (max != null) {
                        if (field.kind() == Kind.DATE && upper.length() == 10)
                            query.lt(column, LocalDate.parse(upper).plusDays(1).atStartOfDay());
                        else query.le(column, max);
                    }
                }
                default -> throw new IllegalArgumentException("高级筛选匹配方式无效");
            }
        }
    }
    private static Comparable<?> value(String raw, Kind kind) {
        try {
            return kind == Kind.DATE ? dateValue(raw) : new BigDecimal(raw);
        } catch (Exception e) { throw new IllegalArgumentException("日期或数值格式无效"); }
    }
    private static LocalDateTime dateValue(String raw) {
        return raw.length() == 10 ? LocalDate.parse(raw).atStartOfDay()
                : LocalDateTime.parse(raw.replace(' ', 'T'));
    }

    /** Shared inclusive ranges for already-authorized in-memory query collections. */
    public static boolean matchesRange(String actual, String lower, String upper, boolean numeric) {
        if (actual == null || actual.isBlank()) return false;
        if (numeric) {
            var v = new BigDecimal(actual);
            return (lower.isBlank() || v.compareTo(new BigDecimal(lower)) >= 0)
                    && (upper.isBlank() || v.compareTo(new BigDecimal(upper)) <= 0);
        }
        var v = dateValue(actual);
        return (lower.isBlank() || !v.isBefore(dateValue(lower)))
                && (upper.isBlank() || (upper.length() == 10
                    ? v.isBefore(LocalDate.parse(upper).plusDays(1).atStartOfDay())
                    : !v.isAfter(dateValue(upper))));
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Comparable a, Comparable b) { return a.compareTo(b); }
}
