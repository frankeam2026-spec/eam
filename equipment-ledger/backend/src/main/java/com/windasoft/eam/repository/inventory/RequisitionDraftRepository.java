package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.dto.inventory.RequisitionDetail;
import com.windasoft.eam.entity.inventory.RequisitionHeader;
import com.windasoft.eam.entity.inventory.RequisitionLine;
import java.util.List;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Draft persistence and source locks; transaction and permission decisions belong to Service. */
@Repository
public class RequisitionDraftRepository {
  private final RequisitionHeaderMapper headers;
  private final RequisitionLineMapper lines;
  private final JdbcTemplate db;
  private final RequisitionDetailMapper details;

  public RequisitionDraftRepository(
      RequisitionHeaderMapper headers,
      RequisitionLineMapper lines,
      JdbcTemplate db,
      RequisitionDetailMapper details) {
    this.headers = headers;
    this.lines = lines;
    this.db = db;
    this.details = details;
  }

  public List<RequisitionDetail.Line> lines(long tenant, long id) {
    return details.lines(tenant, id);
  }

  public List<RequisitionDetail.Reservation> reservations(long tenant, long id) {
    return details.reservations(tenant, id);
  }

  public List<RequisitionDetail.Issue> issues(long tenant, long id) {
    return details.issues(tenant, id);
  }

  public List<RequisitionDetail.Allocation> allocations(long tenant, long id) {
    return details.allocations(tenant, id);
  }

  public List<RequisitionDetail.Event> events(long tenant, long id) {
    return details.events(tenant, id);
  }

  public RequisitionHeader header(long tenant, long id, boolean lock) {
    var header = headers.selectCurrent(tenant, id, lock);
    if (header != null && header.getStatus() == null) {
      throw new IllegalStateException("Unknown persisted requisition status");
    }
    return header;
  }

  public void insert(RequisitionHeader header) {
    headers.insert(header);
  }

  public int update(RequisitionHeader header, int expectedVersion) {
    return headers.update(
        null,
        new LambdaUpdateWrapper<RequisitionHeader>()
            .eq(RequisitionHeader::getTenantId, header.getTenantId())
            .eq(RequisitionHeader::getId, header.getId())
            .eq(RequisitionHeader::getRowVersion, expectedVersion)
            .set(RequisitionHeader::getCompanyId, header.getCompanyId())
            .set(RequisitionHeader::getWarehouseId, header.getWarehouseId())
            .set(RequisitionHeader::getTaskKind, header.getTaskKind())
            .set(RequisitionHeader::getTaskId, header.getTaskId())
            .set(RequisitionHeader::getTaskCode, header.getTaskCode())
            .set(RequisitionHeader::getRemark, header.getRemark())
            .setSql("row_version=row_version+1"));
  }

  public void replaceLines(long tenant, long id, List<RequisitionLine> values) {
    lines.delete(
        new LambdaQueryWrapper<RequisitionLine>()
            .eq(RequisitionLine::getTenantId, tenant)
            .eq(RequisitionLine::getRequisitionId, id));
    insertLines(values);
  }

  public void insertLines(List<RequisitionLine> values) {
    for (var line : values) {
      lines.insert(line);
    }
  }

  public record Warehouse(long id, long companyId, String name, boolean enabled) {}

  public record Material(long id, boolean enabled) {}

  public record SourceTask(
      long id,
      Long departmentOrgId,
      String equipmentCode,
      String code,
      String status,
      String approvalGate,
      Long companyId) {}

  public Warehouse warehouse(long tenant, long id, boolean lock) {
    return db
        .query(
            "SELECT id,company_id,name,enabled FROM inv_warehouse WHERE tenant_id=? AND id=?"
                + (lock ? " FOR UPDATE" : ""),
            DataClassRowMapper.newInstance(Warehouse.class),
            tenant,
            id)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public Material material(long tenant, long id) {
    return db
        .query(
            "SELECT id,enabled FROM inv_material WHERE tenant_id=? AND id=? FOR UPDATE",
            DataClassRowMapper.newInstance(Material.class),
            tenant,
            id)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public SourceTask source(long tenant, long id, String kind, boolean lock) {
    String sql =
        switch (kind) {
          case "维修" ->
              "SELECT id,department_org_id,equipment_code,order_no code,status,approval_gate,NULL"
                  + " company_id FROM eam_repair_order WHERE tenant_id=? AND id=? AND deleted=0";
          case "保养" ->
              "SELECT id,NULL department_org_id,NULL equipment_code,code,status,NULL"
                  + " approval_gate,company_id FROM v_maintenance_task WHERE tenant_id=? AND id=?";
          case "点巡检" ->
              "SELECT id,NULL department_org_id,NULL equipment_code,code,status,NULL"
                  + " approval_gate,company_id FROM v_inspection_task WHERE tenant_id=? AND id=?"
                  + " AND deleted=0";
          default ->
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.inventory.message0018");
        };
    return db
        .query(
            sql + (lock ? " FOR UPDATE" : ""),
            DataClassRowMapper.newInstance(SourceTask.class),
            tenant,
            id)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public Long equipmentCompany(long tenant, String code) {
    var companies =
        db.query(
            "SELECT company_org_id FROM eam_equipment WHERE tenant_id=? AND equipment_code=? AND"
                + " deleted=0",
            (rs, n) -> rs.getObject(1, Long.class),
            tenant,
            code);
    return companies.size() == 1 ? companies.getFirst() : null;
  }
}
