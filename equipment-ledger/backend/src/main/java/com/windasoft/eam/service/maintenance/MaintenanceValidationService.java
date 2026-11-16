package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.deny;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.ids;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.rows;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.text;

import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.dto.maintenance.MaintenanceFileReference;
import com.windasoft.eam.dto.maintenance.MaintenancePlanRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceTypeRequest;
import com.windasoft.eam.repository.maintenance.MaintenanceFileRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.system.AuthContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 保养输入约束及人员、附件业务校验；字段约束复用Bean Validation，关联事实从所属仓储读取。 */
@Service
public class MaintenanceValidationService {
  private final SystemUserRepository users;
  private final MaintenanceFileRepository files;
  private final AuthContext auth;
  private final MaintenanceListService lists;
  private final Validator validator;

  public MaintenanceValidationService(
      SystemUserRepository users,
      MaintenanceFileRepository files,
      AuthContext auth,
      MaintenanceListService lists,
      Validator validator) {
    this.users = users;
    this.files = files;
    this.auth = auth;
    this.lists = lists;
    this.validator = validator;
  }

  // 分别验证名称及完整输入，沿用各用例的版本/归属校验顺序，内部调用和HTTP共用DTO字段约束。
  void validateName(MaintenanceTypeRequest value) {
    reject(validator.validateProperty(value, "name"));
  }

  void validateName(MaintenanceStandardRequest value) {
    reject(validator.validateProperty(value, "name"));
  }

  void validate(MaintenanceTypeRequest value) {
    reject(validator.validate(value));
  }

  void validate(MaintenanceStandardRequest value) {
    reject(validator.validate(value));
  }

  void validateName(MaintenancePlanRequest value) {
    reject(validator.validateProperty(value, "name"));
  }

  void validate(MaintenancePlanRequest value) {
    reject(validator.validate(value));
  }

  private void reject(Set<? extends ConstraintViolation<?>> violations) {
    violations.stream()
        .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
        .findFirst()
        .ifPresent(
            v -> {
              throw new FieldValidationException(
                  v.getPropertyPath().toString(), MessageContext.annotation(v.getMessage()));
            });
  }

  /** 新选人员必须仍可用；历史姓名查询不能替代此处的启用、未删除约束。 */
  void validUsers(Object value, boolean required) {
    validateUserIds(ids(value), required);
  }

  /** 类型化人员逐项校验；不能过滤坏ID后，又把原始坏关系写入人员表。旧输入格式仅由上方兼容入口转换。 */
  void validUsers(List<Long> value, boolean required) {
    var selected = value == null ? List.<Long>of() : value;
    require(
        selected.stream().allMatch(id -> id != null && id > 0),
        MessageContext.text("server.maintenance.message0013"));
    validateUserIds(selected.stream().distinct().toList(), required);
  }

  private void validateUserIds(List<Long> selected, boolean required) {
    require(!required || !selected.isEmpty(), MessageContext.text("ui.equipment.text0336"));
    for (long id : selected) {
      require(
          users.activeUser(auth.tenantId(), id) != null,
          MessageContext.text("server.maintenance.message0013"));
    }
  }

  /**
   * 关联写入前，文件必须由本人上传或已在可查看的保养单据中。否则仅凭同租户 UUID 就能把私有文件挂到自己的单据并获得下载权。先校验存在性和图片格式，再检查引用权限； 兼容旧请求省略
   * ID，但显式 ID 必须与下载 URL 一致。
   */
  void validAttachments(Object value, boolean imagesOnly) {
    for (var file : rows(value)) {
      validateAttachment(text(file, "id"), text(file, "url"), imagesOnly);
    }
  }

  void validFileReferences(List<MaintenanceFileReference> value, boolean imagesOnly) {
    if (value == null) {
      return;
    }
    for (var file : value) {
      validateAttachment(
          Objects.toString(file.id(), "").trim(),
          Objects.toString(file.url(), "").trim(),
          imagesOnly);
    }
  }

  private void validateAttachment(String suppliedId, String url, boolean imagesOnly) {
    require(
        url.matches("/api/v1/maintenance/files/[0-9a-f-]{36}"),
        MessageContext.text("server.inspection.message0075"));
    String id = url.substring(url.lastIndexOf('/') + 1);
    var file = files.find(auth.tenantId(), id);
    require(file != null, MessageContext.text("server.repair.message0011"));
    if (imagesOnly) {
      require(
          files.exists(auth.tenantId(), id, true),
          MessageContext.text("server.maintenance.message0014"));
    }
    require(
        suppliedId.isEmpty() || id.equals(suppliedId),
        MessageContext.text("server.inspection.message0075"));
    if (!Objects.equals(file.getUserId(), auth.userId()) && !lists.hasFile(id)) {
      deny();
    }
  }
}
