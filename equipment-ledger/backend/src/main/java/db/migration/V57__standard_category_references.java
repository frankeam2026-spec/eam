package db.migration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.LoggerFactory;

/** Backfill standard scopes only when names uniquely identify same-tenant category records. */
public class V57__standard_category_references extends BaseJavaMigration {
  final ObjectMapper json = new ObjectMapper();

  @Override
  public void migrate(Context context) throws Exception {
    var c = context.getConnection();
    int migrated = 0, unresolved = 0;
    for (String table : List.of("eam_maintenance_definition", "eam_inspection_definition")) {
      var pending = new ArrayList<Map<String, Object>>();
      try (var s = c.createStatement();
          var rs =
              s.executeQuery(
                  "SELECT tenant_id,id,payload FROM " + table + " WHERE kind='standards'")) {
        while (rs.next())
          pending.add(
              Map.of("tenant", rs.getLong(1), "id", rs.getLong(2), "payload", rs.getString(3)));
      }
      for (var row : pending) {
        var data = (ObjectNode) json.readTree(row.get("payload").toString());
        var ids = new LinkedHashSet<String>();
        for (var v : data.path("categoryIds")) ids.add(v.asText());
        var legacy = new LinkedHashSet<String>();
        for (var v : data.path("legacyCategoryNames")) legacy.add(v.asText());
        if (!data.has("categoryIds"))
          for (var value : data.path("categoryNames")) {
            var matches = new ArrayList<String>();
            try (var q =
                c.prepareStatement(
                    "SELECT id FROM eam_base_item WHERE tenant_id=? AND item_type='CATEGORY' AND"
                        + " name=? AND deleted=0")) {
              q.setLong(1, (Long) row.get("tenant"));
              q.setString(2, value.asText());
              try (var rs = q.executeQuery()) {
                while (rs.next()) matches.add(rs.getString(1));
              }
            }
            if (matches.size() == 1) ids.add(matches.getFirst());
            else {
              legacy.add(value.asText());
              unresolved++;
            }
          }
        var array = data.putArray("categoryIds");
        ids.forEach(array::add);
        var old = data.putArray("legacyCategoryNames");
        legacy.forEach(old::add);
        try (var q =
            c.prepareStatement(
                "UPDATE " + table + " SET payload=CAST(? AS JSON) WHERE tenant_id=? AND id=?")) {
          q.setString(1, json.writeValueAsString(data));
          q.setLong(2, (Long) row.get("tenant"));
          q.setLong(3, (Long) row.get("id"));
          q.executeUpdate();
          migrated++;
        }
      }
    }
    LoggerFactory.getLogger(getClass())
        .info(
            "Base category reference migration: standards={}, unresolved names={}",
            migrated,
            unresolved);
    try (var s = c.createStatement();
        var rs =
            s.executeQuery(
                "SELECT (SELECT COUNT(*) FROM inv_material WHERE unit_id IS NULL AND unit<>'')"
                    + " material_units,(SELECT COUNT(*) FROM inv_material WHERE category_id IS NULL"
                    + " AND category<>'') material_categories,(SELECT COUNT(*) FROM"
                    + " eam_repair_order WHERE fault_type_id IS NULL AND fault_type<>'')"
                    + " fault_types,(SELECT COUNT(*) FROM eam_repair_order WHERE fault_cause_id IS"
                    + " NULL AND fault_cause<>'') fault_causes,(SELECT COUNT(*) FROM"
                    + " eam_repair_order WHERE processing_result_id IS NULL AND"
                    + " processing_result<>'') processing_results")) {
      rs.next();
      LoggerFactory.getLogger(getClass())
          .info(
              "Unresolved dictionary references: material units={}, categories={}, repair types={},"
                  + " causes={}, results={}",
              rs.getInt(1),
              rs.getInt(2),
              rs.getInt(3),
              rs.getInt(4),
              rs.getInt(5));
    }
  }
}
