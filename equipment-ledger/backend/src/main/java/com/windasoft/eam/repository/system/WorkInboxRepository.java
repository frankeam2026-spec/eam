package com.windasoft.eam.repository.system;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkInboxRepository {
  private final JdbcTemplate db;

  public WorkInboxRepository(JdbcTemplate db) {
    this.db = db;
  }

  private static final String ROWS =
      """
(SELECT CAST(i.id AS CHAR) id,'repair' module,CAST(r.id AS CHAR) orderId,
  r.order_no orderNo,r.equipment_name equipmentName,r.status,i.kind,i.title,
  i.is_todo todo,i.done,(i.read_at IS NOT NULL) isRead,
  DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time
 FROM eam_repair_inbox i JOIN eam_repair_order r ON r.id=i.order_id AND r.tenant_id=i.tenant_id
 WHERE i.tenant_id=? AND i.user_id=? AND r.deleted=0
 UNION ALL
 SELECT CAST(i.id AS CHAR) id,'maintenance' module,CAST(t.id AS CHAR) orderId,
  t.code orderNo,JSON_UNQUOTE(JSON_EXTRACT(t.snapshot,'$.equipmentName')) equipmentName,
  t.status,i.kind,i.title,i.is_todo todo,i.done,(i.read_at IS NOT NULL) isRead,
  DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time
 FROM eam_maintenance_inbox i JOIN eam_maintenance_task t ON t.id=i.task_id AND t.tenant_id=i.tenant_id
 WHERE i.tenant_id=? AND i.user_id=?
 UNION ALL
 SELECT CAST(i.id AS CHAR) id,'inspection' module,CAST(t.id AS CHAR) orderId,
  t.code orderNo,JSON_UNQUOTE(JSON_EXTRACT(t.snapshot,'$.equipmentName')) equipmentName,
  t.status,i.kind,i.title,i.is_todo todo,i.done,(i.read_at IS NOT NULL) isRead,
  DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time
 FROM eam_inspection_inbox i JOIN eam_inspection_task t ON t.id=i.task_id AND t.tenant_id=i.tenant_id
 WHERE i.tenant_id=? AND i.user_id=? AND t.deleted=0
 UNION ALL
 SELECT CAST(i.id AS CHAR) id,'inventory' module,CAST(r.id AS CHAR) orderId,
 r.code orderNo,r.task_code equipmentName,r.status,i.kind,i.title,i.is_todo todo,i.done,(i.read_at IS NOT NULL) isRead,
 DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time
 FROM inv_inbox i JOIN inv_requisition r ON r.tenant_id=i.tenant_id AND r.id=i.document_id
 WHERE i.tenant_id=? AND i.user_id=? AND r.deleted=0 AND i.route_kind='requisitions'
 UNION ALL
 SELECT CAST(i.id AS CHAR) id,IF(i.route_kind='returns','inventoryReturn','inventoryTransfer') module,CAST(d.id AS CHAR) orderId,
 d.code orderNo,w.name equipmentName,d.status,i.kind,i.title,i.is_todo todo,i.done,(i.read_at IS NOT NULL) isRead,
 DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time FROM inv_inbox i JOIN inv_operation d ON d.tenant_id=i.tenant_id AND d.id=i.document_id JOIN inv_warehouse w ON w.tenant_id=d.tenant_id AND w.id=IF(i.route_kind='returns',d.warehouse_id,d.target_warehouse_id)
 WHERE i.tenant_id=? AND i.user_id=? AND d.deleted=0 AND i.route_kind IN ('transfers','returns')
 UNION ALL
 SELECT CAST(i.id AS CHAR) id,'inventoryOutbound' module,CAST(d.id AS CHAR) orderId,d.code orderNo,w.name equipmentName,d.status,i.kind,i.title,i.is_todo todo,i.done,(i.read_at IS NOT NULL) isRead,DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time FROM inv_inbox i JOIN inv_issue d ON d.tenant_id=i.tenant_id AND d.id=i.document_id JOIN inv_warehouse w ON w.tenant_id=d.tenant_id AND w.id=d.warehouse_id WHERE i.tenant_id=? AND i.user_id=? AND d.deleted=0 AND i.route_kind='outbounds'
 UNION ALL SELECT CAST(i.id AS CHAR) id,'anomaly' module,CAST(a.id AS CHAR) orderId,a.code orderNo,e.name equipmentName,a.status,i.kind,i.title,i.is_todo todo,i.done,(i.read_at IS NOT NULL) isRead,DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time FROM eam_experience_inbox i JOIN eam_equipment_anomaly a ON a.tenant_id=i.tenant_id AND a.id=i.object_id JOIN eam_equipment e ON e.tenant_id=a.tenant_id AND e.id=a.equipment_id WHERE i.tenant_id=? AND i.user_id=? AND i.object_kind='anomaly'
 UNION ALL SELECT CAST(i.id AS CHAR) id,'repairPlan' module,CAST(p.id AS CHAR) orderId,p.code orderNo,e.name equipmentName,p.status,i.kind,i.title,i.is_todo todo,i.done,(i.read_at IS NOT NULL) isRead,DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time FROM eam_experience_inbox i JOIN eam_single_repair_plan p ON p.tenant_id=i.tenant_id AND p.id=i.object_id JOIN eam_equipment e ON e.tenant_id=p.tenant_id AND e.id=p.equipment_id WHERE i.tenant_id=? AND i.user_id=? AND i.object_kind='repairPlan') inbox
""";

  private Object[] identity(long tenantId, long userId) {
    var values = new ArrayList<Object>();
    for (int i = 0; i < 8; i++) {
      values.add(tenantId);
      values.add(userId);
    }
    return values.toArray();
  }

  public Object summary(long tenantId, long userId) {
    var counts =
        db.queryForMap(
            "SELECT COALESCE(SUM(todo=1 AND done=0),0) pending,COALESCE(SUM(todo=1 AND done=1),0)"
                + " completed,COALESCE(SUM(isRead=0),0) unread FROM "
                + ROWS,
            identity(tenantId, userId));
    var kinds =
        db.queryForList(
            "SELECT module,kind,COALESCE(SUM(done=0),0) count,COALESCE(SUM(done=1),0) completed"
                + " FROM "
                + ROWS
                + " WHERE todo=1 GROUP BY module,kind ORDER BY module,kind",
            identity(tenantId, userId));
    return Map.of("counts", counts, "kinds", kinds);
  }

  public Object list(
      long tenantId,
      long userId,
      String channel,
      String state,
      String module,
      String kind,
      String search,
      int page,
      int size) {
    var args = new ArrayList<Object>(Arrays.asList(identity(tenantId, userId)));
    String where = " WHERE 1=1";
    if (channel.equals("todo"))
      where += " AND todo=1 AND done=" + (state.equals("completed") ? 1 : 0);
    else if (state.equals("unread")) where += " AND isRead=0";
    if (module.equals("repair")) where += " AND module IN ('repair','anomaly','repairPlan')";
    else if (module.equals("inventory"))
      where +=
          " AND module IN ('inventory','inventoryTransfer','inventoryOutbound','inventoryReturn')";
    else if (!module.isBlank()) {
      where += " AND module=?";
      args.add(module);
    }
    if (!kind.isBlank()) {
      where += " AND kind=?";
      args.add(kind);
    }
    if (!search.isBlank()) {
      where += " AND (LOCATE(?,orderNo)>0 OR LOCATE(?,equipmentName)>0 OR LOCATE(?,title)>0)";
      args.add(search.trim());
      args.add(search.trim());
      args.add(search.trim());
    }
    long total =
        db.queryForObject("SELECT COUNT(*) FROM " + ROWS + where, Long.class, args.toArray());
    args.add(size);
    args.add((long) (page - 1) * size);
    return Map.of(
        "data",
        db.queryForList(
            "SELECT * FROM " + ROWS + where + " ORDER BY time DESC,id DESC LIMIT ? OFFSET ?",
            args.toArray()),
        "total",
        total);
  }

  public Object read(long tenantId, long userId, String module, String id) {
    if (module.isBlank() || module.equals("anomaly") || module.equals("repairPlan")) {
      var args = new ArrayList<Object>();
      args.add(tenantId);
      args.add(userId);
      String filter = "";
      if (!module.isBlank()) {
        filter += " AND object_kind=?";
        args.add(module);
      }
      if (!id.isBlank()) {
        filter += " AND id=?";
        args.add(id);
      }
      db.update(
          "UPDATE eam_experience_inbox SET read_at=NOW(3) WHERE tenant_id=? AND user_id=? AND"
              + " read_at IS NULL"
              + filter,
          args.toArray());
    }
    for (var entry :
        Map.of(
                "inventory",
                "inv_inbox",
                "repair",
                "eam_repair_inbox",
                "maintenance",
                "eam_maintenance_inbox",
                "inspection",
                "eam_inspection_inbox")
            .entrySet()) {
      if (!module.isBlank() && !entry.getKey().equals(module)) continue;
      var args = new ArrayList<Object>();
      args.add(tenantId);
      args.add(userId);
      if (!id.isBlank()) args.add(id);
      db.update(
          "UPDATE "
              + entry.getValue()
              + " SET read_at=NOW() WHERE tenant_id=? AND user_id=? AND read_at IS NULL"
              + (id.isBlank() ? "" : " AND id=?"),
          args.toArray());
    }
    return Map.of("success", true);
  }
}
