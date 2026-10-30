package com.windasoft.eam.service.experience;

import static com.windasoft.eam.common.experience.ExperienceSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.experience.HomeRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomeService {
  final HomeRepository repository;
  final ObjectMapper json;
  final AuthContext auth;
  final PermissionService permissions;
  final HomeAnalyticsService analytics;
  static final Set<String> TYPES =
      Set.of(
          "todo",
          "today",
          "active",
          "created",
          "anomalies",
          "load",
          "inventory",
          "surplus",
          "shortcuts",
          "recent",
          "stats");

  public HomeService(
      HomeRepository repository,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions,
      HomeAnalyticsService analytics) {
    this.repository = repository;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
    this.analytics = analytics;
  }

  public void access() {
    if (!permissions.menuActions().getOrDefault("workspace", Set.of()).contains("查看"))
      permissions.require("workspace", "查看");
  }

  public Map<String, Object> normalize(Map<String, Object> payload) {
    var blocks = rows(payload.get("blocks"));
    check(blocks.size() <= 20, "首页最多20个区块");
    var ids = new HashSet<String>();
    var out = new ArrayList<Map<String, Object>>();
    for (var b : blocks) {
      var key = required(b, "id", 64);
      check(key.matches("[a-zA-Z0-9_-]+") && ids.add(key), "区块标识无效或重复");
      var type = required(b, "type", 30);
      check(TYPES.contains(type), "未知首页区块");
      int width = (int) id(b.getOrDefault("width", 6));
      check(
          Set.of(4, 6, 12).contains(width) && (type.equals("stats") || width != 4),
          "区块宽度无效");
      int limit = (int) id(b.getOrDefault("limit", 5));
      check(Set.of(5, 10, 20).contains(limit), "展示数量无效");
      String scope = text(b, "scope");
      check(Set.of("", "personal", "team").contains(scope), "区块范围无效");
      if (scope.equals("team")) permissions.require("workspace", "团队工作查看");
      var value = new LinkedHashMap<String, Object>();
      value.put("id", key);
      value.put("type", type);
      value.put("title", required(b, "title", 80));
      value.put("width", width);
      value.put("limit", limit);
      value.put("scope", scope.equals("team") ? "team" : "personal");
      String module = text(b, "module");
      check(
          Set.of("", "repair", "maintenance", "inspection", "inventory").contains(module),
          "业务模块无效");
      value.put("module", module);
      for (String field : List.of("status", "companyId", "departmentId")) {
        String f = text(b, field);
        check(f.length() <= 100, "条件过长");
        if (field.endsWith("Id") && !f.isBlank()) id(f);
        value.put(field, f);
      }
      out.add(value);
    }
    return Map.of("blocks", out);
  }

  public static Map<String, Object> defaults(String purpose) {
    var types =
        switch (purpose) {
          case "reporter" -> List.of("created", "todo", "anomalies", "shortcuts");
          case "supervisor" -> List.of("stats", "todo", "load", "anomalies");
          case "warehouse" -> List.of("inventory", "todo", "surplus", "shortcuts");
          case "manager" -> List.of("stats", "anomalies", "load", "active");
          case "system" -> List.of("shortcuts", "recent", "todo");
          default -> List.of("todo", "today", "active", "surplus", "shortcuts", "recent");
        };
    var labels =
        Map.ofEntries(
            Map.entry("todo", "待我处理"),
            Map.entry("today", "今日任务"),
            Map.entry("active", "正在执行"),
            Map.entry("created", "我发起的事项"),
            Map.entry("anomalies", "设备异常"),
            Map.entry("load", "人员任务负荷"),
            Map.entry("inventory", "库存事项"),
            Map.entry("surplus", "我的余料"),
            Map.entry("shortcuts", "快捷入口"),
            Map.entry("recent", "最近访问"),
            Map.entry("stats", "工作概况"));
    return Map.of(
        "blocks",
        types.stream()
            .map(
                t ->
                    Map.of(
                        "id",
                        t,
                        "type",
                        t,
                        "title",
                        labels.get(t),
                        "width",
                        6,
                        "limit",
                        5,
                        "scope",
                        purpose.equals("manager") || purpose.equals("supervisor")
                            ? "team"
                            : "personal",
                        "module",
                        ""))
            .toList());
  }

  public Object pagePreferences(String key) {
    access();
    check(Set.of("anomalies", "repair-plans", "material-custody").contains(key), "不支持的页面偏好");
    var rows = repository.loadPagePreference(auth.tenantId(), auth.userId(), key);
    return Map.of(
        "data", rows.isEmpty() ? Map.of() : object(json, rows.getFirst().get("preferences")));
  }

  @Transactional
  public Object pagePreferences(String key, Map<String, Object> values) {
    pagePreferences(key);
    check(
        values.keySet().stream()
            .allMatch(
                Set.of(
                        "query",
                        "conditions",
                        "columnFilters",
                        "hiddenColumns",
                        "page",
                        "size",
                        "personal")
                    ::contains),
        "页面偏好字段无效");
    String encoded = encode(json, values);
    check(encoded.length() <= 32000, "页面偏好过大");
    repository.savePagePreference(auth.tenantId(), auth.userId(), key, encoded);
    return Map.of("success", true);
  }

  // 初始化是入口事务的一部分，内部调用不依赖事务代理。
  private void seed() {
    var names =
        Map.of(
            "reporter",
            "设备使用与报修",
            "executor",
            "运维执行",
            "supervisor",
            "设备主管",
            "warehouse",
            "仓库工作",
            "manager",
            "公司与集团管理",
            "system",
            "系统维护");
    for (var key :
        List.of("reporter", "executor", "supervisor", "warehouse", "manager", "system")) {
      long tid = IdWorker.getId();
      var roleIds =
          repository
              .findPresetRole(
                  auth.tenantId(),
                  key.equals("system")
                      ? "SYS_ADMIN"
                      : key.equals("supervisor")
                          ? "EQUIP_MANAGER"
                          : key.equals("manager") ? "DEPT_MANAGER" : "__unassigned__")
              .stream()
              .map(r -> r.get("id"))
              .toList();
      int inserted =
          repository.insertPreset(
              tid,
              auth.tenantId(),
              names.get(key),
              encode(json, defaults(key)),
              encode(json, roleIds),
              key.equals("system") ? 10 : 100,
              auth.userId(),
              key);
      if (inserted == 1) {
        var published = new LinkedHashMap<String, Object>(defaults(key));
        published.put("templateName", names.get(key));
        repository.insertPresetVersion(
            auth.tenantId(),
            tid,
            encode(json, published),
            encode(json, roleIds),
            key.equals("system") ? 10 : 100,
            auth.userId());
        repository.publishPreset(auth.tenantId(), tid);
      }
    }
    var metricTypes = new HashSet<String>();
    AnalyticsCatalog.ALL.forEach(m -> metricTypes.add("analytics:" + m.id()));
    var analysisPresets = OverviewLayoutSchema.presets(true, metricTypes);
    for (int i = 0; i < analysisPresets.size(); i++) {
      var preset = analysisPresets.get(i);
      long templateId = IdWorker.getId();
      if (repository.insertPreset(
              templateId,
              auth.tenantId(),
              preset.get("name"),
              encode(json, defaults(i == 2 ? "warehouse" : "manager")),
              "[]",
              100,
              auth.userId(),
              "analytics-" + i)
          == 1)
        repository.overviewDraft(auth.tenantId(), templateId, encode(json, preset.get("layout")));
    }
  }

  @Transactional
  public List<Map<String, Object>> templates() {
    permissions.require("homeTemplate", "查看");
    seed();
    return repository.listTemplates(auth.tenantId()).stream().map(this::templateView).toList();
  }

  Map<String, Object> templateView(Map<String, Object> r) {
    var v = view(r);
    v.put("draft", object(json, r.get("draft")));
    v.put("roleIds", decode(json, r.get("role_ids")));
    v.put(
        "overviewDraft",
        r.get("overview_draft") == null ? null : object(json, r.get("overview_draft")));
    return v;
  }

  Map<String, Object> raw(long id, boolean lock) {
    var rows = repository.loadTemplate(auth.tenantId(), id, lock);
    check(rows.size() == 1, "首页模板不存在");
    return rows.getFirst();
  }

  @Transactional
  public Object save(Long tid, Map<String, Object> body) {
    permissions.require("homeTemplate", tid == null ? "新增" : "编辑");
    var payload = normalize((Map<String, Object>) body.getOrDefault("draft", Map.of()));
    String name = required(body, "name", 100);
    var ids = body.get("roleIds") instanceof List<?> l ? l : List.of();
    check(ids.size() <= 100, "角色数量过多");
    for (var role : ids)
      check(repository.countActiveRole(auth.tenantId(), id(role)) == 1, "角色不存在或停用");
    int priority = (int) id(body.getOrDefault("priority", 100));
    check(priority >= 0 && priority <= 10000, "优先级应为0到10000");
    if (tid == null) {
      tid = IdWorker.getId();
      repository.insertTemplate(
          tid,
          auth.tenantId(),
          name,
          encode(json, payload),
          encode(json, ids),
          priority,
          auth.userId());
    } else {
      var old = raw(tid, true);
      if (id(old.get("row_version")) != id(body.get("rowVersion"))) conflict();
      repository.updateTemplate(
          name, encode(json, payload), encode(json, ids), priority, auth.tenantId(), tid);
    }
    if (body.get("overviewDraft") instanceof Map<?, ?> overview)
      repository.overviewDraft(
          auth.tenantId(), tid, encode(json, normalizeOverview((Map<String, Object>) overview)));
    return templateView(raw(tid, false));
  }

  public Map<String, Object> normalizeOverview(Map<String, Object> value) {
    var available = new HashSet<>(OverviewLayoutSchema.LEGACY);
    if (!permissions.allowed("workspace", "团队工作查看")) available.remove("load");
    analytics.metrics().forEach(m -> available.add("analytics:" + m.id()));
    return OverviewLayoutSchema.normalize(
        value, available, permissions.allowed("workspace", "团队工作查看"), this::normalize);
  }

  @Transactional
  public Object publish(long tid, Map<String, Object> body) {
    permissions.require("homeTemplate", "发布");
    var r = raw(tid, true);
    if (id(r.get("row_version")) != id(body.get("rowVersion"))) conflict();
    var published = new LinkedHashMap<String, Object>(normalize(object(json, r.get("draft"))));
    published.put("templateName", r.get("name"));
    if (r.get("overview_draft") != null)
      published.put("overviewLayout", normalizeOverview(object(json, r.get("overview_draft"))));
    int next = (int) id(r.get("published_version")) + 1;
    repository.insertVersion(
        auth.tenantId(),
        tid,
        next,
        encode(json, published),
        r.get("role_ids"),
        r.get("priority"),
        auth.userId());
    repository.publishVersion(next, auth.tenantId(), tid);
    return templateView(raw(tid, false));
  }

  @Transactional
  public Object disable(long tid, Map<String, Object> b) {
    permissions.require("homeTemplate", "停用");
    var r = raw(tid, true);
    if (id(r.get("row_version")) != id(b.get("rowVersion"))) conflict();
    repository.disableTemplate(auth.tenantId(), tid);
    return Map.of("success", true);
  }

  List<Map<String, Object>> assigned() {
    var roles =
        repository.listUserRoles(auth.tenantId(), auth.userId()).stream()
            .map(r -> r.get("id").toString())
            .collect(java.util.stream.Collectors.toSet());
    return repository.listPublishedTemplates(auth.tenantId()).stream()
        .filter(
            r -> {
              Object x = decode(json, r.get("role_ids"));
              return x instanceof List<?> l
                  && l.stream().anyMatch(v -> roles.contains(v.toString()));
            })
        .map(
            r -> {
              var v = view(r);
              v.put("version", r.get("published_version"));
              var payload = object(json, r.get("payload"));
              v.put("layout", payload);
              if (payload.containsKey("templateName")) v.put("name", payload.get("templateName"));
              v.remove("payload");
              v.remove("role_ids");
              return v;
            })
        .filter(
            v ->
                permissions.allowed("workspace", "团队工作查看")
                    || rows(((Map<String, Object>) v.get("layout")).get("blocks")).stream()
                        .anyMatch(b -> !text(b, "scope").equals("team")))
        .toList();
  }

  Map<String, Object> assigned(long tid) {
    if (tid == 0)
      return new LinkedHashMap<>(
          Map.of("id", "0", "name", "个人首页", "version", 1, "layout", defaults("executor")));
    return assigned().stream()
        .filter(r -> id(r.get("id")) == tid)
        .findFirst()
        .orElseThrow(
            () ->
                new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "首页模板不再适用，请选择其他首页"));
  }

  @Transactional
  public Object home(Long requested) {
    access();
    seed();
    var choices = new ArrayList<>(assigned());
    choices.add(assigned(0));
    long tid = requested == null ? -1 : requested;
    var pref = repository.loadDefaultTemplate(auth.tenantId(), auth.userId());
    if (tid < 0 && !pref.isEmpty() && pref.getFirst().get("default_template_id") != null)
      tid = id(pref.getFirst().get("default_template_id"));
    final long wanted = tid;
    var selected =
        choices.stream()
            .filter(r -> id(r.get("id")) == wanted)
            .findFirst()
            .orElse(choices.getFirst());
    tid = id(selected.get("id"));
    var personal = repository.loadLayout(auth.tenantId(), auth.userId(), tid);
    var out = new LinkedHashMap<String, Object>();
    out.put(
        "templates",
        choices.stream()
            .map(c -> Map.of("id", c.get("id"), "name", c.get("name"), "version", c.get("version")))
            .toList());
    out.put("selected", selected);
    out.put(
        "layout",
        personal.isEmpty()
            ? selected.get("layout")
            : object(json, personal.getFirst().get("payload")));
    out.put("rowVersion", personal.isEmpty() ? -1 : personal.getFirst().get("row_version"));
    out.put(
        "templateVersion",
        personal.isEmpty() ? selected.get("version") : personal.getFirst().get("template_version"));
    out.put(
        "updateAvailable",
        !personal.isEmpty()
            && id(personal.getFirst().get("template_version")) != id(selected.get("version")));
    out.put("permissions", permissions.menuActions());
    return out;
  }

  @Transactional
  public Object layout(Map<String, Object> b) {
    access();
    long tid = id(b.get("templateId"));
    var selected = assigned(tid);
    check(
        id(b.get("templateVersion")) == id(selected.get("version"))
            || repository.countTemplateVersion(auth.tenantId(), tid, id(b.get("templateVersion")))
                == 1,
        "模板版本不存在");
    var normalized = normalize((Map<String, Object>) b.get("layout"));
    int version = (int) id(b.get("rowVersion"));
    if (version == -1) {
      try {
        repository.insertLayout(
            auth.tenantId(),
            auth.userId(),
            tid,
            id(b.get("templateVersion")),
            encode(json, normalized));
      } catch (org.springframework.dao.DuplicateKeyException e) {
        conflict();
      }
    } else {
      if (repository.updateLayout(
              id(b.get("templateVersion")),
              encode(json, normalized),
              auth.tenantId(),
              auth.userId(),
              tid,
              version)
          != 1) conflict();
    }
    return home(tid);
  }

  @Transactional
  public Object reset(long tid) {
    access();
    assigned(tid);
    repository.resetLayout(auth.tenantId(), auth.userId(), tid);
    return home(tid);
  }

  public Object defaultHome(long tid) {
    access();
    assigned(tid);
    repository.saveDefaultTemplate(auth.tenantId(), auth.userId(), tid);
    return Map.of("success", true);
  }

  public Object roleChoices() {
    permissions.require("homeTemplate", "查看");
    return Map.of("data", repository.listRoleChoices(auth.tenantId()));
  }
}
