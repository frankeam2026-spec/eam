package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.basedata.BaseItemMapper;
import com.windasoft.eam.repository.equipment.EquipmentMapper;
import com.windasoft.eam.repository.system.SystemUserMapper;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Repository;

/** Owns workflow persistence, including legacy allocations needed for historical compatibility. */
@Repository
public class RepairWorkflowRepository {
  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.jdbc.core.JdbcTemplate db;

  public Map<String, Object> approvalValues(long tenant, long id) {
    return db.queryForMap(
        "SELECT r.reporter_user_id applicantId,u.org_id applicantDepartmentId,r.company_org_id"
            + " companyId,r.department_org_id departmentId,r.equipment_id equipmentId,e.category_id"
            + " categoryId,r.fault_type_id faultTypeId,r.severity FROM eam_repair_order r JOIN"
            + " eam_equipment e ON e.tenant_id=r.tenant_id AND e.id=r.equipment_id AND e.deleted=0"
            + " JOIN eam_sys_user u ON u.tenant_id=r.tenant_id AND u.id=r.reporter_user_id AND"
            + " u.deleted=0 WHERE r.tenant_id=? AND r.id=? AND r.deleted=0",
        tenant,
        id);
  }

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.repository.system.NotificationRepository notifications;

  private final RepairRelationsRepository collaborators;
  private final RepairWorkflowMapper mapper;
  private final RepairOrderMapper orders;
  private final EquipmentMapper equipment;
  private final SystemUserMapper users;
  private final BaseItemMapper base;

  public RepairWorkflowRepository(
      RepairWorkflowMapper mapper,
      RepairOrderMapper orders,
      EquipmentMapper equipment,
      SystemUserMapper users,
      BaseItemMapper base,
      RepairRelationsRepository collaborators) {
    this.collaborators = collaborators;
    this.mapper = mapper;
    this.orders = orders;
    this.equipment = equipment;
    this.users = users;
    this.base = base;
  }

  public long assignedOrderCount(long tenant, long user) {
    return orders.selectCount(
        new LambdaQueryWrapper<RepairOrder>()
            .eq(RepairOrder::getTenantId, tenant)
            .eq(RepairOrder::getAssigneeUserId, user)
            .eq(RepairOrder::getDeleted, 0));
  }

