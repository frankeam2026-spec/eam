package db.migration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.*;
import org.slf4j.LoggerFactory;

/** Keep task snapshots, bind future plan cycles to validated same-tenant type IDs. */
public class V60__bind_plan_cycles_to_type_ids extends BaseJavaMigration {
  final ObjectMapper json = new ObjectMapper();

  record Type(long tenant, long id, String name, JsonNode payload) {}

  record Row(long tenant, long id, String kind, String payload) {}

  Map<String, Type> types = new HashMap<>();
  int plans = 0, schedules = 0, tasks = 0, missing = 0;

  String key(long tenant, Object id) {
    return tenant + ":" + id;
  }

  Type find(long tenant, String id) {
    return types.get(key(tenant, id));
  }

  void bind(ObjectNode payload, Type type, boolean cycle) {
    payload.put("typeId", Long.toString(type.id()));
    payload.put("typeName", type.name());
    if (cycle) {
      payload.set("period", type.payload().path("period"));
      payload.set("unit", type.payload().path("unit"));
    }
  }

  List<Row> rows(Connection c, String table, String field, String where) throws Exception {
    var result = new ArrayList<Row>();
    try (var s = c.createStatement();
        var rs =
            s.executeQuery(
                "SELECT tenant_id,id,"
                    + (table.endsWith("definition") || table.endsWith("task") ? "kind" : "''")
                    + ","
                    + field
                    + " FROM "
                    + table
                    + " "
                    + where)) {
      while (rs.next())
        result.add(new Row(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)));
    }
    return result;
  }

  void update(Connection c, String table, String field, Row row, ObjectNode payload, Long type)
      throws Exception {
    try (var q =
        c.prepareStatement(
            "UPDATE "
                + table
                + " SET "
                + field
                + "=CAST(? AS JSON)"
                + (type == null ? "" : ",type_id=?")
                + ",row_version=row_version+1 WHERE tenant_id=? AND id=?")) {
      int i = 1;
      q.setString(i++, json.writeValueAsString(payload));
      if (type != null) q.setLong(i++, type);
      q.setLong(i++, row.tenant());
      q.setLong(i, row.id());
      q.executeUpdate();
    }
  }

  @Override
  public void migrate(Context context) throws Exception {
    var c = context.getConnection();
    for (String module : List.of("maintenance", "inspection")) {
      types.clear();
      String definitions = "eam_" + module + "_definition";
      try (var s = c.createStatement();
          var rs =
              s.executeQuery(
                  "SELECT tenant_id,id,name,payload FROM " + definitions + " WHERE kind='types'")) {
        while (rs.next()) {
          var t =
              new Type(
                  rs.getLong(1), rs.getLong(2), rs.getString(3), json.readTree(rs.getString(4)));
          types.put(key(t.tenant(), t.id()), t);
        }
      }
      var dailyByTenant = new HashMap<Long, List<Type>>();
      for (var type : types.values())
        if (type.payload().path("period").asInt() == 1
            && type.payload().path("unit").asText().equals("日"))
          dailyByTenant.computeIfAbsent(type.tenant(), x -> new ArrayList<>()).add(type);
      if (module.equals("maintenance")) {
        try (var s = c.createStatement();
            var rs =
                s.executeQuery(
                    "SELECT tenant_id,id,type_id,payload FROM eam_maintenance_schedule")) {
          var changes = new ArrayList<Object[]>();
          while (rs.next()) {
            long tenant = rs.getLong(1), id = rs.getLong(2);
            var type = find(tenant, rs.getString(3));
            if (type == null) {
              missing++;
              continue;
            }
            var p = (ObjectNode) json.readTree(rs.getString(4));
            boolean changed =
                !p.path("period").equals(type.payload().path("period"))
                    || !p.path("unit").equals(type.payload().path("unit"));
            bind(p, type, true);
            changes.add(new Object[] {tenant, id, json.writeValueAsString(p), changed});
          }
          for (var change : changes)
            try (var q =
                c.prepareStatement(
                    "UPDATE eam_maintenance_schedule SET payload=CAST(? AS JSON)"
                        + ((Boolean) change[3] ? ",anchor_date=next_date,cycle_index=0" : "")
                        + " WHERE tenant_id=? AND id=?")) {
              q.setString(1, (String) change[2]);
              q.setLong(2, (Long) change[0]);
              q.setLong(3, (Long) change[1]);
              q.executeUpdate();
              schedules++;
            }
        }
        for (var row : rows(c, definitions, "payload", "WHERE kind='plans'")) {
          var p = (ObjectNode) json.readTree(row.payload());
          for (var d : p.path("details")) {
            var type = find(row.tenant(), d.path("typeId").asText());
            if (type != null) bind((ObjectNode) d, type, true);
          }
          update(c, definitions, "payload", row, p, null);
          plans++;
        }
      } else {
        for (var row :
            rows(c, definitions, "payload", "WHERE kind IN ('daily-plans','periodic-plans')")) {
          var p = (ObjectNode) json.readTree(row.payload());
          var type = find(row.tenant(), p.path("typeId").asText());
          if (type == null && row.kind().equals("daily-plans")) {
            var daily = dailyByTenant.getOrDefault(row.tenant(), List.of());
            if (daily.size() == 1) type = daily.getFirst();
          }
          if (type == null) {
            missing++;
            continue;
          }
          bind(p, type, row.kind().equals("periodic-plans"));
          update(c, definitions, "payload", row, p, type.id());
          plans++;
          // Keep the next occurrence; restart its anchor after normalizing the period.
          try (var q =
              c.prepareStatement(
                  "UPDATE eam_inspection_schedule SET anchor_at=next_at,cycle_index=0 WHERE"
                      + " tenant_id=? AND plan_id=?")) {
            q.setLong(1, row.tenant());
            q.setLong(2, row.id());
            q.executeUpdate();
          }
        }
      }
      String taskTable = "eam_" + module + "_task";
      // Maintenance task has no kind column.
      var taskRows = new ArrayList<Row>();
      try (var s = c.createStatement();
          var rs =
              s.executeQuery(
                  "SELECT tenant_id,id,"
                      + (module.equals("inspection") ? "kind" : "''")
                      + ",snapshot FROM "
                      + taskTable)) {
        while (rs.next())
          taskRows.add(new Row(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)));
      }
      for (var row : taskRows) {
        var snap = (ObjectNode) json.readTree(row.payload());
        String id = snap.path("typeId").asText();
        if (id.isBlank()) id = snap.path("cycle").path("typeId").asText();
        var type = find(row.tenant(), id);
        if (type == null && module.equals("inspection") && row.kind().equals("daily")) {
          var daily = dailyByTenant.getOrDefault(row.tenant(), List.of());
          if (daily.size() == 1) type = daily.getFirst();
        }
        if (type == null) {
          missing++;
          continue;
        }
        snap.put("typeId", Long.toString(type.id()));
        if (snap.path("typeName").asText().isBlank()) snap.put("typeName", type.name());
        update(c, taskTable, "snapshot", row, snap, type.id());
        tasks++;
      }
    }
    LoggerFactory.getLogger(getClass())
        .info(
            "Type reference migration: plans={}, schedules={}, tasks={}, unresolved={}",
            plans,
            schedules,
            tasks,
            missing);
  }
}
