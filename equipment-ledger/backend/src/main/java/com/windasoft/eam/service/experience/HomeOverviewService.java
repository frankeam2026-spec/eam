package com.windasoft.eam.service.experience;

import static com.windasoft.eam.common.experience.ExperienceSupport.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.experience.HomeContracts.LayoutForm;
import com.windasoft.eam.dto.experience.HomeContracts.Overview;
import com.windasoft.eam.dto.experience.HomeContracts.Selected;
import com.windasoft.eam.dto.experience.HomeContracts.Summary;
import com.windasoft.eam.repository.experience.HomeOverviewRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    var values = new HashSet<String>(work.catalog());
    values.retainAll(SECTIONS);
    values.add("work");
    analytics.metrics().forEach(m -> values.add("analytics:" + m.id()));
    return values;
  }

  boolean visible(Map<String, Object> b, Set<String> available) {
    return available.contains(text(b, "type"))
        && (!text(b, "scope").equals("team") || permissions.allowed("workspace", PermissionAction.VIEW_TEAM_WORK));
  }

  Map<String, Object> defaults(Selected selected, Set<String> available) {
    var source = home.canvas(selected.layout());
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
          && !permissions.allowed("workspace", PermissionAction.VIEW_TEAM_WORK)) continue;
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
        input, available, permissions.allowed("workspace", PermissionAction.VIEW_TEAM_WORK), home::normalize);
  }

  /** 选择模板仍复用首页授权，个人总览以完整复合键读取，不借管理模板权限扩大范围。 */
  @Transactional
  public Overview get(Long requested) {
    home.access();
    Long template = requested;
    if (template == null) {
      var recent = repository.latest(auth.tenantId(), auth.userId());
      if (recent != null) {
        long candidate = recent.getTemplateId();
        try {
          home.assigned(candidate);
          template = candidate;
        } catch (ResponseStatusException denied) {
          if (denied.getStatusCode().value() != 403) throw denied;
        }
      }
    }
    var base = home.home(template);
    var selected = base.selected();
    long id = Long.parseLong(selected.id());
    var available = available();
    var defaults = defaults(selected, available);
    var stored = repository.find(auth.tenantId(), auth.userId(), id);
    var layout = stored == null ? defaults : object(json, stored.getPayload());
    var compatible = new LinkedHashMap<>(layout);
    compatible.putIfAbsent("schemaVersion", 2);
    compatible.putIfAbsent("layoutMode", "work");
    compatible.putIfAbsent("period", Map.of("preset", "month"));
    compatible.putIfAbsent("companyIds", List.of());
    compatible.putIfAbsent("departmentIds", List.of());
    compatible.put(
        "blocks", rows(layout.get("blocks")).stream().filter(b -> visible(b, available)).toList());
    return new Overview(
        base.templates(),
        new Summary(selected.id(), selected.name(), selected.version()),
        json.valueToTree(compatible),
        json.valueToTree(defaults),
        available.stream().sorted().toList(),
        stored == null ? -1 : stored.getRowVersion(),
        selected.version(),
        stored != null && stored.getTemplateVersion() != selected.version());
  }

  /** 模板版本不匹配不能覆盖个人布局，唯一键与原版本条件更新共同防止重复保存。 */
  @Transactional
  public Overview save(LayoutForm form) {
    home.access();
    long id = form.templateId();
    var selected = home.assigned(id);
    int version = form.templateVersion();
    check(version == selected.version(), "角色模板已更新，请重新加载后再保存");
    String payload = encode(json, normalize(home.canvas(form.layout()), available()));
    check(payload.length() <= 32000, "首页配置过大");
    if (form.rowVersion() == -1) {
      try {
        repository.insert(auth.tenantId(), auth.userId(), id, version, payload);
      } catch (DuplicateKeyException duplicate) {
        conflict();
      }
    } else if (repository.update(
            auth.tenantId(), auth.userId(), id, version, form.rowVersion(), payload)
        != 1) conflict();
    return get(id);
  }
}
