package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
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
    require(
        KINDS.contains(kind),
        com.windasoft.eam.common.MessageContext.text("server.system.message0045"));
    var a = repository.loadDefinition(tenant, kind, id, lock);
    if (a.isEmpty())
      throw new com.windasoft.eam.common.LocalizedStatusException(
          HttpStatus.NOT_FOUND, "server.inspection.message0013");
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

  List<Map<String, Object>> planProjects(
      Map<String, Object> detail, long tenant, Map<String, Object> eq, boolean checkAccess) {
    var result = new ArrayList<Map<String, Object>>();
    var seen = new HashSet<String>();
    var references = rows(detail.get("projectRefs"));
    if (!detail.containsKey("projectRefs")) {
      var legacy = raw("standards", tenant, number(detail.get("standardId")), false);
      references =
          rows(decode(legacy.get("payload")).get("items")).stream()
              .filter(item -> text(item, "typeId").equals(text(detail, "typeId")))
              .map(
                  item -> {
                    var ref = new LinkedHashMap<String, Object>();
                    ref.put("standardId", text(detail, "standardId"));
                    ref.put("itemId", text(item, "id"));
                    return (Map<String, Object>) ref;
                  })
              .toList();
    }
    require(
        references.size() <= 1000,
        com.windasoft.eam.common.MessageContext.text("server.maintenance.message0015"));
    for (var ref : references) {
      var standard = raw("standards", tenant, number(ref.get("standardId")), false);
      if (checkAccess && !visible(standard, "查看")) deny();
      var payload = decode(standard.get("payload"));
      // Explicit selections may use any accessible standard; equipment associations are defaults.
      if (!detail.containsKey("projectRefs"))
        require(
            applicable(payload, eq, tenant),
            com.windasoft.eam.common.MessageContext.text("server.maintenance.message0016"));
      var item =
          rows(payload.get("items")).stream()
              .filter(i -> text(i, "id").equals(text(ref, "itemId")))
              .findFirst()
              .orElseThrow(
                  () ->
                      new com.windasoft.eam.common.LocalizedArgumentException(
                          "server.maintenance.message0010"));
      require(
          text(item, "typeId").equals(text(detail, "typeId")),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0017"));
      String key = text(ref, "standardId") + ":" + text(ref, "itemId");
      if (!seen.add(key)) continue;
      var selected = new LinkedHashMap<String, Object>(item);
      // Keep legacy item IDs for existing schedule result prefill; cross-standard IDs are
      // namespaced.
      if (detail.containsKey("projectRefs")) selected.put("id", key);
      selected.put("sourceStandardId", text(ref, "standardId"));
      selected.put("sourceItemId", text(item, "id"));
      selected.put("sourceStandardName", Objects.toString(standard.get("name"), ""));
      result.add(selected);
    }
    return result;
  }

  Map<String, Object> equipment(long tenant, long id) {
    var a = repository.loadEquipment(tenant, id);
    require(
        !a.isEmpty(), com.windasoft.eam.common.MessageContext.text("server.common.message0064"));
    return a.getFirst();
  }

  boolean applicable(Map<String, Object> standard, Map<String, Object> equipment, long tenant) {
    return com.windasoft.eam.service.basedata.BaseDataReferenceService.applicable(
        standard, equipment, repository.listCategoryParents(tenant));
  }

  Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.maintenance.message0009");
    }
  }
}
