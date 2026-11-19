package com.windasoft.eam.repository.inventory;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.inventory.InventoryQueryRow;
import com.windasoft.eam.dto.inventory.InventoryQueryRow.Amount;
import com.windasoft.eam.dto.inventory.InventoryQueryRow.Balance;
import com.windasoft.eam.dto.inventory.InventoryQueryRow.BalanceCost;
import com.windasoft.eam.dto.inventory.InventoryQueryRow.Layer;
import com.windasoft.eam.dto.inventory.InventoryQueryRow.LayerCost;
import com.windasoft.eam.dto.inventory.InventoryQueryRow.Source;
import com.windasoft.eam.dto.inventory.InventoryQueryRow.Stock;
import com.windasoft.eam.dto.inventory.InventoryQueryRow.Transaction;
import com.windasoft.eam.dto.inventory.InventoryQueryRow.Transit;
import com.windasoft.eam.repository.common.AssistantReadSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryQueryRepository {
  private final JdbcTemplate db;

  public InventoryQueryRepository(JdbcTemplate db) {
    this.db = db;
  }

  public static final class Query {
    private final String sql;
    private final List<Object> args;
    private final Kind kind;

    private Query(String sql, List<Object> args, Kind kind) {
      this.kind = kind;
      this.sql = sql;
      this.args = Collections.unmodifiableList(new ArrayList<>(args));
    }

    private String sql() {
      return sql;
    }

    private List<Object> args() {
      return args;
    }
  }

  private enum Kind {
    BALANCES("balances", "s.id DESC", "s.id"),
    LAYERS("layers", "c.origin_received_at DESC,c.id DESC", "s.id,c.id"),
    TRANSACTIONS("transactions", "t.created_at DESC,t.id DESC", "s.id,t.id"),
    TRANSIT("transit", "d.confirmed_at DESC,a.id DESC", "s.id,a.id");

    private final String key;
    private final String order;
    private final String exportOrder;

    Kind(String key, String order, String exportOrder) {
      this.key = key;
      this.order = order;
      this.exportOrder = exportOrder;
    }

    static Kind parse(String value) {
      for (var kind : values()) {
        if (kind.key.equals(value)) return kind;
      }
      throw new LocalizedArgumentException("server.inventory.message0004");
    }
  }

  public AssistantReadSource assistantSource(Query query, long tenant) {
    var args = new ArrayList<Object>();
    args.add(tenant);
    args.addAll(query.args());
    return new AssistantReadSource(
        "SELECT CAST(id AS CHAR) id,material_code code,material_name name,model,unit,company_name"
            + " company,CAST((SELECT company_id FROM inv_warehouse w WHERE w.tenant_id=? AND"
            + " w.id=stock.warehouse_id) AS CHAR) companyId,warehouse_name"
            + " warehouse,CAST(warehouse_id AS CHAR) warehouseId,bin_name bin,batch_code"
            + " batch,stock_status status,quantity,reserved_quantity reserved,available_quantity"
            + " available,expiry_date expiryDate FROM ("
            + query.sql()
            + ") stock",
        args);
  }

  public Query query(
      long tenantId,
      String kind,
      String q,
      Map<String, String> filters,
      Collection<Long> warehouses) {
    var type = Kind.parse(kind);
    var fields = new LinkedHashMap<String, String>();
    fields.put("materialCode", "m.code");
    fields.put("materialName", "m.name");
    fields.put("model", "m.model");
    fields.put("unit", "m.unit");
    fields.put("companyName", "o.name");
    fields.put("warehouseName", "w.name");
    fields.put("binName", "b.name");
    fields.put("batchCode", "COALESCE(pc.code,'')");
    fields.put("supplierBatch", "COALESCE(pc.supplier_batch,'')");
    fields.put("productionDate", "COALESCE(CAST(pc.production_date AS CHAR),'')");
    fields.put("expiryDate", "COALESCE(CAST(pc.expiry_date AS CHAR),'')");
    String extra;
    switch (type) {
      case BALANCES -> {
        fields.put("stockStatus", "s.stock_status");
        fields.put("quantity", "CAST(s.quantity AS CHAR)");
        fields.put("reservedQuantity", "CAST(s.reserved_quantity AS CHAR)");
        extra =
            "s.id,s.row_version,s.quantity,s.reserved_quantity,s.stock_status,CASE WHEN"
                + " s.stock_status='可用' AND m.enabled=1 AND w.enabled=1 AND b.enabled=1 AND"
                + " (pc.expiry_date IS NULL OR pc.expiry_date>=?) AND (m.expiry_enabled=0 OR"
                + " pc.expiry_date IS NOT NULL) THEN s.quantity-s.reserved_quantity ELSE 0 END"
                + " available_quantity,CASE WHEN pc.expiry_date<? THEN '已过期' WHEN"
                + " m.expiry_enabled=1 AND pc.expiry_date IS NULL THEN '待补有效期' ELSE '' END"
                + " expiry_status,(SELECT CASE WHEN COUNT(*)=COUNT(c.remaining_cost) THEN"
                + " SUM(c.remaining_cost) ELSE NULL END FROM inv_cost_layer c WHERE"
                + " c.tenant_id=s.tenant_id AND c.balance_id=s.id) remaining_cost";
      }
      case LAYERS -> {
        fields.put("sourceCode", "COALESCE(r.code,d.code)");
        fields.put("costStatus", "c.cost_status");
        fields.put("receivedAt", "CAST(c.origin_received_at AS CHAR)");
        extra =
            "c.id,c.source_line_id,c.origin_received_at"
                + " received_at,c.quantity,c.remaining_quantity,c.unit_cost,c.remaining_cost,c.cost_status,c.currency,COALESCE(r.code,d.code)"
                + " source_code,COALESCE(r.id,d.id) source_id,COALESCE(d.kind,'receipt')"
                + " source_kind";
      }
      case TRANSACTIONS -> {
        fields.put("sourceCode", "COALESCE(r.code,i.code,d.code)");
        fields.put("kind", "t.kind");
        fields.put("createdAt", "CAST(t.created_at AS CHAR)");
        fields.put("actorName", "u.display_name");
        extra =
            "t.id,t.kind,t.quantity,t.amount,t.created_at,u.display_name"
                + " actor_name,COALESCE(r.code,i.code,d.code)"
                + " source_code,COALESCE(r.id,i.id,d.id) source_id,CASE WHEN d.id IS"
                + " NOT NULL THEN d.kind WHEN i.id IS NULL THEN 'receipt' ELSE 'issue' END"
                + " source_kind";
      }
      case TRANSIT -> {
        fields.put("sourceCode", "d.code");
        fields.put("targetWarehouseName", "tw.name");
        extra =
            "a.id,d.code source_code,d.id source_id,'transfers' source_kind,-a.quantity"
                + " quantity,-a.amount amount,tw.name target_warehouse_name,d.confirmed_at"
                + " created_at";
      }
      default -> throw new LocalizedArgumentException("server.inventory.message0004");
    }
    var args = new ArrayList<Object>();
    if (type == Kind.BALANCES) {
      var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
      args.add(today);
      args.add(today);
    }
    String sql =
        "SELECT "
            + extra
            + ",s.warehouse_id,m.code material_code,m.name material_name,m.model,m.unit,o.name"
            + " company_name,w.name warehouse_name,b.name bin_name,pc.code"
            + " batch_code,pc.supplier_batch,pc.production_date,pc.expiry_date FROM inv_balance s"
            + " JOIN inv_material m ON m.tenant_id=s.tenant_id AND m.id=s.material_id JOIN"
            + " inv_warehouse w ON w.tenant_id=s.tenant_id AND w.id=s.warehouse_id JOIN"
            + " eam_org_unit o ON o.tenant_id=w.tenant_id AND o.id=w.company_id JOIN inv_bin b ON"
            + " b.tenant_id=s.tenant_id AND b.id=s.bin_id LEFT JOIN inv_batch pc ON"
            + " pc.tenant_id=s.tenant_id AND pc.id=s.batch_id";
    if (type == Kind.LAYERS)
      sql +=
          " JOIN inv_cost_layer c ON c.tenant_id=s.tenant_id AND c.balance_id=s.id LEFT JOIN"
              + " inv_receipt_line l ON l.tenant_id=c.tenant_id AND l.id=c.source_line_id LEFT JOIN"
              + " inv_receipt r ON r.tenant_id=l.tenant_id AND r.id=l.receipt_id LEFT JOIN"
              + " v_inv_operation d ON d.tenant_id=c.tenant_id AND d.id=c.source_line_id";
    if (type == Kind.TRANSACTIONS)
      sql +=
          " JOIN inv_transaction t ON t.tenant_id=s.tenant_id AND t.balance_id=s.id LEFT JOIN"
              + " inv_receipt r ON r.tenant_id=t.tenant_id AND r.id=t.document_id AND t.kind='入库'"
              + " LEFT JOIN inv_issue i ON i.tenant_id=t.tenant_id AND i.id=t.document_id AND"
              + " t.kind='出库' LEFT JOIN v_inv_operation d ON d.tenant_id=t.tenant_id AND"
              + " d.id=t.document_id LEFT JOIN eam_sys_user u ON u.tenant_id=t.tenant_id AND"
              + " u.id=t.actor_id";
    if (type == Kind.TRANSIT)
      sql +=
          " JOIN inv_movement_allocation a ON a.tenant_id=s.tenant_id AND a.balance_id=s.id AND"
              + " a.quantity<0 JOIN v_inv_operation d ON d.tenant_id=a.tenant_id AND"
              + " d.id=a.document_id AND d.kind='transfers' AND d.status='在途' JOIN inv_warehouse tw"
              + " ON tw.tenant_id=d.tenant_id AND tw.id=d.target_warehouse_id";
    sql += " WHERE s.tenant_id=?";
    args.add(tenantId);
    if (warehouses.isEmpty()) sql += " AND 1=0";
    else {
      sql +=
          " AND s.warehouse_id IN ("
              + String.join(",", Collections.nCopies(warehouses.size(), "?"))
              + ")";
      args.addAll(warehouses);
    }
    if (!q.isBlank()) {
      sql +=
          " AND (m.code LIKE ? ESCAPE '!' OR m.name LIKE ? ESCAPE '!' OR m.model LIKE ? ESCAPE '!'"
              + " OR pc.code LIKE ? ESCAPE '!')";
      for (int i = 0; i < 4; i++) args.add(like(q));
    }
    if (filters == null || !fields.keySet().containsAll(filters.keySet()))
      throw new LocalizedArgumentException("server.inventory.message0005");
    for (var filter : filters.entrySet())
      if (filter.getValue() != null && !filter.getValue().isBlank()) {
        sql += " AND " + fields.get(filter.getKey()) + " LIKE ? ESCAPE '!'";
        args.add(like(filter.getValue()));
      }
    return new Query(sql, args, type);
  }

  private String like(String value) {
    return "%" + value.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }

  public long count(Query query) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM (" + query.sql() + ") results", Long.class, query.args().toArray());
  }

  public List<InventoryQueryRow> list(Query query, int page, int size, Set<Long> costWarehouses) {
    var args = new ArrayList<Object>(query.args());
    args.add(size);
    args.add(((long) page - 1) * size);
    return db.query(
        query.sql() + " ORDER BY " + query.kind.order + " LIMIT ? OFFSET ?",
        (row, index) -> readRow(query.kind, row, costWarehouses),
        args.toArray());
  }

  public List<InventoryQueryRow> export(Query query, Set<Long> costWarehouses) {
    return db.query(
        query.sql() + " ORDER BY " + query.kind.exportOrder,
        (row, index) -> readRow(query.kind, row, costWarehouses),
        query.args().toArray());
  }

  /** 在结果映射时剔除无金额权限的字段，金额未知和不可查看保持不同语义。 */
  private InventoryQueryRow readRow(Kind kind, ResultSet row, Set<Long> costWarehouses)
      throws SQLException {
    var stock =
        new Stock(
            row.getString("warehouse_id"),
            row.getString("material_code"),
            row.getString("material_name"),
            row.getString("model"),
            row.getString("unit"),
            row.getString("company_name"),
            row.getString("warehouse_name"),
            row.getString("bin_name"),
            row.getString("batch_code"),
            row.getString("supplier_batch"),
            row.getDate("production_date"),
            row.getDate("expiry_date"));
    boolean showCost = costWarehouses.contains(row.getLong("warehouse_id"));
    String id = row.getString("id");
    String quantity = decimal(row, "quantity");
    return switch (kind) {
      case BALANCES ->
          new Balance(
              id,
              row.getInt("row_version"),
              quantity,
              decimal(row, "reserved_quantity"),
              row.getString("stock_status"),
              decimal(row, "available_quantity"),
              row.getString("expiry_status"),
              stock,
              showCost ? new BalanceCost(decimal(row, "remaining_cost")) : null);
      case LAYERS ->
          new Layer(
              id,
              Objects.toString(row.getString("source_line_id"), ""),
              row.getObject("received_at", LocalDateTime.class),
              quantity,
              decimal(row, "remaining_quantity"),
              row.getString("cost_status"),
              row.getString("currency"),
              source(row),
              stock,
              showCost
                  ? new LayerCost(decimal(row, "unit_cost"), decimal(row, "remaining_cost"))
                  : null);
      case TRANSACTIONS ->
          new Transaction(
              id,
              row.getString("kind"),
              quantity,
              row.getObject("created_at", LocalDateTime.class),
              row.getString("actor_name"),
              source(row),
              stock,
              showCost ? new Amount(decimal(row, "amount")) : null);
      case TRANSIT ->
          new Transit(
              id,
              quantity,
              row.getString("target_warehouse_name"),
              row.getObject("created_at", LocalDateTime.class),
              source(row),
              stock,
              showCost ? new Amount(decimal(row, "amount")) : null);
    };
  }

  private Source source(ResultSet row) throws SQLException {
    return new Source(
        row.getString("source_code"),
        Objects.toString(row.getString("source_id"), ""),
        row.getString("source_kind"));
  }

  private String decimal(ResultSet row, String field) throws SQLException {
    var value = row.getBigDecimal(field);
    return value == null ? null : value.toPlainString();
  }
}