  public RepairOrder findOrder(long tenant, long id) {
    return collaborators.hydrate(
        orders.selectOne(
            new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTenantId, tenant)
                .eq(RepairOrder::getId, id)));
  }

  public int updateOrder(RepairOrder row) {
    int changed = orders.updateById(row);
    if (changed > 0) collaborators.save(row);
    return changed;
  }

  public Equipment equipment(long tenant, String code) {
    return equipment.selectOne(
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getEquipmentCode, code));
  }

  public SystemUser activeUser(long tenant, long id) {
    return users.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getId, id)
            .eq(SystemUser::getStatus, 1));
  }

  public List<SystemUser> activeUsers(long tenant) {
    return users.selectList(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getStatus, 1));
  }

  public List<BaseItem> processingResults(long tenant) {
    return base.selectList(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getItemType, "PROCESS_RESULT")
            .eq(BaseItem::getStatus, 1)
            .orderByAsc(BaseItem::getSortNo));
  }

  public boolean personalAccess(long tenant, long userId) {
    return mapper.personalAccess(tenant, userId);
  }

  public RepairOrder lock(long tenant, long id) {
    return collaborators.hydrate(mapper.lock(tenant, id));
  }

  public void closeApprovalReturnTodos(long tenant, long id) {
    db.update(
        "UPDATE eam_repair_inbox SET done=1 WHERE tenant_id=? AND order_id=? AND kind='报修待修改' AND"
            + " is_todo=1 AND done=0",
        tenant,
        id);
  }

  public List<Map<String, Object>> users(long tenant) {
    return mapper.users(tenant);
  }

  public int candidate(long tenant, long orderId, long userId) {
    return mapper.candidate(tenant, orderId, userId);
  }

  public void clearOwner(long tenant, long id) {
    mapper.clearOwner(tenant, id);
  }

  public void closeIssueTodos(long tenant, long orderId) {
    mapper.closeIssueTodos(tenant, orderId);
  }

  public List<Map<String, Object>> candidates(long tenant, long orderId) {
    return mapper.candidates(tenant, orderId);
  }

  public void clearCandidates(long tenant, long orderId) {
    mapper.clearCandidates(tenant, orderId);
  }

  public void addCandidate(long id, long tenant, long orderId, long userId) {
    mapper.addCandidate(id, tenant, orderId, userId);
  }

  public void claim(long tenant, long orderId, long userId) {
    mapper.claim(tenant, orderId, userId);
  }

  public void event(
      long id,
      long tenant,
      long orderId,
      String action,
      long actorId,
      String actorName,
      String details) {
    mapper.event(id, tenant, orderId, action, actorId, actorName, details);
  }

  public List<Map<String, Object>> events(long tenant, long orderId) {
    return mapper.events(tenant, orderId);
  }

  public void inbox(
      long id, long tenant, long userId, long orderId, String kind, String title, boolean todo) {
    mapper.inbox(id, tenant, userId, orderId, kind, title, todo);
    mapper.inboxMetadata(
        tenant,
        id,
        com.windasoft.eam.common.NotificationMessages.key(kind),
        com.windasoft.eam.common.NotificationMessages.arguments(kind, title));
    if (notifications != null) notifications.captureSource("eam_repair_inbox", tenant, id);
  }

  public void closeTodos(long tenant, long orderId) {
    mapper.closeTodos(tenant, orderId);
  }

  public List<Map<String, Object>> inboxRows(long tenant, long userId) {
    return mapper.inboxRows(tenant, userId);
  }

  public int markRead(long tenant, long userId, long id) {
    return mapper.markRead(tenant, userId, id);
  }

  public int hasInbox(long tenant, long userId, long orderId) {
    return mapper.hasInbox(tenant, userId, orderId);
  }

  public List<Map<String, Object>> stocks(long tenant, long company) {
    return mapper.stocks(tenant, company);
  }

  public void addStock(
      long id,
      long tenant,
      long company,
      String code,
      String name,
      String model,
      BigDecimal quantity) {
    mapper.addStock(id, tenant, company, code, name, model, quantity);
  }

  public Map<String, Object> lockStock(long tenant, long id) {
    return mapper.lockStock(tenant, id);
  }

  public int issueStock(long tenant, long id, BigDecimal quantity) {
    return mapper.issueStock(tenant, id, quantity);
  }

  public void material(
      long id, long tenant, long orderId, long stockId, long userId, BigDecimal quantity) {
    mapper.material(id, tenant, orderId, stockId, userId, quantity);
  }

  public List<Map<String, Object>> materials(long tenant, long orderId) {
    return mapper.materials(tenant, orderId);
  }

  public int cancelMaterial(long tenant, long id, long orderId) {
    return mapper.cancelMaterial(tenant, id, orderId);
  }

  public Map<String, Object> lockMaterial(long tenant, long id, long orderId) {
    return mapper.lockMaterial(tenant, id, orderId);
  }

  public int issued(long tenant, long id, long userId) {
    return mapper.issued(tenant, id, userId);
  }

  public int consume(long tenant, long id, BigDecimal quantity) {
    return mapper.consume(tenant, id, quantity);
  }

  public void install(
      long id, long tenant, long equipmentId, long stockId, long orderId, BigDecimal quantity) {
    mapper.install(id, tenant, equipmentId, stockId, orderId, quantity);
  }

  public List<Map<String, Object>> installed(long tenant, long equipmentId) {
    return mapper.installed(tenant, equipmentId);
  }

  public int remove(long tenant, long equipmentId, long id) {
    return mapper.remove(tenant, equipmentId, id);
  }

  public void history(
      long id,
      long tenant,
      long equipmentId,
      String number,
      String description,
      String actor,
      String status,
      String result) {
    mapper.history(id, tenant, equipmentId, number, description, actor, status, result);
  }
}
