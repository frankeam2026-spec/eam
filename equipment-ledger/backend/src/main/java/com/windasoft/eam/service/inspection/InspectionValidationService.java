package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

/** 点巡检定义与任务执行共用的输入校验规则。 */
@Service
public class InspectionValidationService {
  private final InspectionRepository repository;
  private final InspectionProjectService projectSource;
  private final InspectionViewAssembler views;
  private final AuthContext auth;
  private final PermissionService permissions;

  public InspectionValidationService(
      InspectionRepository repository,
      InspectionProjectService projectSource,
      InspectionViewAssembler views,
      AuthContext auth,
      PermissionService permissions) {
    this.repository = repository;
    this.projectSource = projectSource;
    this.views = views;
    this.auth = auth;
    this.permissions = permissions;
  }

  Map<String, Object> cycleType(long tenant, Object id) {
    var type = views.definition(projectSource.raw("types", tenant, number(id), true));
    require(
        yes(type.get("enabled")),
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0042"));
    return type;
  }

  void checkEquipment(Map<String, Object> row, String kind, String action) {
    var scope = permissions.inspection(kind, action);
    if (!scope.matches(
        nullable(row.get("company_org_id")),
        nullable(row.get("department_org_id")),
        nullable(row.get("owner_user_id")),
        auth.userId())) deny();
  }

  void users(long tenant, Object value, boolean required) {
    var selected = ids(value);
    require(
        !required || !selected.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("ui.equipment.text0336"));
    for (var id : selected)
      require(
          repository.countActiveUser(tenant, id) > 0,
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0074"));
  }

  void attachments(long tenant, Object value, boolean images) {
    for (var a : rows(value)) {
      String url = text(a, "url");
      require(
          url.matches("/api/v1/inspection/files/[0-9a-f-]{36}"),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0075"));
      String id = url.substring(url.lastIndexOf('/') + 1);
      require(repository.countAttachment(tenant, id, images) > 0, images ? "请上传图片" : "附件不存在");
    }
  }

  void validateItems(long tenant, List<Map<String, Object>> items) {
    require(
        !items.isEmpty() && items.size() <= 1000,
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0044"));
    var seen = new HashSet<String>();
    for (var item : items) {
      item.remove("module");
      if (text(item, "id").isBlank()) item.put("id", Long.toString(IdWorker.getId()));
      if (text(item, "code").isBlank()) item.put("code", "II-" + IdWorker.getId());
      require(
          seen.add(text(item, "id")),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0045"));
      require(
          !text(item, "name").isBlank() && !text(item, "content").isBlank(),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0046"));
      var itemType = cycleType(tenant, item.get("typeId"));
      item.put("typeId", itemType.get("id"));
      item.put("typeName", itemType.get("name"));
      String type = text(item, "valueType");
      require(
          Set.of("选项", "数值", "文本").contains(type),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0047"));
      if (type.equals("选项")) {
        var options = strings(item.get("options"));
        var normals = strings(item.get("normalOptions"));
        require(
            options.size() >= 2 && !normals.isEmpty() && options.containsAll(normals),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0048"));
      }
      if (type.equals("数值")) {
        BigDecimal
            lower = text(item, "lower").isBlank() ? null : new BigDecimal(text(item, "lower")),
            upper = text(item, "upper").isBlank() ? null : new BigDecimal(text(item, "upper"));
        require(
            lower == null || upper == null || lower.compareTo(upper) <= 0,
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0049"));
      }
      attachments(tenant, item.get("references"), false);
    }
  }
}
