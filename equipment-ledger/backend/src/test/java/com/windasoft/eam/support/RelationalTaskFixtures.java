package com.windasoft.eam.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Bulk conversion of isolated legacy-shaped fixtures to the real participant relations. */
public final class RelationalTaskFixtures {
  private RelationalTaskFixtures() {}

  public static void capture(JdbcTemplate db) {
    for (String module : java.util.List.of("maintenance", "inspection")) {
      String table = "eam_" + module + "_task", people = table + "_person";
      for (var role : java.util.Map.of("owners", "owner", "reviewers", "reviewer").entrySet()) {
        String condition = "JSON_CONTAINS_PATH(t.snapshot,'one','$." + role.getKey() + "')=1";
        db.update(
            "DELETE p FROM "
                + people
                + " p JOIN "
                + table
                + " t ON t.tenant_id=p.tenant_id AND t.id=p.task_id WHERE p.role_code=? AND "
                + condition,
            role.getValue());
        db.update(
            "INSERT INTO "
                + people
                + " SELECT t.tenant_id,t.id,?,j.user_id,MIN(j.n) FROM "
                + table
                + " t JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(t.snapshot,'$."
                + role.getKey()
                + "'),JSON_ARRAY()),'$[*]' COLUMNS(n FOR ORDINALITY,user_id BIGINT PATH '$')) j"
                + " WHERE "
                + condition
                + " GROUP BY t.tenant_id,t.id,j.user_id",
            role.getValue());
      }
      if (module.equals("inspection"))
        db.update(
            "UPDATE "
                + table
                + " SET person_mode=COALESCE(JSON_UNQUOTE(JSON_EXTRACT(snapshot,'$.personMode')),'')"
                + " WHERE JSON_CONTAINS_PATH(snapshot,'one','$.personMode')=1");
      db.update(
          "UPDATE "
              + table
              + " SET snapshot=JSON_REMOVE(snapshot,'$.owners','$.reviewers','$.personMode') WHERE"
              + " JSON_CONTAINS_PATH(snapshot,'one','$.owners','$.reviewers','$.personMode')=1");
    }
  }
}
