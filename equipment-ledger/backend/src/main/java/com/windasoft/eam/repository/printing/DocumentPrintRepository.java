package com.windasoft.eam.repository.printing;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.printing.PrintViews;
import java.util.List;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 打印来源只读联查，复用库存记账结果；不写库存或打印配置表。 */
@Repository
public class DocumentPrintRepository {
  private final JdbcTemplate db;

  public DocumentPrintRepository(JdbcTemplate db) {
    this.db = db;
  }

  public PrintViews.Source source(long tenant, String reference) {
    boolean legacy = reference.startsWith("g");
    String value = legacy ? reference.substring(1) : reference;
    if (!value.matches("[1-9][0-9]{0,18}"))
      throw new LocalizedArgumentException("server.printing.message0002");
    var rows =
        db.query(
            "SELECT i.id,i.warehouse_id,w.company_id,o.name company_name FROM "
                + (legacy ? "inv_issue_group" : "inv_issue")
                + " i JOIN inv_warehouse w ON w.tenant_id=i.tenant_id AND w.id=i.warehouse_id LEFT"
                + " JOIN eam_org_unit o ON o.tenant_id=w.tenant_id AND o.id=w.company_id WHERE"
                + " i.tenant_id=? AND i.id=?"
                + (legacy ? "" : " AND i.deleted=0"),
            DataClassRowMapper.newInstance(PrintViews.Source.class),
            tenant,
            Long.parseLong(value));
    if (rows.isEmpty()) throw new LocalizedArgumentException("server.printing.message0003");
    return rows.getFirst();
  }

  public List<PrintViews.Allocation> allocations(long tenant, String reference) {
    boolean group = reference.startsWith("g");
    return db.query(
        """
SELECT a.id,a.quantity,a.unit_cost,a.amount,a.outbound_line_id,
 m.code,m.name,m.model,m.unit,COALESCE(pc.code,'') batch,COALESCE(b.name,'') bin,
 COALESCE(l.recipient_name,r.applicant_name,'') recipient,COALESCE(d.name,'') department,
 COALESCE(l.task_code,r.task_code,'') task,COALESCE(r.code,'') requisition,
 COALESCE(c.currency,'未标注币种') currency
FROM inv_issue_allocation a JOIN inv_issue i ON i.tenant_id=a.tenant_id AND i.id=a.issue_id
 JOIN inv_balance s ON s.tenant_id=a.tenant_id AND s.id=a.balance_id
 JOIN inv_material m ON m.tenant_id=s.tenant_id AND m.id=s.material_id
 LEFT JOIN inv_bin b ON b.tenant_id=s.tenant_id AND b.id=s.bin_id
 LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id
 LEFT JOIN inv_cost_layer c ON c.tenant_id=a.tenant_id AND c.id=a.cost_layer_id
 LEFT JOIN inv_outbound_line l ON l.tenant_id=a.tenant_id AND l.id=a.outbound_line_id
 LEFT JOIN inv_requisition_line ql ON ql.tenant_id=a.tenant_id AND ql.id=a.requisition_line_id
 LEFT JOIN inv_requisition r ON r.tenant_id=a.tenant_id AND r.id=COALESCE(l.requisition_id,ql.requisition_id)
 LEFT JOIN eam_org_unit d ON d.tenant_id=l.tenant_id AND d.id=l.department_id
WHERE a.tenant_id=? AND
"""
            + (group ? " i.group_id=?" : " i.id=?")
            + " ORDER BY i.id,COALESCE(l.line_no,0),a.id",
        DataClassRowMapper.newInstance(PrintViews.Allocation.class),
        tenant,
        Long.parseLong(group ? reference.substring(1) : reference));
  }
}
