package com.windasoft.eam.service.imports;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.windasoft.eam.dto.equipment.EquipmentForm;
import com.windasoft.eam.dto.imports.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.imports.EquipmentImportRepository;
import com.windasoft.eam.service.business.*;
import com.windasoft.eam.service.equipment.EquipmentInputValidation;
import com.windasoft.eam.service.system.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class EquipmentImportValidation {
  private final EquipmentImportRepository repo;
  private final BusinessConfigService configs;
  private final BusinessReferenceService references;
  private final BusinessValueService values;
  private final EquipmentInputValidation input;
  private final PermissionService permissions;
  private final AuthContext auth;
  private final ObjectMapper json;

  public EquipmentImportValidation(
      EquipmentImportRepository repo,
      BusinessConfigService configs,
      BusinessReferenceService references,
      BusinessValueService values,
      EquipmentInputValidation input,
      PermissionService permissions,
      AuthContext auth,
      ObjectMapper json) {
    this.repo = repo;
    this.configs = configs;
    this.references = references;
    this.values = values;
    this.input = input;
    this.permissions = permissions;
    this.auth = auth;
    this.json = json;
  }

  public record Checked(
      List<PreparedEquipmentImport> rows, List<ImportIssue> issues, Context context) {}

  public static final class Context {
    int version;
    JsonNode configuration;
    Map<String, JsonNode> fields;
    long tenant, actor;
    PermissionScope create, imports, view, edit;
    Long ownCompany, ownDepartment;
    Map<Long, Map<String, Object>> orgs = new LinkedHashMap<>(),
        bases = new LinkedHashMap<>(),
        users = new LinkedHashMap<>();
    Map<Long, String> orgPaths = new HashMap<>(), basePaths = new HashMap<>();
    Map<Long, Long> companies = new HashMap<>();
    Map<String, Map<String, String>> referenceNames = new HashMap<>(),
        referenceInputs = new HashMap<>();
    Map<String, List<String>> choices = new LinkedHashMap<>();
    List<ImportColumn> columns;
    Map<String, Equipment> existing = Map.of();
    Map<String, String> codeKeys = Map.of();

    public String codeKey(String code) {
      return codeKeys.getOrDefault(code, code.toLowerCase(Locale.ROOT));
    }

    Map<String, List<Long>> lookups = new HashMap<>();
    Map<String, List<Long>> accounts = new HashMap<>();

    public int version() {
      return version;
    }

    public List<ImportColumn> columns() {
      return columns;
    }

    public Map<String, List<String>> choices() {
      return choices;
    }
  }

  public Context context() {
    permissions.require("equipment", "导入");
    permissions.require("equipment", "新增");
    var c = new Context();
    c.tenant = auth.tenantId();
    c.actor = auth.userId();
    c.create = permissions.equipment("新增");
    c.imports = permissions.equipment("导入");
    try {
      c.view = permissions.equipment("查看");
    } catch (Exception e) {
      c.view = new PermissionScope(false, null, Set.of(), false);
    }
    try {
      c.edit = permissions.equipment("编辑");
    } catch (Exception e) {
      c.edit = new PermissionScope(false, null, Set.of(), false);
    }
    for (var r : repo.organizations(c.tenant)) c.orgs.put(id(r.get("id")), r);
    for (var r : repo.bases(c.tenant)) c.bases.put(id(r.get("id")), r);
    for (var r : repo.users(c.tenant)) c.users.put(id(r.get("id")), r);
    for (var r : c.orgs.values()) {
      long key = id(r.get("id"));
      c.orgPaths.put(key, path(c.orgs, key, " > "));
      c.companies.put(key, company(c.orgs, key));
    }
    c.ownCompany = c.companies.get(auth.user().getOrgId());
    c.ownDepartment = auth.user().getOrgId();
    for (var e : c.orgs.entrySet())
      if (active(e.getValue()) && "部门".equals(e.getValue().get("org_type")))
        for (String candidate :
            new LinkedHashSet<>(
                List.of(c.orgPaths.get(e.getKey()), path(c.orgs, e.getKey(), " · "))))
          c.lookups
              .computeIfAbsent("ORG|" + candidate.toLowerCase(Locale.ROOT), x -> new ArrayList<>())
              .add(e.getKey());
    for (var e : c.users.entrySet())
      if (active(e.getValue()))
        c.accounts
            .computeIfAbsent(
                e.getValue().get("account").toString().toLowerCase(Locale.ROOT),
                x -> new ArrayList<>())
            .add(e.getKey());
    c.bases.forEach(
        (key, r) ->
            c.basePaths.put(
                key, path(c.bases, key, "LOCATION".equals(r.get("item_type")) ? " > " : " · ")));
    for (var e : c.bases.entrySet())
      if (active(e.getValue())) {
        var r = e.getValue();
        String type = r.get("item_type").toString();
        String display =
            Set.of("CATEGORY", "LOCATION").contains(type)
                ? c.basePaths.get(e.getKey())
                : r.get("name").toString();
        String prefix =
            type
                + "|"
                + (type.equals("LOCATION")
                    ? Objects.toString(r.get("company_org_id"), "") + "|"
                    : "");
        for (String candidate :
            new LinkedHashSet<>(
                List.of(
                    display, type.equals("LOCATION") ? path(c.bases, e.getKey(), " · ") : display)))
          c.lookups
              .computeIfAbsent(prefix + candidate.toLowerCase(Locale.ROOT), x -> new ArrayList<>())
              .add(e.getKey());
      }
    var schema = configs.current("equipment", false);
    c.version = ((Number) schema.get("published_version")).intValue();
    c.configuration = configs.decode(schema.get("published"));
    c.fields = configs.fields(c.configuration);
    var columns = new ArrayList<>(EquipmentImportFields.core());
    c.choices.put(
        "department",
        c.orgs.entrySet().stream()
            .filter(
                e ->
                    active(e.getValue())
                        && "部门".equals(e.getValue().get("org_type"))
                        && departmentAllowed(c, e.getKey(), c.create)
                        && departmentAllowed(c, e.getKey(), c.imports))
            .map(e -> c.orgPaths.get(e.getKey()))
            .distinct()
            .sorted()
            .toList());
    var companies =
        c.orgs.entrySet().stream()
            .filter(
                e ->
                    departmentAllowed(c, e.getKey(), c.create)
                        && departmentAllowed(c, e.getKey(), c.imports))
            .map(e -> c.companies.get(e.getKey()))
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    for (var spec :
        Map.of(
                "category",
                "CATEGORY",
                "functionLocation",
                "LOCATION",
                "unit",
                "UNIT",
                "useState",
                "USE_STATUS",
                "source",
                "SOURCE")
            .entrySet())
      c.choices.put(
          spec.getKey(),
          c.bases.entrySet().stream()
              .filter(
                  e ->
                      active(e.getValue())
                          && spec.getValue().equals(e.getValue().get("item_type"))
                          && (!spec.getValue().equals("LOCATION")
                              || companies.contains(id(e.getValue().get("company_org_id")))))
              .map(
                  e ->
                      Set.of("CATEGORY", "LOCATION").contains(spec.getValue())
                          ? c.basePaths.get(e.getKey())
                          : e.getValue().get("name").toString())
              .distinct()
              .sorted()
              .toList());
    for (var entry : c.bases.entrySet())
      if (active(entry.getValue())
          && "LOCATION".equals(entry.getValue().get("item_type"))
          && companies.contains(id(entry.getValue().get("company_org_id"))))
        c.choices
            .computeIfAbsent(
                "__locationCompany:" + c.basePaths.get(entry.getKey()), x -> new ArrayList<>())
            .add(c.orgPaths.get(id(entry.getValue().get("company_org_id"))));
    c.choices.put(
        "owner",
        c.users.values().stream()
            .filter(EquipmentImportValidation::active)
            .map(r -> r.get("account").toString())
            .sorted()
            .toList());
    c.choices.put("state", input.states().stream().sorted().toList());
    c.choices.put("level", List.of("关键设备", "主要设备", "一般设备"));
    for (String key : List.of("specialEquipment", "metrologyEquipment", "depreciationEnabled"))
      c.choices.put(key, List.of("是", "否"));
    for (var e : c.fields.entrySet()) {
      var f = e.getValue();
      if (!f.path("enabled").asBoolean(true)) continue;
      String key = "custom:" + e.getKey();
      String type = f.path("type").asText();
      columns.add(
          new ImportColumn(
              key,
              f.path("name").asText(),
              f.path("required").asBoolean(false),
              com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3250")
                  + type
                  + (BusinessRules.multiple(f)
                      ? com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3251")
                      : "")));
      if (BusinessRules.reference(f)) {
        List<Map<String, Object>> opts;
        if (type.equals("USER"))
          opts =
              c.users.values().stream()
                  .filter(
                      u -> active(u) && referenceAllowed(c, id(u.get("org_id")), id(u.get("id"))))
                  .map(
                      u ->
                          Map.<String, Object>of(
                              "id",
                              u.get("id").toString(),
                              "name",
                              u.get("display_name").toString()))
                  .toList();
        else if (type.equals("DEPARTMENT"))
          opts =
              c.orgs.values().stream()
                  .filter(
                      o ->
                          active(o)
                              && "部门".equals(o.get("org_type"))
                              && referenceAllowed(c, id(o.get("id")), null))
                  .map(
                      o ->
                          Map.<String, Object>of(
                              "id",
                              o.get("id").toString(),
                              "name",
                              c.orgPaths.get(id(o.get("id")))))
                  .toList();
        else opts = references.fieldOptions("equipment", f, false);
        var names = new LinkedHashMap<String, String>();
        var inputs = new LinkedHashMap<String, String>();
        var ambiguous = new HashSet<String>();
        var displays = new ArrayList<String>();
        for (var o : opts) {
          String oid = o.get("id").toString(), name = o.get("name").toString();
          String display = name;
          if (type.equals("USER")) {
            var u = c.users.get(Long.valueOf(oid));
            if (!active(u)) continue;
            display = u.get("account").toString();
          }
          if (type.equals("DEPARTMENT")) {
            display = c.orgPaths.getOrDefault(Long.valueOf(oid), name);
          }
          names.put(oid, name);
          String lookup = display.toLowerCase(Locale.ROOT);
          if (inputs.put(lookup, oid) != null) ambiguous.add(lookup);
          displays.add(display);
        }
        ambiguous.forEach(inputs::remove);
        c.referenceNames.put(e.getKey(), names);
        c.referenceInputs.put(e.getKey(), inputs);
        c.choices.put(key, displays.stream().distinct().sorted().toList());
      } else if (type.equals("BOOLEAN")) c.choices.put(key, List.of("是", "否"));
    }
    if (columns.size() > ImportWorkbookService.MAX_COLUMNS)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0018");
    c.columns = columns;
    return c;
  }

  private static boolean referenceAllowed(Context c, Long org, Long user) {
    return c.view.matches(c.companies.get(org), org, user, c.actor)
        || c.view.owner()
            && Objects.equals(org, c.ownDepartment)
            && (user == null || user == c.actor);
  }

  private static boolean departmentAllowed(Context c, Long department, PermissionScope scope) {
    Long company = c.companies.get(department);
    return company != null
        && (scope.all()
            || scope.companyIds().contains(company)
            || scope.departmentIds().contains(department)
            || scope.owner() && Objects.equals(company, c.ownCompany));
  }

  private static boolean allowed(Context c, Equipment e, PermissionScope scope) {
    return scope.matches(e.getCompanyOrgId(), e.getDepartmentOrgId(), e.getOwnerUserId(), c.actor)
        || scope.owner() && e.getOwnerIds() != null && e.getOwnerIds().contains(c.actor);
  }

  private static boolean active(Map<String, Object> r) {
    return r != null && ((Number) r.get("status")).intValue() == 1;
  }

  private static Long id(Object v) {
    return v == null ? null : ((Number) v).longValue();
  }

  private static Long company(Map<Long, Map<String, Object>> orgs, Long id) {
    var seen = new HashSet<Long>();
    while (id != null && seen.add(id)) {
      var r = orgs.get(id);
      if (r == null) return null;
      if (Set.of("集团", "公司").contains(r.get("org_type"))) return id;
      id = id(r.get("parent_id"));
    }
    return null;
  }

  static String path(Map<Long, Map<String, Object>> records, Long id, String separator) {
    var parts = new ArrayList<String>();
    var seen = new HashSet<Long>();
    while (id != null) {
      if (!seen.add(id))
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0019");
      var r = records.get(id);
      if (r == null) break;
      parts.addFirst(r.get("name").toString());
      id = id(r.get("parent_id"));
    }
    return String.join(separator, parts);
  }

  public Checked check(ImportWorkbook book, Context c) {
    if (book.schemaVersion() != c.version)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0020");
    var incoming = new HashSet<String>();
    book.columns().forEach(col -> incoming.add(col.key()));
    if (!incoming.equals(
        c.columns.stream().map(ImportColumn::key).collect(java.util.stream.Collectors.toSet())))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0021");
    var codes = new HashSet<String>();
    var byCode = new HashMap<String, List<Integer>>();
    for (var r : book.rows())
      for (String field : List.of("code", "parentCode")) {
        String code = r.values().getOrDefault(field, "").trim();
        if (!code.isBlank()) codes.add(code);
      }
    c.codeKeys = repo.codeKeys(codes);
    for (var r : book.rows()) {
      String code = r.values().getOrDefault("code", "");
      if (!code.isBlank())
        byCode.computeIfAbsent(c.codeKey(code), x -> new ArrayList<>()).add(r.row());
    }
    var existingRows = repo.equipment(c.tenant, codes);
    var mapped = new HashMap<String, Equipment>();
    existingRows.forEach((code, e) -> mapped.put(c.codeKey(code), e));
    c.existing = mapped;
    var owners = repo.owners(c.tenant, new HashSet<>(c.existing.values()));
    c.existing.values().forEach(e -> e.setOwnerIds(owners.getOrDefault(e.getId(), List.of())));
    var issues = new ArrayList<ImportIssue>();
    var prepared = new ArrayList<PreparedEquipmentImport>();
    for (var r : book.rows()) {
      var local = new LinkedHashMap<String, String>();
      var body = json.createObjectNode();
      for (var col : EquipmentImportFields.core())
        if (!col.key().equals("parentCode")) body.put(col.key(), "");
      r.values()
          .forEach(
              (key, value) -> {
                if (!key.startsWith("custom:") && !key.equals("parentCode")) body.put(key, value);
              });
      for (String field : r.formulas())
        local.put(
            field, com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3225"));
      String code = r.values().getOrDefault("code", "");
      if (!code.isBlank()) {
        if (byCode.get(c.codeKey(code)).size() > 1)
          local.put(
              "code",
              com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3226")
                  + byCode.get(c.codeKey(code)));
        else if (c.existing.containsKey(c.codeKey(code)))
          local.put(
              "code", com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3227"));
      }
      Long dept =
          lookup(c, "ORG|" + r.values().getOrDefault("department", "").toLowerCase(Locale.ROOT));
      Long company = dept == null ? null : c.companies.get(dept);
      if (dept == null
          || !departmentAllowed(c, dept, c.create)
          || !departmentAllowed(c, dept, c.imports)) {
        local.put(
            "department",
            com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3228"));
        dept = null;
        company = null;
      }
      put(body, "departmentOrgId", dept);
      for (var spec :
          Map.of(
                  "category",
                  "categoryId",
                  "unit",
                  "unitId",
                  "useState",
                  "useStateId",
                  "source",
                  "sourceId",
                  "functionLocation",
                  "locationId")
              .entrySet()) {
        String key = spec.getKey(),
            type =
                Map.of(
                        "category",
                        "CATEGORY",
                        "unit",
                        "UNIT",
                        "useState",
                        "USE_STATUS",
                        "source",
                        "SOURCE",
                        "functionLocation",
                        "LOCATION")
                    .get(key);
        String raw = r.values().getOrDefault(key, "");
        boolean required = !key.equals("source");
        Long found =
            raw.isBlank()
                ? null
                : lookup(
                    c,
                    type
                        + "|"
                        + (key.equals("functionLocation")
                            ? Objects.toString(company, "") + "|"
                            : "")
                        + raw.toLowerCase(Locale.ROOT));
        if ((required || !raw.isBlank())
            && (found == null || key.equals("functionLocation") && company == null))
          local.put(
              key, com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3229"));
        put(body, spec.getValue(), found);
      }
      var ownerIds = json.createArrayNode();
      String ownerRaw = r.values().getOrDefault("owner", "");
      if (!ownerRaw.isBlank())
        for (String account : ownerRaw.split("；", -1)) {
          var found = c.accounts.getOrDefault(account.trim().toLowerCase(Locale.ROOT), List.of());
          if (account.isBlank() || found.size() != 1)
            local.put(
                "owner",
                com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3230"));
          else ownerIds.add(found.getFirst().toString());
        }
      // Deduplicate repeated account tokens without changing the existing multi-owner semantics.
      var unique = new LinkedHashSet<String>();
      ownerIds.forEach(n -> unique.add(n.asText()));
      ownerIds.removeAll();
      unique.forEach(ownerIds::add);
      body.set("ownerIds", ownerIds);
      for (String date :
          List.of(
              "purchaseDate",
              "acquiredAt",
              "inServiceDate",
              "warranty",
              "retirementDate",
              "calibrationDate",
              "calibrationValidUntil")) {
        String raw = r.values().getOrDefault(date, "");
        if (raw.isBlank()) body.putNull(date);
        else
          try {
            if (!raw.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException();
            LocalDate.parse(raw);
          } catch (Exception e) {
            local.put(
                date, com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3231"));
            body.putNull(date);
          }
      }
      String amount = r.values().getOrDefault("amount", "");
      if (amount.isBlank()) body.putNull("amount");
      else
        try {
          body.put("amount", new BigDecimal(amount));
        } catch (Exception e) {
          local.putIfAbsent(
              "amount",
              com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3232"));
          body.putNull("amount");
        }
      for (String key : List.of("specialEquipment", "metrologyEquipment", "depreciationEnabled"))
        if (body.path(key).asText().isBlank()) body.put(key, "否");
      if (!body.path("level").asText().isBlank()
          && !c.choices.get("level").contains(body.path("level").asText()))
        local.put(
            "level", com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3233"));
      var custom = json.createObjectNode();
      for (var entry : c.fields.entrySet()) {
        String field = entry.getKey(), key = "custom:" + field;
        String raw = r.values().getOrDefault(key, "");
        if (raw.isBlank()) continue;
        var f = entry.getValue();
        try {
          switch (f.path("type").asText()) {
            case "BOOLEAN" -> {
              if (!Set.of("是", "否").contains(raw))
                throw new com.windasoft.eam.common.LocalizedArgumentException(
                    "server.imports.message0022");
              custom.put(field, raw.equals("是"));
            }
            case "NUMBER" -> custom.put(field, new BigDecimal(raw));
            case "DATE" -> custom.put(field, LocalDate.parse(raw).toString());
            case "DATETIME" ->
                custom.put(field, LocalDateTime.parse(raw.replace(' ', 'T')).toString());
            case "CHOICE", "MULTI_CHOICE", "USER", "DEPARTMENT" -> {
              var selected = json.createArrayNode();
              for (String text :
                  (BusinessRules.multiple(f) ? raw.split("；", -1) : new String[] {raw})) {
                String ref =
                    c.referenceInputs
                        .getOrDefault(field, Map.of())
                        .get(text.trim().toLowerCase(Locale.ROOT));
                if (ref == null)
                  throw new com.windasoft.eam.common.LocalizedArgumentException(
                      "server.imports.message0023");
                selected.add(ref);
              }
              custom.set(field, BusinessRules.multiple(f) ? selected : selected.get(0));
            }
            default -> custom.put(field, raw);
          }
        } catch (Exception e) {
          local.put(
              key,
              e instanceof DateTimeParseException
                  ? com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3234")
                  : e instanceof NumberFormatException
                      ? com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3235")
                      : Objects.toString(
                          e.getMessage(),
                          com.windasoft.eam.common.MessageContext.text(
                              "output.import.diagnostic3236")));
        }
      }
      body.set("customFields", custom);
      body.put("customSchemaVersion", c.version);
      EquipmentForm form = json.convertValue(body, EquipmentForm.class);
      input
          .errors(form)
          .forEach(
              (k, v) ->
                  local.putIfAbsent(
                      switch (k) {
                        case "departmentOrgId" -> "department";
                        case "locationId" -> "functionLocation";
                        case "categoryId" -> "category";
                        case "unitId" -> "unit";
                        case "useStateId" -> "useState";
                        case "ownerIds" -> "owner";
                        default -> k;
                      },
                      v));
      var customErrors = new LinkedHashMap<String, String>();
      var normalized =
          values.prepareNew(
              "equipment",
              c.configuration,
              custom,
              Objects.toString(form.categoryId(), ""),
              c.referenceNames,
              customErrors);
      customErrors.forEach((k, v) -> local.putIfAbsent("custom:" + k, v));
      Equipment entity = null;
      if (local.isEmpty()) {
        entity = entity(form, c);
        local.putAll(input.entityErrors(entity));
        if (!allowed(c, entity, c.create) || !allowed(c, entity, c.imports))
          local.put(
              "department",
              com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3237"));
      }
      String parent = r.values().getOrDefault("parentCode", "");
      if (parent.length() > 32)
        local.put(
            "parentCode",
            com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3238"));
      for (var e : local.entrySet()) issues.add(issue(c, r, e.getKey(), e.getValue()));
      prepared.add(
          new PreparedEquipmentImport(
              r.row(),
              local.isEmpty() ? form : null,
              local.isEmpty() ? entity : null,
              parent,
              normalized,
              unique.stream()
                  .map(Long::valueOf)
                  .collect(
                      java.util.stream.Collectors.toMap(
                          id -> id, id -> c.users.get(id).get("display_name").toString()))));
    }
    var validByCode = new HashMap<String, PreparedEquipmentImport>();
    for (var p : prepared)
      if (p.entity() != null && !p.form().code().isBlank())
        validByCode.put(c.codeKey(p.form().code()), p);
    // A single parent per imported row. Existing parent chains cannot link to new IDs yet.
    var graph = new HashMap<Integer, Integer>();
    var inputRows =
        book.rows().stream()
            .collect(java.util.stream.Collectors.toMap(ImportWorkbook.InputRow::row, r -> r));
    for (var p : prepared) {
      if (p.parentCode().isBlank()) continue;
      String key = c.codeKey(p.parentCode());
      var parent = validByCode.get(key);
      var existing = c.existing.get(key);
      String error = null;
      if (p.entity() == null) continue;
      if (parent != null) {
        if (parent.row() == p.row())
          error = com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3239");
        else if (!Objects.equals(parent.entity().getCompanyOrgId(), p.entity().getCompanyOrgId()))
          error = com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3240");
        else if (!allowed(c, p.entity(), c.view)
            || !allowed(c, p.entity(), c.edit)
            || !allowed(c, parent.entity(), c.view)
            || !allowed(c, parent.entity(), c.edit))
          error = com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3241");
        else graph.put(p.row(), parent.row());
      } else if (existing == null
          || existing.getDeleted() != 0
          || !allowed(c, existing, c.view)
          || !allowed(c, existing, c.edit)
          || !allowed(c, p.entity(), c.view)
          || !allowed(c, p.entity(), c.edit))
        error = com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3242");
      else if (!Objects.equals(existing.getCompanyOrgId(), p.entity().getCompanyOrgId()))
        error = com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3240");
      if (error != null) issues.add(issue(c, inputRows.get(p.row()), "parentCode", error));
    }
    var cyclic = new HashSet<Integer>();
    var visited = new HashSet<Integer>();
    for (var start : graph.keySet()) {
      if (visited.contains(start)) continue;
      var path = new ArrayList<Integer>();
      var positions = new HashMap<Integer, Integer>();
      Integer next = start;
      while (next != null && !visited.contains(next)) {
        Integer position = positions.get(next);
        if (position != null) {
          cyclic.addAll(path.subList(position, path.size()));
          break;
        }
        positions.put(next, path.size());
        path.add(next);
        next = graph.get(next);
      }
      visited.addAll(path);
    }
    for (var r : book.rows())
      if (cyclic.contains(r.row()))
        issues.add(
            issue(
                c,
                r,
                "parentCode",
                com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3243")));
    return new Checked(prepared, issues, c);
  }

  private static void put(ObjectNode body, String key, Long value) {
    if (value == null) body.putNull(key);
    else body.put(key, value);
  }

  private static Long lookup(Context c, String key) {
    var found = c.lookups.getOrDefault(key, List.of());
    return found.size() == 1 ? found.getFirst() : null;
  }

  private static ImportIssue issue(
      Context c, ImportWorkbook.InputRow r, String field, String reason) {
    String label =
        c.columns.stream()
            .filter(x -> x.key().equals(field))
            .map(ImportColumn::label)
            .findFirst()
            .orElse(field);
    String suggestion =
        switch (field) {
          case "code" ->
              com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3244");
          case "owner" ->
              com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3245");
          case "department", "category", "functionLocation" ->
              com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3246");
          case "parentCode" ->
              com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3247");
          default ->
              field.startsWith("custom:")
                  ? com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3248")
                  : com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3249");
        };
    return new ImportIssue(
        r.row(), field, label, r.values().getOrDefault(field, ""), reason, suggestion);
  }

  private Equipment entity(EquipmentForm f, Context c) {
    var e = new Equipment();
    e.setTenantId(c.tenant);
    e.setName(f.name().trim());
    e.setEquipmentCode(f.code());
    e.setDepartmentOrgId(f.departmentOrgId());
    e.setCompanyOrgId(c.companies.get(f.departmentOrgId()));
    e.setCompany(c.orgs.get(e.getCompanyOrgId()).get("name").toString());
    e.setDepartment(c.orgs.get(f.departmentOrgId()).get("name").toString());
    e.setDepartmentPath(c.orgPaths.get(f.departmentOrgId()));
    e.setCategoryId(f.categoryId());
    e.setCategory(c.bases.get(f.categoryId()).get("name").toString());
    e.setUnitId(f.unitId());
    e.setUnitName(c.bases.get(f.unitId()).get("name").toString());
    e.setUseStatusId(f.useStateId());
    e.setUseStatus(c.bases.get(f.useStateId()).get("name").toString());
    e.setFunctionLocationId(f.locationId());
    e.setFunctionLocation(c.basePaths.get(f.locationId()));
    e.setSourceId(f.sourceId());
    e.setSource(f.sourceId() == null ? "" : c.bases.get(f.sourceId()).get("name").toString());
    e.setOwnerIds(f.ownerIds());
    e.setOwnerUserId(f.ownerIds().isEmpty() ? null : f.ownerIds().getFirst());
    e.setOwnerName(
        String.join(
            "、",
            f.ownerIds().stream()
                .map(id -> c.users.get(id).get("display_name").toString())
                .toList()));
    e.setEquipmentStatus(f.state());
    e.setModel(f.model());
    e.setSerialNo(f.serial());
    e.setBrand(f.brand());
    e.setAssetNo(f.assetNo());
    e.setTagCode(f.tagCode());
    e.setSupplier(f.supplier());
    e.setAcquiredAmount(f.amount());
    e.setAcquiredAt(f.acquiredAt());
    e.setPurchaseDate(f.purchaseDate());
    e.setInServiceDate(f.inServiceDate());
    e.setWarrantyUntil(f.warranty());
    e.setExpectedRetirementDate(f.retirementDate());
    e.setEquipmentLevel(f.level());
    e.setSpecialEquipment(f.specialEquipment());
    e.setMetrologyEquipment(f.metrologyEquipment());
    e.setDepreciationEnabled(f.depreciationEnabled());
    e.setCalibrationDate(f.calibrationDate());
    e.setCalibrationValidUntil(f.calibrationValidUntil());
    e.setInspectorName(f.inspectorName());
    e.setTechnicalParams(f.technicalParams());
    e.setDescription(f.remark());
    e.setRowVersion(0);
    e.setDeleted(0);
    e.setCreatedBy(c.actor);
    e.setUpdatedBy(c.actor);
    return e;
  }
}
