package com.windasoft.eam.service.equipment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Period;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Reference;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.ResultSort;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Text;
import com.windasoft.eam.dto.equipment.EquipmentAssistantResult;
import com.windasoft.eam.dto.equipment.EquipmentHistoryView;
import com.windasoft.eam.dto.equipment.EquipmentListQuery;
import com.windasoft.eam.dto.equipment.EquipmentNavigationNode;
import com.windasoft.eam.dto.equipment.EquipmentOptions;
import com.windasoft.eam.dto.equipment.EquipmentOwnerView;
import com.windasoft.eam.dto.equipment.EquipmentPage;
import com.windasoft.eam.dto.equipment.EquipmentView;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentAssistantRepository;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.equipment.EquipmentOwnerRepository;
import com.windasoft.eam.entity.equipment.EquipmentOwner;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class EquipmentQueryService {
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.business.BusinessValueService custom;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.business.BusinessQueryService customQueries;

  private final EquipmentQueryRepository repository;
  private final EquipmentOwnerRepository ownerRepository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper objectMapper;
  private final EquipmentAssistantRepository assistantRepository;

  public EquipmentQueryService(
      EquipmentQueryRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper objectMapper,
      EquipmentAssistantRepository assistantRepository,
      EquipmentOwnerRepository ownerRepository) {
    this.repository = repository;
    this.ownerRepository = ownerRepository;
    this.auth = auth;
    this.permissions = permissions;
    this.objectMapper = objectMapper;
    this.assistantRepository = assistantRepository;
  }

  public EquipmentPage list(EquipmentListQuery request) {
    return list(request, false);
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public EquipmentAssistantResult queryAssistant(EquipmentAssistantQuery query) {
    permissions.require("equipment", "查看");
    validateAssistant(query);
    if (query.equipmentId() != null) {
      get(query.equipmentId());
    }
    var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).withNano(0);
    var range = assistantRange(query, now.toLocalDate());
    var scope = Objects.requireNonNull(permissions.equipment("查看"));
    var ambiguous =
        assistantRepository.ambiguousName(auth.tenantId(), auth.userId(), scope, query, now, range);
    if (ambiguous.isPresent()) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.equipment.assistant.ambiguous", ambiguous.get().label());
    }
    return new EquipmentAssistantResult(
        assistantRepository.execute(auth.tenantId(), auth.userId(), scope, query, now, range),
        now,
        range);
  }

  private static void assistantValid(boolean condition) {
    if (!condition) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.equipment.assistant.invalid");
    }
  }

  private static void validateAssistant(EquipmentAssistantQuery q) {
    assistantValid(q != null);
    assistantValid(
        q.operation() != null
            && q.aggregate() != null
            && q.direction() != null
            && q.period() != null
            && q.bucket() != null
            && q.groupBy() != null
            && q.where() != null);
    assistantValid(
        q.page() >= 1
            && q.page() <= 1_000_000
            && Set.of(20, 50, 100).contains(q.size())
            && q.topN() >= 0
            && q.topN() <= 100
            && (q.equipmentId() == null || q.equipmentId() > 0));
    assistantValid(
        q.groupBy().size() <= 2 && new HashSet<>(q.groupBy()).size() == q.groupBy().size());
    for (var field : q.groupBy()) {
      assistantValid(field != null && field.kind() == Kind.TEXT);
    }
    assistantValid(
        q.aggregate() == Aggregate.DISTINCT
            ? q.measure() != null && Set.of(Kind.TEXT, Kind.ID).contains(q.measure().kind())
            : q.measure() == null);
    assistantValid(
        q.timeField() == null || Set.of(Kind.DATE, Kind.DATETIME).contains(q.timeField().kind()));
    assistantValid(q.period() == Period.ALL || q.timeField() != null);
    assistantValid((q.period() == Period.CUSTOM) == (q.customRange() != null));
    if (q.operation() == Operation.COUNT) {
      assistantValid(q.groupBy().isEmpty() && q.topN() == 0 && q.sortBy() == null);
    }
    if (q.operation() == Operation.LIST) {
      assistantValid(q.groupBy().isEmpty() && q.aggregate() == Aggregate.COUNT);
    }
    if (q.operation() == Operation.GROUP) {
      assistantValid(!q.groupBy().isEmpty());
    }
    if (q.operation() == Operation.TREND) {
      assistantValid(q.timeField() != null);
    }
    if (q.sortBy() instanceof FieldSort(Field field)) {
      assistantValid(
          field != null
              && (q.operation() == Operation.LIST || q.groupBy().contains(field)));
    } else if (q.sortBy() instanceof ResultSort sort) {
      assistantValid(
          q.operation() != Operation.LIST
              && (sort != ResultSort.PERIOD || q.operation() == Operation.TREND));
    }
    validateAssistantFilter(q.where(), 0, new int[] {0});
  }

  private static void validateAssistantFilter(Filter filter, int depth, int[] count) {
    assistantValid(filter != null && depth <= 4 && ++count[0] <= 24);
    if (filter instanceof All) {
      assistantValid(depth == 0);
      return;
    }
    if (filter instanceof Junction(Logical operator, List<Filter> children)) {
      assistantValid(
          operator != null
              && children != null
              && !children.isEmpty()
              && children.size() <= 12
              && (operator != Logical.NOT || children.size() == 1));
      for (var child : children) {
        validateAssistantFilter(child, depth + 1, count);
      }
      return;
    }
    var condition = (Condition) filter;
    assistantValid(
        condition.field() != null
            && condition.operator() != null
            && condition.values() != null
            && condition.values().size() <= 20);
    if (Set.of(Comparison.EMPTY, Comparison.NOT_EMPTY).contains(condition.operator())) {
      assistantValid(condition.values().isEmpty());
      return;
    }
    assistantValid(
        !condition.values().isEmpty()
            && (condition.operator() == Comparison.IN || condition.values().size() == 1));
    if (condition.operator() == Comparison.CONTAINS) {
      assistantValid(condition.field().kind() == Kind.TEXT);
    }
    if (Set.of(Comparison.GT, Comparison.GTE, Comparison.LT, Comparison.LTE)
        .contains(condition.operator())) {
      assistantValid(Set.of(Kind.DATE, Kind.DATETIME).contains(condition.field().kind()));
    }
    for (var value : condition.values()) {
      boolean valid =
          switch (condition.field().kind()) {
            case TEXT -> value instanceof Text(String value1) && value1 != null && value1.length() <= 200;
            case ID ->
                value instanceof Identifier(Long value1) && value1 != null && value1 > 0
                    || value == Reference.ME && condition.field() == Field.OWNER_ID;
            case DATE ->
                value instanceof DateValue(LocalDate value1)
                        && value1 != null
                        && value1.getYear() >= 1000
                        && value1.getYear() <= 9999
                    || value == Reference.TODAY;
            case DATETIME ->
                value instanceof DateTimeValue(java.time.LocalDateTime value1)
                        && value1 != null
                        && value1.getYear() >= 1000
                        && value1.getYear() <= 9999
                    || value == Reference.TODAY
                    || value == Reference.NOW;
          };
      assistantValid(valid);
    }
  }

  private static DateRange assistantRange(EquipmentAssistantQuery q, LocalDate today) {
    if (q.period() == Period.ALL) {
      return null;
    }
    LocalDate start = today, end = today;
    switch (q.period()) {
      case TODAY -> {}
      case YESTERDAY -> {
        start = today.minusDays(1);
        end = start;
      }
      case WEEK, LAST_WEEK -> {
        start = today.minusDays(today.getDayOfWeek().getValue() - 1);
        if (q.period() == Period.LAST_WEEK) {
          start = start.minusWeeks(1);
        }
        end = start.plusDays(6);
      }
      case MONTH -> {
        start = today.withDayOfMonth(1);
        end = today.withDayOfMonth(today.lengthOfMonth());
      }
      case LAST_MONTH -> {
        end = today.withDayOfMonth(1).minusDays(1);
        start = end.withDayOfMonth(1);
      }
      case YEAR, LAST_YEAR -> {
        start = today.withDayOfYear(1);
        if (q.period() == Period.LAST_YEAR) {
          start = start.minusYears(1);
        }
        end = start.plusYears(1).minusDays(1);
      }
      case LAST7 -> start = today.minusDays(6);
      case LAST30 -> start = today.minusDays(29);
      case LAST90 -> start = today.minusDays(89);
      case CUSTOM -> {
        start = q.customRange().start();
        end = q.customRange().end();
      }
      default -> throw new IllegalStateException("未处理的设备查询周期");
    }
    assistantValid(
        start != null
            && end != null
            && !start.isAfter(end)
            && !start.isBefore(LocalDate.of(1900, 1, 1))
            && !end.isAfter(today.plusYears(10)));
    return new DateRange(start, end);
  }

  private record PreparedQuery(
      com.windasoft.eam.repository.common.SqlPredicate extensions, String order) {}

  private PreparedQuery prepare(EquipmentListQuery request) {
    var extensions =
        request.conditions().stream().filter(c -> c.field().startsWith("custom:")).toList();
    var predicate =
        extensions.isEmpty()
            ? com.windasoft.eam.repository.common.SqlPredicate.of("1=1")
            : customQueries.predicate(
                "equipment", objectMapper.valueToTree(extensions), "eam_equipment");
    return new PreparedQuery(
        predicate,
        customQueries.orderExpression("equipment", request.sort().field(), "eam_equipment"));
  }

  private EquipmentPage list(EquipmentListQuery request, boolean exporting) {
    return load(request, exporting, prepare(request));
  }

  private EquipmentPage load(
      EquipmentListQuery request, boolean exporting, PreparedQuery prepared) {
    var result =
        repository.list(
            auth.tenantId(),
            request,
            q -> {
              permissions.filterEquipment(q);
              if (exporting) permissions.filterEquipment(q, "导出");
            },
            prepared.extensions(),
            prepared.order());
    var extra =
        custom.batch("equipment", result.getRecords().stream().map(Equipment::getId).toList());
    result
        .getRecords()
        .forEach(
            e -> {
              var v = extra.get(e.getId());
              e.setCustomFields(v.customFields());
              e.setCustomFieldLabels(v.customFieldLabels());
              e.setCustomSchemaVersion(v.customSchemaVersion());
            });
    var relations = repository.pageRelations(auth.tenantId(), result.getRecords());
    var ownersByEquipment =
        ownerRepository
            .listByEquipmentIds(
                auth.tenantId(), result.getRecords().stream().map(Equipment::getId).toList())
            .stream()
            .collect(java.util.stream.Collectors.groupingBy(EquipmentOwner::getEquipmentId));
    return new EquipmentPage(
        result.getRecords().stream().map(row -> view(row, relations, ownersByEquipment)).toList(),
        new EquipmentPage.PageInfo(result.getCurrent(), result.getSize(), result.getTotal()));
  }

  private static final Map<String, String> EXPORT_FIELDS =
      Map.ofEntries(
          Map.entry("code", "设备编号"),
          Map.entry("name", "设备名称"),
          Map.entry("state", "设备状态"),
          Map.entry("useState", "使用状态"),
          Map.entry("assetNo", "资产编号"),
          Map.entry("category", "设备分类"),
          Map.entry("department", "所属部门"),
          Map.entry("functionLocation", "位置"),
          Map.entry("model", "规格型号"),
          Map.entry("serial", "出厂编号"),
          Map.entry("owner", "负责人"),
          Map.entry("brand", "品牌"),
          Map.entry("source", "设备来源"),
          Map.entry("unit", "单位"),
          Map.entry("supplier", "供应商"),
          Map.entry("acquiredAt", "购置日期"),
          Map.entry("amount", "采购金额（元）"),
          Map.entry("warranty", "保修期至"));

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public void export(EquipmentListQuery request, List<String> columns, java.io.PrintWriter writer) {
    permissions.require("equipment", "导出");
    var fields = customQueries.fields("equipment");
    var labels = new HashMap<>(EXPORT_FIELDS);
    EXPORT_FIELDS
        .keySet()
        .forEach(
            k ->
                labels.put(
                    k,
                    com.windasoft.eam.common.MessageContext.text("output.equipment.field." + k)));
    fields.forEach(
        (id, f) -> {
          if (f.path("exportable").asBoolean()) labels.put("custom:" + id, f.path("name").asText());
        });
    if (columns.isEmpty()
        || columns.size() > labels.size()
        || !labels.keySet().containsAll(columns)
        || new HashSet<>(columns).size() != columns.size())
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0028");
    // 首次写出响应前完成整份查询的校验，避免响应头提交后再报错。
    var prepared = prepare(request);
    var first = load(request.at(1, 500), true, prepared);
    writer.print('\ufeff');
    writer.println(
        columns.stream()
            .map(k -> csv(labels.get(k)))
            .collect(java.util.stream.Collectors.joining(",")));
    for (int page = 1; ; page++) {
      var result = page == 1 ? first : load(request.at(page, 500), true, prepared);
      for (var row : result.data()) {
        Map<String, Object> view =
            objectMapper.convertValue(
                row, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        writer.println(
            columns.stream()
                .map(
                    k ->
                        csv(
                            k.startsWith("custom:")
                                ? row.customFieldLabels().get(k.substring(7))
                                : Objects.toString(view.get(k), "")))
                .collect(java.util.stream.Collectors.joining(",")));
      }
      if (result.data().size() < 500) break;
      if (writer.checkError()) throw new IllegalStateException("导出连接已断开");
    }
  }

  private static String csv(String value) {
    String text = Objects.toString(value, "");
    if (!text.isBlank() && "=+-@".indexOf(text.stripLeading().charAt(0)) >= 0
        || text.startsWith("\t")
        || text.startsWith("\r")) text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }

  public EquipmentView get(long id) {
    var e = require(id);
    permissions.checkEquipment(e, "查看");
    return view(e);
  }

  public byte[] qrCode(long id) throws Exception {
    var equipment = require(id);
    permissions.checkEquipment(equipment, "查看");
    var matrix =
        new com.google.zxing.MultiFormatWriter()
            .encode(
                equipment.getEquipmentCode(),
                com.google.zxing.BarcodeFormat.QR_CODE,
                240,
                240,
                Map.of(
                    com.google.zxing.EncodeHintType.CHARACTER_SET,
                    "UTF-8",
                    com.google.zxing.EncodeHintType.MARGIN,
                    2));
    var bytes = new java.io.ByteArrayOutputStream();
    com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(matrix, "PNG", bytes);
    return bytes.toByteArray();
  }

  public List<EquipmentNavigationNode> navigationTree() {
    permissions.require("equipment", "查看");
    var visible = new java.util.HashSet<Long>();
    for (var e : repository.visibleOrganizations(auth.tenantId(), permissions::filterEquipment)) {
      if (e.getCompanyOrgId() != null) visible.add(e.getCompanyOrgId());
      if (e.getDepartmentOrgId() != null) visible.add(e.getDepartmentOrgId());
    }
    var all = repository.organizations(auth.tenantId(), false);
    boolean changed;
    do {
      changed = false;
      for (var o : all)
        if (visible.contains(o.getId()) && o.getParentId() != null)
          changed |= visible.add(o.getParentId());
    } while (changed);
    return all.stream()
        .filter(o -> visible.contains(o.getId()))
        .map(
            o ->
                new EquipmentNavigationNode(
                    String.valueOf(o.getId()),
                    o.getName(),
                    o.getParentId() == null ? "" : String.valueOf(o.getParentId()),
                    o.getOrgType()))
        .toList();
  }

  public com.windasoft.eam.dto.common.PageResult<EquipmentOptions.Employee> ownerCandidates(
      String action, com.windasoft.eam.dto.common.ListQuery query) {
    if (!List.of("新增", "编辑").contains(action))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0029");
    permissions.require("equipment", action);
    var page = repository.ownerCandidates(auth.tenantId(), query);
    return new com.windasoft.eam.dto.common.PageResult<>(
        page.getRecords().stream()
            .map(
                u ->
                    new EquipmentOptions.Employee(
                        String.valueOf(u.getId()), u.getDisplayName(), u.getAccount()))
            .toList(),
        query.page(),
        query.size(),
        page.getTotal());
  }

  public EquipmentOptions batchOptions(String action) {
    if (!List.of("新增", "编辑").contains(action))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0029");
    permissions.require("equipment", action);
    var allOrgs = repository.organizations(auth.tenantId(), true);
    var selectable =
        allOrgs.stream()
            .filter(
                o ->
                    "部门".equals(o.getOrgType())
                        && permissions.equipmentDepartmentAllowed(o.getId(), action))
            .map(com.windasoft.eam.entity.system.OrgUnit::getId)
            .collect(java.util.stream.Collectors.toSet());
    var visible = new java.util.HashSet<Long>(selectable);
    var orgMap =
        allOrgs.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    com.windasoft.eam.entity.system.OrgUnit::getId, o -> o));
    for (Long id : selectable) {
      var node = orgMap.get(id);
      var visited = new java.util.HashSet<Long>();
      while (node != null && visited.add(node.getId())) {
        visible.add(node.getId());
        node = orgMap.get(node.getParentId());
      }
    }
    var departments =
        repository.organizations(auth.tenantId(), true).stream()
            .filter(o -> visible.contains(o.getId()))
            .map(
                o ->
                    new EquipmentOptions.Department(
                        !selectable.contains(o.getId()),
                        String.valueOf(o.getId()),
                        o.getName(),
                        o.getDisplayType() == null ? o.getOrgType() : o.getDisplayType(),
                        o.getParentId() == null ? "" : String.valueOf(o.getParentId())))
            .toList();
    var employees =
        repository.employees(auth.tenantId()).stream()
            .map(
                u ->
                    new EquipmentOptions.Employee(
                        String.valueOf(u.getId()), u.getDisplayName(), u.getAccount()))
            .toList();
    return new EquipmentOptions(departments, employees);
  }

  public EquipmentHistoryView history(long id) {
    var equipment = require(id);
    permissions.checkEquipment(equipment, "查看");
    var repairRows = repository.repairs(auth.tenantId(), equipment.getEquipmentCode());
    var repairHistory =
        repairRows.stream()
            .filter(
                r -> {
                  try {
                    permissions.checkRepair(r, "查看");
                    return true;
                  } catch (org.springframework.web.server.ResponseStatusException denied) {
                    return false;
                  }
                })
            .map(
                r ->
                    Map.<String, Object>ofEntries(
                        Map.entry("number", safe(r.getOrderNo())),
                        Map.entry(
                            "time", r.getReportedAt() == null ? "" : r.getReportedAt().toString()),
                        Map.entry("description", safe(r.getDescription())),
                        Map.entry("person", safe(r.getAssignee())),
                        Map.entry("status", safe(r.getStatus())),
                        Map.entry("result", safe(r.getRepairAction()))))
            .toList();
    var records = repository.history(auth.tenantId(), id);
    return new EquipmentHistoryView(
        repository.installedSpares(auth.tenantId(), id),
        view(equipment),
        repairHistory,
        records.stream().filter(r -> "MAINTENANCE".equals(r.get("type"))).toList(),
        records.stream().filter(r -> "INSPECTION".equals(r.get("type"))).toList(),
        records.stream().filter(r -> "SPARE_CHANGE".equals(r.get("type"))).toList());
  }

  private Equipment require(long id) {
    var e = repository.find(auth.tenantId(), id);
    if (e == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0064");
    return e;
  }

  private String locationPath(
      Long id, Long company, boolean required, EquipmentQueryRepository.PageRelations context) {
    if (id == null) {
      if (required)
        throw new com.windasoft.eam.common.LocalizedArgumentException("ui.equipment.text0109");
      return null;
    }
    var parts = new java.util.ArrayList<String>();
    var visited = new java.util.HashSet<Long>();
    var node = baseItem(id, context);
    if (node == null) {
      if (required)
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0030");
      return null;
    }
    while (node != null) {
      if (!visited.add(node.getId()))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0031");
      if (!node.getTenantId().equals(auth.tenantId())
          || !"LOCATION".equals(node.getItemType())
          || !java.util.Objects.equals(node.getCompanyOrgId(), company))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0032");
      if (required && node.getId().equals(id) && !Integer.valueOf(1).equals(node.getStatus()))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0033");
      parts.addFirst(node.getName());
      node = node.getParentId() == null ? null : baseItem(node.getParentId(), context);
    }
    return String.join(" · ", parts);
  }

  private com.windasoft.eam.entity.basedata.BaseItem baseItem(
      Long id, EquipmentQueryRepository.PageRelations context) {
    return context == null ? repository.baseItem(id) : context.dictionary().get(id);
  }

  private String currentBaseName(
      Long id, String type, String snapshot, EquipmentQueryRepository.PageRelations context) {
    if (id == null) return safe(snapshot);
    var node = baseItem(id, context);
    return node != null
            && java.util.Objects.equals(node.getTenantId(), auth.tenantId())
            && type.equals(node.getItemType())
        ? node.getName()
        : safe(snapshot);
  }

  private static String date(LocalDate d) {
    return d == null ? "" : d.toString();
  }

  private JsonNode json(String value) {
    try {
      return value == null ? objectMapper.createArrayNode() : objectMapper.readTree(value);
    } catch (Exception ignored) {
      return objectMapper.createArrayNode();
    }
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  public EquipmentView view(Equipment e) {
    return view(e, null, null);
  }

  private static List<EquipmentOwnerView> ownerViews(List<EquipmentOwner> rows) {
    return rows.stream()
        .map(row -> new EquipmentOwnerView(Long.toString(row.getUserId()), row.getOwnerName()))
        .toList();
  }

  private EquipmentView view(
      Equipment e,
      EquipmentQueryRepository.PageRelations context,
      Map<Long, List<EquipmentOwner>> ownersByEquipment) {
    if (e.getCustomFields() == null) {
      var v = custom.read("equipment", e.getId());
      e.setCustomFields(v.customFields());
      e.setCustomFieldLabels(v.customFieldLabels());
      e.setCustomSchemaVersion(v.customSchemaVersion());
    }
    var owners =
        ownerViews(
            ownersByEquipment == null
                ? ownerRepository.list(auth.tenantId(), e.getId())
                : ownersByEquipment.getOrDefault(e.getId(), List.of()));
    return new EquipmentView(
        String.valueOf(e.getId()),
        safe(e.getEquipmentCode()),
        safe(e.getName()),
        currentBaseName(e.getCategoryId(), "CATEGORY", e.getCategory(), context),
        e.getCategoryId() == null ? "" : String.valueOf(e.getCategoryId()),
        e.getUnitId() == null ? "" : String.valueOf(e.getUnitId()),
        e.getUseStatusId() == null ? "" : String.valueOf(e.getUseStatusId()),
        e.getSourceId() == null ? "" : String.valueOf(e.getSourceId()),
        owners,
        owners.stream().map(EquipmentOwnerView::id).toList(),
        e.getOwnerUserId() == null ? "" : String.valueOf(e.getOwnerUserId()),
        safe(e.getDepartmentPath()),
        safe(e.getModel()),
        safe(e.getSerialNo()),
        safe(e.getCompany()),
        e.getCompanyOrgId() == null ? "" : String.valueOf(e.getCompanyOrgId()),
        safe(e.getDepartment()),
        e.getDepartmentOrgId() == null ? "" : e.getDepartmentOrgId().toString(),
        safe(e.getOwnerName()),
        safe(e.getBrand()),
        safe(e.getEquipmentStatus()),
        currentBaseName(e.getUseStatusId(), "USE_STATUS", e.getUseStatus(), context),
        currentBaseName(e.getSourceId(), "SOURCE", e.getSource(), context),
        date(e.getPurchaseDate() != null ? e.getPurchaseDate() : e.getAcquiredAt()),
        e.getAcquiredAmount() == null ? "" : e.getAcquiredAmount().toPlainString(),
        e.getWarrantyUntil() == null ? "" : e.getWarrantyUntil().toString(),
        safe(e.getDescription()),
        e.getRowVersion() == null ? 0 : e.getRowVersion(),
        safe(e.getAssetNo()),
        safe(e.getTagCode()),
        currentBaseName(e.getUnitId(), "UNIT", e.getUnitName(), context),
        safe(e.getSupplier()),
        date(e.getPurchaseDate()),
        date(e.getInServiceDate()),
        date(e.getExpectedRetirementDate()),
        safe(e.getEquipmentLevel()),
        e.getFunctionLocationId() == null ? "" : String.valueOf(e.getFunctionLocationId()),
        safe(
            e.getFunctionLocationId() == null
                ? e.getFunctionLocation()
                : java.util.Objects.requireNonNullElse(
                    locationPath(e.getFunctionLocationId(), e.getCompanyOrgId(), false, context),
                    safe(e.getFunctionLocation()))),
        safe(e.getSpecialEquipment()),
        safe(e.getMetrologyEquipment()),
        safe(e.getDepreciationEnabled()),
        safe(e.getTechnicalParams()),
        date(e.getCalibrationDate()),
        date(e.getCalibrationValidUntil()),
        safe(e.getInspectorName()),
        safe(e.getImageName()),
        safe(e.getImageFileId()),
        json(e.getImagesJson()),
        json(e.getDocumentsJson()),
        context == null
            ? repository.children(auth.tenantId(), e.getId())
            : context.children().getOrDefault(e.getId(), List.of()),
        context == null
            ? repository.bom(auth.tenantId(), e.getId())
            : context.bom().getOrDefault(e.getId(), List.of()),
        e.getCustomFields(),
        e.getCustomFieldLabels(),
        e.getCustomSchemaVersion());
  }
}
