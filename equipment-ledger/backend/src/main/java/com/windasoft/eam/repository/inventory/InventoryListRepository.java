package com.windasoft.eam.repository.inventory;

import static com.windasoft.eam.repository.common.SqlListFilters.Kind.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.repository.common.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryListRepository {
  private final JdbcListRepository lists;
  private final ObjectMapper json;

  public InventoryListRepository(JdbcListRepository lists, ObjectMapper json) {
    this.lists = lists;
    this.json = json;
  }

  public record Selection(String projection, String from, SqlPredicate predicate, String order) {}

  public Set<Long> warehouseIds(long tenant) {
    var ids = new HashSet<Long>();
    for (var row :
        lists.distinct("t.id", "FROM inv_warehouse t", SqlPredicate.of("t.tenant_id=?", tenant)))
      ids.add(((Number) row.get("id")).longValue());
    return ids;
  }

  private static SqlListFilters.Field f(String sql, SqlListFilters.Kind type) {
    return new SqlListFilters.Field(sql, type);
  }

  public Selection masters(
      long tenant, String kind, ListQuery query, Set<Long> warehouses, boolean money) {
    String table = InventoryMasterRepository.table(kind), from = "FROM " + table + " t";
    var fields = new LinkedHashMap<String, SqlListFilters.Field>();
    for (String key : List.of("code", "name", "remark")) fields.put(key, f("t." + key, TEXT));
    fields.put("enabled", f("CASE WHEN t.enabled=1 THEN '启用' ELSE '停用' END", CHOICE));
    String projection = "t.*";
    var predicate = SqlPredicate.of("t.tenant_id=?", tenant);
    if (kind.equals("materials")) {
      for (String key : List.of("model", "unit", "category")) fields.put(key, f("t." + key, TEXT));
      fields.put("usageType", f("t.usage_type", CHOICE));
      fields.put("batchEnabled", f("CASE WHEN t.batch_enabled=1 THEN '启用' ELSE '未启用' END", CHOICE));
      fields.put(
          "expiryEnabled", f("CASE WHEN t.expiry_enabled=1 THEN '启用' ELSE '未启用' END", CHOICE));
      if (money) fields.put("referencePrice", f("t.reference_price", NUMBER));
    } else if (kind.equals("warehouses")) {
      from +=
          " LEFT JOIN eam_org_unit o ON o.tenant_id=t.tenant_id AND o.id=t.company_id LEFT JOIN"
              + " eam_sys_user u ON u.tenant_id=t.tenant_id AND u.id=t.manager_id";
      fields.put("companyId", f("COALESCE(o.name,'')", TEXT));
      fields.put("managerId", f("COALESCE(u.display_name,'')", TEXT));
      projection += ",o.name company_name,u.display_name manager_name";
      predicate = predicate.and(SqlScopes.in("t.id", warehouses));
    } else {
      from += " LEFT JOIN inv_warehouse w ON w.tenant_id=t.tenant_id AND w.id=t.warehouse_id";
      fields.put("warehouseId", f("COALESCE(w.name,'')", TEXT));
      projection += ",w.name warehouse_name";
      predicate = predicate.and(SqlScopes.in("t.warehouse_id", warehouses));
    }
    if (!kind.equals("bins"))
      fields.put(
          "allocationRule",
          f(
              "CASE t.allocation_rule WHEN 'FIFO' THEN '先进先出' WHEN 'FEFO' THEN '先到期先出' ELSE '跟随仓库'"
                  + " END",
              CHOICE));
    var clean = query;
    try {
      var filters = json.readTree(query.filters()).deepCopy();
      if (filters.has("_warehouseId")) {
        if (!kind.equals("bins"))
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.inventory.message0001");
        predicate =
            predicate.and(
                SqlPredicate.of(
                    "t.warehouse_id=?", Long.parseLong(filters.path("_warehouseId").asText())));
        ((com.fasterxml.jackson.databind.node.ObjectNode) filters).remove("_warehouseId");
        clean =
            new ListQuery(
                query.page(),
                query.size(),
                query.keyword(),
                query.conditions(),
                filters.toString(),
                query.sortBy(),
                query.direction(),
                query.queue(),
                query.organization());
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0009");
    }
    predicate = predicate.and(SqlListFilters.apply(json, clean, fields));
    if (!query.keyword().isBlank())
      predicate =
          predicate.and(
              SqlListFilters.contains(
                  "CONCAT_WS(' ',t.code,t.name"
                      + (kind.equals("materials") ? ",t.model" : "")
                      + ")",
                  query.keyword()));
    return new Selection(
        projection, from, predicate, SqlListFilters.order(query, fields, "t.code,t.id"));
  }

  public Selection receipts(
      long tenant, ListQuery query, Set<Long> warehouses, SqlPredicate custom, String customOrder) {
    String actor =
        "COALESCE((SELECT e.actor_name FROM inv_document_event e WHERE e.tenant_id=t.tenant_id AND"
            + " e.document_kind='入库' AND e.document_id=t.id ORDER BY e.created_at DESC,e.id DESC"
            + " LIMIT 1),u.display_name,'')";
    String from =
        "FROM inv_receipt t JOIN inv_warehouse w ON w.tenant_id=t.tenant_id AND w.id=t.warehouse_id"
            + " LEFT JOIN eam_sys_user u ON u.tenant_id=t.tenant_id AND u.id=t.created_by";
    var fields = new LinkedHashMap<String, SqlListFilters.Field>();
    fields.put("code", f("t.code", TEXT));
    fields.put("supplier", f("t.supplier", TEXT));
    fields.put("warehouseName", f("w.name", TEXT));
    fields.put("status", f("t.status", CHOICE));
    fields.put("actorName", f(actor, TEXT));
    fields.put("createdAt", f("t.created_at", DATETIME));
    fields.put("confirmedAt", f("t.confirmed_at", DATETIME));
    var predicate =
        SqlPredicate.of("t.tenant_id=? AND t.deleted=0", tenant)
            .and(SqlScopes.in("t.warehouse_id", warehouses))
            .and(SqlListFilters.apply(json, query, fields))
            .and(custom);
    if (!query.keyword().isBlank())
      predicate =
          predicate.and(
              SqlListFilters.contains("CONCAT_WS(' ',t.code,t.supplier,w.name)", query.keyword()));
    return new Selection(
        "t.*,DATE_FORMAT(t.created_at,'%Y-%m-%d %H:%i:%s')"
            + " fmt_created_at,DATE_FORMAT(t.confirmed_at,'%Y-%m-%d %H:%i:%s')"
            + " fmt_confirmed_at,w.name warehouse_name,"
            + actor
            + " actor_name",
        from,
        predicate,
        customOrder == null
            ? SqlListFilters.order(query, fields, "t.created_at DESC,t.id DESC")
            : customOrder + " " + query.direction() + ",t.id " + query.direction());
  }

  public Selection outbounds(
      long tenant,
      ListQuery query,
      Set<Long> warehouses,
      boolean money,
      SqlPredicate custom,
      String customOrder) {
    String regular =
        "SELECT CAST(i.id AS CHAR)"
            + " id,i.tenant_id,i.code,i.company_id,i.warehouse_id,i.requisition_id,i.source_kind,i.status,i.row_version,i.actor_id,i.actor_name,i.created_at,i.confirmed_at,i.total_amount,0"
            + " legacy,COALESCE((SELECT GROUP_CONCAT(DISTINCT l.recipient_name ORDER BY"
            + " l.recipient_name SEPARATOR '、') FROM inv_outbound_line l WHERE"
            + " l.tenant_id=i.tenant_id AND l.issue_id=i.id),(SELECT r.applicant_name FROM"
            + " inv_requisition r WHERE r.tenant_id=i.tenant_id AND r.id=i.requisition_id))"
            + " recipient_name,COALESCE((SELECT GROUP_CONCAT(DISTINCT l.task_code ORDER BY"
            + " l.task_code SEPARATOR '、') FROM inv_outbound_line l WHERE l.tenant_id=i.tenant_id"
            + " AND l.issue_id=i.id),'') task_code,COALESCE((SELECT SUM(a.quantity) FROM"
            + " inv_issue_allocation a WHERE a.tenant_id=i.tenant_id AND a.issue_id=i.id),(SELECT"
            + " SUM(l.quantity) FROM inv_outbound_line l WHERE l.tenant_id=i.tenant_id AND"
            + " l.issue_id=i.id)) total_quantity FROM inv_issue i WHERE i.deleted=0 AND i.group_id"
            + " IS NULL";
    String legacy =
        "SELECT CONCAT('g',g.id),g.tenant_id,g.code,w.company_id,g.warehouse_id,NULL,'历史合并',CASE"
            + " WHEN EXISTS(SELECT 1 FROM inv_issue i WHERE i.tenant_id=g.tenant_id AND"
            + " i.group_id=g.id AND i.reversed=0) THEN '已出库' ELSE '已冲销'"
            + " END,0,g.actor_id,g.actor_name,g.created_at,NULL,(SELECT SUM(i.total_amount) FROM"
            + " inv_issue i WHERE i.tenant_id=g.tenant_id AND i.group_id=g.id),1,'','',(SELECT"
            + " SUM(a.quantity) FROM inv_issue_allocation a JOIN inv_issue i ON"
            + " i.tenant_id=a.tenant_id AND i.id=a.issue_id WHERE i.tenant_id=g.tenant_id AND"
            + " i.group_id=g.id) FROM inv_issue_group g JOIN inv_warehouse w ON"
            + " w.tenant_id=g.tenant_id AND w.id=g.warehouse_id";
    String from =
        "FROM ("
            + regular
            + " UNION ALL "
            + legacy
            + ") t JOIN inv_warehouse w ON w.tenant_id=t.tenant_id AND w.id=t.warehouse_id";
    var fields = new LinkedHashMap<String, SqlListFilters.Field>();
    for (String key : List.of("code", "status")) fields.put(key, f("t." + key, TEXT));
    fields.put("sourceKind", f("t.source_kind", CHOICE));
    fields.put("warehouseName", f("w.name", TEXT));
    fields.put("actorName", f("t.actor_name", TEXT));
    fields.put("recipientName", f("t.recipient_name", TEXT));
    fields.put("taskCode", f("t.task_code", TEXT));
    fields.put("createdAt", f("t.created_at", DATETIME));
    fields.put("confirmedAt", f("t.confirmed_at", DATETIME));
    fields.put("totalQuantity", f("t.total_quantity", NUMBER));
    if (money) fields.put("totalAmount", f("t.total_amount", NUMBER));
    var predicate =
        SqlPredicate.of("t.tenant_id=?", tenant)
            .and(SqlScopes.in("t.warehouse_id", warehouses))
            .and(SqlListFilters.apply(json, query, fields))
            .and(custom);
    if (!query.keyword().isBlank())
      predicate =
          predicate.and(
              SqlListFilters.contains(
                  "CONCAT_WS(' ',t.code,t.recipient_name,w.name,t.task_code)", query.keyword()));
    return new Selection(
        "t.*,w.name warehouse_name,DATE_FORMAT(t.created_at,'%Y-%m-%d %H:%i:%s')"
            + " fmt_created_at,DATE_FORMAT(t.confirmed_at,'%Y-%m-%d %H:%i:%s') fmt_confirmed_at",
        from,
        predicate,
        customOrder == null
            ? SqlListFilters.order(query, fields, "t.created_at DESC,t.id DESC")
            : customOrder + " " + query.direction() + ",t.id " + query.direction());
  }

  public Selection requisitions(
      long tenant, long user, ListQuery query, Set<Long> warehouses, boolean own, boolean pending) {
    String
        remaining =
            "(SELECT COALESCE(SUM(l.quantity-l.issued_quantity),0) FROM inv_requisition_line l"
                + " WHERE l.tenant_id=t.tenant_id AND l.requisition_id=t.id)",
        issued =
            "(SELECT COALESCE(SUM(l.issued_quantity),0) FROM inv_requisition_line l WHERE"
                + " l.tenant_id=t.tenant_id AND l.requisition_id=t.id)",
        reserved =
            "(SELECT COALESCE(SUM(r.remaining_quantity),0) FROM inv_reservation r WHERE"
                + " r.tenant_id=t.tenant_id AND r.requisition_id=t.id)";
    String status =
        "CASE WHEN t.status IN ('待预留','部分预留','已预留','部分发料') AND "
            + issued
            + ">0 THEN '部分出库' WHEN t.status IN ('待预留','部分预留','已预留') THEN '待出库' WHEN t.status='部分发料'"
            + " THEN '部分出库' ELSE t.status END";
    String reservation =
        "CASE WHEN "
            + reserved
            + "=0 THEN '未预留' WHEN "
            + reserved
            + ">="
            + remaining
            + " THEN '已预留' ELSE '部分预留' END";
    String from =
        "FROM inv_requisition t JOIN inv_warehouse w ON w.tenant_id=t.tenant_id AND"
            + " w.id=t.warehouse_id";
    var fields = new LinkedHashMap<String, SqlListFilters.Field>();
    fields.put("code", f("t.code", TEXT));
    fields.put("warehouseName", f("w.name", TEXT));
    fields.put("taskKind", f("t.task_kind", CHOICE));
    fields.put("taskCode", f("t.task_code", TEXT));
    fields.put("applicantName", f("t.applicant_name", TEXT));
    fields.put("status", f(status, CHOICE));
    fields.put("reservationStatus", f(reservation, CHOICE));
    fields.put("remainingQuantity", f(remaining, NUMBER));
    fields.put("createdAt", f("t.created_at", DATETIME));
    var access = SqlScopes.in("t.warehouse_id", warehouses);
    if (own) access = access.or(SqlPredicate.of("t.applicant_id=?", user))
        .or(SqlPredicate.of("EXISTS(SELECT 1 FROM wf_instance wi JOIN wf_approval wa ON wa.tenant_id=wi.tenant_id AND wa.instance_id=wi.id WHERE wi.tenant_id=t.tenant_id AND wi.business_type='requisition' AND wi.business_id=t.id AND wa.user_id=?)",user));
    var predicate = SqlPredicate.of("t.tenant_id=? AND t.deleted=0", tenant).and(access);
    var clean = query;
    try {
      var filters = json.readTree(query.filters()).deepCopy();
      if (filters.has("pendingOut")) {
        if (filters.path("pendingOut").asText().equals("1")) pending = true;
        ((com.fasterxml.jackson.databind.node.ObjectNode) filters).remove("pendingOut");
        clean =
            new ListQuery(
                query.page(),
                query.size(),
                query.keyword(),
                query.conditions(),
                filters.toString(),
                query.sortBy(),
                query.direction(),
                query.queue(),
                query.organization());
      }
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0009");
    }
    if (pending)
      predicate = predicate.and(SqlPredicate.of("t.status IN ('待预留','部分预留','已预留','部分发料')"));
    predicate = predicate.and(SqlListFilters.apply(json, clean, fields));
    if (!query.keyword().isBlank())
      predicate =
          predicate.and(
              SqlListFilters.contains(
                  "CONCAT_WS(' ',t.code,t.task_code,t.applicant_name,w.name)", query.keyword()));
    return new Selection(
        "t.*,w.name warehouse_name,"
            + status
            + " business_status,"
            + reservation
            + " reservation_status,"
            + remaining
            + " remaining_quantity,DATE_FORMAT(t.created_at,'%Y-%m-%d %H:%i:%s') fmt_created_at",
        from,
        predicate,
        SqlListFilters.order(query, fields, "t.created_at DESC,t.id DESC"));
  }

  public Selection operations(
      long tenant,
      long user,
      String kind,
      ListQuery query,
      Set<Long> readWarehouses,
      Set<Long> exportWarehouses,
      boolean export) {
    String from =
        "FROM v_inv_operation t JOIN inv_warehouse w ON w.tenant_id=t.tenant_id AND"
            + " w.id=t.warehouse_id";
    var access = SqlScopes.in("t.warehouse_id", readWarehouses);
    if (kind.equals("transfers"))
      access = access.or(SqlScopes.in("t.target_warehouse_id", readWarehouses));
    if (kind.equals("returns"))
      access = access.or(SqlPredicate.of("t.surplus_requested_by=?", Long.toString(user)));
    var predicate =
        SqlPredicate.of("t.tenant_id=? AND t.kind=? AND t.deleted=0", tenant, kind).and(access);
    if (export) predicate = predicate.and(SqlScopes.in("t.warehouse_id", exportWarehouses));
    var fields =
        Map.of(
            "code",
            f("t.code", TEXT),
            "warehouseName",
            f("w.name", TEXT),
            "status",
            f("t.status", CHOICE),
            "createdAt",
            f("t.created_at", DATETIME));
    predicate = predicate.and(SqlListFilters.apply(json, query, fields));
    if (!query.keyword().isBlank())
      predicate =
          predicate.and(
              SqlListFilters.contains("CONCAT_WS(' ',t.code,w.name,t.status)", query.keyword()));
    return new Selection(
        "t.*,w.name warehouse_name,DATE_FORMAT(t.created_at,'%Y-%m-%d %H:%i:%s')"
            + " fmt_created_at,DATE_FORMAT(t.confirmed_at,'%Y-%m-%d %H:%i:%s') fmt_confirmed_at",
        from, predicate, SqlListFilters.order(query, fields, "t.created_at DESC,t.id DESC"));
  }

  public Selection restrictWarehouse(Selection selected, Set<Long> warehouses) {
    return new Selection(
        selected.projection(),
        selected.from(),
        selected.predicate().and(SqlScopes.in("t.warehouse_id", warehouses)),
        selected.order());
  }

  public long count(Selection query) {
    return lists.count(query.from(), query.predicate());
  }

  public List<Map<String, Object>> page(Selection selected, ListQuery query) {
    return lists.rows(
        selected.projection(), selected.from(), selected.predicate(), selected.order(), query);
  }
}
