package com.windasoft.eam.service.experience;

import static com.windasoft.eam.common.experience.ExperienceSupport.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.experience.HomeOverviewRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomeOverviewService {
  private final HomeService home;
  private final WorkCenterService work;
  private final HomeOverviewRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;
  private final HomeAnalyticsService analytics;
  private static final List<String> METRICS = List.of("pending", "active", "today", "notification");
  private static final Set<String> SECTIONS =
      Set.of(
          "work",
          "anomalies",
          "shortcuts",
          "recent",
          "surplus",
          "stats",
          "load",
          "inventory",
          "created");

  public HomeOverviewService(
      HomeService home,
      WorkCenterService work,
      HomeOverviewRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      HomeAnalyticsService analytics) {
    this.home = home;
    this.work = work;
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
    this.analytics = analytics;
  }

  Set<String> available() {
    var values = new HashSet<String>((List<String>) ((Map<?, ?>) work.catalog()).get("data"));
    values.retainAll(SECTIONS);
    values.add("work");
    analytics.metrics().forEach(m -> values.add("analytics:" + m.id()));
    return values;
  }

  boolean visible(Map<String, Object> b, Set<String> available) {
    return available.contains(text(b, "type"))
        && (!text(b, "scope").equals("team") || permissions.allowed("workspace", "团队工作查看"));
  }

  Map<String, Object> defaults(Map<String, Object> selected, Set<String> available) {
    var source = (Map<String, Object>) selected.get("layout");
    if (source.get("overviewLayout") instanceof Map<?, ?> configured) {
      var value = new LinkedHashMap<>((Map<String, Object>) configured);
      value.put(
          "blocks", rows(value.get("blocks")).stream().filter(b -> visible(b, available)).toList());
      return value;
    }
    var result = new ArrayList<Map<String, Object>>();
    var seen = new HashSet<String>();
    for (var original : rows(source.get("blocks"))) {
      String type = text(original, "type");
      if (Set.of("todo", "today", "active").contains(type)) type = "work";
      if (!available.contains(type) || seen.contains(type)) continue;
      if (!type.equals("work")
          && text(original, "scope").equals("team")
          && !permissions.allowed("workspace", "团队工作查看")) continue;
      seen.add(type);
      var block = new LinkedHashMap<>(original);
      block.put("id", "overview-" + type);
      block.put("type", type);
      block.put("width", 12);
      block.put(
          "column",
          Set.of("anomalies", "shortcuts", "inventory", "surplus").contains(type)
              ? "side"
              : "main");
      if (type.equals("work")) {
        block.put("title", "我的工作");
        block.put("scope", "personal");
        block.put("module", "");
      }
      result.add(block);
    }
    if (result.isEmpty())
      result.add(
          new LinkedHashMap<>(
              Map.of(
                  "id",
                  "overview-work",
                  "type",
                  "work",
                  "title",
                  "我的工作",
                  "width",
                  12,
                  "column",
                  "main",
                  "limit",
                  5,
                  "scope",
                  "personal",
                  "module",
                  "")));
    return Map.of("metrics", METRICS, "blocks", result);
  }

  Map<String, Object> normalize(Map<String, Object> input, Set<String> available) {
    return OverviewLayoutSchema.normalize(
        input, available, permissions.allowed("workspace", "团队工作查看"), home::normalize);
  }

  @Transactional
  public Object get(Long requested) {
    home.access();
    Long template = requested;
    if (template == null) {
      var recent = repository.latest(auth.tenantId(), auth.userId());
      if (!recent.isEmpty()) {
        long candidate = id(recent.getFirst().get("template_id"));
        try {
          home.assigned(candidate);
          template = candidate;
        } catch (org.springframework.web.server.ResponseStatusException ignored) {
          /* Fall back to the current role template. */
        }
      }
    }
    var base = (Map<String, Object>) home.home(template);
    var selected = (Map<String, Object>) base.get("selected");
    long tid = id(selected.get("id"));
    var available = available();
    var defaults = defaults(selected, available);
    var stored = repository.load(auth.tenantId(), auth.userId(), tid);
    Map<String, Object> layout =
        stored.isEmpty() ? defaults : object(json, stored.getFirst().get("payload"));
    var output = new LinkedHashMap<String, Object>();
    output.put("templates", base.get("templates"));
    output.put(
        "selected",
        Map.of(
            "id",
            selected.get("id"),
            "name",
            selected.get("name"),
            "version",
            selected.get("version")));
    var compatible = new LinkedHashMap<>(layout);
    compatible.putIfAbsent("schemaVersion", 2);
    compatible.putIfAbsent("layoutMode", "work");
    compatible.putIfAbsent("period", Map.of("preset", "month"));
    compatible.putIfAbsent("companyIds", List.of());
    compatible.putIfAbsent("departmentIds", List.of());
    compatible.put(
        "blocks", rows(layout.get("blocks")).stream().filter(b -> visible(b, available)).toList());
    output.put("layout", compatible);
    output.put("defaultLayout", defaults);
    output.put("catalog", available.stream().sorted().toList());
    output.put("rowVersion", stored.isEmpty() ? -1 : stored.getFirst().get("row_version"));
    output.put("templateVersion", selected.get("version"));
    output.put(
        "updateAvailable",
        !stored.isEmpty()
            && id(stored.getFirst().get("template_version")) != id(selected.get("version")));
    return output;
  }

  @Transactional
  public Object save(Map<String, Object> body) {
    home.access();
    check(
        body.containsKey("templateId")
            && body.containsKey("templateVersion")
            && body.containsKey("rowVersion"),
        "缺少配置版本，请重新加载首页");
    long tid = id(body.get("templateId"));
    var selected = home.assigned(tid);
    long version = id(body.get("templateVersion"));
    check(version == id(selected.get("version")), "角色模板已更新，请重新加载后再保存");
    check(body.get("layout") instanceof Map<?, ?>, "首页布局无效");
    String payload = encode(json, normalize((Map<String, Object>) body.get("layout"), available()));
    check(payload.length() <= 32000, "首页配置过大");
    long rowVersion = id(body.get("rowVersion"));
    if (rowVersion == -1) {
      try {
        repository.insert(auth.tenantId(), auth.userId(), tid, version, payload);
      } catch (DuplicateKeyException duplicate) {
        conflict();
      }
    } else if (repository.update(auth.tenantId(), auth.userId(), tid, version, rowVersion, payload)
        != 1) conflict();
    return get(tid);
  }
}
