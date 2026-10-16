package db.migration;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V46__equipment_relations extends BaseJavaMigration {
  record Device(long tenant, long id, Long company, String code, String children, String bom) {}

  record Edge(Device parent, Device child, int index, JsonNode snapshot) {}

  final ObjectMapper json = new ObjectMapper();

  @Override
  public void migrate(Context context) throws Exception {
    var c = context.getConnection();
    try (var s = c.createStatement()) {
      s.execute(
          "CREATE TABLE eam_equipment_child (tenant_id BIGINT NOT NULL,parent_id BIGINT NOT"
              + " NULL,child_id BIGINT NOT NULL,sort_no INT NOT NULL DEFAULT 0,PRIMARY"
              + " KEY(tenant_id,child_id),INDEX"
              + " idx_equipment_child_parent(tenant_id,parent_id),FOREIGN KEY(parent_id) REFERENCES"
              + " eam_equipment(id),FOREIGN KEY(child_id) REFERENCES eam_equipment(id))");
      s.execute(
          "CREATE TABLE eam_equipment_bom (tenant_id BIGINT NOT NULL,equipment_id BIGINT NOT"
              + " NULL,material_id BIGINT NOT NULL,quantity DECIMAL(18,2) NOT NULL,code VARCHAR(64)"
              + " NOT NULL,name VARCHAR(120) NOT NULL,model VARCHAR(255) NULL,unit VARCHAR(80)"
              + " NULL,sort_no INT NOT NULL DEFAULT 0,PRIMARY"
              + " KEY(tenant_id,equipment_id,material_id),FOREIGN KEY(equipment_id) REFERENCES"
              + " eam_equipment(id),FOREIGN KEY(material_id) REFERENCES inv_material(id))");
      s.execute(
          "CREATE TABLE eam_equipment_relation_legacy (tenant_id BIGINT NOT NULL,equipment_id"
              + " BIGINT NOT NULL,kind VARCHAR(16) NOT NULL,source_index INT NOT NULL,snapshot_json"
              + " JSON NOT NULL,reason VARCHAR(120) NOT NULL,PRIMARY"
              + " KEY(tenant_id,equipment_id,kind,source_index))");
    }
    var devices = new ArrayList<Device>();
    try (var s = c.createStatement();
        var rs =
            s.executeQuery(
                "SELECT"
                    + " tenant_id,id,company_org_id,equipment_code,children_json,related_spares_json"
                    + " FROM eam_equipment WHERE deleted=0")) {
      while (rs.next())
        devices.add(
            new Device(
                rs.getLong(1),
                rs.getLong(2),
                (Long) rs.getObject(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6)));
    }
    var edges = new ArrayList<Edge>();
    for (var d : devices) {
      var nodes = array(d.children());
      for (int i = 0; i < nodes.size(); i++) {
        var n = nodes.get(i);
        String id = n.path("equipmentId").asText("");
        var matches =
            devices.stream()
                .filter(
                    x ->
                        x.tenant() == d.tenant()
                            && (id.isEmpty()
                                ? Objects.equals(x.code(), n.path("code").asText())
                                : Long.toString(x.id()).equals(id)))
                .toList();
        if (matches.size() != 1
            || d.company() == null
            || !Objects.equals(matches.getFirst().company(), d.company())
            || matches.getFirst().id() == d.id())
          legacy(c, d, "child", i, n, "设备编号无法唯一匹配、公司不一致或自关联");
        else edges.add(new Edge(d, matches.getFirst(), i, n));
      }
      var bom = array(d.bom());
      var ids = new HashMap<Long, List<Integer>>();
      var materials = new HashMap<Integer, Map<String, Object>>();
      for (int i = 0; i < bom.size(); i++) {
        var n = bom.get(i);
        var rows = new ArrayList<Map<String, Object>>();
        String id = n.path("materialId").asText("");
        try (var q =
            c.prepareStatement(
                "SELECT id,code,name,model,unit FROM inv_material WHERE tenant_id=?"
                    + " AND "
                    + (id.isEmpty() ? "code=?" : "id=?"))) {
          q.setLong(1, d.tenant());
          q.setString(2, id.isEmpty() ? n.path("code").asText() : id);
          try (var rs = q.executeQuery()) {
            while (rs.next()) {
              var r = new HashMap<String, Object>();
              for (String key : List.of("id", "code", "name", "model", "unit"))
                r.put(key, rs.getObject(key));
              rows.add(r);
            }
          }
        }
        if (rows.size() != 1) legacy(c, d, "bom", i, n, "料号无法唯一匹配");
        else {
          var m = rows.getFirst();
          materials.put(i, m);
          ids.computeIfAbsent(((Number) m.get("id")).longValue(), k -> new ArrayList<>()).add(i);
        }
      }
      for (var entry : materials.entrySet()) {
        int i = entry.getKey();
        var m = entry.getValue();
        var n = bom.get(i);
        try {
          var quantity =
              new BigDecimal(n.path("quantity").asText("1"))
                  .setScale(2, java.math.RoundingMode.UNNECESSARY);
          if (quantity.signum() <= 0
              || quantity.precision() > 18
              || ids.get(((Number) m.get("id")).longValue()).size() > 1)
            throw new IllegalArgumentException();
          try (var q =
              c.prepareStatement("INSERT INTO eam_equipment_bom VALUES(?,?,?,?,?,?,?,?,?)")) {
            q.setLong(1, d.tenant());
            q.setLong(2, d.id());
            q.setObject(3, m.get("id"));
            q.setBigDecimal(4, quantity);
            q.setObject(5, m.get("code"));
            q.setObject(6, m.get("name"));
            q.setObject(7, m.get("model"));
            q.setObject(8, m.get("unit"));
            q.setInt(9, i);
            q.executeUpdate();
          }
        } catch (IllegalArgumentException | ArithmeticException ex) {
          legacy(c, d, "bom", i, n, "数量无效或同一物料重复");
        }
      }
    }
    for (var edge : edges) {
      var owners =
          edges.stream()
              .filter(
                  x ->
                      x.child().tenant() == edge.child().tenant()
                          && x.child().id() == edge.child().id())
              .toList();
      boolean conflict = owners.size() != 1;
      var visited = new HashSet<Long>();
      long ancestor = edge.parent().id();
      while (!conflict) {
        if (ancestor == edge.child().id() || !visited.add(ancestor)) {
          conflict = true;
          break;
        }
        final long key = ancestor;
        var parents =
            edges.stream()
                .filter(x -> x.parent().tenant() == edge.parent().tenant() && x.child().id() == key)
                .toList();
        if (parents.isEmpty()) break;
        if (parents.size() != 1) {
          conflict = true;
          break;
        }
        ancestor = parents.getFirst().parent().id();
      }
      if (conflict)
        legacy(c, edge.parent(), "child", edge.index(), edge.snapshot(), "重复父设备或循环结构，需确认");
      else
        try (var q = c.prepareStatement("INSERT INTO eam_equipment_child VALUES(?,?,?,?)")) {
          q.setLong(1, edge.parent().tenant());
          q.setLong(2, edge.parent().id());
          q.setLong(3, edge.child().id());
          q.setInt(4, edge.index());
          q.executeUpdate();
        }
    }
  }

  JsonNode array(String source) {
    try {
      var n = json.readTree(source == null ? "[]" : source);
      return n.isArray() ? n : json.createArrayNode();
    } catch (Exception e) {
      return json.createArrayNode();
    }
  }

  void legacy(Connection c, Device d, String kind, int index, JsonNode node, String reason)
      throws Exception {
    try (var q =
        c.prepareStatement("INSERT INTO eam_equipment_relation_legacy VALUES(?,?,?,?,?,?)")) {
      q.setLong(1, d.tenant());
      q.setLong(2, d.id());
      q.setString(3, kind);
      q.setInt(4, index);
      q.setString(5, json.writeValueAsString(node.isObject() ? node : json.createObjectNode().set("historicalValue", node)));
      q.setString(6, reason);
      q.executeUpdate();
    }
  }
}
