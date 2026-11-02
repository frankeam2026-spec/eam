package com.windasoft.eam.service.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.repair.RepairOrderStatus;
import com.windasoft.eam.dto.repair.RepairContext;
import com.windasoft.eam.dto.repair.RepairWorkflowDetail;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.equipment.EquipmentSpareRepository;
import com.windasoft.eam.repository.repair.RepairCandidateRepository;
import com.windasoft.eam.repository.repair.RepairEventRepository;
import com.windasoft.eam.repository.repair.RepairMaterialRepository;
import com.windasoft.eam.repository.repair.RepairOrderRepository;
import com.windasoft.eam.service.basedata.BaseDataReferenceService;
import com.windasoft.eam.service.basedata.BaseDataType;
import com.windasoft.eam.service.business.BusinessValueService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 维修详情及知识读取上下文：负责授权后的只读装配，不执行派工、接单和库存写入。 */
@Service
public class RepairDetailService {
  private final RepairOrderRepository orders;
  private final RepairAccessService access;
  private final EquipmentQueryRepository equipmentRows;
  private final RepairQueryService viewData;
  private final RepairViewAssembler views;
  private final BusinessValueService custom;
  private final RepairCandidateRepository candidates;
  private final RepairEventRepository events;
  private final RepairMaterialRepository materials;
  private final EquipmentSpareRepository spares;
  private final BaseItemRepository baseRows;
  private final RepairApprovalService approvals;
  private final AuthContext auth;
  private final PermissionService permissions;

  public RepairDetailService(
      RepairOrderRepository orders,
      RepairAccessService access,
      EquipmentQueryRepository equipmentRows,
      RepairQueryService viewData,
      RepairViewAssembler views,
      BusinessValueService custom,
      RepairCandidateRepository candidates,
      RepairEventRepository events,
      RepairMaterialRepository materials,
      EquipmentSpareRepository spares,
      BaseItemRepository baseRows,
      RepairApprovalService approvals,
      AuthContext auth,
      PermissionService permissions) {
    this.orders = orders;
    this.access = access;
    this.equipmentRows = equipmentRows;
    this.viewData = viewData;
    this.views = views;
    this.custom = custom;
    this.candidates = candidates;
    this.events = events;
    this.materials = materials;
    this.spares = spares;
    this.baseRows = baseRows;
    this.approvals = approvals;
    this.auth = auth;
    this.permissions = permissions;
  }

  public RepairWorkflowDetail detail(long id) {
    var row = require(id);
    viewAllowed(row);
    var eq = equipment(row);
    viewData.prepareDetail(row);
    var processing = new LinkedHashMap<String, Object>();
    processing.put("id", Long.toString(id));
    custom.attach("repair_process", processing);
    var eventRows =
        events.events(auth.tenantId(), id).stream()
            .map(
                e ->
                    new RepairWorkflowDetail.Event(
                        e.getId().toString(),
                        e.getAction(),
                        e.getActorName(),
                        e.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        read(e.getDetailsJson())))
            .toList();
    return new RepairWorkflowDetail(
        processing,
        views.view(row),
        eq.getCategoryId() == null ? "" : eq.getCategoryId().toString(),
        eq.getModel() == null ? "" : eq.getModel(),
        candidates.candidates(auth.tenantId(), id),
        eventRows,
        materials.materials(auth.tenantId(), id),
        spares.installed(auth.tenantId(), eq.getId()),
        RepairOrderStatus.PENDING_CLAIM.code().equals(row.getStatus())
            && candidates.candidate(auth.tenantId(), id, auth.userId()) > 0,
        isOwner(row),
        canAccept(row),
        canDispatch(row),
        canIssue(eq.getCompanyOrgId()),
        results(),
        approvals.history(id),
        RepairOrderStatus.PENDING_SUBMISSION.code().equals(row.getStatus())
            && Objects.equals(row.getReporterUserId(), auth.userId()));
  }

  public List<RepairWorkflowDetail.ProcessResult> results() {
    return baseRows.activeItemsByType(auth.tenantId(), BaseDataType.PROCESS_RESULT.code()).stream()
        .map(
            item ->
                new RepairWorkflowDetail.ProcessResult(
                    item.getId().toString(), item.getName(), BaseDataReferenceService.fixed(item)))
        .toList();
  }

  public RepairContext context(long id) {
    var row = require(id);
    viewAllowed(row);
    return new RepairContext(row, equipment(row).getModel(), isOwner(row));
  }

  private boolean canDispatch(RepairOrder row) {
    try {
      permissions.checkRepair(row, "派工");
      return true;
    } catch (ResponseStatusException denied) {
      return false;
    }
  }

  private boolean canIssue(long company) {
    try {
      permissions.checkInventoryCompany(company, "出库");
      return true;
    } catch (ResponseStatusException denied) {
      return false;
    }
  }

  private RepairOrder require(long id) {
    var row = orders.find(auth.tenantId(), id);
    if (row == null) {
      throw new LocalizedArgumentException("server.repair.message0012");
    }
    return row;
  }

  private void viewAllowed(RepairOrder row) {
    access.requireView(row);
  }

  private Equipment equipment(RepairOrder row) {
    var eq = equipmentRows.findByCode(auth.tenantId(), row.getEquipmentCode());
    if (eq == null) {
      throw new LocalizedArgumentException("server.common.message0064");
    }
    return eq;
  }

  private boolean isOwner(RepairOrder row) {
    return Objects.equals(row.getAssigneeUserId(), auth.userId());
  }

  private boolean canAccept(RepairOrder row) {
    return permissions.canAccept(auth.user(), row);
  }

  private JsonNode read(String value) {
    return views.json(value);
  }
}
