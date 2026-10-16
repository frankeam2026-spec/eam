package com.windasoft.eam.service.experience;

import com.windasoft.eam.repository.experience.AnalyticsRepository;
import com.windasoft.eam.service.system.AuthContext;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsSourceService {
  private final AnalyticsAccess access;
  private final AuthContext auth;

  public AnalyticsSourceService(AnalyticsAccess access, AuthContext auth) {
    this.access = access;
    this.auth = auth;
  }

  public AnalyticsRepository.Source source(String domain, String scope, boolean equipmentCost) {
    return switch (domain) {
      case "repair" -> repair(scope);
      case "maintenance" -> task(false, "", scope);
      case "inspection" -> inspection(scope);
      case "anomaly" -> anomaly(scope);
      case "material" -> material(scope, equipmentCost);
      default -> throw new IllegalArgumentException("未知统计来源");
    };
  }

  private AnalyticsRepository.Source repair(String range) {
    var args = new ArrayList<Object>();
    args.add(auth.tenantId());
    String allowed =
        access.scopeSql(
            access.scope("repair"),
            "r.company_org_id",
            "r.department_org_id",
            "(r.assignee_user_id=? OR r.reporter_user_id=? OR EXISTS(SELECT 1 FROM"
                + " eam_repair_candidate c WHERE c.tenant_id=r.tenant_id AND c.order_id=r.id AND"
                + " c.user_id=? AND c.status='待接单'))",
            args);
    allowed +=
        access.teamSql(
            range, "r.company_org_id", "r.department_org_id", "r.assignee_user_id=?", args);
    if (range.equals("personal")) {
      allowed +=
          " AND (r.assignee_user_id=? OR r.reporter_user_id=? OR EXISTS(SELECT 1 FROM"
              + " eam_repair_candidate c WHERE c.tenant_id=r.tenant_id AND c.order_id=r.id AND"
              + " c.user_id=? AND c.status='待接单'))";
      args.add(auth.userId());
      args.add(auth.userId());
      args.add(auth.userId());
    }
    return new AnalyticsRepository.Source(
        """
SELECT r.id source_id,r.order_no code,r.equipment_id,r.equipment_code,r.equipment_name,
r.company_org_id company_id,r.department_org_id department_id,COALESCE(d.name,r.department) department_name,
COALESCE(r.accepted_owner_id,r.assignee_user_id) owner_id,COALESCE(u.display_name,r.assignee,'未分派') owner_name,
r.status,r.reported_at occurred_at,r.accepted_at,r.completed_at submitted_at,r.acceptance_score score,
r.fault_type_id,COALESCE(bt.name,r.fault_type) fault_type,r.fault_cause_id,COALESCE(bc.name,r.fault_cause) fault_cause,
r.collaborators_json collaborators,'repair' module,CONCAT('/repairs/',r.id) url
FROM eam_repair_order r
LEFT JOIN eam_org_unit d ON d.tenant_id=r.tenant_id AND d.id=r.department_org_id
LEFT JOIN eam_sys_user u ON u.tenant_id=r.tenant_id AND u.id=COALESCE(r.accepted_owner_id,r.assignee_user_id)
LEFT JOIN eam_base_item bt ON bt.tenant_id=r.tenant_id AND bt.id=r.fault_type_id
LEFT JOIN eam_base_item bc ON bc.tenant_id=r.tenant_id AND bc.id=r.fault_cause_id
WHERE r.tenant_id=? AND r.deleted=0 AND r.status NOT IN ('已撤销','已取消') AND
"""
            + allowed,
        args);
  }

  private AnalyticsRepository.Source task(boolean inspection, String kind, String range) {
    String table = inspection ? "eam_inspection_task" : "eam_maintenance_task",
        events = inspection ? "eam_inspection_event" : "eam_maintenance_event";
    String menu =
        inspection
            ? (kind.equals("daily") ? "inspectionDailyTask" : "inspectionPeriodicTask")
            : "maintenanceTask";
    var args = new ArrayList<Object>();
    args.add(auth.tenantId());
    String allowed =
        access.scopeSql(
            access.scope(menu), "r.company_id", "r.department_id", "r.owner_id=?", args);
    allowed += access.teamSql(range, "r.company_id", "r.department_id", "r.owner_id=?", args);
    if (range.equals("personal")) {
      allowed +=
          " AND (r.owner_id=? OR"
              + " JSON_CONTAINS(COALESCE(JSON_EXTRACT(r.snapshot,'$.owners'),JSON_ARRAY()),JSON_QUOTE(?)))";
      args.add(auth.userId());
      args.add(Long.toString(auth.userId()));
    }
    if (inspection) {
      allowed += " AND r.kind=? AND r.deleted=0";
      args.add(kind);
    }
    String submit = inspection ? "提交检查结果" : "提交保养", accept = inspection ? "审核通过" : "验收通过";
    String date = inspection ? "r.due_at" : "TIMESTAMP(r.due_date)",
        expiry = inspection ? "r.expires_at" : "TIMESTAMP(r.expires_date,'23:59:59')";
    String accepted =
        "(SELECT MAX(ev.created_at) FROM "
            + events
            + " ev WHERE ev.tenant_id=r.tenant_id AND ev.task_id=r.id AND ev.action='"
            + accept
            + "')";
    String submitted =
        "(SELECT ev.created_at FROM "
            + events
            + " ev WHERE ev.tenant_id=r.tenant_id AND ev.task_id=r.id AND ev.action='"
            + submit
            + "' ORDER BY ev.created_at DESC,ev.id DESC LIMIT 1)";
    String effective =
        "CASE WHEN r.status IN ('待验收','已完成','待审核','已审核') THEN " + submitted + " ELSE NULL END";
    if (inspection)
      accepted = "CASE WHEN r.status='已完成' THEN " + submitted + " ELSE " + accepted + " END";
    return new AnalyticsRepository.Source(
        "SELECT r.id"
            + " source_id,r.code,r.equipment_id,e.equipment_code,COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.snapshot,'$.equipmentName')),e.name,'')"
            + " equipment_name,r.company_id,r.department_id,COALESCE(d.name,'历史部门未确认')"
            + " department_name,r.owner_id,COALESCE(u.display_name,'未接单') owner_name,r.status,"
            + date
            + " occurred_at,"
            + expiry
            + " deadline,"
            + effective
            + " submitted_at,"
            + accepted
            + " accepted_at,'"
            + (inspection ? kind : "maintenance")
            + "' kind,'"
            + (inspection ? "inspection" : "maintenance")
            + "' module,CONCAT('/"
            + (inspection ? "inspection" : "maintenance")
            + "/task/',r.id) url FROM "
            + table
            + " r LEFT JOIN eam_equipment e ON e.tenant_id=r.tenant_id AND e.id=r.equipment_id LEFT"
            + " JOIN eam_org_unit d ON d.tenant_id=r.tenant_id AND d.id=r.department_id LEFT JOIN"
            + " eam_sys_user u ON u.tenant_id=r.tenant_id AND u.id=r.owner_id WHERE r.tenant_id=?"
            + " AND "
            + allowed,
        args);
  }

  private AnalyticsRepository.Source inspection(String scope) {
    var sources = new ArrayList<AnalyticsRepository.Source>();
    for (String kind : List.of("daily", "periodic"))
      try {
        sources.add(task(true, kind, scope));
      } catch (org.springframework.web.server.ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) throw denied;
      }
    if (sources.isEmpty()) AnalyticsAccess.deny();
    var args = new ArrayList<Object>();
    sources.forEach(s -> args.addAll(s.args()));
    return new AnalyticsRepository.Source(
        String.join(" UNION ALL ", sources.stream().map(s -> "(" + s.sql() + ")").toList()), args);
  }

  private AnalyticsRepository.Source anomaly(String range) {
    var args = new ArrayList<Object>();
    args.add(auth.tenantId());
    String allowed =
        access.scopeSql(
            access.scope("anomaly"),
            "r.company_org_id",
            "r.department_org_id",
            "r.discovered_by=?",
            args);
    allowed +=
        access.teamSql(range, "r.company_org_id", "r.department_org_id", "r.discovered_by=?", args);
    if (range.equals("personal")) {
      allowed += " AND r.discovered_by=?";
      args.add(auth.userId());
    }
    return new AnalyticsRepository.Source(
        "SELECT r.id source_id,r.code,r.equipment_id,e.equipment_code,e.name"
            + " equipment_name,r.company_org_id company_id,r.department_org_id"
            + " department_id,r.status,r.discovered_at occurred_at,CONCAT('/anomalies/',r.id)"
            + " url,'anomaly' module FROM eam_equipment_anomaly r LEFT JOIN eam_equipment e ON"
            + " e.tenant_id=r.tenant_id AND e.id=r.equipment_id WHERE r.tenant_id=? AND r.status"
            + " NOT IN ('已作废','作废') AND "
            + allowed,
        args);
  }

  private AnalyticsRepository.Source material(String range, boolean device) {
    var args = new ArrayList<Object>();
    args.add(auth.tenantId());
    String allowed = AnalyticsAccess.in("i.warehouse_id", access.warehouses(true), args);
    allowed +=
        access.teamSql(
            range,
            "COALESCE(r.company_id_snapshot,i.company_id)",
            "COALESCE(r.department_id_snapshot,ol.department_id,rt.department_org_id,mt.department_id,it.department_id)",
            "r.actor_id=?",
            args);
    if (range.equals("personal")) {
      allowed += " AND r.actor_id=?";
      args.add(auth.userId());
    }
    if (device) {
      String equipment =
          access.scopeSql(
              access.scope("equipment"),
              "e.company_org_id",
              "e.department_org_id",
              "(e.owner_user_id=? OR EXISTS(SELECT 1 FROM eam_equipment_owner eo WHERE"
                  + " eo.tenant_id=e.tenant_id AND eo.equipment_id=e.id AND eo.user_id=?))",
              args);
      allowed += " AND (r.equipment_id IS NULL OR (e.id IS NOT NULL AND (" + equipment + ")))";
    }
    return new AnalyticsRepository.Source(
        """
SELECT r.id source_id,COALESCE(i.code,'') code,r.equipment_id,COALESCE(r.equipment_code_snapshot,e.equipment_code) equipment_code,COALESCE(r.equipment_name_snapshot,e.name) equipment_name,
COALESCE(r.company_id_snapshot,i.company_id) company_id,COALESCE(r.department_id_snapshot,ol.department_id,rt.department_org_id,mt.department_id,it.department_id) department_id,
r.material_id,m.code material_code,m.name material_name,m.unit,r.quantity,r.cost_amount amount,
r.currency,r.attribution,r.status,r.created_at occurred_at,r.actor_name owner_name,
a.id allocation_id,a.issue_id,a.cost_layer_id,cl.source_line_id receipt_line_id,
CONCAT('/inventory/outbounds/',i.id) url,'material' module
FROM inv_usage r JOIN inv_issue_allocation a ON a.tenant_id=r.tenant_id AND a.id=r.allocation_id
JOIN inv_issue i ON i.tenant_id=a.tenant_id AND i.id=a.issue_id
JOIN inv_material m ON m.tenant_id=r.tenant_id AND m.id=r.material_id
LEFT JOIN eam_equipment e ON e.tenant_id=r.tenant_id AND e.id=r.equipment_id
LEFT JOIN inv_outbound_line ol ON ol.tenant_id=a.tenant_id AND ol.id=a.outbound_line_id
LEFT JOIN inv_requisition q ON q.tenant_id=r.tenant_id AND q.id=r.requisition_id
LEFT JOIN eam_repair_order rt ON rt.tenant_id=r.tenant_id AND rt.id=COALESCE(ol.task_id,q.task_id) AND COALESCE(ol.task_kind,q.task_kind)='维修'
LEFT JOIN eam_maintenance_task mt ON mt.tenant_id=r.tenant_id AND mt.id=COALESCE(ol.task_id,q.task_id) AND COALESCE(ol.task_kind,q.task_kind)='保养'
LEFT JOIN eam_inspection_task it ON it.tenant_id=r.tenant_id AND it.id=COALESCE(ol.task_id,q.task_id) AND COALESCE(ol.task_kind,q.task_kind)='点巡检'
LEFT JOIN inv_cost_layer cl ON cl.tenant_id=a.tenant_id AND cl.id=a.cost_layer_id
WHERE r.tenant_id=? AND i.reversed=0 AND i.deleted=0 AND r.status NOT IN ('已冲销','已撤销') AND
"""
            + allowed,
        args);
  }
}
