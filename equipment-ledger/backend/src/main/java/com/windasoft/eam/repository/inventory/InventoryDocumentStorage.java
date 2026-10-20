package com.windasoft.eam.repository.inventory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Concrete document headers and lines. Registry is fixed in code; client field/table names are
 * never SQL.
 */
@Repository
public class InventoryDocumentStorage {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public InventoryDocumentStorage(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  private record Field(String key, String column, String type) {}

  private record Document(String table, List<Field> header, List<Field> lines) {}

  private Document document(String kind) {
    return switch (kind) {
      case "returns" ->
          new Document(
              "inv_return",
              List.of(
                  new Field("remark", "remark", "VARCHAR(1000)"),
                  new Field("surplusRequestedBy", "requested_by", "BIGINT"),
                  new Field("surplusRequestedByName", "requested_by_name", "VARCHAR(150)")),
              List.of(
                  new Field("allocationId", "allocation_id", "BIGINT"),
                  new Field("quantity", "quantity", "DECIMAL(20,6)"),
                  new Field("binId", "bin_id", "BIGINT"),
                  new Field("materialName", "material_name", "VARCHAR(200)"),
                  new Field("issueCode", "issue_code", "VARCHAR(64)"),
                  new Field("returnedAmount", "returned_amount", "DECIMAL(20,2)")));
      case "transfers" ->
          new Document(
              "inv_transfer",
              List.of(new Field("remark", "remark", "VARCHAR(1000)")),
              List.of(
                  new Field("balanceId", "balance_id", "BIGINT"),
                  new Field("quantity", "quantity", "DECIMAL(20,6)"),
                  new Field("targetBinId", "target_bin_id", "BIGINT")));
      case "counts" ->
          new Document(
              "inv_count",
              List.of(
                  new Field("remark", "remark", "VARCHAR(1000)"),
                  new Field("baselineAt", "baseline_at", "DATETIME(6)")),
              List.of(
                  new Field("balanceId", "balance_id", "BIGINT"),
                  new Field("baselineQuantity", "baseline_quantity", "DECIMAL(20,6)"),
                  new Field("countedQuantity", "counted_quantity", "DECIMAL(20,6)"),
                  new Field("unitCost", "unit_cost", "DECIMAL(20,6)"),
                  new Field("materialName", "material_name", "VARCHAR(200)"),
                  new Field("binName", "bin_name", "VARCHAR(150)"),
                  new Field("batchCode", "batch_code", "VARCHAR(64)"),
                  new Field("observedQuantity", "observed_quantity", "DECIMAL(20,6)"),
                  new Field("countedAt", "counted_at", "DATETIME(6)"),
                  new Field("difference", "difference", "DECIMAL(20,6)")));
      case "openings" ->
          new Document(
              "inv_opening",
              List.of(
                  new Field("remark", "remark", "VARCHAR(1000)"),
                  new Field("conversionMaterialId", "conversion_material_id", "BIGINT"),
                  new Field("receiptId", "receipt_id", "BIGINT"),
                  new Field("receiptCode", "receipt_code", "VARCHAR(64)")),
              List.of(
                  new Field("materialId", "material_id", "BIGINT"),
                  new Field("quantity", "quantity", "DECIMAL(20,6)"),
                  new Field("unitPrice", "unit_price", "DECIMAL(20,6)"),
                  new Field("binId", "bin_id", "BIGINT"),
                  new Field("batchCode", "batch_code", "VARCHAR(64)"),
                  new Field("supplierBatch", "supplier_batch", "VARCHAR(100)"),
                  new Field("productionDate", "production_date", "DATE"),
                  new Field("expiryDate", "expiry_date", "DATE"),
                  new Field("legacyStockId", "legacy_stock_id", "BIGINT"),
                  new Field("allocatedFee", "allocated_fee", "DECIMAL(20,2)"),
                  new Field("materialName", "material_name", "VARCHAR(200)"),
                  new Field("balanceId", "balance_id", "BIGINT"),
                  new Field("warehouseId", "warehouse_id", "BIGINT"),
                  new Field("balanceVersion", "balance_version", "INT")));
      case "reversals" ->
          new Document(
              "inv_reversal",
              List.of(
                  new Field("remark", "remark", "VARCHAR(1000)"),
                  new Field("sourceKind", "source_kind", "VARCHAR(24)"),
                  new Field("sourceId", "source_id", "BIGINT"),
                  new Field("sourceCode", "source_code", "VARCHAR(64)")),
              List.of());
      default ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.inventory.message0006");
    };
  }

  private Map<String, Object> decode(String content) {
    try {
      return json.readValue(content, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.inventory.message0007"), e);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> rows(Object value) {
    return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
  }

  private Object scalar(Map<String, Object> data, Field field) {
    Object value = data.get(field.key());
    if (value == null || value.toString().isBlank()) return null;
    return switch (field.type()) {
      case "BIGINT", "INT" -> Long.parseLong(value.toString());
      case "DATETIME(6)" -> java.time.LocalDateTime.parse(value.toString());
      case "DATE" -> java.time.LocalDate.parse(value.toString());
      default ->
          field.type().startsWith("DECIMAL") ? new BigDecimal(value.toString()) : value.toString();
    };
  }

  public String kind(long tenant, Object id) {
    return db.queryForObject(
        "SELECT kind FROM v_inv_operation WHERE tenant_id=? AND id=?", String.class, tenant, id);
  }

  public void lock(long tenant, Object id, Object kind) {
    Document doc = document(kind == null ? kind(tenant, id) : kind.toString());
    db.queryForList(
        "SELECT id FROM " + doc.table() + " WHERE tenant_id=? AND id=? FOR UPDATE", tenant, id);
  }

  public int insert(
      Object id,
      long tenant,
      Object kind,
      Object code,
      Object company,
      Object warehouse,
      Object target,
      String content,
      long creator) {
    Document doc = document(kind.toString());
    var data = decode(content);
    var columns = new LinkedHashMap<String, Object>();
    columns.put("id", id);
    columns.put("tenant_id", tenant);
    columns.put("code", code);
    columns.put("company_id", company);
    columns.put("warehouse_id", warehouse);
    columns.put("created_by", creator);
    if (kind.equals("transfers")) columns.put("target_warehouse_id", target);
    for (var f : doc.header()) columns.put(f.column(), scalar(data, f));
    db.update(
        "INSERT INTO "
            + doc.table()
            + " ("
            + String.join(",", columns.keySet())
            + ") VALUES("
            + String.join(",", Collections.nCopies(columns.size(), "?"))
            + ")",
        columns.values().toArray());
    lines(tenant, id, doc, data);
    return 1;
  }

  public int update(
      long tenant, Object id, Object company, Object warehouse, Object target, String content) {
    Document doc = document(kind(tenant, id));
    var columns = new LinkedHashMap<String, Object>();
    columns.put("company_id", company);
    columns.put("warehouse_id", warehouse);
    if (doc.table().equals("inv_transfer")) columns.put("target_warehouse_id", target);
    return content(tenant, id, doc, columns, content, ",row_version=row_version+1");
  }

  public int confirm(long tenant, Object id, Object status, String content) {
    var columns = new LinkedHashMap<String, Object>();
    columns.put("status", status);
    return content(
        tenant,
        id,
        document(kind(tenant, id)),
        columns,
        content,
        ",confirmed_at=CURRENT_TIMESTAMP(3),row_version=row_version+1");
  }

  private int content(
      long tenant,
      Object id,
      Document doc,
      LinkedHashMap<String, Object> columns,
      String content,
      String extra) {
    var data = decode(content);
    for (var f : doc.header()) columns.put(f.column(), scalar(data, f));
    var args = new ArrayList<Object>(columns.values());
    args.add(tenant);
    args.add(id);
    int changed =
        db.update(
            "UPDATE "
                + doc.table()
                + " SET "
                + String.join(",", columns.keySet().stream().map(k -> k + "=?").toList())
                + extra
                + " WHERE tenant_id=? AND id=?",
            args.toArray());
    lines(tenant, id, doc, data);
    return changed;
  }

  private void lines(long tenant, Object id, Document doc, Map<String, Object> data) {
    if (doc.lines().isEmpty()) return;
    db.update(
        "DELETE FROM " + doc.table() + "_line WHERE tenant_id=? AND document_id=?", tenant, id);
    int at = 0;
    for (var row : rows(data.get("lines"))) {
      var args = new ArrayList<Object>();
      args.add(tenant);
      args.add(id);
      args.add(at++);
      for (var field : doc.lines()) args.add(scalar(row, field));
      db.update(
          "INSERT INTO "
              + doc.table()
              + "_line (tenant_id,document_id,line_no,"
              + String.join(",", doc.lines().stream().map(f -> f.column()).toList())
              + ") VALUES("
              + String.join(",", Collections.nCopies(args.size(), "?"))
              + ")",
          args.toArray());
    }
  }

  public int reverse(long tenant, Object id) {
    return db.update(
        "UPDATE "
            + document(kind(tenant, id)).table()
            + " SET status='已冲销',row_version=row_version+1 WHERE tenant_id=? AND id=?",
        tenant,
        id);
  }

  public int delete(long tenant, Object id) {
    return db.update(
        "UPDATE "
            + document(kind(tenant, id)).table()
            + " SET deleted=1,row_version=row_version+1 WHERE tenant_id=? AND id=?",
        tenant,
        id);
  }

  public int reverseOpening(long tenant, Object receipt) {
    return db.update(
        "UPDATE inv_opening SET status='已冲销',row_version=row_version+1 WHERE tenant_id=? AND"
            + " status='已完成' AND receipt_id=?",
        tenant,
        receipt);
  }
}
