package com.windasoft.eam.service.experience;

import static com.windasoft.eam.common.experience.ExperienceSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.common.SuccessResponse;
import com.windasoft.eam.dto.experience.HomeContracts.LayoutForm;
import com.windasoft.eam.dto.experience.HomeContracts.PresetRole;
import com.windasoft.eam.dto.experience.HomeContracts.RoleChoice;
import com.windasoft.eam.dto.experience.HomeContracts.Selected;
import com.windasoft.eam.dto.experience.HomeContracts.State;
import com.windasoft.eam.dto.experience.HomeContracts.Summary;
import com.windasoft.eam.dto.experience.HomeContracts.TemplateForm;
import com.windasoft.eam.dto.experience.HomeContracts.TemplateView;
import com.windasoft.eam.dto.experience.HomeContracts.Version;
import com.windasoft.eam.entity.experience.HomeTemplate;
import com.windasoft.eam.entity.experience.HomeTemplateRole;
import com.windasoft.eam.entity.experience.HomeTemplateVersion;
import com.windasoft.eam.entity.experience.HomeTemplateVersionRole;
import com.windasoft.eam.repository.experience.HomeLayoutRepository;
import com.windasoft.eam.repository.experience.HomePagePreferenceRepository;
import com.windasoft.eam.repository.experience.HomePreferenceRepository;
import com.windasoft.eam.repository.experience.HomeTemplateRepository;
import com.windasoft.eam.repository.experience.HomeTemplateRoleRepository;
import com.windasoft.eam.repository.experience.HomeTemplateVersionRepository;
import com.windasoft.eam.repository.experience.HomeTemplateVersionRoleRepository;
import com.windasoft.eam.repository.system.RoleManagementRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HomeService {
  private final HomeTemplateRepository templates;
  private final HomeTemplateVersionRepository versions;
  private final HomeTemplateRoleRepository templateRoles;
  private final HomeTemplateVersionRoleRepository versionRoles;
  private final HomeLayoutRepository layouts;
  private final HomePreferenceRepository preferences;
  private final HomePagePreferenceRepository pagePreferences;
  private final RoleManagementRepository roles;
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
      HomeTemplateRepository templates,
      HomeTemplateVersionRepository versions,
      HomeTemplateRoleRepository templateRoles,
      HomeTemplateVersionRoleRepository versionRoles,
      HomeLayoutRepository layouts,
      HomePreferenceRepository preferences,
      HomePagePreferenceRepository pagePreferences,
      RoleManagementRepository roles,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions,
      HomeAnalyticsService analytics) {
    this.templates = templates;
    this.versions = versions;
    this.templateRoles = templateRoles;
    this.versionRoles = versionRoles;
    this.layouts = layouts;
    this.preferences = preferences;
    this.pagePreferences = pagePreferences;
    this.roles = roles;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
    this.analytics = analytics;
  }

  public void access() {
    if (!permissions.menuActions().getOrDefault("workspace", Set.of()).contains(PermissionAction.VIEW.code()))
      permissions.require("workspace", PermissionAction.VIEW);
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
      check(Set.of(4, 6, 12).contains(width) && (type.equals("stats") || width != 4), "区块宽度无效");
      int limit = (int) id(b.getOrDefault("limit", 5));
      check(Set.of(5, 10, 20).contains(limit), "展示数量无效");
      String scope = text(b, "scope");
      check(Set.of("", "personal", "team").contains(scope), "区块范围无效");
      if (scope.equals("team")) permissions.require("workspace", PermissionAction.VIEW_TEAM_WORK);
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

  /** 动态画布仅在校验边界转为字典，固定模板元数据不进入任意Map。 */
  Map<String, Object> canvas(JsonNode value) {
    if (value == null || !value.isObject()) throw new IllegalArgumentException("首页布局无效");
    return json.convertValue(value, new TypeReference<Map<String, Object>>() {});
  }

  private JsonNode node(String value) {
    return json.valueToTree(object(json, value));
  }

  public JsonNode pagePreferences(String key) {
    access();
    check(Set.of("anomalies", "repair-plans", "material-custody").contains(key), "不支持的页面偏好");
    var row = pagePreferences.find(auth.tenantId(), auth.userId(), key);
    return row == null ? json.createObjectNode() : node(row.getPreferences());
  }

  @Transactional
  public SuccessResponse pagePreferences(String key, JsonNode values) {
    pagePreferences(key);
    var data = canvas(values);
    check(
        data.keySet().stream()
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
    String encoded = encode(json, data);
    check(encoded.length() <= 32000, "页面偏好过大");
    pagePreferences.save(auth.tenantId(), auth.userId(), key, encoded);
    return new SuccessResponse(true);
  }

  /** 预置创建、版本和角色快照同属调用方事务；唯一预置键防并发重复初始化。 */
  private void seed() {
    var presetRoles =
        roles
            .homePresetRoles(
                auth.tenantId(),
                List.of("SYS_ADMIN", "EQUIP_MANAGER", "DEPT_MANAGER", "__unassigned__"))
            .stream()
            .collect(
                Collectors.groupingBy(
                    PresetRole::code, Collectors.mapping(PresetRole::id, Collectors.toList())));
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
    for (String key :
        List.of("reporter", "executor", "supervisor", "warehouse", "manager", "system")) {
      String code =
          key.equals("system")
              ? "SYS_ADMIN"
              : key.equals("supervisor")
                  ? "EQUIP_MANAGER"
                  : key.equals("manager") ? "DEPT_MANAGER" : "__unassigned__";
      var ids = presetRoles.getOrDefault(code, List.of());
      var row = preset(names.get(key), key, defaults(key), key.equals("system") ? 10 : 100);
      if (templates.insertPreset(row)) {
        templateRoles.replace(auth.tenantId(), row.getId(), ids);
        var published = new LinkedHashMap<String, Object>(defaults(key));
        published.put("templateName", row.getName());
        insertVersion(row, 1, encode(json, published), ids);
        templates.publish(auth.tenantId(), row.getId(), 1, true);
      }
    }
    var metricTypes = new HashSet<String>();
    AnalyticsCatalog.ALL.forEach(m -> metricTypes.add("analytics:" + m.id()));
    var analysis = OverviewLayoutSchema.presets(true, metricTypes);
    for (int i = 0; i < analysis.size(); i++) {
      var configured = analysis.get(i);
      var row =
          preset(
              Objects.toString(configured.get("name")),
              "analytics-" + i,
              defaults(i == 2 ? "warehouse" : "manager"),
              100);
      if (templates.insertPreset(row)) {
        templateRoles.replace(auth.tenantId(), row.getId(), List.of());
        templates.overview(auth.tenantId(), row.getId(), encode(json, configured.get("layout")));
      }
    }
  }

  private HomeTemplate preset(String name, String key, Map<String, Object> layout, int priority) {
    var row = new HomeTemplate();
    row.setId(IdWorker.getId());
    row.setTenantId(auth.tenantId());
    row.setName(name);
    row.setPresetKey(key);
    row.setDraft(encode(json, layout));
    row.setPriority(priority);
    row.setCreatedBy(auth.userId());
    return row;
  }

  private void insertVersion(HomeTemplate template, int version, String payload, List<Long> ids) {
    var row = new HomeTemplateVersion();
    row.setTenantId(auth.tenantId());
    row.setTemplateId(template.getId());
    row.setVersion(version);
    row.setPayload(payload);
    row.setPriority(template.getPriority());
    row.setPublishedBy(auth.userId());
    versions.insert(row);
    versionRoles.insert(auth.tenantId(), template.getId(), version, ids);
  }

  @Transactional
  public List<TemplateView> templates() {
    permissions.require("homeTemplate", PermissionAction.VIEW);
    seed();
    var rows = templates.list(auth.tenantId());
    var related =
        templateRoles
            .find(auth.tenantId(), rows.stream().map(HomeTemplate::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(HomeTemplateRole::getTemplateId));
    return rows.stream()
        .map(row -> templateView(row, related.getOrDefault(row.getId(), List.of())))
        .toList();
  }

  /** 保留旧接口的蛇形字段和JSON文本格式；角色JSON仅为兼容响应派生，不作为存储权威。 */
  private TemplateView templateView(HomeTemplate row, List<HomeTemplateRole> links) {
    var ids = links.stream().map(r -> String.valueOf(r.getRoleId())).toList();
    String legacy = ids.isEmpty() ? "[]" : "[\"" + String.join("\", \"", ids) + "\"]";
    return new TemplateView(
        String.valueOf(row.getId()),
        String.valueOf(row.getTenantId()),
        row.getName(),
        row.getPresetKey(),
        node(row.getDraft()),
        row.getPriority(),
        row.getPublishedVersion(),
        row.getEnabled(),
        row.getRowVersion(),
        String.valueOf(row.getCreatedBy()),
        row.getCreatedAt(),
        row.getUpdatedAt(),
        row.getOverviewDraft(),
        legacy,
        ids,
        row.getOverviewDraft() == null ? null : node(row.getOverviewDraft()));
  }

  private HomeTemplate raw(long id, boolean lock) {
    var row = templates.find(auth.tenantId(), id, lock);
    check(row != null, "首页模板不存在");
    return row;
  }

  private TemplateView view(long id) {
    return templateView(raw(id, false), templateRoles.find(auth.tenantId(), List.of(id)));
  }

  /** 模板主体、角色和画布更新同一事务；编辑必须显式匹配锁定版本，不能把缺失版本当0。 */
  @Transactional
  public TemplateView save(Long id, TemplateForm form) {
    permissions.require("homeTemplate", id == null ? PermissionAction.CREATE : PermissionAction.EDIT);
    var layout = normalize(canvas(form.getDraft()));
    String name = Objects.toString(form.getName(), "").trim();
    if (name.isBlank() || name.length() > 100)
      throw new LocalizedArgumentException("server.experience.message0005", "name", 100);
    var ids = form.getRoleIds() == null ? List.<Long>of() : form.getRoleIds();
    check(ids.size() <= 100, "角色数量过多");
    check(roles.homeActiveRoleIds(auth.tenantId(), ids).containsAll(ids), "角色不存在或停用");
    int priority = form.getPriority();
    check(priority >= 0 && priority <= 10000, "优先级应为0到10000");
    HomeTemplate row;
    if (id == null) {
      row = preset(name, null, layout, priority);
      templates.insert(row);
      id = row.getId();
    } else {
      row = raw(id, true);
      if (form.getRowVersion() == null || !row.getRowVersion().equals(form.getRowVersion()))
        conflict();
      row.setName(name);
      row.setDraft(encode(json, layout));
      row.setPriority(priority);
      templates.update(row);
    }
    templateRoles.replace(auth.tenantId(), id, ids);
    if (form.getOverviewDraft() != null && form.getOverviewDraft().isObject())
      templates.overview(
          auth.tenantId(), id, encode(json, normalizeOverview(canvas(form.getOverviewDraft()))));
    return view(id);
  }

  public Map<String, Object> normalizeOverview(Map<String, Object> value) {
    var available = new HashSet<>(OverviewLayoutSchema.LEGACY);
    if (!permissions.allowed("workspace", PermissionAction.VIEW_TEAM_WORK)) available.remove("load");
    analytics.metrics().forEach(m -> available.add("analytics:" + m.id()));
    return OverviewLayoutSchema.normalize(
        value, available, permissions.allowed("workspace", PermissionAction.VIEW_TEAM_WORK), this::normalize);
  }

  /** 发布版本与其角色快照冻结，后续草稿变化不能影响已经发布的访问范围。 */
  @Transactional
  public TemplateView publish(long id, Version form) {
    permissions.require("homeTemplate", PermissionAction.PUBLISH);
    var row = raw(id, true);
    if (!Objects.equals(row.getRowVersion(), form.rowVersion())) conflict();
    var links = templateRoles.find(auth.tenantId(), List.of(id));
    var payload = new LinkedHashMap<String, Object>(normalize(object(json, row.getDraft())));
    payload.put("templateName", row.getName());
    if (row.getOverviewDraft() != null)
      payload.put("overviewLayout", normalizeOverview(object(json, row.getOverviewDraft())));
    int next = Math.addExact(row.getPublishedVersion(), 1);
    insertVersion(
        row, next, encode(json, payload), links.stream().map(HomeTemplateRole::getRoleId).toList());
    templates.publish(auth.tenantId(), id, next, false);
    row.setPublishedVersion(next);
    row.setEnabled(1);
    row.setRowVersion(row.getRowVersion() + 1);
    return templateView(row, links);
  }

  @Transactional
  public SuccessResponse disable(long id, Version form) {
    permissions.require("homeTemplate", PermissionAction.DISABLE);
    var row = raw(id, true);
    if (!Objects.equals(row.getRowVersion(), form.rowVersion())) conflict();
    templates.disable(auth.tenantId(), id);
    return new SuccessResponse(true);
  }

  /** 只按当前用户角色和当前发布版本取模板；工作台团队权限不能通过模板布局绕过。 */
  List<Selected> assigned() {
    var member = new HashSet<>(roles.homeUserRoleIds(auth.tenantId(), auth.userId()));
    var links =
        versionRoles.published(auth.tenantId()).stream()
            .collect(Collectors.groupingBy(HomeTemplateVersionRole::getTemplateId));
    boolean team = permissions.allowed("workspace", PermissionAction.VIEW_TEAM_WORK);
    var out = new ArrayList<Selected>();
    for (var row : templates.published(auth.tenantId())) {
      if (links.getOrDefault(row.id(), List.of()).stream()
          .noneMatch(r -> member.contains(r.getRoleId()))) continue;
      var layout = node(row.payload());
      if (!team
          && rows(canvas(layout).get("blocks")).stream()
              .noneMatch(b -> !text(b, "scope").equals("team"))) continue;
      String name = layout.has("templateName") ? layout.path("templateName").asText() : row.name();
      out.add(
          new Selected(
              String.valueOf(row.id()),
              name,
              row.publishedVersion(),
              row.priority(),
              row.publishedVersion(),
              layout));
    }
    return out;
  }

  Selected assigned(long id) {
    if (id == 0)
      return new Selected("0", "个人首页", null, null, 1, json.valueToTree(defaults("executor")));
    return assigned().stream()
        .filter(row -> Long.parseLong(row.id()) == id)
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "首页模板不再适用，请选择其他首页"));
  }

  @Transactional
  public State home(Long requested) {
    access();
    seed();
    var choices = new ArrayList<>(assigned());
    choices.add(assigned(0));
    long id = requested == null ? -1 : requested;
    var preferred = preferences.find(auth.tenantId(), auth.userId());
    if (id < 0 && preferred != null && preferred.getDefaultTemplateId() != null)
      id = preferred.getDefaultTemplateId();
    final long wanted = id;
    var selected =
        choices.stream()
            .filter(row -> Long.parseLong(row.id()) == wanted)
            .findFirst()
            .orElse(choices.getFirst());
    id = Long.parseLong(selected.id());
    var stored = layouts.find(auth.tenantId(), auth.userId(), id);
    return new State(
        choices.stream().map(row -> new Summary(row.id(), row.name(), row.version())).toList(),
        selected,
        stored == null ? selected.layout() : node(stored.getPayload()),
        stored == null ? -1 : stored.getRowVersion(),
        stored == null ? selected.version() : stored.getTemplateVersion(),
        stored != null && stored.getTemplateVersion() != selected.version(),
        permissions.menuActions());
  }

  /** 个人布局与默认模板各自保存，按完整租户/用户/模板复合键和版本控制，不覆盖其他用户。 */
  @Transactional
  public State layout(LayoutForm form) {
    access();
    long id = form.templateId();
    var selected = assigned(id);
    check(
        form.templateVersion() == selected.version()
            || versions.exists(auth.tenantId(), id, form.templateVersion()),
        "模板版本不存在");
    String value = encode(json, normalize(canvas(form.layout())));
    if (form.rowVersion() == -1) {
      try {
        layouts.insert(auth.tenantId(), auth.userId(), id, form.templateVersion(), value);
      } catch (DuplicateKeyException duplicate) {
        conflict();
      }
    } else if (layouts.update(
            auth.tenantId(), auth.userId(), id, form.templateVersion(), form.rowVersion(), value)
        != 1) conflict();
    return home(id);
  }

  @Transactional
  public State reset(long id) {
    access();
    assigned(id);
    layouts.delete(auth.tenantId(), auth.userId(), id);
    return home(id);
  }

  public SuccessResponse defaultHome(long id) {
    access();
    assigned(id);
    preferences.saveDefault(auth.tenantId(), auth.userId(), id);
    return new SuccessResponse(true);
  }

  public List<RoleChoice> roleChoices() {
    permissions.require("homeTemplate", PermissionAction.VIEW);
    return roles.homeRoleChoices(auth.tenantId()).stream()
        .map(row -> new RoleChoice(String.valueOf(row.getId()), row.getName()))
        .toList();
  }
}
