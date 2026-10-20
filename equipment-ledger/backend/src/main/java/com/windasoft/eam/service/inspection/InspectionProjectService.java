package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InspectionProjectService {
  private static final Set<String> KINDS =
      Set.of("types", "standards", "daily-plans", "periodic-plans");
  private final InspectionRepository repository;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;

  public InspectionProjectService(
      InspectionRepository repository,
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
        com.windasoft.eam.common.MessageContext.text("server.system.message0046"));
    var r = repository.loadDefinition(tenant, kind, id, lock);
    if (r.isEmpty())
      throw new com.windasoft.eam.common.LocalizedStatusException(
          HttpStatus.NOT_FOUND, "server.inspection.message0013");
    return r.getFirst();
  }

  boolean scoped(Map<String, Object> row, String action) {
    try {
      return permissions
          .inspection(text(row, "kind"), action)
          .matches(
              nullable(row.get("company_id")),
              nullable(row.get("department_id")),
              nullable(row.get("creator_id")),
              auth.userId());
    } catch (ResponseStatusException ex) {
      return false;
    }
  }

  Map<String, Object> equipment(long tenant, long id) {
    var r = repository.loadEquipment(tenant, id);
    require(
        !r.isEmpty(), com.windasoft.eam.common.MessageContext.text("server.common.message0064"));
    return r.getFirst();
  }

  boolean applicable(Map<String, Object> standard, Map<String, Object> eq, long tenant) {
    return com.windasoft.eam.service.basedata.BaseDataReferenceService.applicable(
        standard, eq, repository.listCategoryParents(tenant));
  }

  List<Map<String, Object>> projects(long tenant, Map<String, Object> detail, boolean checkAccess) {
    var result = new ArrayList<Map<String, Object>>();
    var seen = new HashSet<String>();
    for (var ref : rows(detail.get("projectRefs"))) {
      var st = raw("standards", tenant, number(ref.get("standardId")), false);
      if (checkAccess && !scoped(st, "查看")) deny();
      var item =
          rows(decode(st.get("payload")).get("items")).stream()
              .filter(i -> text(i, "id").equals(text(ref, "itemId")))
              .findFirst()
              .orElseThrow(
                  () ->
                      new com.windasoft.eam.common.LocalizedArgumentException(
                          "server.inspection.message0014"));
      String id = text(ref, "standardId") + ":" + text(ref, "itemId");
      if (seen.add(id)) {
        var selected = new LinkedHashMap<>(item);
        selected.put("id", id);
        selected.put("sourceStandardId", text(ref, "standardId"));
        selected.put("sourceItemId", text(item, "id"));
        selected.put("sourceStandardName", st.get("name"));
        result.add(selected);
      }
    }
    require(
        !result.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0041"));
    return result;
  }

  Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }
}
