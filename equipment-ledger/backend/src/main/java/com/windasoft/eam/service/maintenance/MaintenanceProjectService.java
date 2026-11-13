package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.deny;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.number;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.rows;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.text;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.service.basedata.BaseDataReferenceService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaintenanceProjectService {
  private final MaintenanceRepository repository;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;
  private static final Set<String> KINDS = Set.of("types", "standards", "plans");

  public MaintenanceProjectService(
      MaintenanceRepository repository,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions) {
    this.repository = repository;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
  }

  Map<String, Object> raw(String kind, long tenant, long id, boolean lock) {
    require(KINDS.contains(kind), MessageContext.text("server.system.message0045"));
    var a = repository.loadDefinition(tenant, kind, id, lock);
    if (a.isEmpty())
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.inspection.message0013");
    return a.getFirst();
  }

  boolean visible(Map<String, Object> row, String action) {
    try {
      var scope = permissions.maintenance(text(row, "kind"), action);
      return scope.matches(
          null,
          row.get("department_id") == null ? null : number(row.get("department_id")),
          number(row.get("creator_id")),
          auth.userId());
    } catch (ResponseStatusException e) {
      return false;
    }
  }

  /** 每次解析按标准 ID 复用读取与项目索引，避免同一标准每个项目重复查询。 缓存只存在于本次调用；项目仍复制为原有历史快照结构，保留选择顺序、去重和旧项目 ID。 */
  List<Map<String, Object>> planProjects(
      Map<String, Object> detail, long tenant, Map<String, Object> eq, boolean checkAccess) {
    var result = new ArrayList<Map<String, Object>>();
    var seen = new HashSet<String>();
    var references = rows(detail.get("projectRefs"));
    Map<String, Object> legacy = null;
    Map<String, Object> legacyPayload = null;
    if (!detail.containsKey("projectRefs")) {
      legacy = raw("standards", tenant, number(detail.get("standardId")), false);
      legacyPayload = decode(legacy.get("payload"));
      references =
          rows(legacyPayload.get("items")).stream()
              .filter(item -> text(item, "typeId").equals(text(detail, "typeId")))
              .map(
                  item ->
                      Map.<String, Object>of(
                          "standardId", text(detail, "standardId"), "itemId", text(item, "id")))
              .toList();
    }
    require(references.size() <= 1000, MessageContext.text("server.maintenance.message0015"));
    if (references.isEmpty()) return result;
    var standards = new HashMap<Long, StandardProjects>();
    if (legacy != null) {
      var resolved = standardProjects(legacy, legacyPayload, checkAccess);
      // 旧安排按标准适用设备筛选；显式选项目沿用原规则，不追加设备关联限制。
      require(
          applicable(legacyPayload, eq, tenant),
          MessageContext.text("server.maintenance.message0016"));
      standards.put(number(detail.get("standardId")), resolved);
    }
    for (var ref : references) {
      var standard =
          standards.computeIfAbsent(
              number(ref.get("standardId")),
              id -> {
                var row = raw("standards", tenant, id, false);
                return standardProjects(row, decode(row.get("payload")), checkAccess);
              });
      var item = standard.items().get(text(ref, "itemId"));
      if (item == null) {
        throw new LocalizedArgumentException("server.maintenance.message0010");
      }
      require(
          text(item, "typeId").equals(text(detail, "typeId")),
          MessageContext.text("server.maintenance.message0017"));
      String key = text(ref, "standardId") + ":" + text(ref, "itemId");
      if (!seen.add(key)) continue;
      var selected = new LinkedHashMap<String, Object>(item);
      // 旧安排沿用原项目 ID，保证上次结果预填；显式跨标准选择使用复合项目键。
      if (detail.containsKey("projectRefs")) selected.put("id", key);
      selected.put("sourceStandardId", text(ref, "standardId"));
      selected.put("sourceItemId", text(item, "id"));
      selected.put("sourceStandardName", standard.name());
      result.add(selected);
    }
    return result;
  }

  // items 保留既有项目快照协议；索引仅用于当前调用查找，不成为另一份持久化权威。
  private record StandardProjects(String name, Map<String, Map<String, Object>> items) {}

  private StandardProjects standardProjects(
      Map<String, Object> row, Map<String, Object> payload, boolean checkAccess) {
    if (checkAccess && !visible(row, "查看")) deny();
    var items = new LinkedHashMap<String, Map<String, Object>>();
    for (var item : rows(payload.get("items"))) {
      // 与旧 findFirst 保持一致，不让重复历史键改为取最后一项。
      items.putIfAbsent(text(item, "id"), item);
    }
    return new StandardProjects(Objects.toString(row.get("name"), ""), items);
  }

  Map<String, Object> equipment(long tenant, long id) {
    var a = repository.loadEquipment(tenant, id);
    require(!a.isEmpty(), MessageContext.text("server.common.message0064"));
    return a.getFirst();
  }

  boolean applicable(Map<String, Object> standard, Map<String, Object> equipment, long tenant) {
    return BaseDataReferenceService.applicable(
        standard, equipment, repository.listCategoryParents(tenant));
  }

  Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new LocalizedArgumentException("server.maintenance.message0009");
    }
  }
}
