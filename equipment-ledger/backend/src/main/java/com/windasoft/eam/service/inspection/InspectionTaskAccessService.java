package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InspectionTaskAccessService {
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;

  public InspectionTaskAccessService(
      ObjectMapper json, AuthContext auth, PermissionService permissions) {
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
  }

  public boolean participant(Map<String, Object> row) {
    var s = decode(row.get("snapshot"));
    return Objects.equals(nullable(row.get("follow_up_user_id")),auth.userId()) || ids(s.get("owners")).contains(auth.userId())
        || ids(s.get("reviewers")).contains(auth.userId())
        || Objects.equals(nullable(row.get("owner_id")), auth.userId())
        || text(s, "personMode").equals("任何人可点检");
  }

  public boolean access(Map<String, Object> row, String action) {
    if (action.equals("查看") && participant(row)) return true;
    try {
      return permissions
          .inspection(text(row, "kind"), action)
          .matches(
              nullable(row.get("company_id")),
              nullable(row.get("department_id")),
              nullable(row.get("owner_id")),
              auth.userId());
    } catch (ResponseStatusException ex) {
      return false;
    }
  }

  public boolean execute(Map<String, Object> row) {
    var s = decode(row.get("snapshot"));
    return (text(s, "personMode").equals("任何人可点检") || ids(s.get("owners")).contains(auth.userId()))
        && (row.get("owner_id") == null || number(row.get("owner_id")) == auth.userId());
  }

  public boolean review(Map<String, Object> row) {
    return ids(decode(row.get("snapshot")).get("reviewers")).contains(auth.userId())
        || access(row, "审核");
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
