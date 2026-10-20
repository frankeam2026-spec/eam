package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Run only against a restored pre-migration copy. Never accepts a development/customer database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_MIGRATION_DB_URL", matches = ".+")
class SchemaMigrationMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    String url = System.getenv("EAM_MIGRATION_DB_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/eam_migration_test(\\?.*)?"))
      throw new IllegalStateException("Only isolated eam_migration_test is allowed");
    p.add("spring.datasource.url", () -> url);
    p.add("spring.datasource.username", () -> System.getenv("EAM_ENGINEERING_DB_USER"));
    p.add("spring.datasource.password", () -> System.getenv("EAM_ENGINEERING_DB_PASSWORD"));
  }

  @MockitoBean com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler maintenanceScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inspection.InspectionScheduler inspectionScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup cleanup;
  @MockitoBean com.windasoft.eam.scheduler.experience.RepairPlanScheduler repairScheduler;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;

  JsonNode decode(Object v) {
    try {
      return json.readTree(Objects.toString(v, "{}"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  Set<String> ids(JsonNode a, String key) {
    var result = new TreeSet<String>();
    for (var v : a) {
      var value = key == null ? v : v.path(key);
      result.add(value.asText());
    }
    return result;
  }

  void sameValue(JsonNode a, JsonNode b, String context) {
    if (a.isNull() || a.isMissingNode() || a.asText().isBlank()) return;
    if (a.isArray()) {
      assertEquals(a.size(), b.size(), context);
      for (int i = 0; i < a.size(); i++) sameValue(a.get(i), b.path(i), context + "[" + i + "]");
      return;
    }
    if (a.isObject()) {
      for (var it = a.fields(); it.hasNext(); ) {
        var f = it.next();
        if (b.has(f.getKey()))
          sameValue(f.getValue(), b.path(f.getKey()), context + "." + f.getKey());
      }
      return;
    }
    try {
      assertEquals(0, new BigDecimal(a.asText()).compareTo(new BigDecimal(b.asText())), context);
    } catch (NumberFormatException e) {
      assertEquals(a.asText(), b.asText(), context);
    }
  }

  void compare(String archive, String view) {
    var old = db.queryForList("SELECT id,tenant_id,payload FROM " + archive);
    assertEquals(old.size(), db.queryForObject("SELECT COUNT(*) FROM " + view, Integer.class));
    for (var r : old) {
      var after =
          db.queryForMap(
              "SELECT payload FROM " + view + " WHERE tenant_id=? AND id=?",
              r.get("tenant_id"),
              r.get("id"));
      var a = decode(r.get("payload"));
      var b = decode(after.get("payload"));
      sameValue(a, b, archive + ":" + r.get("id"));
      for (String key : List.of("owners", "reviewers", "equipmentIds", "categoryIds"))
        if (a.has(key))
          assertEquals(ids(a.path(key), null), ids(b.path(key), null), archive + ":" + key);
      if (a.has("items"))
        assertEquals(ids(a.path("items"), "id"), ids(b.path("items"), "id"), archive + ":items");
      if (a.has("details"))
        assertEquals(
            ids(a.path("details"), "equipmentId"),
            ids(b.path("details"), "equipmentId"),
            archive + ":devices");
    }
  }

  @Test
  void definitionAndInventoryIdentitiesAndValuesAreConserved() {
    compare("migration_v67_maintenance_definition_archive", "v_maintenance_definition");
    compare("migration_v69_inspection_definition_archive", "v_inspection_definition");
    compare("migration_v71_inventory_operation_archive", "v_inv_operation");
  }

  @Test
  void taskResultsParticipantsAndStatusAreConserved() {
    for (String module : List.of("maintenance", "inspection"))
      for (var old : db.queryForList("SELECT * FROM migration_v70_" + module + "_task_archive")) {
        var after =
            db.queryForMap(
                "SELECT * FROM v_" + module + "_task WHERE tenant_id=? AND id=?",
                old.get("tenant_id"),
                old.get("id"));
        assertEquals(old.get("status"), after.get("status"));
        sameValue(decode(old.get("result")), decode(after.get("result")), module + ":result");
        for (String key : List.of("owners", "reviewers"))
          assertEquals(
              ids(decode(old.get("snapshot")).path(key), null),
              ids(decode(after.get("snapshot")).path(key), null),
              module + ":" + key);
        if (old.get("result") != null)
          assertEquals(
              ids(decode(old.get("result")).path("items"), "id"),
              ids(decode(after.get("result")).path("items"), "id"));
      }
  }

  @Test
  void outboundSelectionsAndTypedHeadersAreConserved() {
    for (var old : db.queryForList("SELECT * FROM migration_v64_outbound_archive")) {
      var content = decode(old.get("original_payload"));
      var current =
          db.queryForMap(
              "SELECT * FROM inv_issue WHERE tenant_id=? AND id=?",
              old.get("tenant_id"),
              old.get("issue_id"));
      for (var pair :
          Map.of(
                  "purpose",
                  "purpose",
                  "remark",
                  "remark",
                  "departmentId",
                  "department_id",
                  "recipientId",
                  "recipient_id",
                  "taskId",
                  "task_id",
                  "taskKind",
                  "task_kind")
              .entrySet())
        if (content.hasNonNull(pair.getKey()))
          assertEquals(
              pair.getKey().endsWith("Id") && content.path(pair.getKey()).asText().equals("0")
                  ? ""
                  : content.path(pair.getKey()).asText(),
              Objects.toString(current.get(pair.getValue()), ""),
              pair.getKey());
      int expected = 0;
      for (var lines : content.path("lineSelections")) expected += lines.size();
      assertEquals(
          expected,
          db.queryForObject(
              "SELECT COUNT(*) FROM inv_outbound_selection WHERE tenant_id=? AND issue_id=?",
              Integer.class,
              old.get("tenant_id"),
              old.get("issue_id")));
    }
  }

  @Test
  void currentBusinessTablesHaveNoPayloadOrMutableResultJson() {
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND"
                + " data_type='json' AND (table_name IN"
                + " ('inv_issue','eam_sys_role','eam_sys_role_menu_permission') OR table_name LIKE"
                + " 'eam_maintenance_%' AND table_name NOT IN"
                + " ('eam_maintenance_task','eam_maintenance_event') OR table_name LIKE"
                + " 'eam_inspection_%' AND table_name NOT IN"
                + " ('eam_inspection_task','eam_inspection_event'))",
            Integer.class));
  }

  @Test
  void attachmentsAndHomeRoleBindingsAreConserved() {
    var specs =
        List.of(
            new String[] {"eam_equipment_image", "equipment_id"},
            new String[] {"eam_equipment_document", "equipment_id"},
            new String[] {"eam_repair_report_file", "order_id"},
            new String[] {"eam_repair_process_file", "order_id"},
            new String[] {"eam_anomaly_attachment", "anomaly_id"},
            new String[] {"eam_repair_plan_file", "plan_id"});
    for (var spec : specs)
      for (var old : db.queryForList("SELECT * FROM migration_v74_" + spec[0] + "_archive")) {
        var before = decode(old.get("original_value"));
        var rows =
            db.queryForList(
                "SELECT * FROM "
                    + spec[0]
                    + " WHERE tenant_id=? AND "
                    + spec[1]
                    + "=? ORDER BY sort_no",
                old.get("tenant_id"),
                old.get("id"));
        assertEquals(before.size(), rows.size(), spec[0]);
        for (int i = 0; i < rows.size(); i++) {
          var row = rows.get(i);
          for (var pair :
              Map.of(
                      "id",
                      "file_id",
                      "name",
                      "name_snapshot",
                      "size",
                      "size_bytes",
                      "url",
                      "url_snapshot")
                  .entrySet())
            if (before.get(i).hasNonNull(pair.getKey()))
              assertEquals(
                  before.get(i).path(pair.getKey()).asText(),
                  Objects.toString(row.get(pair.getValue()), ""),
                  spec[0] + ":" + pair.getKey());
        }
      }
    for (boolean version : List.of(false, true))
      for (var old :
          db.queryForList(
              "SELECT * FROM migration_v75_eam_home_template"
                  + (version ? "_version" : "")
                  + "_archive")) {
        var args = new ArrayList<Object>();
        args.add(old.get("tenant_id"));
        args.add(old.get(version ? "template_id" : "id"));
        if (version) args.add(old.get("version"));
        var after =
            db.queryForObject(
                "SELECT role_ids FROM v_home_template"
                    + (version ? "_version" : "")
                    + " WHERE tenant_id=? AND "
                    + (version ? "template_id" : "id")
                    + "=?"
                    + (version ? " AND version=?" : ""),
                String.class,
                args.toArray());
        assertEquals(ids(decode(old.get("role_ids")), null), ids(decode(after), null));
      }
  }

  @Test
  void roleAuthorizationAndRepairCollaboratorsAreConserved() {
    for (var old : db.queryForList("SELECT * FROM migration_v65_role_archive")) {
      var orgs =
          db.queryForList(
              "SELECT org_id FROM eam_role_organization WHERE tenant_id=? AND role_id=?",
              Long.class,
              old.get("tenant_id"),
              old.get("role_id"));
      assertEquals(
          ids(decode(old.get("org_ids")), null),
          new TreeSet<>(orgs.stream().map(Object::toString).toList()));
      var permissions =
          db.queryForList(
              "SELECT CONCAT(menu_key,'.',action_code) FROM eam_role_legacy_action WHERE"
                  + " tenant_id=? AND role_id=?",
              String.class,
              old.get("tenant_id"),
              old.get("role_id"));
      assertEquals(ids(decode(old.get("permissions")), null), new TreeSet<>(permissions));
    }
    for (var old : db.queryForList("SELECT * FROM migration_v65_menu_archive"))
      for (var pair :
          Map.of(
                  "actions",
                  "eam_role_menu_action:action_name",
                  "scopes",
                  "eam_role_menu_scope:scope_code",
                  "targets",
                  "eam_role_menu_target:target_id")
              .entrySet()) {
        var parts = pair.getValue().split(":");
        var actual =
            db.queryForList(
                "SELECT " + parts[1] + " FROM " + parts[0] + " WHERE tenant_id=? AND grant_id=?",
                String.class,
                old.get("tenant_id"),
                old.get("grant_id"));
        assertEquals(
            ids(decode(old.get(pair.getKey())), null), new TreeSet<>(actual), pair.getKey());
      }
    for (var old : db.queryForList("SELECT * FROM migration_v66_repair_collaborators_archive")) {
      var actual =
          db.queryForList(
              "SELECT user_id FROM eam_repair_collaborator WHERE tenant_id=? AND order_id=?",
              String.class,
              old.get("tenant_id"),
              old.get("order_id"));
      assertEquals(ids(decode(old.get("original_collaborators")), "id"), new TreeSet<>(actual));
    }
  }

  @Test
  void generatedTaskMembershipAndSourceIdsAreConserved() {
    for (String module : List.of("maintenance", "inspection"))
      for (var task :
          db.queryForList(
              "SELECT id,tenant_id,snapshot FROM migration_v70_" + module + "_task_archive")) {
        var template = decode(task.get("snapshot"));
        var projects =
            db.queryForList(
                "SELECT item_key FROM eam_"
                    + module
                    + "_task_project WHERE tenant_id=? AND task_id=? ORDER BY sort_no",
                String.class,
                task.get("tenant_id"),
                task.get("id"));
        assertEquals(ids(template.path("items"), "id"), new TreeSet<>(projects));
        if (module.equals("inspection")) {
          var devices =
              db.queryForList(
                  "SELECT equipment_id FROM eam_inspection_task_equipment WHERE tenant_id=? AND"
                      + " task_id=?",
                  String.class,
                  task.get("tenant_id"),
                  task.get("id"));
          assertEquals(ids(template.path("equipment"), "id"), new TreeSet<>(devices));
        }
      }
    for (var group : db.queryForList("SELECT * FROM migration_v78_issue_group_archive")) {
      var actual =
          db.queryForList(
              "SELECT requisition_id FROM inv_issue_group_request WHERE tenant_id=? AND group_id=?",
              String.class,
              group.get("tenant_id"),
              group.get("id"));
      assertEquals(ids(decode(group.get("request_ids")), null), new TreeSet<>(actual));
    }
  }
}
