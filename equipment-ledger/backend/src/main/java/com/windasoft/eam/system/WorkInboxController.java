package com.windasoft.eam.system;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/work-inbox")
public class WorkInboxController {
  private final JdbcTemplate db;
  private final AuthContext auth;

  public WorkInboxController(JdbcTemplate db, AuthContext auth) {
    this.db = db;
    this.auth = auth;
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
 SELECT CAST(i.id AS CHAR) id,'inventoryTransfer' module,CAST(d.id AS CHAR) orderId,
 d.code orderNo,w.name equipmentName,d.status,i.kind,i.title,i.is_todo todo,i.done,(i.read_at IS NOT NULL) isRead,
 DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time FROM inv_inbox i JOIN inv_operation d ON d.tenant_id=i.tenant_id AND d.id=i.document_id JOIN inv_warehouse w ON w.tenant_id=d.tenant_id AND w.id=d.target_warehouse_id
 WHERE i.tenant_id=? AND i.user_id=? AND d.deleted=0 AND i.route_kind='transfers'
 UNION ALL
 SELECT CAST(i.id AS CHAR) id,'inventoryOutbound' module,CAST(d.id AS CHAR) orderId,d.code orderNo,w.name equipmentName,d.status,i.kind,i.title,i.is_todo todo,i.done,(i.read_at IS NOT NULL) isRead,DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time FROM inv_inbox i JOIN inv_issue d ON d.tenant_id=i.tenant_id AND d.id=i.document_id JOIN inv_warehouse w ON w.tenant_id=d.tenant_id AND w.id=d.warehouse_id WHERE i.tenant_id=? AND i.user_id=? AND d.deleted=0 AND i.route_kind='outbounds') inbox
""";

  private Object[] identity() {
    return new Object[] {
      auth.tenantId(),
      auth.userId(),
      auth.tenantId(),
      auth.userId(),
      auth.tenantId(),
      auth.userId(),
      auth.tenantId(),
      auth.userId(),
      auth.tenantId(),
      auth.userId(),
      auth.tenantId(),
      auth.userId()
    };
  }

  @GetMapping("/summary")
  public Object summary() {
    var counts =
        db.queryForMap(
            "SELECT COALESCE(SUM(todo=1 AND done=0),0) pending,COALESCE(SUM(todo=1 AND done=1),0)"
                + " completed,COALESCE(SUM(isRead=0),0) unread FROM "
                + ROWS,
            identity());
    var kinds =
        db.queryForList(
            "SELECT module,kind,COALESCE(SUM(done=0),0) count,COALESCE(SUM(done=1),0) completed"
                + " FROM "
                + ROWS
                + " WHERE todo=1 GROUP BY module,kind ORDER BY module,kind",
            identity());
    return Map.of("counts", counts, "kinds", kinds);
  }

  @GetMapping
  public Object list(
      @RequestParam(defaultValue = "todo") String channel,
      @RequestParam(defaultValue = "pending") String state,
      @RequestParam(defaultValue = "") String module,
      @RequestParam(defaultValue = "") String kind,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    if (!Set.of("todo", "notification").contains(channel)
        || !Set.of("pending", "completed", "unread", "all").contains(state))
      throw new IllegalArgumentException("未知收件箱筛选");
    page = Math.max(1, page);
    size = Math.min(100, Math.max(1, size));
    var args = new ArrayList<Object>(Arrays.asList(identity()));
    String where = " WHERE 1=1";
    if (channel.equals("todo"))
      where += " AND todo=1 AND done=" + (state.equals("completed") ? 1 : 0);
    else if (state.equals("unread")) where += " AND isRead=0";
    if (module.equals("inventory"))
      where += " AND module IN ('inventory','inventoryTransfer','inventoryOutbound')";
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

  @PatchMapping("/read")
  public Object read(@RequestBody Map<String, Object> body) {
    String module = Objects.toString(body.get("module"), "");
    String id = Objects.toString(body.get("id"), "");
    if (module.equals("inventoryTransfer") || module.equals("inventoryOutbound"))
      module = "inventory";
    if (!module.isBlank()
        && !Set.of("repair", "maintenance", "inspection", "inventory").contains(module))
      throw new IllegalArgumentException("未知消息模块");
    if (!id.isBlank() && !id.matches("[0-9]{1,20}")) throw new IllegalArgumentException("消息编号无效");
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
      args.add(auth.tenantId());
      args.add(auth.userId());
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
