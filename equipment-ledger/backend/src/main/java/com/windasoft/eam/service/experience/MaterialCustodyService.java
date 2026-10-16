package com.windasoft.eam.service.experience;

import static com.windasoft.eam.common.experience.ExperienceSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.experience.ExperienceSupport;
import com.windasoft.eam.repository.experience.MaterialCustodyRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialCustodyService {
  final MaterialCustodyRepository repository;
  final AuthContext auth;
  final PermissionService permissions;
  final ObjectMapper json;
  final com.windasoft.eam.service.inventory.InventoryMutex mutex;

  public MaterialCustodyService(
      MaterialCustodyRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      com.windasoft.eam.service.inventory.InventoryMutex mutex) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
    this.mutex = mutex;
  }

  boolean visible(Map<String, Object> r, String action) {
    if (action.equals("查看") && id(r.get("holderId")) == auth.userId()) return true;
    try {
      return permissions
          .scope("materialCustody", action)
          .matches(
              id(r.get("companyId")),
              r.get("departmentId") == null ? null : id(r.get("departmentId")),
              r.get("holderId") == null ? null : id(r.get("holderId")),
              auth.userId());
    } catch (org.springframework.web.server.ResponseStatusException e) {
      if (e.getStatusCode().value() != 403) throw e;
      return false;
    }
  }

  public Map<String, Object> list(boolean personal, String q, int page, int size) {
    return list(personal, q, page, size, "");
  }

  public Map<String, Object> list(
      boolean personal, String q, int page, int size, String rawFilters) {
    var condition =
        filters(
            json,
            rawFilters,
            Set.of(
                "materialCode",
                "materialName",
                "specification",
                "remaining",
                "holderName",
                "warehouse",
                "taskCode",
                "remark"));
    page = Math.max(1, page);
    size = Math.min(100, Math.max(1, size));
    if (!personal) permissions.require("materialCustody", "查看");
    var rows =
        repository.listRemaining(auth.tenantId()).stream()
            .filter(condition)
            .filter(r -> visible(r, "查看") && (!personal || id(r.get("holderId")) == auth.userId()))
            .filter(
                r ->
                    q.isBlank()
                        || Objects.toString(r.get("materialName"), "").contains(q)
                        || Objects.toString(r.get("materialCode"), "").contains(q))
            .toList();
    return Map.of(
        "data",
        rows.stream()
            .skip((long) (page - 1) * size)
            .limit(size)
            .map(ExperienceSupport::view)
            .toList(),
        "total",
        rows.size(),
        "url",
        "/material-custody");
  }

  @Transactional
  public Object save(long allocation, Map<String, Object> body) {
    mutex.enter();
    var rows = repository.lockAllocation(auth.tenantId(), allocation);
    check(rows.size() == 1, "余料不存在或已全部处理");
    var r = rows.getFirst();
    check(visible(r, "登记暂存") || id(r.get("holderId")) == auth.userId(), "无权登记该余料");
    return write(r, required(body, "remark", 1000), body);
  }

  Object write(Map<String, Object> r, String remark, Map<String, Object> body) {
    long allocation = id(r.get("id"));
    long holder = r.get("holderId") == null ? id(body.get("holderId")) : id(r.get("holderId"));
    check(holder > 0, "请先补登记领用人");
    var users = repository.findHolder(auth.tenantId(), holder);
    check(users.size() == 1, "保管人不存在或已停用");
    long old = id(r.get("rowVersion"));
    if (body.containsKey("rowVersion") && old != id(body.get("rowVersion"))) conflict();
    repository.saveCustody(
        auth.tenantId(),
        allocation,
        holder,
        users.getFirst().get("display_name"),
        remark,
        auth.userId());
    repository.insertEvent(
        IdWorker.getId(),
        auth.tenantId(),
        allocation,
        auth.userId(),
        auth.user().getDisplayName(),
        encode(json, Map.of("remark", remark, "remaining", r.get("remaining").toString())));
    return Map.of("success", true);
  }

  public void finishTask(String kind, long task, Map<String, Object> body) {
    var rows = repository.lockTaskAllocations(auth.tenantId(), kind, task);
    if (rows.isEmpty()) return;
    String remark = required(body, "surplusRemark", 1000);
    for (var r : rows) write(r, remark, body);
  }
}
