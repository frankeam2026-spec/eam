package com.windasoft.eam.service.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.business.BusinessConfigRepository;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class BusinessReferenceService {
  private final BusinessConfigRepository repo;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final BusinessConfigService configs;

  public BusinessReferenceService(
      BusinessConfigRepository repo,
      AuthContext auth,
      PermissionService permissions,
      BusinessConfigService configs) {
    this.repo = repo;
    this.auth = auth;
    this.permissions = permissions;
    this.configs = configs;
  }

  public String menu(String object) {
    if (!BusinessConfigService.OBJECTS.containsKey(object))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0039");
    return BusinessObjects.get(object).menu();
  }

  public void access(String object) {
    String menu = menu(object);
    if (BusinessObjects.get(object).actions().stream()
        .noneMatch(action -> permissions.allowed(menu, action))) permissions.require(menu, "查看");
  }

  public boolean allowed(String object, Object organization, Object user) {
    PermissionScope scope;
    String menu = menu(object);
    try {
      scope = permissions.scope(menu(object), "查看");
    } catch (org.springframework.web.server.ResponseStatusException e) {
      return false;
    }
    Long org = null;
    try {
      org = organization == null ? null : Long.valueOf(organization.toString());
    } catch (NumberFormatException ignored) {
    }
    Long company = org == null ? null : permissions.companyOfOrganization(org);
    if (scope.matches(
        company, org, user == null ? null : Long.valueOf(user.toString()), auth.userId()))
      return true;
    return scope.owner()
        && Objects.equals(org, auth.user().getOrgId())
        && (user == null || user.toString().equals(Long.toString(auth.userId())));
  }

  public List<Map<String, Object>> all(String object, String source, boolean inactive) {
    access(object);
    var rows = new ArrayList<Map<String, Object>>();
    if (source.startsWith("field:")) {
      var fields = configs.fields(configs.decode(configs.current(object, false).get("published")));
      var field = fields.get(source.substring(6));
      if (field == null || !BusinessRules.inlineOptions(field))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.business.message0040");
      if (!inactive && !field.path("enabled").asBoolean(true)) return rows;
      return fieldOptions(object, field, inactive);
    }
    if (source.startsWith("dictionary:")) {
      var dict = repo.dictionary(auth.tenantId(), source.substring(11), false);
      if (dict.isEmpty() || !inactive && ((Number) dict.getFirst().get("enabled")).intValue() != 1)
        return rows;
      rows.addAll(repo.options(auth.tenantId(), source.substring(11)));
    } else if (source.startsWith("base:")
        && BusinessConfigService.BASES.containsKey(source.substring(5)))
      rows.addAll(repo.baseOptions(auth.tenantId(), source.substring(5)));
    else if (source.equals("department")) {
      var all = repo.organizations(auth.tenantId());
      var byId = new HashMap<String, Map<String, Object>>();
      all.forEach(o -> byId.put(o.get("id").toString(), o));
      for (var org : all)
        if ("部门".equals(org.get("type")) && allowed(object, org.get("id"), null)) {
          var row = new LinkedHashMap<>(org);
          var path = new ArrayList<String>();
          var visited = new HashSet<String>();
          var node = org;
          while (node != null && visited.add(node.get("id").toString())) {
            path.addFirst(node.get("name").toString());
            node = byId.get(Objects.toString(node.get("parentId"), ""));
          }
          row.put("name", String.join(" · ", path));
          row.put("enabled", 1);
          rows.add(row);
        }
    } else if (source.equals("user")) {
      for (var row : repo.users(auth.tenantId()))
        if (allowed(object, row.get("org_id"), row.get("id"))) {
          var r = new LinkedHashMap<>(row);
          r.remove("org_id");
          r.put("enabled", 1);
          rows.add(r);
        }
    } else
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0002");
    return rows.stream().filter(r -> inactive || enabled(r.get("enabled"))).toList();
  }

  public Map<String, Object> choices(
      String object, String source, String q, int page, int size, boolean inactive) {
    access(object);
    if (q.length() > 200)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0041");
    size = Math.max(1, Math.min(100, size));
    page = Math.max(1, page);
    if (source.startsWith("field:")) {
      var term = q.toLowerCase(Locale.ROOT);
      var matches =
          all(object, source, inactive).stream()
              .filter(row -> row.get("name").toString().toLowerCase(Locale.ROOT).contains(term))
              .toList();
      long start = (long) (page - 1) * size;
      return Map.of(
          "data", matches.stream().skip(start).limit(size).toList(), "total", matches.size());
    }
    if (source.startsWith("base:") && !BusinessConfigService.BASES.containsKey(source.substring(5)))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0042");
    if (!source.startsWith("base:")
        && !source.startsWith("dictionary:")
        && !Set.of("user", "department").contains(source))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0002");
    var orgs = new ArrayList<String>();
    Long ownUser = null;
    if (source.equals("user") || source.equals("department")) {
      var scope = permissions.scope(menu(object), "查看");
      for (var org : repo.organizations(auth.tenantId())) {
        Long id = Long.valueOf(org.get("id").toString());
        Long company = permissions.companyOfOrganization(id);
        if (scope.matches(company, id, null, auth.userId())
            || source.equals("department")
                && scope.owner()
                && Objects.equals(id, auth.user().getOrgId())) orgs.add(id.toString());
      }
      if (scope.owner()) ownUser = auth.userId();
    }
    var result = repo.pageOptions(auth.tenantId(), source, q, page, size, inactive, orgs, ownUser);
    if (source.equals("department")) {
      var names = new HashMap<String, String>();
      for (var row : all(object, source, inactive))
        names.put(row.get("id").toString(), row.get("name").toString());
      if (result.get("data") instanceof List<?> rows)
        for (var value : rows) {
          @SuppressWarnings("unchecked")
          var row = (Map<String, Object>) value;
          row.put("name", names.getOrDefault(row.get("id").toString(), row.get("name").toString()));
        }
    }
    return result;
  }

  public List<Map<String, Object>> fieldOptions(String object, JsonNode field, boolean inactive) {
    access(object);
    if (!BusinessRules.inlineOptions(field))
      return all(object, field.path("source").asText(), inactive);
    var result = new ArrayList<Map<String, Object>>();
    for (var option : field.path("options"))
      if (inactive || option.path("enabled").asBoolean())
        result.add(
            Map.of(
                "id",
                option.path("id").asText(),
                "name",
                option.path("name").asText(),
                "enabled",
                option.path("enabled").asBoolean(),
                "sortOrder",
                option.path("sortOrder").asInt()));
    result.sort(
        Comparator.<Map<String, Object>, Integer>comparing(r -> (Integer) r.get("sortOrder"))
            .thenComparing(r -> r.get("id").toString()));
    return result;
  }

  public String resolve(String object, JsonNode field, String id) {
    if (!BusinessRules.inlineOptions(field))
      return resolve(object, field.path("source").asText(), id);
    return fieldOptions(object, field, false).stream()
        .filter(o -> id.equals(o.get("id")))
        .map(o -> o.get("name").toString())
        .findFirst()
        .orElseThrow(
            () ->
                new com.windasoft.eam.common.LocalizedArgumentException(
                    "server.business.message0043"));
  }

  public String resolve(String object, String source, String id) {
    try {
      if (source.startsWith("dictionary:")) UUID.fromString(id);
      else if (Long.parseLong(id) <= 0) throw new NumberFormatException();
    } catch (IllegalArgumentException e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0044");
    }
    var row = repo.reference(auth.tenantId(), source, id);
    if (row == null || !enabled(row.get("enabled")))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0045");
    if ((source.equals("user") || source.equals("department"))
        && !allowed(object, row.get("orgId"), source.equals("user") ? id : null))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0046");
    if (source.equals("department"))
      return all(object, source, false).stream()
          .filter(r -> r.get("id").toString().equals(id))
          .map(r -> r.get("name").toString())
          .findFirst()
          .orElseThrow();
    return row.get("name").toString();
  }

  public static boolean enabled(Object v) {
    return v instanceof Boolean b ? b : v instanceof Number n && n.intValue() == 1;
  }
}
