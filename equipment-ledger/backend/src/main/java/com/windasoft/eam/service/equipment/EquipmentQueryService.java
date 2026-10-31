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
import com.windasoft.eam.dto.equipment.EquipmentListQuery;
import com.windasoft.eam.dto.equipment.EquipmentNavigationNode;
import com.windasoft.eam.dto.equipment.EquipmentOptions;
import com.windasoft.eam.dto.equipment.EquipmentOwnerView;
import com.windasoft.eam.dto.equipment.EquipmentPage;
import com.windasoft.eam.dto.equipment.EquipmentView;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentAssistantRepository;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
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
  private final com.windasoft.eam.service.business.BusinessValueService custom;
  private final com.windasoft.eam.service.business.BusinessQueryService customQueries;

  private final EquipmentQueryRepository repository;
  private final BaseItemRepository baseItems;
  private final OrgUnitRepository organizations;
  private final SystemUserRepository users;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper objectMapper;
  private final EquipmentViewAssembler views;
  private final EquipmentHistoryQueryService historyQueries;
  private final EquipmentAssistantQueryService assistantQueries;

  public EquipmentQueryService(
      EquipmentQueryRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper objectMapper,
      EquipmentViewAssembler views,
      EquipmentHistoryQueryService historyQueries,
      EquipmentAssistantQueryService assistantQueries,
      EquipmentViewAssembler views,
      EquipmentHistoryQueryService historyQueries,
      EquipmentAssistantQueryService assistantQueries,
      BaseItemRepository baseItems,
      OrgUnitRepository organizations,
      SystemUserRepository users,
      com.windasoft.eam.service.business.BusinessValueService custom,
      com.windasoft.eam.service.business.BusinessQueryService customQueries) {
    this.repository = repository;
    this.views = views;
    this.historyQueries = historyQueries;
    this.assistantQueries = assistantQueries;
    this.baseItems = baseItems;
    this.organizations = organizations;
    this.users = users;
    this.custom = custom;
    this.customQueries = customQueries;
    this.auth = auth;
    this.permissions = permissions;
    this.objectMapper = objectMapper;
  }

  public EquipmentPage list(EquipmentListQuery request) {
    return list(request, false);
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public EquipmentAssistantResult queryAssistant(EquipmentAssistantQuery query) {
    return assistantQueries.query(query);
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
    var all = organizations.organizations(auth.tenantId(), false);
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
    var page = users.ownerCandidates(auth.tenantId(), query);
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
    var allOrgs = organizations.organizations(auth.tenantId(), true);
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
        allOrgs.stream()
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
        users.employees(auth.tenantId()).stream()
            .map(
                u ->
                    new EquipmentOptions.Employee(
                        String.valueOf(u.getId()), u.getDisplayName(), u.getAccount()))
            .toList();
    return new EquipmentOptions(departments, employees);
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public com.windasoft.eam.dto.equipment.EquipmentHistoryView history(long id) {
    var equipment = require(id);
    permissions.checkEquipment(equipment, "查看");
    return historyQueries.history(equipment, view(equipment));
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
    return context == null ? baseItems.findById(id) : context.dictionary().get(id);
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
    return views.view(e);
  }
}
